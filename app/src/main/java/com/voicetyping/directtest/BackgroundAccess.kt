package com.voicetyping.directtest

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.VisibleForTesting

/**
 * The floating mic is a user-started, continuously visible control, so it has to survive the
 * aggressive background limits some vendors apply - Xiaomi's MIUI in particular, where a
 * foreground service is still stopped once the screen has been off for a while.
 *
 * Two settings decide this and neither can be changed from inside the app: the battery
 * restriction, which is standard Android and can at least be read back, and the vendor's own
 * autostart list, which can only be opened for the user to switch on themselves.
 */
object BackgroundAccess {

    /** True when Android will leave the service alone. Not a promise that MIUI will. */
    fun isBatteryExempt(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the screen that holds this app's own battery restriction.
     *
     * Deliberately not ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS. That list is the AOSP one,
     * and on MIUI and HyperOS the restriction that actually stops the service does not live
     * there, so the user lands on a page with no unrestricted choice to make. MIUI keeps it in
     * Power keeper's per-app screen; everywhere else it is on the app's own details page, under
     * Battery.
     *
     * The one-tap dialog would need REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, which Google Play only
     * accepts from a short list of app types that a dictation overlay is not on, so this takes
     * the extra tap instead of risking the listing.
     */
    fun openBatterySettings(context: Context) {
        val miui = brand(Build.MANUFACTURER) == "xiaomi"
        if (miui && context.startActivitySafely(miuiBatterySaver(context))) return
        context.startActivitySafely(appDetails(context))
    }

    /** MIUI's per-app battery saver chooser, where "No restrictions" is. */
    private fun miuiBatterySaver(context: Context): Intent = Intent()
        .setComponent(
            ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
            ),
        )
        .putExtra("package_name", context.packageName)
        .putExtra("package_label", context.getString(R.string.app_name))

    /**
     * Whether this phone has a vendor autostart list at all. Decided from the manufacturer rather
     * than by resolving the activity, because package visibility hides other apps from
     * resolveActivity unless they are declared in <queries>, and enumerating packages is exactly
     * what this app promises not to do.
     */
    fun hasVendorAutostartList(): Boolean =
        autostartActions(Build.MANUFACTURER).isNotEmpty() || autostartTargets(Build.MANUFACTURER).isNotEmpty()

    /** The brand name to put in front of the user, e.g. "Xiaomi phones stop apps...". */
    fun vendorName(): String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    /**
     * Opens the vendor's autostart list, trying the known ways in turn.
     *
     * Actions come first: an implicit intent is resolved by the system, so it works without the
     * target package being visible to this app, which an explicit component is not guaranteed to
     * be on Android 11 and up. The components after it are what each brand has shipped - several
     * per brand, because they get renamed between versions - and the app's own settings page is
     * the last resort, since every skin puts its background switches one tap from there.
     */
    fun openAutostartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER
        for (action in autostartActions(manufacturer)) {
            if (context.startActivitySafely(Intent(action).addCategory(Intent.CATEGORY_DEFAULT))) return
        }
        for ((packageName, className) in autostartTargets(manufacturer)) {
            if (context.startActivitySafely(Intent().setComponent(ComponentName(packageName, className)))) return
        }
        context.startActivitySafely(appDetails(context))
    }

    /** Implicit actions a skin publishes for its autostart screen. */
    @VisibleForTesting
    fun autostartActions(manufacturer: String): List<String> = when (brand(manufacturer)) {
        "xiaomi" -> listOf("miui.intent.action.OP_AUTO_START")
        else -> emptyList()
    }

    /** Known autostart components as package to class, most recent skin first. */
    @VisibleForTesting
    fun autostartTargets(manufacturer: String): List<Pair<String, String>> = when (brand(manufacturer)) {
        "xiaomi" -> listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        )
        "oppo" -> listOf(
            "com.coloros.phonemanager" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        )
        "vivo" -> listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.iqoo.secure" to "com.iqoo.secure.safeguard.PurviewTabActivity",
        )
        "huawei" -> listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        )
        "oneplus" -> listOf(
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        )
        "samsung" -> listOf(
            "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
            "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
        )
        "transsion" -> listOf(
            "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity",
        )
        "asus" -> listOf(
            "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity",
            "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity",
        )
        "meizu" -> listOf(
            "com.meizu.safe" to "com.meizu.safe.permission.SmartBGActivity",
        )
        "letv" -> listOf(
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
        )
        "nokia" -> listOf(
            "com.evenwell.powersaving.g3" to
                "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity",
        )
        else -> emptyList()
    }

    /** Sub-brands ship the same skin as their parent, so they share one entry. */
    private fun brand(manufacturer: String): String = when (val name = manufacturer.lowercase().trim()) {
        "redmi", "poco" -> "xiaomi"
        "realme" -> "oppo"
        "iqoo" -> "vivo"
        "honor" -> "huawei"
        "infinix", "tecno", "itel", "infinix mobility limited" -> "transsion"
        "hmd global" -> "nokia"
        "asustek", "asus computer" -> "asus"
        else -> name
    }

    /** This app's own system settings page, where a twice-denied permission can still be granted. */
    fun openAppSettings(context: Context) {
        context.startActivitySafely(appDetails(context))
    }

    private fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))

    private fun Context.startActivitySafely(intent: Intent): Boolean = runCatching {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
