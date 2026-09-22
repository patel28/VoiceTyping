package com.voicetyping.directtest

object BubbleVisibilityRules {
    fun shouldShow(
        mode: BubbleVisibilityMode,
        imeVisible: Boolean,
        state: BubbleFlowState,
    ): Boolean = when (mode) {
        BubbleVisibilityMode.ALWAYS_SHOW -> true
        BubbleVisibilityMode.WHEN_KEYBOARD_VISIBLE_EXPERIMENTAL -> imeVisible ||
            state == BubbleFlowState.RECORDING || state == BubbleFlowState.PROCESSING
    }
}
