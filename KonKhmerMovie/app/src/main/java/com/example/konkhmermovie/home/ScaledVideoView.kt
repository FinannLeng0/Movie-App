package com.example.konkhmermovie.home

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

class ScaledVideoView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : VideoView(context, attrs, defStyle) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)

        // Desired aspect ratio 9:16 (width:height)
        val desiredRatio = 9f / 16f

        // Calculate height based on width and desired aspect ratio
        var width = parentWidth
        var height = (width / desiredRatio).toInt()

        if (height < parentHeight) {
            // If calculated height less than parent height, fill height and crop width
            height = parentHeight
            width = (height * desiredRatio).toInt()
        }

        setMeasuredDimension(width, height)
    }
}