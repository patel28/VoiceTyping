package com.voicetyping.directtest

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders every floating-control state and checks the spoken label for each one.
 *
 * The control is attached to the app's own content view rather than to an overlay window, so the
 * captures do not depend on display-over-other-apps being granted on the test device. It is the
 * same view the service shows, rendered through the same [BubbleControlView.render] call.
 */
@RunWith(AndroidJUnit4::class)
class BubbleControlStateTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun everyFloatingControlStateRendersWithItsOwnSpokenLabel() {
        val size = (BUBBLE_SIZE_DP * context.resources.displayMetrics.density).toInt()
        val margin = (18 * context.resources.displayMetrics.density).toInt()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var control: BubbleControlView
            scenario.onActivity { activity ->
                control = BubbleControlView(activity)
                activity.findViewById<ViewGroup>(android.R.id.content).addView(
                    control,
                    FrameLayout.LayoutParams(size, size).apply {
                        gravity = Gravity.BOTTOM or Gravity.END
                        rightMargin = margin
                        bottomMargin = margin * 4
                    },
                )
            }
            try {
                capture("bubble-idle", scenario, control) {
                    it.render(BubbleFlowState.IDLE, RecordingMode.HOLD_TO_RECORD, 0L)
                }
                assertEquals(
                    context.getString(R.string.bubble_a11y_idle_hold),
                    control.contentDescription,
                )

                capture("bubble-armed", scenario, control) {
                    it.render(BubbleFlowState.IDLE, RecordingMode.HOLD_TO_RECORD, 0L)
                    it.setArmed(true)
                }

                capture("bubble-recording", scenario, control) {
                    it.setArmed(false)
                    it.render(BubbleFlowState.RECORDING, RecordingMode.HOLD_TO_RECORD, 12_000L)
                }
                assertEquals(
                    context.getString(R.string.bubble_a11y_recording_hold, "0:12"),
                    control.contentDescription,
                )

                capture("bubble-processing", scenario, control) {
                    it.render(BubbleFlowState.PROCESSING, RecordingMode.HOLD_TO_RECORD, 0L)
                }
                assertEquals(
                    context.getString(R.string.bubble_a11y_processing),
                    control.contentDescription,
                )

                capture("bubble-copied", scenario, control) {
                    it.render(BubbleFlowState.COPIED, RecordingMode.HOLD_TO_RECORD, 0L)
                }
                assertEquals(
                    context.getString(R.string.bubble_a11y_copied),
                    control.contentDescription,
                )

                capture("bubble-error", scenario, control) {
                    it.render(BubbleFlowState.ERROR, RecordingMode.HOLD_TO_RECORD, 0L)
                }
                assertEquals(
                    context.getString(R.string.bubble_a11y_error),
                    control.contentDescription,
                )

                capture("bubble-idle-tap-mode", scenario, control) {
                    it.render(BubbleFlowState.IDLE, RecordingMode.TAP_TO_TOGGLE, 0L)
                }
                assertEquals(
                    context.getString(R.string.bubble_a11y_idle_tap),
                    control.contentDescription,
                )
            } finally {
                scenario.onActivity {
                    (control.parent as? ViewGroup)?.removeView(control)
                }
            }
        }
    }

    private fun capture(
        name: String,
        scenario: ActivityScenario<MainActivity>,
        control: BubbleControlView,
        render: (BubbleControlView) -> Unit,
    ) {
        scenario.onActivity { render(control) }
        ScreenshotStore.capture(name)
    }

    private companion object {
        const val BUBBLE_SIZE_DP = 62
    }
}
