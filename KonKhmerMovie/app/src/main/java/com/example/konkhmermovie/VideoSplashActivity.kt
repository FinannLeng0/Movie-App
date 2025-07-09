package com.example.konkhmermovie

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class VideoSplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_splash)

        val videoView = findViewById<VideoView>(R.id.videoView)
        val fadeOverlay = findViewById<View>(R.id.fadeOverlay)

        val videoUri = Uri.parse("android.resource://$packageName/${R.raw.videointro}")
        videoView.setVideoURI(videoUri)

        videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.isLooping = false

            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            val screenWidth = displayMetrics.widthPixels.toFloat()
            val screenHeight = displayMetrics.heightPixels.toFloat()

            val videoWidth = mediaPlayer.videoWidth.toFloat()
            val videoHeight = mediaPlayer.videoHeight.toFloat()

            val scaleX = screenWidth / videoWidth
            val scaleY = screenHeight / videoHeight
            val scale = maxOf(scaleX, scaleY)

            val params = videoView.layoutParams
            params.width = (videoWidth * scale).toInt()
            params.height = (videoHeight * scale).toInt()
            videoView.layoutParams = params

            videoView.x = (screenWidth - params.width) / 2
            videoView.y = (screenHeight - params.height) / 2
        }

        videoView.setOnCompletionListener {
            // Show and fade in the black overlay
            fadeOverlay.visibility = View.VISIBLE
            fadeOverlay.alpha = 0f
            fadeOverlay.animate()
                .alpha(1f)
                .setDuration(800)
                .withEndAction {
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(0, 0)  // No default animation
                    finish()
                }
                .start()
        }

        videoView.start()
    }
}
