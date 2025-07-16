package com.example.konkhmermovie

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

class AspectRatioVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VideoView(context, attrs, defStyleAttr) {

    private var videoWidth = 0
    private var videoHeight = 0

    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = MeasureSpec.getSize(widthMeasureSpec)
        var height = MeasureSpec.getSize(heightMeasureSpec)

        if (videoWidth != 0 && videoHeight != 0) {
            val aspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
            val viewRatio = width.toFloat() / height.toFloat()

            if (viewRatio > aspectRatio) {
                // fix width according to height
                width = (height * aspectRatio).toInt()
            } else {
                // fix height according to width
                height = (width / aspectRatio).toInt()
            }
        }
        setMeasuredDimension(width, height)
    }
}
