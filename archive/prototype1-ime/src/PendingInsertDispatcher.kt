package com.voicetyping.directtest

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/** Performs the final editor recheck, one-way claim, and commit as a single testable operation. */
object PendingInsertDispatcher {
    enum class Result { NO_PENDING, UNSUPPORTED_FIELD, NO_CONNECTION, SENT, FAILED_UNCERTAIN }

    fun dispatch(
        store: PendingInsertStore,
        editor: EditorInfo?,
        connection: InputConnection?,
        nowMillis: Long = System.currentTimeMillis(),
    ): Result {
        if (!InputFieldSafety.isOrdinaryTextField(editor) || InputFieldSafety.isRecorderHost(editor)) {
            return Result.UNSUPPORTED_FIELD
        }
        if (connection == null) return Result.NO_CONNECTION
        val claimed = store.claimReady(nowMillis) ?: return Result.NO_PENDING
        val committed = runCatching { connection.commitText(claimed.text, 1) }.getOrDefault(false)
        return if (committed) {
            store.markSent(claimed.requestId)
            Result.SENT
        } else {
            store.markFailedUncertain(claimed.requestId)
            Result.FAILED_UNCERTAIN
        }
    }
}
