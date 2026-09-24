package com.arjun.absolutra.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class SelectionView(
    context: Context,
    private val onCancel: () -> Unit,
    private val onSelectionComplete: (Int, Int) -> Unit
) : View(context) {
    private val paintDim = Paint().apply { color = Color.parseColor("#80000000") }
    private val paintClear = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        color = Color.TRANSPARENT
    }
    private var startY = -1f
    private var currentY = -1f
    private var isCancelled = false

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintDim)

        if (startY != -1f && currentY != -1f) {
            val top = min(startY, currentY)
            val bottom = max(startY, currentY)
            canvas.drawRect(0f, top, width.toFloat(), bottom, paintClear)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isCancelled) return true

        if (event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            isCancelled = true
            onCancel()
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.y
                currentY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                currentY = event.y
                invalidate()
                val top = min(startY, currentY).toInt()
                val bottom = max(startY, currentY).toInt()
                onSelectionComplete(top, bottom)
                startY = -1f
                currentY = -1f
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
