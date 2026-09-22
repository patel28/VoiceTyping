package com.voicetyping.directtest

import android.annotation.SuppressLint
import android.content.Context
import java.util.UUID

@SuppressLint("ApplySharedPref") // commit() is intentional: state transitions must be durable before IME dispatch.
class PendingInsertStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class State {
        READY,
        DISPATCHING,
        SENT,
        EXPIRED,
        CANCELLED,
        FAILED_UNCERTAIN,
    }

    data class PendingInsert(
        val requestId: String,
        val text: String,
        val createdAtMillis: Long,
        val state: State,
    )

    fun create(
        text: String = DUMMY_TEXT,
        nowMillis: Long = System.currentTimeMillis(),
    ): PendingInsert = synchronized(PROCESS_LOCK) {
        PendingInsert(UUID.randomUUID().toString(), text, nowMillis, State.READY).also(::write)
    }

    fun read(nowMillis: Long = System.currentTimeMillis()): PendingInsert? = synchronized(PROCESS_LOCK) {
        val pending = readRaw() ?: return null
        if (nowMillis - pending.createdAtMillis > TTL_MILLIS) {
            val staleState = when (pending.state) {
                State.READY -> State.EXPIRED
                State.DISPATCHING -> State.FAILED_UNCERTAIN
                else -> pending.state
            }
            if (staleState != pending.state) {
                return pending.copy(state = staleState).also(::write)
            }
        }
        return pending
    }

    fun claimReady(nowMillis: Long = System.currentTimeMillis()): PendingInsert? = synchronized(PROCESS_LOCK) {
        val pending = read(nowMillis) ?: return null
        if (pending.state != State.READY) return null
        val claimed = pending.copy(state = State.DISPATCHING)
        write(claimed)
        return claimed
    }

    fun markSent(requestId: String): Boolean = synchronized(PROCESS_LOCK) {
        transition(requestId, State.DISPATCHING, State.SENT)
    }

    fun markFailedUncertain(requestId: String): Boolean = synchronized(PROCESS_LOCK) {
        transition(requestId, State.DISPATCHING, State.FAILED_UNCERTAIN)
    }

    fun cancelReady(): Boolean = synchronized(PROCESS_LOCK) {
        val pending = readRaw() ?: return false
        if (pending.state != State.READY) return false
        write(pending.copy(state = State.CANCELLED))
        return true
    }

    fun retryFailedUncertain(nowMillis: Long = System.currentTimeMillis()): PendingInsert? =
        synchronized(PROCESS_LOCK) {
            val pending = readRaw() ?: return null
            if (pending.state != State.FAILED_UNCERTAIN) return null
            pending.copy(
                requestId = UUID.randomUUID().toString(),
                createdAtMillis = nowMillis,
                state = State.READY,
            ).also(::write)
        }

    fun clear() = synchronized(PROCESS_LOCK) {
        prefs.edit().clear().commit()
    }

    private fun transition(requestId: String, from: State, to: State): Boolean {
        val pending = readRaw() ?: return false
        if (pending.requestId != requestId || pending.state != from) return false
        write(pending.copy(state = to))
        return true
    }

    private fun readRaw(): PendingInsert? {
        val requestId = prefs.getString(KEY_REQUEST_ID, null) ?: return null
        val text = prefs.getString(KEY_TEXT, null) ?: return null
        val createdAt = prefs.getLong(KEY_CREATED_AT, -1L).takeIf { it >= 0L } ?: return null
        val stateName = prefs.getString(KEY_STATE, null) ?: return null
        val state = runCatching { State.valueOf(stateName) }.getOrNull() ?: return null
        return PendingInsert(requestId, text, createdAt, state)
    }

    private fun write(pending: PendingInsert) {
        prefs.edit()
            .putString(KEY_REQUEST_ID, pending.requestId)
            .putString(KEY_TEXT, pending.text)
            .putLong(KEY_CREATED_AT, pending.createdAtMillis)
            .putString(KEY_STATE, pending.state.name)
            .commit()
    }

    companion object {
        const val DUMMY_TEXT = "Hello World from App Name"
        const val RECORDER_PRIVATE_IME_OPTION = "voicetyping:recorder_host"
        const val TTL_MILLIS = 2 * 60 * 1000L

        private const val PREFS_NAME = "pending_insert"
        private const val KEY_REQUEST_ID = "request_id"
        private const val KEY_TEXT = "text"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_STATE = "state"
        private val PROCESS_LOCK = Any()
    }
}
