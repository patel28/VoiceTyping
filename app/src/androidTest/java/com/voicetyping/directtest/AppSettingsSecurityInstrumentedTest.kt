package com.voicetyping.directtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsSecurityInstrumentedTest {
    @Test fun apiKeyRoundTripsWithoutPlaintextPreferencesStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rawKey = "sk-device-security-test-only"
        val store = AppSettingsStore(context)
        val original = store.read()

        try {
            assertTrue(
                store.save(
                    AppSettings(
                        recordingMode = RecordingMode.TAP_TO_TOGGLE,
                        apiKey = rawKey,
                        refinementModel = "gpt-5.4",
                    ),
                ),
            )
            assertEquals(rawKey, store.read().apiKey)
            assertEquals(RecordingMode.TAP_TO_TOGGLE, store.read().recordingMode)

            val ordinaryPreferences = context.getSharedPreferences("voicetyping_settings", 0).all.toString()
            val secretPreferences = context.getSharedPreferences("voicetyping_secrets", 0).all.toString()
            assertFalse(ordinaryPreferences.contains(rawKey))
            assertFalse(secretPreferences.contains(rawKey))
            assertTrue(secretPreferences.isNotBlank())
        } finally {
            store.save(original)
        }
    }
}
