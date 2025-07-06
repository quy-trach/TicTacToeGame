package com.example.caroapp

import android.content.Context
import android.util.AttributeSet
import android.view.ScaleGestureDetector
import android.view.MotionEvent
import android.widget.ScrollView

class ZoomableScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ScrollView(context, attrs) {

    private var scaleFactor = 1.0f
    private val scaleGestureDetector: ScaleGestureDetector

    init {
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 3.0f) // Giới hạn từ 50% đến 300%

            // Lấy View con (GridLayout) để áp dụng scale
            val child = getChildAt(0)  // Lấy view đầu tiên trong ScrollView (HorizontalScrollView)
            child?.apply {
                scaleX = scaleFactor
                scaleY = scaleFactor
            }

            return true
        }
    }
}
