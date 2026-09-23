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

    /**
     * An overlay can observe an IME session starting, but cannot inspect which external editor
     * owns it. Terminal feedback must not block the next recording when that session begins.
     */
    fun shouldResetForNewKeyboardSession(
        mode: BubbleVisibilityMode,
        wasImeVisible: Boolean,
        isImeVisible: Boolean,
        state: BubbleFlowState,
    ): Boolean = mode == BubbleVisibilityMode.WHEN_KEYBOARD_VISIBLE_EXPERIMENTAL &&
        !wasImeVisible && isImeVisible &&
        (state == BubbleFlowState.COPIED || state == BubbleFlowState.ERROR)
}
