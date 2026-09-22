package com.voicetyping.directtest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingGestureRulesTest {
    @Test fun holdModeArmsOnlyFromIdle() {
        assertTrue(RecordingGestureRules.shouldArmHold(RecordingMode.HOLD_TO_RECORD, BubbleFlowState.IDLE))
        assertFalse(RecordingGestureRules.shouldArmHold(RecordingMode.TAP_TO_TOGGLE, BubbleFlowState.IDLE))
        assertFalse(RecordingGestureRules.shouldArmHold(RecordingMode.HOLD_TO_RECORD, BubbleFlowState.COPIED))
    }

    @Test fun tapModeStartsOnlyAfterAnUnmovedIdleTap() {
        assertTrue(RecordingGestureRules.shouldStartOnRelease(RecordingMode.TAP_TO_TOGGLE, BubbleFlowState.IDLE, moved = false))
        assertFalse(RecordingGestureRules.shouldStartOnRelease(RecordingMode.TAP_TO_TOGGLE, BubbleFlowState.IDLE, moved = true))
        assertFalse(RecordingGestureRules.shouldStartOnRelease(RecordingMode.HOLD_TO_RECORD, BubbleFlowState.IDLE, moved = false))
    }

    @Test fun eitherModeStopsAnActiveRecordingOnRelease() {
        assertTrue(RecordingGestureRules.shouldStopOnRelease(BubbleFlowState.RECORDING))
        assertFalse(RecordingGestureRules.shouldStopOnRelease(BubbleFlowState.PROCESSING))
    }
}
