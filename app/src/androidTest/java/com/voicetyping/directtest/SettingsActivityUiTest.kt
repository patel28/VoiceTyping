package com.voicetyping.directtest

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsActivityUiTest {

    private val store = AppSettingsStore(InstrumentationRegistry.getInstrumentation().targetContext)
    private lateinit var original: AppSettings

    @Before fun captureSettings() { original = store.read() }

    // The explicit save in the last test writes real settings; put the device back as it was.
    @After fun restoreSettings() { store.save(original) }

    @Test fun recordingControlOffersBothGesturesWithDiagrams() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.settings_toolbar)).check(matches(isDisplayed()))
            onView(withText(R.string.settings_title)).check(matches(isDisplayed()))
            onView(withId(R.id.mode_hold))
                .check(matches(isDisplayed()))
                .check(matches(withSubstring(string(R.string.mode_hold))))
                .check(matches(withSubstring(string(R.string.mode_hold_detail))))
            onView(withId(R.id.mode_tap))
                .check(matches(isDisplayed()))
                .check(matches(withSubstring(string(R.string.mode_tap))))
                .check(matches(withSubstring(string(R.string.mode_tap_detail))))
            onView(withText(R.string.recording_limit_note)).check(matches(isDisplayed()))
        }
    }

    @Test fun refinementIsCollapsedAndTheKeyStaysMaskedUntilRevealed() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            onView(withId(R.id.advanced_content)).check(matches(not(isDisplayed())))
            scenario.onActivity { it.findViewById<View>(R.id.advanced_row).performClick() }

            onView(withId(R.id.api_key_field)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.model_field)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText(R.string.api_key_warning)).perform(scrollTo())
                .check(matches(isDisplayed()))

            onView(withId(R.id.api_key_visibility)).perform(scrollTo())
                .check(matches(withText(R.string.action_show)))
            scenario.onActivity { it.findViewById<View>(R.id.api_key_visibility).performClick() }
            onView(withId(R.id.api_key_visibility)).check(matches(withText(R.string.action_hide)))
        }
    }

    @Test fun bubbleVisibilityOffersSafeDefaultAndExperimentalOption() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.bubble_visibility_always))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
                .check(matches(isChecked()))
            onView(withId(R.id.bubble_visibility_keyboard))
                .perform(scrollTo())
                .check(matches(withText(R.string.bubble_visibility_keyboard)))
            onView(withText(R.string.bubble_visibility_note))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
        }
    }

    @Test fun saveReportsInlineInsteadOfOnlyToasting() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            onView(withId(R.id.save_status)).check(matches(not(isDisplayed())))
            scenario.onActivity { it.findViewById<View>(R.id.save_action).performClick() }
            onView(withId(R.id.save_status)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText(R.string.save_success)).check(matches(isDisplayed()))
        }
    }

    private fun string(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)
}
