package com.voicetyping.directtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleVisibilityRulesTest {
    @Test fun alwaysShowIgnoresKeyboardAndFlowState() {
        BubbleFlowState.values().forEach { state ->
            assertTrue(BubbleVisibilityRules.shouldShow(BubbleVisibilityMode.ALWAYS_SHOW, false, state))
        }
    }

    @Test fun experimentalFollowsKeyboardExceptDuringActiveWork() {
        BubbleFlowState.values().forEach { state ->
            assertTrue(
                BubbleVisibilityRules.shouldShow(
                    BubbleVisibilityMode.WHEN_KEYBOARD_VISIBLE_EXPERIMENTAL,
                    true,
                    state,
                ),
            )
            assertEquals(
                state == BubbleFlowState.RECORDING || state == BubbleFlowState.PROCESSING,
                BubbleVisibilityRules.shouldShow(
                    BubbleVisibilityMode.WHEN_KEYBOARD_VISIBLE_EXPERIMENTAL,
                    false,
                    state,
                ),
            )
        }
    }

    @Test fun experimentalResetsOnlyTerminalFeedbackWhenANewKeyboardSessionStarts() {
        assertTrue(
            BubbleVisibilityRules.shouldResetForNewKeyboardSession(
                BubbleVisibilityMode.WHEN_KEYBOARD_VISIBLE_EXPERIMENTAL,
                wasImeVisible = false,
                isImeVisible = true,
                state = BubbleFlowState.COPIED,
            ),
        )
        assertTrue(
            BubbleVisibilityRules.shouldResetForNewKeyboardSession(
                BubbleVisibilityMode.WHEN_KEYBOARD_VISIBLE_EXPERIMENTAL,
                wasImeVisible = false,
                isImeVisible = true,
                state = BubbleFlowState.ERROR,
            ),
        )

        BubbleFlowState.values()
            .filterNot { it == BubbleFlowState.COPIED || it == BubbleFlowState.ERROR }
            .forEach { state ->
                assertEquals(
                    false,
                    BubbleVisibilityRules.shouldResetForNewKeyboardSession(
                        BubbleVisibilityMode.WHEN_KEYBOARD_VISIBLE_EXPERIMENTAL,
                        wasImeVisible = false,
                        isImeVisible = true,
                        state = state,
                    ),
                )
            }
        assertEquals(
            false,
            BubbleVisibilityRules.shouldResetForNewKeyboardSession(
                BubbleVisibilityMode.ALWAYS_SHOW,
                wasImeVisible = false,
                isImeVisible = true,
                state = BubbleFlowState.COPIED,
            ),
        )
        assertEquals(
            false,
            BubbleVisibilityRules.shouldResetForNewKeyboardSession(
                BubbleVisibilityMode.WHEN_KEYBOARD_VISIBLE_EXPERIMENTAL,
                wasImeVisible = true,
                isImeVisible = true,
                state = BubbleFlowState.COPIED,
            ),
        )
    }
}
