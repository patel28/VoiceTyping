package com.voicetyping.directtest

/** Pure decisions used to protect the draggable idle bubble from accidental recordings. */
object RecordingGestureRules {
    fun shouldStartOnRelease(mode: RecordingMode, state: BubbleFlowState, moved: Boolean): Boolean =
        mode == RecordingMode.TAP_TO_TOGGLE && state == BubbleFlowState.IDLE && !moved

    fun shouldStopOnRelease(state: BubbleFlowState): Boolean = state == BubbleFlowState.RECORDING

    fun shouldArmHold(mode: RecordingMode, state: BubbleFlowState): Boolean =
        mode == RecordingMode.HOLD_TO_RECORD && state == BubbleFlowState.IDLE
}
