package com.voicetyping.directtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupChecklistTest {

    private val nothingDone = SetupStatus(
        micGranted = false,
        overlayGranted = false,
        batteryExempt = false,
        batteryNeedsConfirmation = false,
        batteryConfirmed = false,
        hasVendorAutostart = true,
        autostartConfirmed = false,
    )

    @Test fun autostartIsOnlyAskedForOnPhonesThatHaveSuchAList() {
        assertEquals(
            listOf(
                SetupRequirement.MICROPHONE,
                SetupRequirement.OVERLAY,
                SetupRequirement.BATTERY,
                SetupRequirement.AUTOSTART,
            ),
            SetupChecklist.requirements(nothingDone),
        )
        assertEquals(
            listOf(SetupRequirement.MICROPHONE, SetupRequirement.OVERLAY, SetupRequirement.BATTERY),
            SetupChecklist.requirements(nothingDone.copy(hasVendorAutostart = false)),
        )
    }

    @Test fun onlyMicrophoneAndOverlayBlockTheFloatingMicFromStarting() {
        assertEquals(
            listOf(SetupRequirement.MICROPHONE, SetupRequirement.OVERLAY),
            SetupChecklist.blocking(nothingDone),
        )
        val permissionsGranted = nothingDone.copy(micGranted = true, overlayGranted = true)
        assertTrue(SetupChecklist.blocking(permissionsGranted).isEmpty())
        // Battery and autostart are still outstanding, and still not blocking.
        assertEquals(
            listOf(SetupRequirement.BATTERY, SetupRequirement.AUTOSTART),
            SetupChecklist.outstanding(permissionsGranted),
        )
    }

    @Test fun autostartNeverCountsAsDoneUntilTheUserSaysSo() {
        val allButAutostart = nothingDone.copy(
            micGranted = true,
            overlayGranted = true,
            batteryExempt = true,
        )
        assertEquals(RequirementState.UNCONFIRMED, SetupChecklist.state(SetupRequirement.AUTOSTART, allButAutostart))
        assertFalse(SetupChecklist.isComplete(allButAutostart))
        assertEquals(3, SetupChecklist.doneCount(allButAutostart))

        val confirmed = allButAutostart.copy(autostartConfirmed = true)
        assertEquals(RequirementState.CONFIRMED_BY_USER, SetupChecklist.state(SetupRequirement.AUTOSTART, confirmed))
        assertTrue(SetupChecklist.isComplete(confirmed))
        assertEquals(4, SetupChecklist.doneCount(confirmed))
    }

    @Test fun phoneWithoutAutostartListIsCompleteOnPermissionsAndBattery() {
        val status = SetupStatus(
            micGranted = true,
            overlayGranted = true,
            batteryExempt = true,
            batteryNeedsConfirmation = false,
            batteryConfirmed = false,
            hasVendorAutostart = false,
            autostartConfirmed = false,
        )
        assertTrue(SetupChecklist.isComplete(status))
        assertEquals(3, SetupChecklist.doneCount(status))
        assertEquals(null, SetupChecklist.next(status))
    }

    @Test fun nextStepFollowsTheOrderTheScreenShows() {
        assertEquals(SetupRequirement.MICROPHONE, SetupChecklist.next(nothingDone))
        assertEquals(
            SetupRequirement.OVERLAY,
            SetupChecklist.next(nothingDone.copy(micGranted = true)),
        )
        assertEquals(
            SetupRequirement.BATTERY,
            SetupChecklist.next(nothingDone.copy(micGranted = true, overlayGranted = true)),
        )
    }

    @Test fun stateReportsWhatWasObservedForEveryReadableRequirement() {
        assertEquals(RequirementState.MISSING, SetupChecklist.state(SetupRequirement.MICROPHONE, nothingDone))
        assertEquals(RequirementState.MISSING, SetupChecklist.state(SetupRequirement.BATTERY, nothingDone))
        val done = nothingDone.copy(micGranted = true, overlayGranted = true, batteryExempt = true)
        assertEquals(RequirementState.DONE, SetupChecklist.state(SetupRequirement.MICROPHONE, done))
        assertEquals(RequirementState.DONE, SetupChecklist.state(SetupRequirement.OVERLAY, done))
        assertEquals(RequirementState.DONE, SetupChecklist.state(SetupRequirement.BATTERY, done))
    }

    /**
     * The bug this guards: a phone can report itself exempt from Android's battery optimisation
     * while the vendor's own battery manager still stops the service, so the row must not claim
     * the step is done on the strength of the half it can read.
     */
    @Test fun batteryIsNotDoneOnTheAndroidExemptionAloneWhereTheVendorRunsItsOwn() {
        val androidExemptOnly = nothingDone.copy(
            batteryExempt = true,
            batteryNeedsConfirmation = true,
        )
        assertEquals(
            RequirementState.UNCONFIRMED,
            SetupChecklist.state(SetupRequirement.BATTERY, androidExemptOnly),
        )
        assertTrue(SetupChecklist.outstanding(androidExemptOnly).contains(SetupRequirement.BATTERY))

        val confirmed = androidExemptOnly.copy(batteryConfirmed = true)
        assertEquals(
            RequirementState.CONFIRMED_BY_USER,
            SetupChecklist.state(SetupRequirement.BATTERY, confirmed),
        )
    }

    @Test fun batteryStillReadsAsMissingUntilAndroidItselfIsSatisfied() {
        val vendorPhone = nothingDone.copy(batteryNeedsConfirmation = true, batteryConfirmed = true)
        assertEquals(
            RequirementState.MISSING,
            SetupChecklist.state(SetupRequirement.BATTERY, vendorPhone),
        )
    }
}
