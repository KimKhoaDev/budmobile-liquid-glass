package com.phimbud.mobile.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import com.phimbud.mobile.core.network.BrowserHeaders
import com.phimbud.mobile.core.network.BrowserHeadersInterceptor
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class PhimBudPlayerManager(
    private val context: Context,
    baseOkHttpClient: OkHttpClient? = null
) {

    private val playerOkHttpClient: OkHttpClient = (
        baseOkHttpClient?.newBuilder()
            ?: OkHttpClient.Builder().addInterceptor(BrowserHeadersInterceptor())
        )
        .cache(null)
        .addInterceptor(HlsDecryptInterceptor())
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val okHttpDataSourceFactory = OkHttpDataSource.Factory(playerOkHttpClient)
        .setUserAgent(BrowserHeaders.USER_AGENT)

    private val hlsDataSourceFactory = DefaultDataSource.Factory(context, okHttpDataSourceFactory)
    private val localDataSourceFactory = DefaultDataSource.Factory(context)

    fun createPlayer(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_200,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500
            )
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage("vi")
            .setSelectUndeterminedTextLanguage(true)
            .build()

        return player
    }

    fun buildMediaSource(m3u8Url: String, subtitleFile: File? = null, language: String = "vi"): MediaSource {
        val mediaItem = MediaItem.fromUri(m3u8Url)
        val hlsSource = HlsMediaSource.Factory(hlsDataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)

        if (subtitleFile != null && subtitleFile.exists() && subtitleFile.length() > 0) {
            val subUri = Uri.fromFile(subtitleFile)
            val mimeType = if (subtitleFile.name.endsWith(".vtt", ignoreCase = true)) {
                MimeTypes.TEXT_VTT
            } else {
                MimeTypes.APPLICATION_SUBRIP
            }

            val subMediaItem = MediaItem.SubtitleConfiguration.Builder(subUri)
                .setMimeType(mimeType)
                .setLanguage(language)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                .build()

            val subSource = SingleSampleMediaSource.Factory(localDataSourceFactory)
                .createMediaSource(subMediaItem, C.TIME_UNSET)

            return MergingMediaSource(hlsSource, subSource)
        }

        return hlsSource
    }
}
