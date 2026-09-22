package com.voicetyping.directtest

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {
    @Test
    fun setupScreenShowsReadinessAndSettingsEntryPoint() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("VoiceTyping")).check(matches(isDisplayed()))
            onView(withText("Settings")).check(matches(isDisplayed()))
            onView(withText("Allow microphone")).check(matches(isDisplayed()))
            onView(withText("Open overlay permission")).check(matches(isDisplayed()))
            onView(withText("Start floating mic")).check(matches(isDisplayed()))
            onView(withText("HOW IT WORKS")).check(matches(isDisplayed()))
            onView(withSubstring("Grant microphone and display-over-other-apps access")).check(matches(isDisplayed()))
            onView(withText("PRIVACY & REFINEMENT")).check(matches(isDisplayed()))
            onView(withSubstring("production refinement should use your server")).check(matches(isDisplayed()))
            onView(withText("Enable / choose VoiceTyping Insert")).check(doesNotExist())
        }
    }
}
