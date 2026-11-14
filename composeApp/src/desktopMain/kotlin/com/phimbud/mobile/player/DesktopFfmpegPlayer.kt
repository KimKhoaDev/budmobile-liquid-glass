package com.phimbud.mobile.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.phimbud.mobile.core.platform.logError
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.ffmpeg.global.swscale
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegLogCallback
import org.bytedeco.javacv.Frame
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
    private val onVideoDimensionsKnown: (width: Int, height: Int) -> Unit = { _, _ -> },
    private val onFramePresented: (ptsUs: Long, isPreroll: Boolean) -> Unit = { _, _ -> }
) {
    companion object {
        /**
         * Compensates downstream audio output latency beyond Java Sound's DirectSound ring buffer
         * (DirectSound -> Windows WASAPI audiodg.exe shared-mode session + audio endpoint FIFO).
         */
        private const val WINDOWS_AUDIO_PIPELINE_LATENCY_US = 85_000L
        private const val SEEK_AUDIO_LEAD_MARGIN_US = 80_000L
        private const val SEEK_VIDEO_PREROLL_TOLERANCE_US = 50_000L

        private val frameGrabbedField = runCatching {
            FFmpegFrameGrabber::class.java.getDeclaredField("frameGrabbed").apply {
                isAccessible = true
            }
        }.getOrNull()

        private val frameField = runCatching {
            FFmpegFrameGrabber::class.java.getDeclaredField("frame").apply {
                isAccessible = true
            }
        }.getOrNull()

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

    // 4-Slot Ring Buffer for off-EDT pixel installation & tear-free Skia Direct3D GPU presentation.
    // With 4 slots and at most 3 held by {currentEdtSlot, previousEdtSlot, pendingFrameRef},
    // the video presenter thread is guaranteed a lock-free idle slot without blocking the Swing EDT.
    private class BitmapSlot(
        val width: Int,
        val height: Int,
        val info: ImageInfo
    ) {
        val bitmap: Bitmap = Bitmap().apply { allocPixels(info) }
        val composeBitmap: ImageBitmap = bitmap.asComposeImageBitmap()

        fun close() {
            runCatching { bitmap.close() }
        }
    }

    private val slotPoolLock = Any()
    private var bitmapSlots: Array<BitmapSlot>? = null
    private var bitmapWidth: Int = 0
    private var bitmapHeight: Int = 0
    private var bitmapRingIndex: Int = 0

    @Volatile
    private var currentEdtSlot: BitmapSlot? = null

    @Volatile
    private var previousEdtSlot: BitmapSlot? = null

    /**
     * Overrides the 4-argument `grabFrame(doAudio, doVideo, processImage, keyFrames)` invoked internally
     * by `FFmpegFrameGrabber.setTimestamp(long, boolean)`'s `while (this.timestamp < timestamp - 1)` loop
     * so `setTimestamp` executes only `avformat_seek_file` + `avcodec_flush_buffers` and returns in 0ms!
     * All normal demux reads call the 5-argument `grabFrame(doAudio, doVideo, processImage, keyFrames, doData)`.
     */
    private class FastSeekFfmpegGrabber(filename: String) : FFmpegFrameGrabber(filename) {
        override fun grabFrame(
            doAudio: Boolean,
            doVideo: Boolean,
            processImage: Boolean,
            keyFrames: Boolean
        ): Frame? = null

        override fun setTimestamp(timestamp: Long, exact: Boolean) {
            super.setTimestamp(timestamp, exact)
            frameGrabbedField?.setBoolean(this, false)
            (frameField?.get(this) as? Frame)?.let { f ->
                f.image = null
                f.samples = null
                f.data = null
            }
        }

        fun materializeGrabbedVideoFrame(): Frame? {
            frameGrabbedField?.setBoolean(this, true)
            return grabImage()
        }
    }

    private class PendingReadyFrame(
        val session: PlaybackSession,
        val slot: BitmapSlot,
        val ptsUs: Long,
        val generation: Int
    )

    private val pendingFrameRef = AtomicReference<PendingReadyFrame?>(null)
    private val isFrameScheduledOnEdt = AtomicBoolean(false)

    fun openStream(
        localPlaylistUrl: String,
        initialResumeMs: Long,
        autoPlay: Boolean
    ) {
        val previous = activeSession
        activeSession = null
        pendingFrameRef.set(null)
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
        pendingFrameRef.set(null)
        activeSession?.requestSeek(positionMs.coerceAtLeast(0L))
    }

    fun release() {
        val previous = activeSession
        activeSession = null
        pendingFrameRef.set(null)
        previous?.stopAsync()
        SwingUtilities.invokeLater {
            val slotsToClose = synchronized(slotPoolLock) {
                val old = bitmapSlots
                bitmapSlots = null
                bitmapWidth = 0
                bitmapHeight = 0
                bitmapRingIndex = 0
                currentEdtSlot = null
                previousEdtSlot = null
                old
            }
            slotsToClose?.forEach { it.close() }
        }
    }

    private fun publishFrameOnEdt(
        session: PlaybackSession,
        width: Int,
        height: Int,
        bgraBytes: ByteArray,
        ptsUs: Long,
        generation: Int,
        recycleBuffer: (ByteArray) -> Unit
    ) {
        var targetSlot: BitmapSlot? = null
        var obsoleteSlotsToClose: Array<BitmapSlot>? = null
        try {
            if (activeSession !== session || width <= 0 || height <= 0 || generation != session.currentGeneration()) {
                return
            }
            synchronized(slotPoolLock) {
                if (activeSession !== session || generation != session.currentGeneration()) {
                    return
                }
                var slots = bitmapSlots
                if (slots == null || bitmapWidth != width || bitmapHeight != height) {
                    obsoleteSlotsToClose = slots
                    val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
                    slots = Array(4) { BitmapSlot(width, height, info) }
                    bitmapSlots = slots
                    bitmapWidth = width
                    bitmapHeight = height
                    bitmapRingIndex = 0
                }
                val curEdt = currentEdtSlot
                val prevEdt = previousEdtSlot
                val pendSlot = pendingFrameRef.get()?.slot
                var selected: BitmapSlot? = null
                for (i in 0 until 4) {
                    val idx = (bitmapRingIndex + i) and 3
                    val candidate = slots[idx]
                    if (candidate !== curEdt && candidate !== prevEdt && candidate !== pendSlot) {
                        selected = candidate
                        bitmapRingIndex = (idx + 1) and 3
                        break
                    }
                }
                val chosen = selected ?: slots[0]
                chosen.bitmap.installPixels(chosen.info, bgraBytes, width * 4)
                chosen.bitmap.notifyPixelsChanged()
                targetSlot = chosen
            }
        } finally {
            recycleBuffer(bgraBytes)
            obsoleteSlotsToClose?.let { oldSlots ->
                SwingUtilities.invokeLater {
                    oldSlots.forEach { it.close() }
                }
            }
        }

        val readySlot = targetSlot ?: return
        pendingFrameRef.set(PendingReadyFrame(session, readySlot, ptsUs, generation))

        if (isFrameScheduledOnEdt.compareAndSet(false, true)) {
            SwingUtilities.invokeLater {
                isFrameScheduledOnEdt.set(false)
                val frameToRender = pendingFrameRef.getAndSet(null) ?: return@invokeLater
                if (activeSession !== frameToRender.session ||
                    frameToRender.generation != frameToRender.session.currentGeneration()
                ) {
                    return@invokeLater
                }
                previousEdtSlot = currentEdtSlot
                currentEdtSlot = frameToRender.slot
                onFramePresented(frameToRender.ptsUs, false)
                onFrameRendered(frameToRender.slot.composeBitmap)
            }
        }
    }

    private inner class VideoPacket(
        val width: Int,
        val height: Int,
        val bgraBytes: ByteArray,
        val ptsUs: Long,
        val generation: Int,
        private val recycleBuffer: (ByteArray) -> Unit
    ) {
        private val recycled = AtomicBoolean(false)

        fun recycleOnce() {
            if (recycled.compareAndSet(false, true)) {
                recycleBuffer(bgraBytes)
            }
        }
    }

    private inner class AudioPacket(
        val pcmBytes: ByteArray,
        val length: Int,
        val ptsUs: Long,
        val durationUs: Long,
        val generation: Int,
        private val recycleBuffer: (ByteArray) -> Unit
    ) {
        private val recycled = AtomicBoolean(false)

        fun recycleOnce() {
            if (recycled.compareAndSet(false, true)) {
                recycleBuffer(pcmBytes)
            }
        }
    }

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
        private val activeSeekFloorUs = AtomicLong(initialResumeMs.coerceAtLeast(0L) * 1000L)
        private val lastSeekRequestNano = AtomicLong(0L)

        private val audioClockUs = AtomicLong(initialResumeMs * 1000L)
        private val hasAudioStream = AtomicBoolean(false)
        private val clockLock = Any()
        private var lastObservedRawAudioUs: Long = -1L
        private var lastObservedAudioWallNs: Long = 0L
        private var lastReturnedMasterUs: Long = initialResumeMs.coerceAtLeast(0L) * 1000L
        private var lastClockGen: Int = -1

        private val videoQueue = ArrayBlockingQueue<VideoPacket>(48)
        private val audioQueue = ArrayBlockingQueue<AudioPacket>(64)
        private val byteArrayPool = ArrayBlockingQueue<ByteArray>(56)
        private val audioByteArrayPool = ArrayBlockingQueue<ByteArray>(72)
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

        fun currentGeneration(): Int = seekGeneration.get()

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
            activeSeekFloorUs.set(targetUs)
            lastSeekRequestNano.set(System.nanoTime())
            val gen = seekGeneration.incrementAndGet()
            requestedSeekUs.set(targetUs)
            demuxReachedEof.set(false)
            audioClockUs.set(targetUs)
            clearQueuesAndRecycle()
            runCatching { soundLine?.flush() }
            notifyPosition(positionMs, force = true, generation = gen)
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
            pendingFrameRef.set(null)
            val drainedVideo = ArrayList<VideoPacket>()
            videoQueue.drainTo(drainedVideo)
            for (vp in drainedVideo) {
                vp.recycleOnce()
            }
            val drainedAudio = ArrayList<AudioPacket>()
            audioQueue.drainTo(drainedAudio)
            for (ap in drainedAudio) {
                ap.recycleOnce()
            }
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

        private fun borrowAudioBytes(requiredSize: Int): ByteArray {
            val pooled = audioByteArrayPool.poll()
            if (pooled != null && pooled.size >= requiredSize) {
                return pooled
            }
            return ByteArray(maxOf(requiredSize, 8192))
        }

        private fun recycleAudioBytes(bytes: ByteArray) {
            if (!stopped.get()) {
                audioByteArrayPool.offer(bytes)
            }
        }

        private fun currentMasterClockUs(): Long {
            val gen = seekGeneration.get()
            val floorUs = activeSeekFloorUs.get()
            val baseUs = audioClockUs.get()
            val line = soundLine
            val bps = audioBytesPerSecond
            if (hasAudioStream.get() && line != null && bps > 0L) {
                val bufferedBytes = (line.bufferSize - line.available()).coerceAtLeast(0)
                val bufferedUs = (bufferedBytes.toLong() * 1_000_000L) / bps
                val isFlowing = line.isRunning && wantToPlay.get() && bufferedBytes > 0
                val pipelineCompensationUs = if (isFlowing) {
                    WINDOWS_AUDIO_PIPELINE_LATENCY_US
                } else {
                    0L
                }
                val rawAudioUs = (baseUs - bufferedUs - pipelineCompensationUs).coerceAtLeast(floorUs)
                synchronized(clockLock) {
                    val nowNs = System.nanoTime()
                    if (gen != lastClockGen) {
                        lastClockGen = gen
                        lastObservedRawAudioUs = rawAudioUs
                        lastObservedAudioWallNs = nowNs
                        lastReturnedMasterUs = rawAudioUs
                        return rawAudioUs
                    }
                    if (rawAudioUs != lastObservedRawAudioUs) {
                        lastObservedRawAudioUs = rawAudioUs
                        lastObservedAudioWallNs = nowNs
                    }
                    val interpUs = if (isFlowing) {
                        ((nowNs - lastObservedAudioWallNs) / 1000L).coerceIn(0L, 25_000L)
                    } else {
                        0L
                    }
                    val candidateUs = (rawAudioUs + interpUs).coerceAtLeast(floorUs)
                    if (candidateUs >= lastReturnedMasterUs || lastReturnedMasterUs - candidateUs > 25_000L) {
                        lastReturnedMasterUs = candidateUs
                    }
                    return lastReturnedMasterUs
                }
            }
            return baseUs.coerceAtLeast(floorUs)
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
            var grabber: FastSeekFfmpegGrabber? = null
            try {
                val g = FastSeekFfmpegGrabber(url)
                grabber = g
                g.setOption("protocol_whitelist", "file,http,https,tcp,tls,crypto,data")
                g.setOption("reconnect", "1")
                g.setOption("reconnect_streamed", "1")
                g.setOption("reconnect_delay_max", "5")
                g.setOption("rw_timeout", "15000000")
                g.setOption("fflags", "+genpts+discardcorrupt")
                g.setOption("probesize", "327680")
                g.setOption("analyzeduration", "500000")
                g.setOption("fpsprobesize", "5")
                g.setOption("buffer_size", "2097152")

                // Cap multi-core decoder threads to 2..6 so frame-threading pipeline delay stays short (~4-6 frames)
                val cpuCores = Runtime.getRuntime().availableProcessors()
                val optimalVideoThreads = cpuCores.coerceIn(2, 6).toString()
                g.setVideoOption("threads", optimalVideoThreads)
                g.setVideoOption("thread_type", "slice+frame")

                g.imageMode = FrameGrabber.ImageMode.COLOR
                g.pixelFormat = avutil.AV_PIX_FMT_BGRA
                g.imageScalingFlags = swscale.SWS_FAST_BILINEAR
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

                var videoSeekGateUs = 0L
                var audioSeekGateUs = 0L
                if (initialResumeMs > 1_000L) {
                    val resumeUs = initialResumeMs * 1000L
                    activeSeekFloorUs.set(resumeUs)
                    seekGrabberNormalized(g, resumeUs, streamStartOffsetUs)
                    audioClockUs.set(resumeUs)
                    videoSeekGateUs = (resumeUs - SEEK_VIDEO_PREROLL_TOLERANCE_US).coerceAtLeast(0L)
                    audioSeekGateUs = (resumeUs - SEEK_AUDIO_LEAD_MARGIN_US).coerceAtLeast(0L)
                }

                val defaultFrameDurUs = if (g.frameRate > 1.0 && g.frameRate.isFinite()) {
                    (1_000_000.0 / g.frameRate).toLong().coerceIn(8_000L, 100_000L)
                } else {
                    41_667L
                }

                var firstPacketDelivered = false
                var clockAnchoredForGen = false
                var lastVideoPtsUs = -1L
                var maxVideoPtsUsInGen = -1L
                var lastAudioEndPtsUs = -1L
                var consecutiveSeeksWithoutFrame = 0
                var lastLuminanceSampleNs = 0L
                var smoothedLuminance = 0.25f
                var lastReportedLuminance = -1f

                while (!stopped.get() && !Thread.currentThread().isInterrupted) {
                    var seekTargetUs = requestedSeekUs.getAndSet(-1L)
                    if (seekTargetUs >= 0L) {
                        consecutiveSeeksWithoutFrame++
                        // When the user holds or mashes the seek key (2+ seeks before any frame is decoded),
                        // coalesce rapid back-to-back targets so we don't open & abort intermediate HTTP
                        // segment streams on slow networks. Single seeks execute immediately with 0ms delay.
                        if (consecutiveSeeksWithoutFrame >= 2) {
                            while (!stopped.get()) {
                                val newer = requestedSeekUs.getAndSet(-1L)
                                if (newer >= 0L) {
                                    seekTargetUs = newer
                                    consecutiveSeeksWithoutFrame++
                                }
                                val quietMs = (System.nanoTime() - lastSeekRequestNano.get()) / 1_000_000L
                                if (quietMs >= 90L) break
                                Thread.sleep(5L)
                            }
                        }
                        demuxReachedEof.set(false)
                        activeSeekFloorUs.set(seekTargetUs)
                        clearQueuesAndRecycle()
                        runCatching { soundLine?.flush() }
                        seekGrabberNormalized(g, seekTargetUs, streamStartOffsetUs)
                        audioClockUs.set(seekTargetUs)
                        videoSeekGateUs = (seekTargetUs - SEEK_VIDEO_PREROLL_TOLERANCE_US).coerceAtLeast(0L)
                        audioSeekGateUs = (seekTargetUs - SEEK_AUDIO_LEAD_MARGIN_US).coerceAtLeast(0L)
                        firstPacketDelivered = false
                        clockAnchoredForGen = false
                        lastVideoPtsUs = -1L
                        maxVideoPtsUsInGen = -1L
                        lastAudioEndPtsUs = -1L
                        // If user pressed ← / → again while setTimestamp was in flight, jump straight to newest target!
                        if (requestedSeekUs.get() >= 0L) {
                            continue
                        }
                    }

                    val genBeforeGrab = seekGeneration.get()
                    val isPreroll = maxVideoPtsUsInGen < 0L && videoSeekGateUs > 0L
                    val frame = if (isPreroll) {
                        // Decode compressed packets without running sws_scale (processImage = false)
                        // until we reach the post-seek target timestamp!
                        g.grabFrame(
                            /* doAudio = */ hasAudioStream.get(),
                            /* doVideo = */ true,
                            /* processImage = */ false,
                            /* keyFrames = */ false,
                            /* doData = */ false
                        )
                    } else {
                        g.grab()
                    }

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
                    val isVideoFrame = (frame.image != null && frame.image.isNotEmpty()) ||
                        (isPreroll && frame.samples == null && frame.imageWidth > 0 && frame.imageHeight > 0)

                    if (isVideoFrame) {
                        val ptsUs = if (rawTsUs == 0L && streamStartOffsetUs > 0L && lastVideoPtsUs >= 0L) {
                            lastVideoPtsUs + defaultFrameDurUs
                        } else {
                            (rawTsUs - streamStartOffsetUs).coerceAtLeast(0L)
                        }
                        lastVideoPtsUs = ptsUs

                        // Drop pre-seek keyframe preroll frames (before sws_scale!) and any out-of-order B-frame regressions
                        if (ptsUs < videoSeekGateUs || (maxVideoPtsUsInGen >= 0L && ptsUs + 5_000L < maxVideoPtsUsInGen)) {
                            if (ptsUs < videoSeekGateUs) {
                                onFramePresented(ptsUs, true)
                            }
                            continue
                        }

                        val readyFrame = if (frame.image == null || frame.image.isEmpty()) {
                            g.materializeGrabbedVideoFrame() ?: continue
                        } else {
                            frame
                        }
                        maxVideoPtsUsInGen = maxOf(maxVideoPtsUsInGen, ptsUs)
                        consecutiveSeeksWithoutFrame = 0

                        val srcBuf = readyFrame.image?.getOrNull(0) as? ByteBuffer
                        val w = readyFrame.imageWidth
                        val h = readyFrame.imageHeight
                        val stride = readyFrame.imageStride
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
                                    val clampedPtsUs = maxOf(ptsUs, activeSeekFloorUs.get())
                                    audioClockUs.set(clampedPtsUs)
                                    notifyPosition(clampedPtsUs / 1000L, force = true, generation = genBeforeGrab)
                                }

                                val vp = VideoPacket(w, h, dst, ptsUs, genBeforeGrab, ::recycleBytes)
                                var offered = false
                                while (!stopped.get() && genBeforeGrab == seekGeneration.get()) {
                                    if (videoQueue.offer(vp, 40L, TimeUnit.MILLISECONDS)) {
                                        offered = true
                                        break
                                    }
                                }
                                if (!offered) {
                                    vp.recycleOnce()
                                } else if (stopped.get() || genBeforeGrab != seekGeneration.get()) {
                                    if (videoQueue.remove(vp)) {
                                        vp.recycleOnce()
                                    }
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
                                val bps = audioBytesPerSecond.coerceAtLeast(1L)
                                val packetDurUs = (pcmLen.toLong() * 1_000_000L) / bps
                                val rawPtsUs = if (rawTsUs == 0L && streamStartOffsetUs > 0L && lastAudioEndPtsUs >= 0L) {
                                    lastAudioEndPtsUs
                                } else {
                                    (rawTsUs - streamStartOffsetUs).coerceAtLeast(0L)
                                }
                                val packetEndUs = rawPtsUs + packetDurUs
                                lastAudioEndPtsUs = packetEndUs

                                // Drop pre-target audio packets so they never drag audioClockUs backward
                                if (packetEndUs <= audioSeekGateUs) {
                                    continue
                                }
                                val ptsUs = maxOf(rawPtsUs, activeSeekFloorUs.get())

                                val pcm = borrowAudioBytes(pcmLen)
                                var byteIdx = 0
                                while (dup.hasRemaining()) {
                                    val s = dup.get().toInt()
                                    pcm[byteIdx++] = (s and 0xFF).toByte()
                                    pcm[byteIdx++] = ((s shr 8) and 0xFF).toByte()
                                }

                                val ap = AudioPacket(pcm, pcmLen, ptsUs, packetDurUs, genBeforeGrab, ::recycleAudioBytes)
                                var audioOffered = false
                                while (!stopped.get() && genBeforeGrab == seekGeneration.get()) {
                                    if (audioQueue.offer(ap, 40L, TimeUnit.MILLISECONDS)) {
                                        audioOffered = true
                                        break
                                    }
                                }
                                if (!audioOffered) {
                                    ap.recycleOnce()
                                } else if (stopped.get() || genBeforeGrab != seekGeneration.get()) {
                                    if (audioQueue.remove(ap)) {
                                        ap.recycleOnce()
                                    }
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
                    try {
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
                                        val floorUs = activeSeekFloorUs.get()
                                        audioClockUs.set(maxOf(packet.ptsUs + writtenUs, floorUs))
                                    }
                                }
                            }
                        }
                        if (packet.generation == seekGeneration.get()) {
                            val floorUs = activeSeekFloorUs.get()
                            audioClockUs.set(maxOf(packet.ptsUs + packet.durationUs, floorUs))
                            notifyPosition(currentMasterClockUs() / 1000L, force = false, generation = packet.generation)
                        }
                    } finally {
                        packet.recycleOnce()
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
            var firstVideoWallNs = 0L
            var firstVideoPtsUs = 0L
            var firstVideoGen = -1

            try {
                while (!stopped.get() && !Thread.currentThread().isInterrupted) {
                    val activeGen = seekGeneration.get()
                    val isPlayingNow = wantToPlay.get()
                    val needsInitialFrameForGen = (firstVideoGen != activeGen) && (lastWallGen != activeGen)
                    if (!isPlayingNow && !needsInitialFrameForGen) {
                        wallClockAnchorNs = 0L
                        firstVideoWallNs = 0L
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

                    if (vp.generation != seekGeneration.get() ||
                        vp.ptsUs + SEEK_VIDEO_PREROLL_TOLERANCE_US < activeSeekFloorUs.get()
                    ) {
                        vp.recycleOnce()
                        continue
                    }

                    val playingAtFrame = wantToPlay.get()
                    notifyState(playing = playingAtFrame, buffering = false)

                    if (hasAudioStream.get() && soundLine != null) {
                        if (firstVideoGen != vp.generation || firstVideoWallNs == 0L) {
                            firstVideoGen = vp.generation
                            firstVideoWallNs = if (playingAtFrame) System.nanoTime() else 0L
                            firstVideoPtsUs = vp.ptsUs
                            if (!playingAtFrame) {
                                val clampedPtsUs = maxOf(vp.ptsUs, activeSeekFloorUs.get())
                                notifyPosition(clampedPtsUs / 1000L, force = true, generation = vp.generation)
                            }
                        } else {
                            while (!stopped.get() && wantToPlay.get() && vp.generation == seekGeneration.get()) {
                                // Safety valve: never stall demuxer if videoQueue is full while audioQueue awaits interleaved packets
                                if (videoQueue.remainingCapacity() == 0 && audioQueue.isEmpty()) {
                                    break
                                }
                                val rawMasterUs = currentMasterClockUs()
                                val wallElapsedUs = ((System.nanoTime() - firstVideoWallNs) / 1000L).coerceAtLeast(0L)
                                // Cap post-seek master clock advancement to wall-clock reality during audio buffer warmup
                                val effectiveMasterUs = if (wallElapsedUs < 1_500_000L) {
                                    minOf(rawMasterUs, firstVideoPtsUs + wallElapsedUs + 25_000L)
                                } else {
                                    rawMasterUs
                                }
                                val diffUs = vp.ptsUs - effectiveMasterUs
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
                            // Drop genuinely late non-final frames (> 80ms behind effective master clock)
                            val rawMasterUs = currentMasterClockUs()
                            val wallElapsedUs = ((System.nanoTime() - firstVideoWallNs) / 1000L).coerceAtLeast(0L)
                            val effectiveMasterUs = if (wallElapsedUs < 1_500_000L) {
                                minOf(rawMasterUs, firstVideoPtsUs + wallElapsedUs + 25_000L)
                            } else {
                                rawMasterUs
                            }
                            val lateByUs = effectiveMasterUs - vp.ptsUs
                            if (lateByUs > 80_000L && videoQueue.isNotEmpty()) {
                                vp.recycleOnce()
                                continue
                            }
                        }
                    } else {
                        // Silent stream fallback: pace against monotonic System.nanoTime()
                        if (wallClockAnchorNs == 0L || lastWallGen != vp.generation) {
                            wallClockAnchorNs = if (playingAtFrame) System.nanoTime() else 0L
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
                        val clampedPtsUs = maxOf(vp.ptsUs, activeSeekFloorUs.get())
                        audioClockUs.set(clampedPtsUs)
                        notifyPosition(clampedPtsUs / 1000L, force = !playingAtFrame, generation = vp.generation)
                    }

                    if (stopped.get() || vp.generation != seekGeneration.get()) {
                        vp.recycleOnce()
                        continue
                    }

                    publishFrameOnEdt(
                        session = this,
                        width = vp.width,
                        height = vp.height,
                        bgraBytes = vp.bgraBytes,
                        ptsUs = vp.ptsUs,
                        generation = vp.generation,
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

        private fun notifyPosition(posMs: Long, force: Boolean = false, generation: Int = seekGeneration.get()) {
            val self = this
            if (!force && kotlin.math.abs(posMs - lastNotifiedPositionMs) < 120L) {
                return
            }
            lastNotifiedPositionMs = posMs
            SwingUtilities.invokeLater {
                if (activeSession === self && !stopped.get() && generation == seekGeneration.get()) {
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
