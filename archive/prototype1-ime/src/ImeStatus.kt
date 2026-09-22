package com.voicetyping.directtest

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager

/** Android 13-15 safe IME status queries without restricted secure-setting reads. */
object ImeStatus {
    fun isOurImeEnabled(context: Context): Boolean {
        val manager = context.getSystemService(InputMethodManager::class.java)
        return manager.enabledInputMethodList.any { it.isOurs(context) }
    }

    fun isOurImeSelected(context: Context): Boolean {
        val manager = context.getSystemService(InputMethodManager::class.java)
        if (Build.VERSION.SDK_INT >= 34) {
            return manager.currentInputMethodInfo?.isOurs(context) == true
        }
        // Android 13 permits this read; Android 14+ is handled by the public API above.
        val selected = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        ) ?: return false
        return selected.contains(context.packageName) && selected.contains(SERVICE_CLASS_NAME)
    }

    private fun InputMethodInfo.isOurs(context: Context): Boolean =
        packageName == context.packageName && serviceName.endsWith(SERVICE_CLASS_NAME)

    private const val SERVICE_CLASS_NAME = "VoiceTypingInputMethodService"
}
