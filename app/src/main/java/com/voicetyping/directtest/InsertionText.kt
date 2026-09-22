package com.voicetyping.directtest

/** Produces the exact text used for clipboard and drag insertion, ending in one ASCII space. */
object InsertionText {
    fun normalize(text: String): String? {
        val trimmed = text.trim()
        return trimmed.takeIf { it.isNotEmpty() }?.plus(' ')
    }
}
