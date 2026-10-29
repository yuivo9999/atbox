package com.yuivo9999.mybox123.domain.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

class NativePlaybackService(private val context: Context) {

    @OptIn(UnstableApi::class)
    fun createPlayer(
        url: String,
        headers: Map<String, String> = emptyMap(),
        onPlayerError: (String) -> Unit = {},
    ): ExoPlayer {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        if (headers.isNotEmpty()) {
            httpDataSourceFactory.setDefaultRequestProperties(headers)
        }

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val mediaItem = MediaItem.fromUri(url)
        val mediaSource: MediaSource = if (url.contains(".m3u8", ignoreCase = true) || url.contains("hls", ignoreCase = true)) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }

        return ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    onPlayerError(error.localizedMessage ?: "播放错误")
                }
            })
            prepare()
            playWhenReady = true
        }
    }
}
