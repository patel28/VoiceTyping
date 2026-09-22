package com.voicetyping.directtest

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The 62dp floating control. Each state is carried by fill colour *and* a distinct glyph, so the
 * state is never signalled by colour alone. The view never takes focus; the hosting window keeps
 * FLAG_NOT_FOCUSABLE so the destination field and its keyboard stay as they are.
 */
class BubbleControlView(context: Context) : FrameLayout(context) {

    private val ring: ImageView
    private val arc: ImageView
    private val stack: LinearLayout
    private val glyph: ImageView
    private val timer: TextView
    private var arcAnimator: ObjectAnimator? = null
    private var lastState: BubbleFlowState? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_bubble_control, this, true)
        ring = findViewById(R.id.bubble_ring)
        arc = findViewById(R.id.bubble_arc)
        stack = findViewById(R.id.bubble_stack)
        glyph = findViewById(R.id.bubble_glyph)
        timer = findViewById(R.id.bubble_timer)
        setBackgroundResource(R.drawable.bubble_circle)
        elevation = dp(8f)
        isClickable = true
        isFocusable = false
    }

    // The service drives clicks from its own touch listener; swallow the default handling.
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Pressed/arming response: a ring plus a small scale, no colour change. */
    fun setArmed(armed: Boolean) {
        ring.visibility = if (armed) View.VISIBLE else View.GONE
        val target = if (armed) ARMED_SCALE else 1f
        scaleX = target
        scaleY = target
    }

    fun render(state: BubbleFlowState, mode: RecordingMode, elapsedMillis: Long) {
        val announceCopied = state == BubbleFlowState.COPIED && lastState != BubbleFlowState.COPIED
        lastState = state
        if (state != BubbleFlowState.PROCESSING) stopArc()

        when (state) {
            BubbleFlowState.IDLE -> {
                tint(R.color.signal_blue)
                showGlyph(R.drawable.ic_mic, GLYPH_STANDARD_DP)
                timer.visibility = View.GONE
                contentDescription = context.getString(
                    if (mode == RecordingMode.TAP_TO_TOGGLE) {
                        R.string.bubble_a11y_idle_tap
                    } else {
                        R.string.bubble_a11y_idle_hold
                    },
                )
            }

            BubbleFlowState.RECORDING -> {
                tint(R.color.recording_coral)
                showGlyph(R.drawable.ic_stop, GLYPH_COMPACT_DP)
                val elapsed = formatElapsed(elapsedMillis)
                timer.text = elapsed
                timer.visibility = View.VISIBLE
                contentDescription = context.getString(
                    if (mode == RecordingMode.TAP_TO_TOGGLE) {
                        R.string.bubble_a11y_recording_tap
                    } else {
                        R.string.bubble_a11y_recording_hold
                    },
                    elapsed,
                )
            }

            BubbleFlowState.PROCESSING -> {
                tint(R.color.signal_blue)
                stack.visibility = View.GONE
                timer.visibility = View.GONE
                arc.visibility = View.VISIBLE
                startArcIfMotionAllowed()
                contentDescription = context.getString(R.string.bubble_a11y_processing)
            }

            BubbleFlowState.COPIED -> {
                tint(R.color.success_mint)
                showGlyph(R.drawable.ic_check, GLYPH_STANDARD_DP)
                timer.visibility = View.GONE
                contentDescription = context.getString(R.string.bubble_a11y_copied)
            }

            BubbleFlowState.ERROR -> {
                // Amber, not coral: coral already means "recording" on this control.
                tint(R.color.warning_amber)
                showGlyph(R.drawable.ic_warning, GLYPH_STANDARD_DP)
                timer.visibility = View.GONE
                contentDescription = context.getString(R.string.bubble_a11y_error)
            }
        }

        if (announceCopied) announceForAccessibility(context.getString(R.string.bubble_a11y_copied))
    }

    private fun formatElapsed(elapsedMillis: Long): String {
        val totalSeconds = (elapsedMillis.coerceAtLeast(0L) / 1000L).toInt()
        return context.getString(R.string.bubble_timer_format, totalSeconds / 60, totalSeconds % 60)
    }

    private fun showGlyph(drawableRes: Int, sizeDp: Float) {
        arc.visibility = View.GONE
        stack.visibility = View.VISIBLE
        glyph.visibility = View.VISIBLE
        glyph.setImageResource(drawableRes)
        val size = dp(sizeDp).toInt()
        glyph.layoutParams = glyph.layoutParams.apply {
            width = size
            height = size
        }
        glyph.requestLayout()
    }

    private fun tint(colorRes: Int) {
        backgroundTintList = ColorStateList.valueOf(context.getColor(colorRes))
    }

    /**
     * The rotating arc is the decoration; the arc itself is the accessible static alternative, so
     * with animations turned off the same shape is shown without motion.
     */
    private fun startArcIfMotionAllowed() {
        if (arcAnimator != null) return
        if (animationsDisabled()) {
            arc.rotation = 0f
            return
        }
        arcAnimator = ObjectAnimator.ofFloat(arc, View.ROTATION, 0f, 360f).apply {
            duration = ARC_SPIN_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = null
            start()
        }
    }

    private fun stopArc() {
        arcAnimator?.cancel()
        arcAnimator = null
        arc.rotation = 0f
    }

    private fun animationsDisabled(): Boolean = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    override fun onDetachedFromWindow() {
        stopArc()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    companion object {
        private const val ARMED_SCALE = 0.94f
        private const val ARC_SPIN_MS = 900L
        private const val GLYPH_STANDARD_DP = 26f
        private const val GLYPH_COMPACT_DP = 17f
    }
}
