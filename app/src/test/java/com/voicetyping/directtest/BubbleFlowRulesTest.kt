package com.voicetyping.directtest

import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleFlowRulesTest {
    @Test fun normalFlowReachesCopied() {
        val recording = BubbleFlowRules.hold(BubbleFlowState.IDLE)
        val processing = BubbleFlowRules.release(recording)
        val copied = BubbleFlowRules.processingFinished(success = true)
        assertEquals(BubbleFlowState.RECORDING, recording)
        assertEquals(BubbleFlowState.PROCESSING, processing)
        assertEquals(BubbleFlowState.COPIED, copied)
    }

    @Test fun failedProcessingReachesError() {
        assertEquals(BubbleFlowState.ERROR, BubbleFlowRules.processingFinished(success = false))
    }

    @Test fun copiedAndErrorResetToIdle() {
        assertEquals(BubbleFlowState.IDLE, BubbleFlowRules.reset(BubbleFlowState.COPIED))
        assertEquals(BubbleFlowState.IDLE, BubbleFlowRules.reset(BubbleFlowState.ERROR))
    }

    @Test fun invalidEventsDoNotSkipStates() {
        assertEquals(BubbleFlowState.PROCESSING, BubbleFlowRules.hold(BubbleFlowState.PROCESSING))
        assertEquals(BubbleFlowState.IDLE, BubbleFlowRules.release(BubbleFlowState.IDLE))
        assertEquals(BubbleFlowState.RECORDING, BubbleFlowRules.reset(BubbleFlowState.RECORDING))
    }
}
