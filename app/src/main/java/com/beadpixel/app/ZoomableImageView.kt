package com.beadpixel.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val baseMatrix = Matrix()
    private val viewMatrix = Matrix()
    private var scaleFactor = 1f
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val ns = (scaleFactor * detector.scaleFactor).coerceIn(1f, 8f)
            viewMatrix.postScale(ns / scaleFactor, ns / scaleFactor, detector.focusX, detector.focusY)
            scaleFactor = ns
            imageMatrix = viewMatrix
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && scaleFactor > 1f) {
                    viewMatrix.postTranslate(event.x - lastX, event.y - lastY)
                    lastX = event.x
                    lastY = event.y
                    imageMatrix = viewMatrix
                }
            }
        }
        return true
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        post { fitToView() }
    }

    private fun fitToView() {
        val d = drawable ?: return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (dw <= 0 || dh <= 0 || vw <= 0 || vh <= 0) return
        val s = min(vw / dw, vh / dh)
        val dx = (vw - dw * s) / 2f
        val dy = (vh - dh * s) / 2f
        baseMatrix.reset()
        baseMatrix.postScale(s, s)
        baseMatrix.postTranslate(dx, dy)
        viewMatrix.set(baseMatrix)
        scaleFactor = 1f
        imageMatrix = viewMatrix
    }
}
