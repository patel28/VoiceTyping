package com.voicetyping.directtest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.ArrayDeque
import kotlin.math.max

/** Small RMS meter. Values are transient and represent no recoverable audio data. */
class SignalWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val levels = ArrayDeque<Float>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.recording_coral)
        style = Paint.Style.FILL
    }
    private val barBounds = RectF()

    @Synchronized
    fun addLevel(rms: Float) {
        levels.addLast(rms.coerceIn(0f, 1f))
        while (levels.size > MAX_BARS) levels.removeFirst()
        postInvalidateOnAnimation()
    }

    @Synchronized
    fun clearLevels() {
        levels.clear()
        invalidate()
    }

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val values = levels.toList()
        if (values.isEmpty()) return
        val gap = width.toFloat() / MAX_BARS
        val barWidth = max(2f, gap * 0.56f)
        val centerY = height / 2f
        val halfMax = height * 0.43f
        values.forEachIndexed { index, level ->
            val half = max(height * 0.06f, level * halfMax)
            val x = width - (values.size - index) * gap + (gap - barWidth) / 2
            barBounds.set(x, centerY - half, x + barWidth, centerY + half)
            canvas.drawRoundRect(barBounds, barWidth, barWidth, paint)
        }
    }

    companion object {
        private const val MAX_BARS = 48
    }
}
