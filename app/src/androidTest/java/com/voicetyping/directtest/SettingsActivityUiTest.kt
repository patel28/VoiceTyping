package com.voicetyping.directtest

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsActivityUiTest {
    @Test fun settingsShowsBothRecordingModesAndPrivateKeyControls() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withText("Settings")).check(matches(isDisplayed()))
            onView(withText("Hold to record\nHold the floating mic while speaking; release to finish."))
                .check(matches(isDisplayed()))
            onView(withText("Tap to toggle\nTap once to start recording and once again to finish."))
                .check(matches(isDisplayed()))
            onView(withText("API key")).check(matches(isDisplayed()))
            onView(withText("Refinement model")).check(matches(isDisplayed()))
            onView(withText("Save settings")).check(matches(isDisplayed()))
        }
    }
}
