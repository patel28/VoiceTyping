package com.voicetyping.directtest

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsCodecTest {
    @Test fun defaultsAreHoldToRecordWithNoKeyAndCurrentModel() {
        assertEquals(AppSettings(), AppSettingsCodec.decode(emptyMap()))
    }

    @Test fun encodesStableRecordingModeValues() {
        val encoded = AppSettingsCodec.encode(AppSettings(RecordingMode.TAP_TO_TOGGLE, " key ", " model "))
        assertEquals("TAP_TO_TOGGLE", encoded[AppSettingsCodec.KEY_RECORDING_MODE])
        assertEquals("key", encoded[AppSettingsCodec.KEY_API_KEY])
        assertEquals("model", encoded[AppSettingsCodec.KEY_REFINEMENT_MODEL])
    }

    @Test fun decodesUnknownOrBlankValuesSafely() {
        val settings = AppSettingsCodec.decode(mapOf(
            AppSettingsCodec.KEY_RECORDING_MODE to "something_new",
            AppSettingsCodec.KEY_API_KEY to "  secret  ",
            AppSettingsCodec.KEY_REFINEMENT_MODEL to " ",
        ))
        assertEquals(RecordingMode.HOLD_TO_RECORD, settings.recordingMode)
        assertEquals("secret", settings.apiKey)
        assertEquals(AppSettings.DEFAULT_REFINEMENT_MODEL, settings.refinementModel)
    }
}
