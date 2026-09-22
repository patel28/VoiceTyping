package com.voicetyping.directtest

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures every screen state through the production rendering path, including the states this
 * device's real permissions are not currently in. Screenshots are published to
 * Download/voicetyping-shots by [ScreenshotStore].
 */
@RunWith(AndroidJUnit4::class)
class ScreenCaptureTest {

    @Test fun capturesMainScreenStates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.render(readiness(mic = false, overlay = false)) }
            ScreenshotStore.capture("main-missing-permission")

            scenario.onActivity { it.render(readiness(mic = true, overlay = false)) }
            ScreenshotStore.capture("main-overlay-missing")

            scenario.onActivity { it.render(readiness(mic = true, overlay = true)) }
            ScreenshotStore.capture("main-ready")

            scenario.onActivity { it.render(readiness(mic = true, overlay = true, running = true)) }
            ScreenshotStore.capture("main-active")

            scenario.onActivity {
                it.render(readiness(mic = true, overlay = true, running = true, copied = true))
            }
            ScreenshotStore.capture("main-copied")

            scenario.onActivity {
                it.render(
                    readiness(mic = true, overlay = true, mode = RecordingMode.TAP_TO_TOGGLE),
                )
            }
            ScreenshotStore.capture("main-ready-tap-mode")
        }
    }

    @Test fun capturesSettingsCollapsedAndExpanded() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            ScreenshotStore.capture("settings-collapsed")
            scenario.onActivity { it.findViewById<View>(R.id.advanced_row).performClick() }
            ScreenshotStore.capture("settings-advanced")
        }
    }

    private fun readiness(
        mic: Boolean,
        overlay: Boolean,
        running: Boolean = false,
        copied: Boolean = false,
        mode: RecordingMode = RecordingMode.HOLD_TO_RECORD,
    ) = MainActivity.Readiness(
        micGranted = mic,
        overlayGranted = overlay,
        serviceRunning = running,
        resultCopied = copied,
        mode = mode,
    )
}
