package com.voicetyping.directtest

import android.text.InputType
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingInsertDispatcherTest {
    private lateinit var store: PendingInsertStore

    @Before fun setUp() {
        store = PendingInsertStore(ApplicationProvider.getApplicationContext())
        store.clear()
    }

    @After fun tearDown() {
        store.clear()
    }

    @Test fun fakeConnectionReceivesExactTextOnce() {
        store.create(nowMillis = 100L)
        val connection = CapturingConnection(true)
        val editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }

        assertEquals(PendingInsertDispatcher.Result.SENT, PendingInsertDispatcher.dispatch(store, editor, connection, 101L))
        assertEquals(PendingInsertStore.DUMMY_TEXT, connection.committed)
        assertEquals(PendingInsertDispatcher.Result.NO_PENDING, PendingInsertDispatcher.dispatch(store, editor, connection, 102L))
    }

    @Test fun falseCommitBecomesUncertainAndIsNotReplayed() {
        store.create(nowMillis = 200L)
        val editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        assertEquals(
            PendingInsertDispatcher.Result.FAILED_UNCERTAIN,
            PendingInsertDispatcher.dispatch(store, editor, CapturingConnection(false), 201L),
        )
        assertEquals(PendingInsertStore.State.FAILED_UNCERTAIN, store.read(202L)?.state)
        assertEquals(
            PendingInsertDispatcher.Result.NO_PENDING,
            PendingInsertDispatcher.dispatch(store, editor, CapturingConnection(true), 203L),
        )
    }

    @Test fun nullConnectionDoesNotConsumePendingText() {
        store.create(nowMillis = 300L)
        val editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        assertEquals(PendingInsertDispatcher.Result.NO_CONNECTION, PendingInsertDispatcher.dispatch(store, editor, null, 301L))
        assertEquals(PendingInsertStore.State.READY, store.read(302L)?.state)
    }

    private class CapturingConnection(private val result: Boolean) : BaseInputConnection(
        View(ApplicationProvider.getApplicationContext()),
        false,
    ) {
        var committed: CharSequence? = null
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committed = text
            return result
        }
    }
}
