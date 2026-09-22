package com.voicetyping.directtest

enum class BubbleFlowState { IDLE, RECORDING, PROCESSING, COPIED, ERROR }

/** Pure state rules kept separate from Android gesture and audio plumbing. */
object BubbleFlowRules {
    fun hold(state: BubbleFlowState): BubbleFlowState = when (state) {
        BubbleFlowState.IDLE -> BubbleFlowState.RECORDING
        else -> state
    }

    fun release(state: BubbleFlowState): BubbleFlowState = when (state) {
        BubbleFlowState.RECORDING -> BubbleFlowState.PROCESSING
        else -> state
    }

    fun processingFinished(success: Boolean): BubbleFlowState =
        if (success) BubbleFlowState.COPIED else BubbleFlowState.ERROR

    fun reset(state: BubbleFlowState): BubbleFlowState = when (state) {
        BubbleFlowState.COPIED, BubbleFlowState.ERROR -> BubbleFlowState.IDLE
        else -> state
    }
}
