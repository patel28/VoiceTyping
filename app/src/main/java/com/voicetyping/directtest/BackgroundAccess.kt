package com.voicetyping.directtest

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * The floating mic is a user-started, continuously visible control, so it has to survive the
 * aggressive background limits some vendors apply - Xiaomi's MIUI in particular, where a
 * foreground service is still stopped once the screen has been off for a while.
 *
 * Two settings decide this and neither can be changed from inside the app: the battery
 * optimisation exemption, which is standard Android and can at least be read back, and the
 * vendor's own autostart list, which can only be opened for the user to switch on themselves.
 */
object BackgroundAccess {

    /** True when Android will leave the service alone. Not a promise that MIUI will. */
    fun isBatteryExempt(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the battery optimisation list for the user to pick VoiceTyping out of.
     *
     * The one-tap dialog would need REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, which Google Play only
     * accepts from a short list of app types that a dictation overlay is not on, so this takes
     * the extra tap instead of risking the listing.
     */
    fun openBatterySettings(context: Context) {
        context.startActivitySafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /**
     * Whether this phone has a vendor autostart list at all. Decided from the manufacturer rather
     * than by resolving the activity, because package visibility hides other apps from
     * resolveActivity unless they are declared in <queries>, and enumerating packages is exactly
     * what this app promises not to do.
     */
    fun hasVendorAutostartList(): Boolean =
        Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

    /**
     * Opens MIUI's autostart screen, falling back to this app's system settings page when the
     * component has been renamed - it has changed more than once across MIUI versions.
     */
    fun openAutostartSettings(context: Context) {
        val autostart = Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
        )
        if (context.startActivitySafely(autostart)) return
        context.startActivitySafely(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null)),
        )
    }

    private fun Context.startActivitySafely(intent: Intent): Boolean = runCatching {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
