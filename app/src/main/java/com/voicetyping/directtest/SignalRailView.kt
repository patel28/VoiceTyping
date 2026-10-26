package com.voicetyping.directtest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.max

/** One stop on the copy pipeline, in the order the user moves through it. */
enum class SignalStage(val labelRes: Int) {
    PERMISSION(R.string.rail_permission),
    READY(R.string.rail_ready),
    LISTENING(R.string.rail_listening),
    REFINING(R.string.rail_refining),
    COPIED(R.string.rail_copied),
}

/** How far the user has come. Shape, weight and label carry this as well as colour. */
enum class StageState { DONE, CURRENT, AHEAD }

/**
 * The signature instrument: a vertical rail of the five real pipeline stops.
 *
 * Labels are drawn here rather than laid out as sibling TextViews so that node centres and
 * baselines cannot drift apart when the system font scale changes. Text is measured in sp, so
 * the rail still grows with the user's font size. There is no animation, so nothing needs to be
 * suppressed when the user has reduced motion.
 */
class SignalRailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val nodeRadius = dp(5.5f)
    private val ringRadius = dp(9.5f)
    private val labelInset = dp(30f)
    private val minRowHeight = dp(40f)

    private val railPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.6f)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.6f)
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.9f)
        strokeCap = Paint.Cap.ROUND
        color = context.themeColor(R.attr.vtOnAccent)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(14f)
    }
    private val checkPath = Path()
    private val fontMetrics = Paint.FontMetrics()
    private val nodeCenters = FloatArray(SignalStage.entries.size)

    private val doneColor = context.getColor(R.color.success_mint)
    private val currentColor = context.themeColor(R.attr.vtAccent)
    private val aheadColor = context.themeColor(R.attr.vtHairline)
    private val inkColor = context.themeColor(R.attr.vtInk)
    private val mutedColor = context.themeColor(R.attr.vtInkMuted)

    private var states = SignalStage.entries.map { StageState.AHEAD }

    /** Renders the rail. [states] must hold one entry per [SignalStage], in order. */
    fun render(states: List<StageState>) {
        require(states.size == SignalStage.entries.size) { "one state per stage is required" }
        if (states == this.states) return
        this.states = states
        contentDescription = buildContentDescription()
        requestLayout()
        invalidate()
    }

    private fun buildContentDescription(): CharSequence {
        val steps = SignalStage.entries.mapIndexed { index, stage ->
            val label = context.getString(stage.labelRes)
            when (states[index]) {
                StageState.DONE -> context.getString(R.string.rail_a11y_done, label)
                StageState.CURRENT -> context.getString(R.string.rail_a11y_current, label)
                StageState.AHEAD -> context.getString(R.string.rail_a11y_ahead, label)
            }
        }
        return (listOf(context.getString(R.string.rail_a11y_label)) + steps).joinToString(". ")
    }

    private fun rowHeight(): Float {
        labelPaint.getFontMetrics(fontMetrics)
        return max(minRowHeight, fontMetrics.descent - fontMetrics.ascent + dp(16f))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = (rowHeight() * SignalStage.entries.size).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val row = rowHeight()
        val centerX = paddingLeft + ringRadius
        val centers = nodeCenters
        for (index in centers.indices) centers[index] = paddingTop + row * index + row / 2f

        // Segments first, so nodes always sit on top of the rail.
        for (index in 0 until centers.size - 1) {
            railPaint.color = if (states[index] == StageState.DONE) doneColor else aheadColor
            canvas.drawLine(
                centerX,
                centers[index] + ringRadius + dp(2f),
                centerX,
                centers[index + 1] - ringRadius - dp(2f),
                railPaint,
            )
        }

        SignalStage.entries.forEachIndexed { index, stage ->
            val centerY = centers[index]
            when (states[index]) {
                StageState.DONE -> {
                    fillPaint.color = doneColor
                    canvas.drawCircle(centerX, centerY, nodeRadius + dp(1.5f), fillPaint)
                    drawCheck(canvas, centerX, centerY)
                }
                StageState.CURRENT -> {
                    fillPaint.color = currentColor
                    canvas.drawCircle(centerX, centerY, nodeRadius, fillPaint)
                    ringPaint.color = currentColor
                    canvas.drawCircle(centerX, centerY, ringRadius, ringPaint)
                }
                StageState.AHEAD -> {
                    strokePaint.color = aheadColor
                    canvas.drawCircle(centerX, centerY, nodeRadius, strokePaint)
                }
            }

            labelPaint.isFakeBoldText = states[index] == StageState.CURRENT
            labelPaint.color = if (states[index] == StageState.AHEAD) mutedColor else inkColor
            labelPaint.getFontMetrics(fontMetrics)
            val baseline = centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(context.getString(stage.labelRes), paddingLeft + labelInset, baseline, labelPaint)
        }
    }

    private fun drawCheck(canvas: Canvas, centerX: Float, centerY: Float) {
        val unit = nodeRadius / 2.4f
        checkPath.reset()
        checkPath.moveTo(centerX - unit * 1.5f, centerY)
        checkPath.lineTo(centerX - unit * 0.3f, centerY + unit * 1.2f)
        checkPath.lineTo(centerX + unit * 1.6f, centerY - unit * 1.1f)
        canvas.drawPath(checkPath, checkPaint)
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
