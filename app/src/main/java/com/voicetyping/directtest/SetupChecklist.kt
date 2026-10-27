package com.voicetyping.directtest

/**
 * The four switches the floating mic depends on. Microphone and overlay stop it from starting at
 * all; unrestricted battery use and the vendor autostart list decide whether it is still alive an
 * hour later, which is the failure users actually report.
 */
enum class SetupRequirement { MICROPHONE, OVERLAY, BATTERY, AUTOSTART }

/**
 * Autostart is the odd one out: no API reads a vendor's own list back, so the screen can only ask
 * the user and remember the answer. Keeping that in its own state stops the checklist from
 * claiming a switch it never saw.
 */
enum class RequirementState { DONE, MISSING, CONFIRMED_BY_USER, UNCONFIRMED }

/** Everything the checklist is allowed to claim, read once so no two rows can disagree. */
data class SetupStatus(
    val micGranted: Boolean,
    val overlayGranted: Boolean,
    val batteryExempt: Boolean,
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
            SetupRequirement.BATTERY -> done(status.batteryExempt)
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
