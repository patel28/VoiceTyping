package com.voicetyping.directtest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * The four switches the floating mic depends on. Microphone and overlay stop it from starting at
 * all; unrestricted battery use and the vendor autostart list decide whether it is still alive an
 * hour later, which is the failure users actually report.
 */
enum class SetupRequirement { MICROPHONE, OVERLAY, BATTERY, AUTOSTART }

/**
 * Some switches cannot be read back at all: no API reports a vendor's autostart list, and on
 * skins with their own battery manager the Android exemption says nothing about the vendor's own
 * setting. For those the screen can only ask the user and remember the answer, and keeping that
 * in its own state stops the checklist from claiming a switch it never saw.
 */
enum class RequirementState { DONE, MISSING, CONFIRMED_BY_USER, UNCONFIRMED }

/** Everything the checklist is allowed to claim, read once so no two rows can disagree. */
data class SetupStatus(
    val micGranted: Boolean,
    val overlayGranted: Boolean,
    val batteryExempt: Boolean,
    val batteryNeedsConfirmation: Boolean,
    val batteryConfirmed: Boolean,
    val hasVendorAutostart: Boolean,
    val autostartConfirmed: Boolean,
)

object SetupChecklist {

    /** Autostart is dropped on phones with no such list, so the count never asks for the impossible. */
    fun requirements(status: SetupStatus): List<SetupRequirement> =
        SetupRequirement.entries.filter { it != SetupRequirement.AUTOSTART || status.hasVendorAutostart }

    fun state(requirement: SetupRequirement, status: SetupStatus): RequirementState =
        when (requirement) {
            SetupRequirement.MICROPHONE -> done(status.micGranted)
            SetupRequirement.OVERLAY -> done(status.overlayGranted)
            // On a skin with its own battery manager, the Android exemption is only half of it:
            // the vendor's own setting decides the rest and cannot be read, so the user confirms.
            SetupRequirement.BATTERY -> when {
                !status.batteryExempt -> RequirementState.MISSING
                !status.batteryNeedsConfirmation -> RequirementState.DONE
                status.batteryConfirmed -> RequirementState.CONFIRMED_BY_USER
                else -> RequirementState.UNCONFIRMED
            }
            SetupRequirement.AUTOSTART ->
                if (status.autostartConfirmed) RequirementState.CONFIRMED_BY_USER else RequirementState.UNCONFIRMED
        }

    /** Requirements that stop the floating mic from starting. */
    fun blocking(status: SetupStatus): List<SetupRequirement> =
        listOf(SetupRequirement.MICROPHONE, SetupRequirement.OVERLAY)
            .filter { state(it, status) == RequirementState.MISSING }

    /** Everything still to do, blocking or not, in the order the screen shows them. */
    fun outstanding(status: SetupStatus): List<SetupRequirement> =
        requirements(status).filter { state(it, status).isOutstanding() }

    fun next(status: SetupStatus): SetupRequirement? = outstanding(status).firstOrNull()

    fun doneCount(status: SetupStatus): Int = requirements(status).size - outstanding(status).size

    fun isComplete(status: SetupStatus): Boolean = outstanding(status).isEmpty()

    private fun done(granted: Boolean) = if (granted) RequirementState.DONE else RequirementState.MISSING

    private fun RequirementState.isOutstanding() =
        this == RequirementState.MISSING || this == RequirementState.UNCONFIRMED
}

/**
 * Reads every switch at once, so the home screen, the setup run and Settings cannot show three
 * different answers. Kept out of [SetupChecklist] itself, which stays free of Android types and
 * so can be tested off-device.
 */
fun readSetupStatus(context: Context, store: AppSettingsStore) = SetupStatus(
    micGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED,
    overlayGranted = Settings.canDrawOverlays(context),
    batteryExempt = BackgroundAccess.isBatteryExempt(context),
    batteryNeedsConfirmation = BackgroundAccess.batteryNeedsConfirmation(),
    batteryConfirmed = store.readBatteryConfirmed(),
    hasVendorAutostart = BackgroundAccess.hasVendorAutostartList(),
    autostartConfirmed = store.readAutostartConfirmed(),
)
