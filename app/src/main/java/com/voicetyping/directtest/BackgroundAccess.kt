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
     * The one-tap dialog that adds this app to Android's own battery exemption list. Needs
     * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, which Google Play only accepts from a short list of
     * app types; a dictation overlay that has to survive the screen going off is the whole
     * reason this app exists, and the alternative is sending the user hunting through Settings.
     */
    fun batteryOptimisationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.fromParts("package", context.packageName, null))

    /**
     * Candidate intents for the vendor's own battery restriction, best first.
     *
     * This is the second half of the battery step on skins that run their own battery manager:
     * [isBatteryExempt] reports Android's list and knows nothing about this one. MIUI keeps it in
     * Power keeper's per-app screen; everywhere else it is on the app's own details page, under
     * Battery.
     *
     * The caller launches them in order and stops at the first one that resolves, which is why
     * these are returned rather than started here: a setup run has to know when each one ends.
     */
    fun vendorBatteryIntents(context: Context): List<Intent> = buildList {
        if (isMiui()) add(miuiBatterySaver(context))
        add(appDetails(context))
    }

    /**
     * Whether this phone's battery step ends in a switch no API can read.
     *
     * The skins that keep their own autostart list are the same ones that keep their own battery
     * manager, and on those Android's exemption is only half the answer - which is why a phone
     * can report itself exempt while the vendor still stops the service.
     */
    fun batteryNeedsConfirmation(): Boolean = hasVendorAutostartList()

    /**
     * This app's "display over other apps" switch.
     *
     * MIUI ignores the package URI and opens the list of installed apps, where the user has to
     * find this one. Its own per-app permission page was tried instead and reverted: that page
     * only leads to Other permissions, and the screen holding the switch itself,
     * OtherPermissionsActivity, is guarded by a signature|privileged MIUI permission no
     * third-party app can hold. Landing a row above the switch read as the wrong screen, so the
     * list - where the switch is next to the app's own name - is what this opens.
     */
    fun overlayIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            .setData(Uri.fromParts("package", context.packageName, null)),
    )

    /** True where that action lands on a list of every app rather than on this app's own page. */
    fun overlayOpensAppList(): Boolean = isMiui()

    private fun isMiui(): Boolean = brand(Build.MANUFACTURER) == "xiaomi"

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
     * Candidate intents for the vendor's autostart list, best first.
     *
     * Actions come first: an implicit intent is resolved by the system, so it works without the
     * target package being visible to this app, which an explicit component is not guaranteed to
     * be on Android 11 and up. The components after them are what each brand has shipped -
     * several per brand, because they get renamed between versions - and the app's own settings
     * page is the last resort, since every skin puts its background switches one tap from there.
     */
    fun autostartIntents(context: Context): List<Intent> = buildList {
        val manufacturer = Build.MANUFACTURER
        for (action in autostartActions(manufacturer)) {
            add(Intent(action).addCategory(Intent.CATEGORY_DEFAULT))
        }
        for ((packageName, className) in autostartTargets(manufacturer)) {
            add(Intent().setComponent(ComponentName(packageName, className)))
        }
        add(appDetails(context))
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
    fun appSettingsIntent(context: Context): Intent = appDetails(context)

    private fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
}
