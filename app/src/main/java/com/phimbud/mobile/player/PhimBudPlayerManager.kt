package com.phimbud.mobile.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class PhimBudPlayerManager(private val context: Context) {

    private val playerOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HlsDecryptInterceptor())
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val dataSourceFactory = OkHttpDataSource.Factory(playerOkHttpClient)
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

    fun createPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()
    }

    fun buildMediaSource(m3u8Url: String, subtitleFile: File? = null): MediaSource {
        val mediaItem = MediaItem.fromUri(m3u8Url)
        val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)

        if (subtitleFile != null && subtitleFile.exists()) {
            val subUri = Uri.fromFile(subtitleFile)
            val subMediaItem = MediaItem.SubtitleConfiguration.Builder(subUri)
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage("vi")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            val subSource = SingleSampleMediaSource.Factory(dataSourceFactory)
                .createMediaSource(subMediaItem, C.TIME_UNSET)

            return MergingMediaSource(hlsSource, subSource)
        }

        return hlsSource
    }
}
