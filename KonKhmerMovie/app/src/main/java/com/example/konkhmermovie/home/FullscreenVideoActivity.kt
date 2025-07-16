package com.example.konkhmermovie.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.konkhmermovie.databinding.ActivityFullscreenVideoBinding
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class FullscreenVideoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullscreenVideoBinding
    private lateinit var player: ExoPlayer

    private var videoUrl: String? = null
    private var startPosition: Long = 0L
    private var isPlaying: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullscreenVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )

        player = ExoPlayerHolder.getPlayer(this)

        videoUrl = intent.getStringExtra("videoUrl")
        startPosition = intent.getLongExtra("position", 0L)
        isPlaying = intent.getBooleanExtra("isPlaying", true)

        if (videoUrl.isNullOrEmpty()) {
            finish()
            return
        }

        val currentItem = player.currentMediaItem
        if (currentItem == null || currentItem.localConfiguration?.uri.toString() != videoUrl) {
            player.stop()
            player.clearMediaItems()

            val mediaItem = MediaItem.fromUri(videoUrl!!)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.seekTo(startPosition)
            player.playWhenReady = isPlaying
        } else {
            player.seekTo(startPosition)
            player.playWhenReady = isPlaying
        }

        binding.playerView.player = player

        binding.backButton.setOnClickListener {
            returnToCaller()
        }
    }

    private fun returnToCaller() {
        val resultIntent = Intent().apply {
            putExtra("position", player.currentPosition)
            putExtra("isPlaying", player.isPlaying)
        }
        setResult(RESULT_OK, resultIntent)
        binding.playerView.player = null
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        returnToCaller()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.playerView.player = null
    }
}
