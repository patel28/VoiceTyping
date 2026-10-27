package com.voicetyping.directtest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar

/**
 * The setup run. Opened by the home screen's one button, it walks the requirements itself -
 * microphone, then overlay, then battery - opening each system screen in turn so the user answers
 * prompts rather than hunting for switches. It stops at the first one that comes back unchanged,
 * and at autostart, which no API can read back and so has to be confirmed by hand.
 *
 * The rows are the status of that run: what is done, what is left, and why each one matters.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var store: AppSettingsStore
    private lateinit var stepContainer: LinearLayout
    private lateinit var progress: TextView
    private lateinit var completeNote: TextView
    private lateinit var footerAction: Button
    private var appliedTheme = AppTheme.AMBER
    private var micRequested = false

    /** True while the run is walking the list by itself; false once it stops for the user. */
    private var autoRun = false
    private var pending: SetupRequirement? = null
    private var pendingState: RequirementState? = null

    private val stepLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { onStepFinished() }

    private val micLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // A second denial is permanent until the user changes it in system settings, and the
        // dialog stops appearing at all, so send them where the switch still exists.
        if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            launchFirst(listOf(BackgroundAccess.appSettingsIntent(this)))
        } else {
            onStepFinished()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        store = AppSettingsStore(this)
        appliedTheme = store.readTheme()
        setTheme(appliedTheme.styleRes())
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        stepContainer = findViewById(R.id.setup_steps)
        progress = findViewById(R.id.setup_progress)
        completeNote = findViewById(R.id.setup_complete_note)

        footerAction = findViewById(R.id.setup_done)
        findViewById<MaterialToolbar>(R.id.setup_toolbar).setNavigationOnClickListener { finish() }

        applyEdgeToEdgeInsets()
        // A run spends most of its time in system screens, so it has to survive this process
        // being killed behind them: the step it was waiting on is restored, not restarted.
        if (savedInstanceState != null) {
            autoRun = savedInstanceState.getBoolean(STATE_AUTO_RUN)
            pending = savedInstanceState.getString(STATE_PENDING)?.let(SetupRequirement::valueOf)
            pendingState = savedInstanceState.getString(STATE_PENDING_STATE)?.let(RequirementState::valueOf)
        } else if (intent.getBooleanExtra(EXTRA_AUTO_RUN, false)) {
            autoRun = true
            runNext()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_AUTO_RUN, autoRun)
        outState.putString(STATE_PENDING, pending?.name)
        outState.putString(STATE_PENDING_STATE, pendingState?.name)
    }

    override fun onResume() {
        super.onResume()
        if (store.readTheme() != appliedTheme) {
            recreate()
            return
        }
        // Every requirement is changed in a system screen, so a return to this one is the only
        // signal that anything moved.
        render()
    }

    private fun readStatus() = readSetupStatus(this, store)

    private fun render() {
        val status = readStatus()
        val requirements = SetupChecklist.requirements(status)
        stepContainer.removeAllViews()
        for (requirement in requirements) {
            stepContainer.addView(stepView(requirement, SetupChecklist.state(requirement, status)))
        }
        progress.text = getString(
            R.string.setup_progress,
            SetupChecklist.doneCount(status),
            requirements.size,
        )
        completeNote.visibility = if (SetupChecklist.isComplete(status)) View.VISIBLE else View.GONE
        bindFooter(status)
    }

    /** One button at the bottom: carry on with the run, or leave once there is nothing left. */
    private fun bindFooter(status: SetupStatus) {
        val done = SetupChecklist.outstanding(status).isEmpty()
        footerAction.setText(if (done) R.string.action_done else R.string.action_continue_setup)
        footerAction.setOnClickListener {
            if (SetupChecklist.outstanding(readStatus()).isEmpty()) {
                finishRun()
            } else {
                autoRun = true
                runNext()
            }
        }
    }

    /**
     * Walks the checklist instead of handing back a list of things to tap: each system screen is
     * opened in turn, and the run stops the moment one of them comes back unchanged.
     */
    private fun runNext() {
        val next = SetupChecklist.next(readStatus())
        if (next == null) {
            finishRun()
            return
        }
        pending = next
        pendingState = SetupChecklist.state(next, readStatus())
        open(next)
    }

    private fun onStepFinished() {
        render()
        if (!autoRun) return
        val step = pending ?: return
        val before = pendingState
        pending = null
        pendingState = null
        // A step can need two screens - Android's battery dialog and then the vendor's own - so
        // the run carries on while the state keeps moving, and stops the moment it does not.
        if (SetupChecklist.state(step, readStatus()) == before) {
            autoRun = false
            render()
            return
        }
        runNext()
    }

    private fun finishRun() {
        setResult(RESULT_OK)
        finish()
    }

    private fun stepView(requirement: SetupRequirement, state: RequirementState): View {
        val row = LayoutInflater.from(this).inflate(R.layout.view_setup_step, stepContainer, false)
        val margin = resources.getDimensionPixelSize(R.dimen.space_2)
        (row.layoutParams as LinearLayout.LayoutParams).bottomMargin = margin

        val settled = state == RequirementState.DONE || state == RequirementState.CONFIRMED_BY_USER
        val tint = stateColor(state)

        val title = getString(titleRes(requirement))
        row.findViewById<TextView>(R.id.setup_step_title).text = title
        row.findViewById<TextView>(R.id.setup_step_why).apply {
            text = whyText(requirement)
            // A settled row keeps only its header: the reasoning is for whoever still has to act.
            visibility = if (settled) View.GONE else View.VISIBLE
        }
        row.findViewById<ImageView>(R.id.setup_step_icon).apply {
            setImageResource(iconRes(requirement))
            imageTintList = ColorStateList.valueOf(tint)
        }
        row.findViewById<View>(R.id.setup_step_chip).backgroundTintList =
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(tint, 56))
        row.findViewById<ImageView>(R.id.setup_step_check).visibility =
            if (settled) View.VISIBLE else View.GONE

        val stateLabel = getString(stateRes(state))
        row.findViewById<TextView>(R.id.setup_step_state).apply {
            text = stateLabel
            setTextColor(stateColor(state))
        }
        row.contentDescription = getString(R.string.setup_row_a11y, title, stateLabel)

        row.findViewById<Button>(R.id.setup_step_action).apply {
            visibility = if (state == RequirementState.DONE) View.GONE else View.VISIBLE
            setOnClickListener {
                pending = requirement
                open(requirement)
            }
        }
        bindConfirm(row.findViewById(R.id.setup_step_confirm), requirement, state)
        return row
    }

    /** A confirmation appears only where the switch itself cannot be read back. */
    private fun bindConfirm(confirm: Button, requirement: SetupRequirement, state: RequirementState) {
        val confirmable = state == RequirementState.UNCONFIRMED ||
            state == RequirementState.CONFIRMED_BY_USER
        if (!confirmable) {
            confirm.visibility = View.GONE
            return
        }
        val confirmed = state == RequirementState.CONFIRMED_BY_USER
        confirm.visibility = View.VISIBLE
        confirm.setText(if (confirmed) R.string.action_not_switched_on else R.string.action_confirm)
        confirm.setOnClickListener {
            writeConfirmed(requirement, !confirmed)
            render()
        }
    }

    private fun writeConfirmed(requirement: SetupRequirement, confirmed: Boolean) {
        if (requirement == SetupRequirement.BATTERY) {
            store.writeBatteryConfirmed(confirmed)
        } else {
            store.writeAutostartConfirmed(confirmed)
        }
    }

    private fun open(requirement: SetupRequirement) {
        when (requirement) {
            SetupRequirement.MICROPHONE -> requestMicrophone()
            SetupRequirement.OVERLAY -> {
                // On MIUI this is a list of every installed app, so say what to look for.
                if (BackgroundAccess.overlayOpensAppList()) {
                    showHint(R.string.setup_find_in_list, getString(R.string.app_name))
                }
                launchFirst(BackgroundAccess.overlayIntents(this))
            }
            // Android's own exemption first, which is one tap in a dialog; the vendor's own
            // battery manager after it, which is a screen and cannot be read back.
            SetupRequirement.BATTERY -> launchFirst(
                if (BackgroundAccess.isBatteryExempt(this)) {
                    BackgroundAccess.vendorBatteryIntents(this)
                } else {
                    listOf(BackgroundAccess.batteryOptimisationIntent(this))
                },
            )
            SetupRequirement.AUTOSTART -> {
                // The only screen left that is a list of every app, so say what to look for.
                showHint(R.string.setup_find_in_list, getString(R.string.app_name))
                launchFirst(BackgroundAccess.autostartIntents(this))
            }
        }
    }

    /** Said over the system screen, because that is where the user has to act on it. */
    private fun showHint(message: Int, vararg args: Any) =
        Toast.makeText(this, getString(message, *args), Toast.LENGTH_LONG).show()

    /** Vendor screens get renamed between skin versions, so the first one that resolves wins. */
    private fun launchFirst(intents: List<Intent>) {
        for (intent in intents) {
            if (runCatching { stepLauncher.launch(intent) }.isSuccess) return
        }
    }

    private fun requestMicrophone() {
        if (micRequested && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            launchFirst(listOf(BackgroundAccess.appSettingsIntent(this)))
            return
        }
        micRequested = true
        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun titleRes(requirement: SetupRequirement) = when (requirement) {
        SetupRequirement.MICROPHONE -> R.string.setup_mic_title
        SetupRequirement.OVERLAY -> R.string.setup_overlay_title
        SetupRequirement.BATTERY -> R.string.setup_battery_title
        SetupRequirement.AUTOSTART -> R.string.setup_autostart_title
    }

    private fun whyText(requirement: SetupRequirement): String = when (requirement) {
        SetupRequirement.MICROPHONE -> getString(R.string.setup_mic_why)
        SetupRequirement.OVERLAY -> getString(R.string.setup_overlay_why)
        SetupRequirement.BATTERY -> if (BackgroundAccess.batteryNeedsConfirmation()) {
            getString(R.string.setup_battery_why) + " " +
                getString(R.string.setup_battery_why_vendor, BackgroundAccess.vendorName())
        } else {
            getString(R.string.setup_battery_why)
        }
        SetupRequirement.AUTOSTART ->
            getString(R.string.setup_autostart_why, BackgroundAccess.vendorName())
    }

    private fun iconRes(requirement: SetupRequirement) = when (requirement) {
        SetupRequirement.MICROPHONE -> R.drawable.ic_mic
        SetupRequirement.OVERLAY -> R.drawable.ic_overlay
        SetupRequirement.BATTERY -> R.drawable.ic_battery
        SetupRequirement.AUTOSTART -> R.drawable.ic_autostart
    }

    private fun stateRes(state: RequirementState) = when (state) {
        RequirementState.DONE -> R.string.setup_state_done
        RequirementState.MISSING -> R.string.setup_state_missing
        RequirementState.CONFIRMED_BY_USER -> R.string.setup_state_confirmed
        RequirementState.UNCONFIRMED -> R.string.setup_state_unconfirmed
    }

    private fun stateColor(state: RequirementState) = when (state) {
        RequirementState.DONE, RequirementState.CONFIRMED_BY_USER -> getColor(R.color.success_mint)
        RequirementState.MISSING -> themeColor(R.attr.vtWarningText)
        RequirementState.UNCONFIRMED -> themeColor(R.attr.vtAccent)
    }

    private fun applyEdgeToEdgeInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = findViewById<LinearLayout>(R.id.setup_root)
        val toolbar = findViewById<MaterialToolbar>(R.id.setup_toolbar)
        val scroll = findViewById<ScrollView>(R.id.setup_scroll)
        val footer = findViewById<LinearLayout>(R.id.setup_footer)
        val toolbarTop = toolbar.paddingTop
        val footerBottom = footer.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.updatePadding(left = bars.left, right = bars.right)
            toolbar.updatePadding(top = toolbarTop + bars.top)
            footer.updatePadding(bottom = footerBottom + bars.bottom)
            insets
        }
        scroll.isFillViewport = true
    }

    companion object {
        /** Set by the home screen's one button: open and work through the list without stopping. */
        const val EXTRA_AUTO_RUN = "auto_run"
        private const val STATE_AUTO_RUN = "state_auto_run"
        private const val STATE_PENDING = "state_pending"
        private const val STATE_PENDING_STATE = "state_pending_state"
    }
}
