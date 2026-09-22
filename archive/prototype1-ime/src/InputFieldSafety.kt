package com.voicetyping.directtest

import android.text.InputType
import android.view.inputmethod.EditorInfo

object InputFieldSafety {
    fun isOrdinaryTextField(editorInfo: EditorInfo?): Boolean {
        editorInfo ?: return false
        if (editorInfo.inputType == InputType.TYPE_NULL) return false
        if (editorInfo.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false

        val variation = editorInfo.inputType and InputType.TYPE_MASK_VARIATION
        return variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
    }

    fun isRecorderHost(editorInfo: EditorInfo?): Boolean =
        editorInfo?.privateImeOptions
            ?.split(',')
            ?.map(String::trim)
            ?.contains(PendingInsertStore.RECORDER_PRIVATE_IME_OPTION) == true
}
