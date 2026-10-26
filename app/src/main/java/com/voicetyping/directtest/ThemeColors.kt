package com.voicetyping.directtest

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat

/** Resolves one of the vt* colour attributes against whichever theme the context carries. */
@ColorInt
fun Context.themeColor(@AttrRes attr: Int): Int {
    val resolved = TypedValue()
    theme.resolveAttribute(attr, resolved, true)
    return if (resolved.resourceId != 0) ContextCompat.getColor(this, resolved.resourceId) else resolved.data
}

/** The style each saved theme maps to. Kept out of [AppSettings] so the codec stays framework-free. */
@StyleRes
fun AppTheme.styleRes(): Int = when (this) {
    AppTheme.AMBER -> R.style.Theme_VoiceTyping_Amber
    AppTheme.LAGOON -> R.style.Theme_VoiceTyping_Lagoon
    AppTheme.SIGNAL -> R.style.Theme_VoiceTyping_Signal
}
