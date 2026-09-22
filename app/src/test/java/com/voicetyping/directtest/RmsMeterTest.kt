package com.voicetyping.directtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RmsMeterTest {
    @Test fun silenceHasNoSignal() {
        assertEquals(0f, RmsMeter.level(ShortArray(64), 64), 0.0001f)
    }

    @Test fun fakeAudioProducesVisibleSignal() {
        val samples = ShortArray(64) { if (it % 2 == 0) 12_000 else -12_000 }
        assertTrue(RmsMeter.level(samples, samples.size) > 0.3f)
    }
}
