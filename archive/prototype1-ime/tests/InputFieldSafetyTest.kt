package com.voicetyping.directtest

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputFieldSafetyTest {
    @Test
    fun acceptsOrdinaryTextAndEmail() {
        assertTrue(InputFieldSafety.isOrdinaryTextField(editor(InputType.TYPE_CLASS_TEXT)))
        assertTrue(
            InputFieldSafety.isOrdinaryTextField(
                editor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
            ),
        )
    }

    @Test
    fun rejectsPasswordsPinAndNonText() {
        assertFalse(InputFieldSafety.isOrdinaryTextField(editor(InputType.TYPE_NULL)))
        assertFalse(InputFieldSafety.isOrdinaryTextField(editor(InputType.TYPE_CLASS_NUMBER)))
        assertFalse(
            InputFieldSafety.isOrdinaryTextField(
                editor(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD),
            ),
        )
        assertFalse(
            InputFieldSafety.isOrdinaryTextField(
                editor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
            ),
        )
        assertFalse(
            InputFieldSafety.isOrdinaryTextField(
                editor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
            ),
        )
        assertFalse(
            InputFieldSafety.isOrdinaryTextField(
                editor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD),
            ),
        )
    }

    @Test
    fun recognizesRecorderHostMarker() {
        val info = editor(InputType.TYPE_CLASS_TEXT).apply {
            privateImeOptions = "other,${PendingInsertStore.RECORDER_PRIVATE_IME_OPTION}"
        }
        assertTrue(InputFieldSafety.isRecorderHost(info))
    }

    @Test
    fun rejectsMissingEditorAndIgnoresUnrelatedMarker() {
        assertFalse(InputFieldSafety.isOrdinaryTextField(null))
        val info = editor(InputType.TYPE_CLASS_TEXT).apply { privateImeOptions = "unrelated" }
        assertFalse(InputFieldSafety.isRecorderHost(info))
    }

    private fun editor(inputType: Int) = EditorInfo().apply { this.inputType = inputType }
}
