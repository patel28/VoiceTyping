package com.voicetyping.directtest

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsCodecTest {
    @Test fun defaultsAreHoldToRecordWithNoKeyAndCurrentModel() {
        assertEquals(AppSettings(), AppSettingsCodec.decode(emptyMap()))
    }

    @Test fun encodesStableRecordingModeValues() {
        val encoded = AppSettingsCodec.encode(
            AppSettings(
                recordingMode = RecordingMode.TAP_TO_TOGGLE,
                apiKey = " key ",
                refinementModel = " model ",
            ),
        )
        assertEquals("TAP_TO_TOGGLE", encoded[AppSettingsCodec.KEY_RECORDING_MODE])
        assertEquals("ALWAYS_SHOW", encoded[AppSettingsCodec.KEY_BUBBLE_VISIBILITY_MODE])
        assertEquals("AMBER", encoded[AppSettingsCodec.KEY_THEME])
        assertEquals("key", encoded[AppSettingsCodec.KEY_API_KEY])
        assertEquals("model", encoded[AppSettingsCodec.KEY_REFINEMENT_MODEL])
    }

    @Test fun decodesUnknownOrBlankValuesSafely() {
        val settings = AppSettingsCodec.decode(mapOf(
            AppSettingsCodec.KEY_RECORDING_MODE to "something_new",
            AppSettingsCodec.KEY_BUBBLE_VISIBILITY_MODE to "something_new",
            AppSettingsCodec.KEY_THEME to "something_new",
            AppSettingsCodec.KEY_API_KEY to "  secret  ",
            AppSettingsCodec.KEY_REFINEMENT_MODEL to " ",
        ))
        assertEquals(RecordingMode.HOLD_TO_RECORD, settings.recordingMode)
        assertEquals(BubbleVisibilityMode.ALWAYS_SHOW, settings.bubbleVisibilityMode)
        assertEquals(AppTheme.AMBER, settings.theme)
        assertEquals("secret", settings.apiKey)
        assertEquals(AppSettings.DEFAULT_REFINEMENT_MODEL, settings.refinementModel)
    }

    @Test fun decodesExperimentalVisibilityAndDefaultsWhenMissing() {
        assertEquals(
            BubbleVisibilityMode.ALWAYS_SHOW,
            AppSettingsCodec.decode(emptyMap()).bubbleVisibilityMode,
        )
        assertEquals(
            BubbleVisibilityMode.WHEN_KEYBOARD_VISIBLE_EXPERIMENTAL,
            AppSettingsCodec.decode(
                mapOf(
                    AppSettingsCodec.KEY_BUBBLE_VISIBILITY_MODE to
                        "WHEN_KEYBOARD_VISIBLE_EXPERIMENTAL",
                ),
            ).bubbleVisibilityMode,
        )
    }

    @Test fun themeDefaultsToAmberAndRoundTripsTheOthers() {
        assertEquals(AppTheme.AMBER, AppSettingsCodec.decode(emptyMap()).theme)
        for (theme in AppTheme.entries) {
            val encoded = AppSettingsCodec.encode(AppSettings(theme = theme))
            assertEquals(theme, AppSettingsCodec.decode(encoded).theme)
        }
    }

    @Test fun unreadableThemeFallsBackToAmber() {
        assertEquals(AppTheme.AMBER, AppSettingsCodec.decodeTheme(null))
        assertEquals(AppTheme.AMBER, AppSettingsCodec.decodeTheme(""))
        assertEquals(AppTheme.AMBER, AppSettingsCodec.decodeTheme("MIDNIGHT"))
    }
}
