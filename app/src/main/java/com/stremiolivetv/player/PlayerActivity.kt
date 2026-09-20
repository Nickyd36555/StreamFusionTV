package com.stremiolivetv.player

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

class PlayerActivity:AppCompatActivity() {
    private var player:ExoPlayer?=null
    override fun onCreate(b:Bundle?) { super.onCreate(b); val view=PlayerView(this); setContentView(view)
        val loadControl=DefaultLoadControl.Builder().setBufferDurationsMs(8_000,35_000,800,1_500).setPrioritizeTimeOverSizeThresholds(true).build()
        val renderers=DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        val http=DefaultHttpDataSource.Factory().setConnectTimeoutMs(8_000).setReadTimeoutMs(20_000).setAllowCrossProtocolRedirects(true).setUserAgent("StreamFusionTV/0.7")
        player=ExoPlayer.Builder(this,renderers).setLoadControl(loadControl).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build().also { p -> view.player=p; intent.getStringExtra("url")?.let { p.setMediaItem(MediaItem.fromUri(it)); p.prepare(); p.playWhenReady=true } }
    }
    override fun onStop(){ super.onStop(); player?.release(); player=null }
}
