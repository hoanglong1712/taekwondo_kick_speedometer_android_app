package com.example.tkdspeed

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class BoundingBoxOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.parseColor("#00FFCC") // Cyan/Aqua glow
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        // Thêm hiệu ứng phát sáng nhẹ
        setShadowLayer(12f, 0f, 0f, Color.parseColor("#00FFCC"))
    }


    var boundingBox: RectF? = null
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        boundingBox?.let { rect ->
            // rect is normalized [0, 1]
            val left = rect.left * width
            val top = rect.top * height
            val right = rect.right * width
            val bottom = rect.bottom * height
            
            // Vẽ hình chữ nhật bo góc
            canvas.drawRoundRect(left, top, right, bottom, 20f, 20f, paint)
        }
    }

}
