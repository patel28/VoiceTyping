package com.voicetyping.directtest

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingInsertStoreTest {
    private lateinit var store: PendingInsertStore

    @Before
    fun setUp() {
        store = PendingInsertStore(ApplicationProvider.getApplicationContext())
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun createAndConsumeOnlyOnce() {
        val pending = store.create(nowMillis = 1_000L)
        assertEquals(PendingInsertStore.State.READY, store.read(1_001L)?.state)

        val claimed = store.claimReady(1_002L)
        assertNotNull(claimed)
        assertNull(store.claimReady(1_003L))
        assertTrue(store.markSent(pending.requestId))
        assertEquals(PendingInsertStore.State.SENT, store.read(1_004L)?.state)
    }

    @Test
    fun readyRequestExpiresAfterTwoMinutes() {
        store.create(nowMillis = 10L)
        val expired = store.read(10L + PendingInsertStore.TTL_MILLIS + 1L)
        assertEquals(PendingInsertStore.State.EXPIRED, expired?.state)
        assertNull(store.claimReady(10L + PendingInsertStore.TTL_MILLIS + 2L))
    }

    @Test
    fun cancelOnlyAppliesToReadyRequest() {
        store.create(nowMillis = 20L)
        assertTrue(store.cancelReady())
        assertFalse(store.cancelReady())
        assertEquals(PendingInsertStore.State.CANCELLED, store.read(21L)?.state)
    }

    @Test
    fun failedUncertainRequiresExplicitRetry() {
        val pending = store.create(nowMillis = 30L)
        assertNotNull(store.claimReady(31L))
        assertTrue(store.markFailedUncertain(pending.requestId))
        assertNull(store.claimReady(32L))

        val retried = store.retryFailedUncertain(33L)
        assertEquals(PendingInsertStore.State.READY, retried?.state)
        assertTrue(retried?.requestId != pending.requestId)
    }

    @Test
    fun staleDispatchBecomesFailedUncertain() {
        store.create(nowMillis = 100L)
        assertNotNull(store.claimReady(101L))
        val stale = store.read(100L + PendingInsertStore.TTL_MILLIS + 1L)
        assertEquals(PendingInsertStore.State.FAILED_UNCERTAIN, stale?.state)
    }
}
