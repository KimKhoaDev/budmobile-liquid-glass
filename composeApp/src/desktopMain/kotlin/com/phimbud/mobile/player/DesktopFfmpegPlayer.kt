package com.phimbud.mobile.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.phimbud.mobile.core.platform.logError
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegLogCallback
import org.bytedeco.javacv.FrameGrabber
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.swing.SwingUtilities

internal class DesktopFfmpegPlayer(
    private val onFrameRendered: (ImageBitmap) -> Unit,
    private val onStateChanged: (playing: Boolean, buffering: Boolean) -> Unit,
    private val onDurationKnown: (Long) -> Unit,
    private val onPositionUpdated: (Long) -> Unit,
    private val onPlaybackEnded: () -> Unit,
    private val onPlaybackError: (String) -> Unit,
    private val onBottomLuminanceChanged: (Float) -> Unit = {},
    private val onVideoDimensionsKnown: (width: Int, height: Int) -> Unit = { _, _ -> }
) {
    companion object {
        /**
         * Compensates downstream audio output latency beyond Java Sound's DirectSound ring buffer
         * (DirectSound -> Windows WASAPI audiodg.exe shared-mode session + audio endpoint FIFO).
         */
        private const val WINDOWS_AUDIO_PIPELINE_LATENCY_US = 85_000L

        init {
            runCatching {
                FFmpegLogCallback.set()
                avutil.av_log_set_level(avutil.AV_LOG_ERROR)
            }
        }

        /**
         * Extracts the container's initial timestamp offset in microseconds (`AVFormatContext.start_time()`).
         * JavaCV's `FFmpegFrameGrabber.grab()` reports raw un-normalized stream PTS (`pts + start_time`),
         * whereas `setTimestamp()` and `.srt` subtitle files use 0-based media timestamps.
         * In MPEG-TS / HLS streams, `start_time` is typically +1.4s to +10.0s.
         */
        internal fun resolveStreamStartOffsetUs(g: FFmpegFrameGrabber): Long {
            val oc = g.formatContext ?: return 0L
            val containerStart = oc.start_time()
            if (containerStart != avutil.AV_NOPTS_VALUE) {
                return containerStart
            }
            var minStreamStartUs = Long.MAX_VALUE
            val nbStreams = oc.nb_streams()
            for (i in 0 until nbStreams) {
                val st = oc.streams(i) ?: continue
                val stStart = st.start_time()
                val tb = st.time_base()
                if (stStart != avutil.AV_NOPTS_VALUE && tb != null && tb.den() > 0) {
                    val startUs = (stStart * 1_000_000L * tb.num().toLong()) / tb.den().toLong()
                    if (startUs < minStreamStartUs) {
                        minStreamStartUs = startUs
                    }
                }
            }
            return if (minStreamStartUs != Long.MAX_VALUE) minStreamStartUs else 0L
        }

        /**
         * Samples a sparse 24x6 grid in the subtitle safe zone (x: 22%..78%, y: 78%..93%)
         * from a BGRA_8888 frame buffer and computes normalized Rec.709 luminance in [0f..1f].
         */
        internal fun sampleBottomBandLuminance(bgraBytes: ByteArray, width: Int, height: Int): Float {
            if (width <= 8 || height <= 8 || bgraBytes.size < width * height * 4) {
                return 0.25f
            }
            val cols = 24
            val rows = 6
            val xStart = (width * 0.22f).toInt().coerceIn(0, width - 1)
            val xEnd = (width * 0.78f).toInt().coerceIn(xStart + 1, width)
            val yStart = (height * 0.78f).toInt().coerceIn(0, height - 1)
            val yEnd = (height * 0.93f).toInt().coerceIn(yStart + 1, height)

            val xSpan = (xEnd - xStart).coerceAtLeast(1)
            val ySpan = (yEnd - yStart).coerceAtLeast(1)

            var lumaSum = 0L
            var count = 0
            for (r in 0 until rows) {
                val y = (yStart + (r * ySpan) / rows).coerceIn(0, height - 1)
                val rowByteBase = y * width * 4
                for (c in 0 until cols) {
                    val x = (xStart + (c * xSpan) / cols).coerceIn(0, width - 1)
                    val pxOffset = rowByteBase + x * 4
                    if (pxOffset + 2 < bgraBytes.size) {
                        val b = bgraBytes[pxOffset].toInt() and 0xFF
                        val g = bgraBytes[pxOffset + 1].toInt() and 0xFF
                        val red = bgraBytes[pxOffset + 2].toInt() and 0xFF
                        lumaSum += ((54 * red + 183 * g + 19 * b) ushr 8).toLong()
                        count++
                    }
                }
            }
            if (count == 0) return 0.25f
            return ((lumaSum.toFloat() / count.toFloat()) / 255f).coerceIn(0f, 1f)
        }
    }

    @Volatile
    private var activeSession: PlaybackSession? = null

    // Triple Buffering for Skia Direct3D GPU presentation (prevents tearing / GPU-CPU stalls)
    private var bitmapA: Bitmap? = null
    private var bitmapB: Bitmap? = null
    private var bitmapC: Bitmap? = null
    private var bitmapWidth: Int = 0
    private var bitmapHeight: Int = 0
    private var bitmapRingIndex: Int = 0

    private class PendingFrame(
        val session: PlaybackSession,
        val width: Int,
        val height: Int,
        val bgraBytes: ByteArray,
        val recycleBuffer: (ByteArray) -> Unit
    )

    private val pendingFrameRef = AtomicReference<PendingFrame?>(null)
    private val isFrameScheduledOnEdt = AtomicBoolean(false)

    fun openStream(
        localPlaylistUrl: String,
        initialResumeMs: Long,
        autoPlay: Boolean
    ) {
        val previous = activeSession
        activeSession = null
        val dropped = pendingFrameRef.getAndSet(null)
        dropped?.let { it.recycleBuffer(it.bgraBytes) }
        previous?.stopAsync()

        val session = PlaybackSession(
            url = localPlaylistUrl,
            initialResumeMs = initialResumeMs.coerceAtLeast(0L),
            initialWantPlay = autoPlay
        )
        activeSession = session
        session.start()
    }

    fun play() {
        activeSession?.setWantToPlay(true)
    }

    fun pause() {
        activeSession?.setWantToPlay(false)
    }

    fun seekTo(positionMs: Long) {
        val dropped = pendingFrameRef.getAndSet(null)
        dropped?.let { it.recycleBuffer(it.bgraBytes) }
        activeSession?.requestSeek(positionMs.coerceAtLeast(0L))
    }

    fun release() {
        val previous = activeSession
        activeSession = null
        val dropped = pendingFrameRef.getAndSet(null)
        dropped?.let { it.recycleBuffer(it.bgraBytes) }
        previous?.stopAsync()
        SwingUtilities.invokeLater {
            runCatching { bitmapA?.close() }
            runCatching { bitmapB?.close() }
            runCatching { bitmapC?.close() }
            bitmapA = null
            bitmapB = null
            bitmapC = null
            bitmapWidth = 0
            bitmapHeight = 0
            bitmapRingIndex = 0
        }
    }

    private fun publishFrameOnEdt(
        session: PlaybackSession,
        width: Int,
        height: Int,
        bgraBytes: ByteArray,
        recycleBuffer: (ByteArray) -> Unit
    ) {
        if (activeSession !== session || width <= 0 || height <= 0) {
            recycleBuffer(bgraBytes)
            return
        }

        val newPending = PendingFrame(session, width, height, bgraBytes, recycleBuffer)
        val oldPending = pendingFrameRef.getAndSet(newPending)
        if (oldPending != null) {
            // Drop stale frame immediately to prevent EDT frame bunching / queuing
            oldPending.recycleBuffer(oldPending.bgraBytes)
        }

        if (isFrameScheduledOnEdt.compareAndSet(false, true)) {
            SwingUtilities.invokeLater {
                isFrameScheduledOnEdt.set(false)
                val frameToRender = pendingFrameRef.getAndSet(null) ?: return@invokeLater
                try {
                    if (activeSession !== frameToRender.session) {
                        return@invokeLater
                    }
                    val w = frameToRender.width
                    val h = frameToRender.height
                    val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
                    if (bitmapWidth != w || bitmapHeight != h || bitmapA == null || bitmapB == null || bitmapC == null) {
                        runCatching { bitmapA?.close() }
                        runCatching { bitmapB?.close() }
                        runCatching { bitmapC?.close() }
                        bitmapA = Bitmap().apply { allocPixels(info) }
                        bitmapB = Bitmap().apply { allocPixels(info) }
                        bitmapC = Bitmap().apply { allocPixels(info) }
                        bitmapWidth = w
                        bitmapHeight = h
                        bitmapRingIndex = 0
                    }
                    val target = when (bitmapRingIndex % 3) {
                        0 -> bitmapA!!
                        1 -> bitmapB!!
                        else -> bitmapC!!
                    }
                    bitmapRingIndex = (bitmapRingIndex + 1) % 3
                    target.installPixels(info, frameToRender.bgraBytes, w * 4)
                    onFrameRendered(target.asComposeImageBitmap())
                } finally {
                    frameToRender.recycleBuffer(frameToRender.bgraBytes)
                }
            }
        }
    }

    private inner class VideoPacket(
        val width: Int,
        val height: Int,
        val bgraBytes: ByteArray,
        val ptsUs: Long,
        val generation: Int
    )

    private inner class AudioPacket(
        val pcmBytes: ByteArray,
        val length: Int,
        val ptsUs: Long,
        val durationUs: Long,
        val generation: Int
    )

    private inner class PlaybackSession(
        private val url: String,
        private val initialResumeMs: Long,
        initialWantPlay: Boolean
    ) {
        private val stopped = AtomicBoolean(false)
        private val wantToPlay = AtomicBoolean(initialWantPlay)
        private val requestedSeekUs = AtomicLong(-1L)
        private val seekGeneration = AtomicInteger(0)
        private val demuxReachedEof = AtomicBoolean(false)

        private val audioClockUs = AtomicLong(initialResumeMs * 1000L)
        private val hasAudioStream = AtomicBoolean(false)

        private val videoQueue = ArrayBlockingQueue<VideoPacket>(48)
        private val audioQueue = ArrayBlockingQueue<AudioPacket>(64)
        private val byteArrayPool = ArrayBlockingQueue<ByteArray>(56)
        private var reportedWidth = -1
        private var reportedHeight = -1
        private var lastNotifiedPositionMs = -1L

        @Volatile
        private var soundLine: SourceDataLine? = null

        @Volatile
        private var audioBytesPerSecond: Long = 0L

        private var demuxThread: Thread? = null
        private var audioThread: Thread? = null
        private var videoThread: Thread? = null

        fun start() {
            notifyState(playing = wantToPlay.get(), buffering = true)

            demuxThread = Thread({ runDemuxLoop() }, "PhimBud-FFmpeg-Demux").apply {
                isDaemon = true
                start()
            }
            audioThread = Thread({ runAudioLoop() }, "PhimBud-FFmpeg-Audio").apply {
                isDaemon = true
                start()
            }
            videoThread = Thread({ runVideoLoop() }, "PhimBud-FFmpeg-Video").apply {
                isDaemon = true
                start()
            }
        }

        fun setWantToPlay(play: Boolean) {
            if (stopped.get()) return
            wantToPlay.set(play)
            val line = soundLine
            if (line != null) {
                runCatching {
                    if (play) line.start() else line.stop()
                }
            }
            notifyState(playing = play, buffering = false)
        }

        fun requestSeek(positionMs: Long) {
            if (stopped.get()) return
            val targetUs = positionMs * 1000L
            seekGeneration.incrementAndGet()
            requestedSeekUs.set(targetUs)
            demuxReachedEof.set(false)
            audioClockUs.set(targetUs)
            clearQueuesAndRecycle()
            runCatching { soundLine?.flush() }
            notifyPosition(positionMs, force = true)
            notifyState(playing = wantToPlay.get(), buffering = true)
        }

        fun stopAsync() {
            if (!stopped.compareAndSet(false, true)) return
            clearQueuesAndRecycle()
            demuxThread?.interrupt()
            audioThread?.interrupt()
            videoThread?.interrupt()
            Thread({
                runCatching {
                    soundLine?.stop()
                    soundLine?.flush()
                    soundLine?.close()
                }
                soundLine = null
            }, "PhimBud-FFmpeg-Cleanup").apply {
                isDaemon = true
                start()
            }
        }

        private fun clearQueuesAndRecycle() {
            val oldPending = pendingFrameRef.getAndSet(null)
            if (oldPending != null) {
                oldPending.recycleBuffer(oldPending.bgraBytes)
            }
            val drainedVideo = ArrayList<VideoPacket>()
            videoQueue.drainTo(drainedVideo)
            for (vp in drainedVideo) {
                recycleBytes(vp.bgraBytes)
            }
            audioQueue.clear()
        }

        private fun borrowBytes(requiredSize: Int): ByteArray {
            val pooled = byteArrayPool.poll()
            if (pooled != null && pooled.size == requiredSize) {
                return pooled
            }
            return ByteArray(requiredSize)
        }

        private fun recycleBytes(bytes: ByteArray) {
            if (!stopped.get()) {
                byteArrayPool.offer(bytes)
            }
        }

        private fun currentMasterClockUs(): Long {
            val baseUs = audioClockUs.get()
            val line = soundLine
            val bps = audioBytesPerSecond
            if (hasAudioStream.get() && line != null && bps > 0L) {
                val bufferedBytes = (line.bufferSize - line.available()).coerceAtLeast(0)
                val bufferedUs = (bufferedBytes.toLong() * 1_000_000L) / bps
                val pipelineCompensationUs = if (line.isRunning && bufferedBytes > 0) {
                    WINDOWS_AUDIO_PIPELINE_LATENCY_US
                } else {
                    0L
                }
                return (baseUs - bufferedUs - pipelineCompensationUs).coerceAtLeast(0L)
            }
            return baseUs
        }

        private fun seekGrabberNormalized(
            g: FFmpegFrameGrabber,
            targetZeroBasedUs: Long,
            streamStartOffsetUs: Long
        ) {
            val oc = g.formatContext
            val grabberTargetUs = if (oc != null && oc.start_time() == avutil.AV_NOPTS_VALUE && streamStartOffsetUs != 0L) {
                (targetZeroBasedUs + streamStartOffsetUs).coerceAtLeast(0L)
            } else {
                targetZeroBasedUs.coerceAtLeast(0L)
            }
            runCatching { g.setTimestamp(grabberTargetUs, false) }
                .recoverCatching { g.setTimestamp(grabberTargetUs) }
        }

        private fun runDemuxLoop() {
            var grabber: FFmpegFrameGrabber? = null
            try {
                val g = FFmpegFrameGrabber(url)
                grabber = g
                g.setOption("protocol_whitelist", "file,http,https,tcp,tls,crypto,data")
                g.setOption("reconnect", "1")
                g.setOption("reconnect_streamed", "1")
                g.setOption("reconnect_delay_max", "5")
                g.setOption("rw_timeout", "15000000")
                g.setOption("fflags", "+genpts+discardcorrupt")
                g.setOption("probesize", "1048576")
                g.setOption("analyzeduration", "2000000")
                g.setOption("buffer_size", "2097152")

                // Parallel multi-core CPU decoding for H.264/HEVC (frees up GPU for Direct3D rendering)
                g.setVideoOption("threads", "auto")
                g.setVideoOption("thread_type", "slice+frame")

                g.imageMode = FrameGrabber.ImageMode.COLOR
                g.pixelFormat = avutil.AV_PIX_FMT_BGRA
                g.sampleMode = FrameGrabber.SampleMode.SHORT
                g.sampleFormat = avutil.AV_SAMPLE_FMT_S16
                // Downmix multi-channel surround (5.1/7.1) to stereo automatically via libswresample
                g.audioChannels = 2

                g.start()
                if (stopped.get()) return

                val initW = g.imageWidth
                val initH = g.imageHeight
                if (initW > 0 && initH > 0) {
                    notifyDimensions(initW, initH)
                }

                val streamStartOffsetUs = resolveStreamStartOffsetUs(g)

                val durUs = g.lengthInTime
                if (durUs > 0L) {
                    notifyDuration(durUs / 1000L)
                }

                // Open Java Sound output line if stream contains audio
                val sampleRate = g.sampleRate
                val channels = g.audioChannels.coerceIn(1, 2)
                if (sampleRate > 0 && g.hasAudio()) {
                    runCatching {
                        val format = AudioFormat(
                            sampleRate.toFloat(),
                            16,
                            channels,
                            /* signed = */ true,
                            /* bigEndian = */ false
                        )
                        val info = DataLine.Info(SourceDataLine::class.java, format)
                        val line = AudioSystem.getLine(info) as SourceDataLine
                        // ~250ms hardware buffer for smooth jitter-free playback and fast pause/seek
                        val bufferBytes = (sampleRate * channels * 2 / 4).coerceAtLeast(8192)
                        line.open(format, bufferBytes)
                        if (wantToPlay.get()) {
                            line.start()
                        }
                        soundLine = line
                        audioBytesPerSecond = (sampleRate * channels * 2).toLong()
                        hasAudioStream.set(true)
                    }.onFailure { audioErr ->
                        logError("DesktopFfmpegPlayer", "Audio line unavailable, playing video silently", audioErr)
                        hasAudioStream.set(false)
                    }
                }

                if (initialResumeMs > 1_000L) {
                    val resumeUs = initialResumeMs * 1000L
                    seekGrabberNormalized(g, resumeUs, streamStartOffsetUs)
                    audioClockUs.set(resumeUs)
                }

                val defaultFrameDurUs = if (g.frameRate > 1.0 && g.frameRate.isFinite()) {
                    (1_000_000.0 / g.frameRate).toLong().coerceIn(8_000L, 100_000L)
                } else {
                    41_667L
                }

                var firstPacketDelivered = false
                var clockAnchoredForGen = false
                var lastVideoPtsUs = -1L
                var lastAudioEndPtsUs = -1L
                var lastLuminanceSampleNs = 0L
                var smoothedLuminance = 0.25f
                var lastReportedLuminance = -1f

                while (!stopped.get() && !Thread.currentThread().isInterrupted) {
                    val seekTargetUs = requestedSeekUs.getAndSet(-1L)
                    if (seekTargetUs >= 0L) {
                        demuxReachedEof.set(false)
                        clearQueuesAndRecycle()
                        runCatching { soundLine?.flush() }
                        seekGrabberNormalized(g, seekTargetUs, streamStartOffsetUs)
                        audioClockUs.set(seekTargetUs)
                        firstPacketDelivered = false
                        clockAnchoredForGen = false
                        lastVideoPtsUs = -1L
                        lastAudioEndPtsUs = -1L
                        // If user pressed ← / → again while setTimestamp was in flight, jump straight to newest target!
                        if (requestedSeekUs.get() >= 0L) {
                            continue
                        }
                    }

                    val genBeforeGrab = seekGeneration.get()
                    val frame = g.grab()

                    if (stopped.get()) break

                    if (requestedSeekUs.get() >= 0L || genBeforeGrab != seekGeneration.get()) {
                        continue
                    }

                    if (frame == null) {
                        demuxReachedEof.set(true)
                        Thread.sleep(40L)
                        if (requestedSeekUs.get() < 0L && videoQueue.isEmpty() && audioQueue.isEmpty()) {
                            // Wait briefly for hardware audio buffer to finish draining, then signal EOF
                            runCatching { soundLine?.drain() }
                            if (!stopped.get() && requestedSeekUs.get() < 0L) {
                                wantToPlay.set(false)
                                notifyEnded()
                                // Stay alive in loop in case user clicks Replay or seeks back!
                                while (!stopped.get() && requestedSeekUs.get() < 0L && !wantToPlay.get()) {
                                    Thread.sleep(50L)
                                }
                            }
                        }
                        continue
                    }

                    val rawTsUs = frame.timestamp

                    if (frame.image != null && frame.image.isNotEmpty()) {
                        val ptsUs = if (rawTsUs == 0L && streamStartOffsetUs > 0L && lastVideoPtsUs >= 0L) {
                            lastVideoPtsUs + defaultFrameDurUs
                        } else {
                            (rawTsUs - streamStartOffsetUs).coerceAtLeast(0L)
                        }
                        lastVideoPtsUs = ptsUs

                        val srcBuf = frame.image[0] as? ByteBuffer
                        val w = frame.imageWidth
                        val h = frame.imageHeight
                        val stride = frame.imageStride
                        if (srcBuf != null && w > 0 && h > 0) {
                            notifyDimensions(w, h)
                            val rowBytes = w * 4
                            val totalBytes = rowBytes * h
                            val dst = borrowBytes(totalBytes)
                            val dup = srcBuf.duplicate()
                            dup.clear()

                            var copiedOk = false
                            if (stride == rowBytes && dup.capacity() >= totalBytes) {
                                dup.limit(totalBytes)
                                dup.get(dst, 0, totalBytes)
                                copiedOk = true
                            } else if (stride >= rowBytes) {
                                copiedOk = runCatching {
                                    for (row in 0 until h) {
                                        val rowStart = row * stride
                                        dup.limit(rowStart + rowBytes)
                                        dup.position(rowStart)
                                        dup.get(dst, row * rowBytes, rowBytes)
                                    }
                                    true
                                }.getOrDefault(false)
                            }

                            if (copiedOk) {
                                val nowNs = System.nanoTime()
                                if (nowNs - lastLuminanceSampleNs >= 250_000_000L) {
                                    lastLuminanceSampleNs = nowNs
                                    val measured = sampleBottomBandLuminance(dst, w, h)
                                    smoothedLuminance = if (lastReportedLuminance < 0f) {
                                        measured
                                    } else {
                                        smoothedLuminance * 0.65f + measured * 0.35f
                                    }
                                    val quantized = (kotlin.math.round(smoothedLuminance * 20f) / 20f).coerceIn(0f, 1f)
                                    if (kotlin.math.abs(quantized - lastReportedLuminance) >= 0.05f) {
                                        lastReportedLuminance = quantized
                                        notifyLuminance(quantized)
                                    }
                                }

                                if (!hasAudioStream.get() && !clockAnchoredForGen && genBeforeGrab == seekGeneration.get()) {
                                    clockAnchoredForGen = true
                                    audioClockUs.set(ptsUs)
                                    notifyPosition(ptsUs / 1000L, force = true)
                                }

                                val vp = VideoPacket(w, h, dst, ptsUs, genBeforeGrab)
                                while (!stopped.get() && genBeforeGrab == seekGeneration.get()) {
                                    if (videoQueue.offer(vp, 40L, TimeUnit.MILLISECONDS)) {
                                        break
                                    }
                                }
                                if (stopped.get() || genBeforeGrab != seekGeneration.get()) {
                                    recycleBytes(dst)
                                } else if (!firstPacketDelivered) {
                                    firstPacketDelivered = true
                                    notifyState(playing = wantToPlay.get(), buffering = false)
                                }
                            } else {
                                recycleBytes(dst)
                            }
                        }
                    } else if (frame.samples != null && frame.samples.isNotEmpty() && hasAudioStream.get()) {
                        val shortBuf = frame.samples[0] as? ShortBuffer
                        if (shortBuf != null) {
                            val dup = shortBuf.duplicate()
                            val sampleCount = dup.remaining()
                            if (sampleCount > 0) {
                                val pcmLen = sampleCount * 2
                                val pcm = ByteArray(pcmLen)
                                var byteIdx = 0
                                while (dup.hasRemaining()) {
                                    val s = dup.get().toInt()
                                    pcm[byteIdx++] = (s and 0xFF).toByte()
                                    pcm[byteIdx++] = ((s shr 8) and 0xFF).toByte()
                                }
                                val bps = audioBytesPerSecond.coerceAtLeast(1L)
                                val packetDurUs = (pcmLen.toLong() * 1_000_000L) / bps
                                val ptsUs = if (rawTsUs == 0L && streamStartOffsetUs > 0L && lastAudioEndPtsUs >= 0L) {
                                    lastAudioEndPtsUs
                                } else {
                                    (rawTsUs - streamStartOffsetUs).coerceAtLeast(0L)
                                }
                                lastAudioEndPtsUs = ptsUs + packetDurUs

                                if (!clockAnchoredForGen && genBeforeGrab == seekGeneration.get()) {
                                    clockAnchoredForGen = true
                                    audioClockUs.set(ptsUs)
                                    notifyPosition(ptsUs / 1000L, force = true)
                                }

                                val ap = AudioPacket(pcm, pcmLen, ptsUs, packetDurUs, genBeforeGrab)
                                while (!stopped.get() && genBeforeGrab == seekGeneration.get()) {
                                    if (audioQueue.offer(ap, 40L, TimeUnit.MILLISECONDS)) {
                                        break
                                    }
                                }
                                if (!firstPacketDelivered && genBeforeGrab == seekGeneration.get()) {
                                    firstPacketDelivered = true
                                    notifyState(playing = wantToPlay.get(), buffering = false)
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                if (!stopped.get() && t !is InterruptedException) {
                    logError("DesktopFfmpegPlayer", "HLS FFmpeg stream error", t)
                    notifyError(
                        "Lỗi luồng phát video (${t.message ?: "FFmpeg decode error"}). Bạn có thể thử lại hoặc đổi Server khác."
                    )
                }
            } finally {
                runCatching { grabber?.stop() }
                runCatching { grabber?.release() }
            }
        }

        private fun runAudioLoop() {
            try {
                while (!stopped.get() && !Thread.currentThread().isInterrupted) {
                    if (!wantToPlay.get()) {
                        Thread.sleep(20L)
                        continue
                    }
                    val packet = audioQueue.poll(30L, TimeUnit.MILLISECONDS) ?: continue
                    if (packet.generation != seekGeneration.get()) {
                        continue
                    }
                    val bps = audioBytesPerSecond.coerceAtLeast(1L)
                    val line = soundLine
                    if (line != null && line.isOpen) {
                        var offset = 0
                        while (offset < packet.length && !stopped.get() && packet.generation == seekGeneration.get()) {
                            if (!wantToPlay.get()) {
                                Thread.sleep(15L)
                                continue
                            }
                            val chunk = minOf(4096, packet.length - offset)
                            val written = line.write(packet.pcmBytes, offset, chunk)
                            if (written <= 0) {
                                Thread.sleep(5L)
                            } else {
                                offset += written
                                if (packet.generation == seekGeneration.get()) {
                                    val writtenUs = (offset.toLong() * 1_000_000L) / bps
                                    audioClockUs.set(packet.ptsUs + writtenUs)
                                }
                            }
                        }
                    }
                    if (packet.generation == seekGeneration.get()) {
                        audioClockUs.set(packet.ptsUs + packet.durationUs)
                        notifyPosition(currentMasterClockUs() / 1000L, force = false)
                    }
                }
            } catch (_: InterruptedException) {
                // Normal shutdown
            } catch (t: Throwable) {
                if (!stopped.get()) {
                    logError("DesktopFfmpegPlayer", "Audio playback loop error", t)
                }
            }
        }

        private fun runVideoLoop() {
            var wallClockAnchorNs = 0L
            var ptsAnchorUs = 0L
            var lastWallGen = -1

            try {
                while (!stopped.get() && !Thread.currentThread().isInterrupted) {
                    if (!wantToPlay.get()) {
                        wallClockAnchorNs = 0L
                        Thread.sleep(20L)
                        continue
                    }

                    val vp = videoQueue.poll(30L, TimeUnit.MILLISECONDS)
                    if (vp == null) {
                        if (!demuxReachedEof.get() && wantToPlay.get() && audioQueue.isEmpty()) {
                            notifyState(playing = true, buffering = true)
                        }
                        continue
                    }

                    if (vp.generation != seekGeneration.get()) {
                        recycleBytes(vp.bgraBytes)
                        continue
                    }

                    notifyState(playing = wantToPlay.get(), buffering = false)

                    if (hasAudioStream.get() && soundLine != null) {
                        while (!stopped.get() && wantToPlay.get() && vp.generation == seekGeneration.get()) {
                            // Safety valve: never stall demuxer if videoQueue is full while audioQueue awaits interleaved packets
                            if (videoQueue.remainingCapacity() == 0 && audioQueue.isEmpty()) {
                                break
                            }
                            val masterUs = currentMasterClockUs()
                            val diffUs = vp.ptsUs - masterUs
                            // Within 6ms, release frame immediately for EDT dispatch + Skia Direct3D GPU presentation
                            if (diffUs <= 6_000L) {
                                break
                            }
                            if (diffUs > 12_000L) {
                                // Coarse sleep with 6ms headroom to never overshoot due to Windows 15.6ms timer quantum
                                val sleepMs = ((diffUs - 6_000L) / 1000L).coerceIn(1L, 20L)
                                Thread.sleep(sleepMs)
                            } else {
                                // Fine-grained sub-millisecond park without Windows 15.6ms sleep rounding
                                LockSupport.parkNanos(1_000_000L)
                            }
                        }
                        // Drop severely late non-final frames (> 120ms behind audio) to catch up cleanly
                        val lateByUs = currentMasterClockUs() - vp.ptsUs
                        if (lateByUs > 120_000L && videoQueue.size > 2) {
                            recycleBytes(vp.bgraBytes)
                            continue
                        }
                    } else {
                        // Silent stream fallback: pace against monotonic System.nanoTime()
                        if (wallClockAnchorNs == 0L || lastWallGen != vp.generation) {
                            wallClockAnchorNs = System.nanoTime()
                            ptsAnchorUs = vp.ptsUs
                            lastWallGen = vp.generation
                        } else {
                            while (!stopped.get() && wantToPlay.get() && vp.generation == seekGeneration.get()) {
                                val elapsedUs = (System.nanoTime() - wallClockAnchorNs) / 1000L
                                val targetUs = ptsAnchorUs + elapsedUs
                                val diffUs = vp.ptsUs - targetUs
                                if (diffUs <= 6_000L) break
                                if (diffUs > 12_000L) {
                                    val sleepMs = ((diffUs - 6_000L) / 1000L).coerceIn(1L, 20L)
                                    Thread.sleep(sleepMs)
                                } else {
                                    LockSupport.parkNanos(1_000_000L)
                                }
                            }
                        }
                        audioClockUs.set(vp.ptsUs)
                        notifyPosition(vp.ptsUs / 1000L, force = false)
                    }

                    if (stopped.get() || vp.generation != seekGeneration.get()) {
                        recycleBytes(vp.bgraBytes)
                        continue
                    }

                    publishFrameOnEdt(
                        session = this,
                        width = vp.width,
                        height = vp.height,
                        bgraBytes = vp.bgraBytes,
                        recycleBuffer = ::recycleBytes
                    )
                }
            } catch (_: InterruptedException) {
                // Normal shutdown
            } catch (t: Throwable) {
                if (!stopped.get()) {
                    logError("DesktopFfmpegPlayer", "Video presenter loop error", t)
                }
            }
        }

        private fun notifyState(playing: Boolean, buffering: Boolean) {
            val self = this
            SwingUtilities.invokeLater {
                if (activeSession === self && !stopped.get()) {
                    onStateChanged(playing, buffering)
                }
            }
        }

        private fun notifyDuration(durMs: Long) {
            val self = this
            SwingUtilities.invokeLater {
                if (activeSession === self && !stopped.get()) {
                    onDurationKnown(durMs)
                }
            }
        }

        private fun notifyPosition(posMs: Long, force: Boolean = false) {
            val self = this
            if (!force && kotlin.math.abs(posMs - lastNotifiedPositionMs) < 120L) {
                return
            }
            lastNotifiedPositionMs = posMs
            SwingUtilities.invokeLater {
                if (activeSession === self && !stopped.get()) {
                    onPositionUpdated(posMs)
                }
            }
        }

        private fun notifyDimensions(w: Int, h: Int) {
            if (w <= 0 || h <= 0 || (w == reportedWidth && h == reportedHeight)) return
            reportedWidth = w
            reportedHeight = h
            val self = this
            SwingUtilities.invokeLater {
                if (activeSession === self && !stopped.get()) {
                    onVideoDimensionsKnown(w, h)
                }
            }
        }

        private fun notifyEnded() {
            val self = this
            SwingUtilities.invokeLater {
                if (activeSession === self && !stopped.get()) {
                    onPlaybackEnded()
                }
            }
        }

        private fun notifyError(msg: String) {
            val self = this
            SwingUtilities.invokeLater {
                if (activeSession === self && !stopped.get()) {
                    onPlaybackError(msg)
                }
            }
        }

        private fun notifyLuminance(lum: Float) {
            val self = this
            SwingUtilities.invokeLater {
                if (activeSession === self && !stopped.get()) {
                    onBottomLuminanceChanged(lum)
                }
            }
        }
    }
}
