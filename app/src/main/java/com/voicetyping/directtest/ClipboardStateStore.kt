package com.voicetyping.directtest

/** In-memory record of the last clipboard copy for the voice flow. */
object ClipboardStateStore {
    const val TTL_MILLIS = 2 * 60 * 1000L

    @Volatile
    private var copiedText: String? = null

    @Volatile
    private var copiedAtMillis: Long = -1L

    @Synchronized
    fun markCopied(text: String, nowMillis: Long = System.currentTimeMillis()) {
        copiedText = text
        copiedAtMillis = nowMillis
    }

    @Synchronized
    fun read(nowMillis: Long = System.currentTimeMillis()): String? {
        val text = copiedText ?: return null
        if (copiedAtMillis < 0L || nowMillis - copiedAtMillis > TTL_MILLIS) {
            clear()
            return null
        }
        return text
    }

    @Synchronized
    fun consume(nowMillis: Long = System.currentTimeMillis()): String? {
        val text = read(nowMillis) ?: return null
        clear()
        return text
    }

    @Synchronized
    fun clear() {
        copiedText = null
        copiedAtMillis = -1L
    }
}
