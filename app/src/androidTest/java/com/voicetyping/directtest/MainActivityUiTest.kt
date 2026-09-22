package com.voicetyping.directtest

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.not
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {

    @Test
    fun setupScreenShowsOneReadinessInstrumentAndOnePrimaryAction() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText(R.string.app_name)).check(matches(isDisplayed()))
            onView(withText(R.string.main_trust_line)).check(matches(isDisplayed()))
            onView(withId(R.id.open_settings)).check(matches(isDisplayed()))

            // The rail and the two permission rows are the readiness instrument.
            onView(withId(R.id.signal_rail)).check(matches(isDisplayed()))
            onView(withText(R.string.perm_mic_title)).check(matches(isDisplayed()))
            onView(withText(R.string.perm_overlay_title)).check(matches(isDisplayed()))

            // Exactly one primary action, plus the mode summary as a quiet secondary row.
            onView(withId(R.id.primary_action)).check(matches(isDisplayed()))
            onView(withId(R.id.mode_summary)).check(matches(isDisplayed()))

            onView(withId(R.id.step_gesture)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText(R.string.sandbox_label)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.sandbox_field)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText(R.string.sandbox_helper_idle)).perform(scrollTo())
                .check(matches(isDisplayed()))

            // This build is not an IME and never claims to insert text by itself.
            onView(withText("Enable / choose VoiceTyping Insert")).check(doesNotExist())
        }
    }

    @Test
    fun privacyExplanationStaysCollapsedUntilTheRowIsOpened() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.privacy_body)).check(matches(not(isDisplayed())))
            scenario.onActivity { it.findViewById<View>(R.id.privacy_row).performClick() }
            onView(withId(R.id.privacy_body)).perform(scrollTo()).check(matches(isDisplayed()))
        }
    }

    @Test
    fun blankApiKeyShowsRefinementWarningAndOpensSettings() = withApiKey("") {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.api_key_missing_warning)).check(matches(isDisplayed()))
            onView(withText(R.string.api_key_missing_warning_body)).check(matches(isDisplayed()))
            onView(withId(R.id.api_key_missing_warning)).perform(click())
            onView(withText(R.string.settings_title)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun savedApiKeyHidesRefinementWarning() = withApiKey("instrumentation-test-key") {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.api_key_missing_warning)).check(matches(not(isDisplayed())))
        }
    }

    private fun withApiKey(apiKey: String, body: () -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AppSettingsStore(context)
        val original = store.read()
        assertTrue(store.save(original.copy(apiKey = apiKey)))
        try {
            body()
        } finally {
            assertTrue(store.save(original))
        }
    }
}
