package com.voicetyping.directtest

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ClipboardStateStoreTest {
    @Before
    fun setUp() {
        ClipboardStateStore.clear()
    }

    @After
    fun tearDown() {
        ClipboardStateStore.clear()
    }

    @Test
    fun copiedTextIsReadableBeforeExpiry() {
        ClipboardStateStore.markCopied("hello world", nowMillis = 1_000L)
        assertEquals("hello world", ClipboardStateStore.read(1_001L))
    }

    @Test
    fun copiedTextExpiresAfterTtl() {
        ClipboardStateStore.markCopied("hello world", nowMillis = 10L)
        assertNull(ClipboardStateStore.read(10L + ClipboardStateStore.TTL_MILLIS + 1L))
        assertNull(ClipboardStateStore.read(10L + ClipboardStateStore.TTL_MILLIS + 2L))
    }

    @Test
    fun consumeReturnsTextOnce() {
        ClipboardStateStore.markCopied("transcribed speech", nowMillis = 20L)
        assertEquals("transcribed speech", ClipboardStateStore.consume(21L))
        assertNull(ClipboardStateStore.read(22L))
        assertNull(ClipboardStateStore.consume(23L))
    }

    @Test
    fun consumeExpiredReturnsNull() {
        ClipboardStateStore.markCopied("transcribed speech", nowMillis = 30L)
        assertNull(ClipboardStateStore.consume(30L + ClipboardStateStore.TTL_MILLIS + 1L))
    }

    @Test
    fun storesArbitraryTranscriptNotFixedText() {
        ClipboardStateStore.markCopied("buy milk tomorrow", nowMillis = 40L)
        assertEquals("buy milk tomorrow", ClipboardStateStore.read(41L))
    }
}
