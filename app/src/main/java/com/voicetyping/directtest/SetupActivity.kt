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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar

/**
 * The setup checklist, opened by itself on a first run and from the home screen or Settings after
 * that. One row per requirement, each saying what it is for and offering the one tap that fixes
 * it, so the reason the floating mic disappears an hour later is on screen before it happens.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var store: AppSettingsStore
    private lateinit var stepContainer: LinearLayout
    private lateinit var progress: TextView
    private lateinit var completeNote: TextView
    private var appliedTheme = AppTheme.AMBER
    private var micRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        store = AppSettingsStore(this)
        appliedTheme = store.readTheme()
        setTheme(appliedTheme.styleRes())
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        stepContainer = findViewById(R.id.setup_steps)
        progress = findViewById(R.id.setup_progress)
        completeNote = findViewById(R.id.setup_complete_note)

        findViewById<MaterialToolbar>(R.id.setup_toolbar).setNavigationOnClickListener { finish() }
        findViewById<Button>(R.id.setup_done).setOnClickListener { finish() }

        store.markSetupSeen()
        applyEdgeToEdgeInsets()
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_MIC) return
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        // A second denial is permanent until the user changes it in system settings, and the
        // dialog stops appearing at all, so send them where the switch still exists.
        if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            BackgroundAccess.openAppSettings(this)
        }
        render()
    }

    private fun readStatus() = SetupStatus(
        micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED,
        overlayGranted = Settings.canDrawOverlays(this),
        batteryExempt = BackgroundAccess.isBatteryExempt(this),
        hasVendorAutostart = BackgroundAccess.hasVendorAutostartList(),
        autostartConfirmed = store.readAutostartConfirmed(),
    )

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
    }

    private fun stepView(requirement: SetupRequirement, state: RequirementState): View {
        val row = LayoutInflater.from(this).inflate(R.layout.view_setup_step, stepContainer, false)
        val margin = resources.getDimensionPixelSize(R.dimen.space_2)
        (row.layoutParams as LinearLayout.LayoutParams).bottomMargin = margin

        val title = getString(titleRes(requirement))
        row.findViewById<TextView>(R.id.setup_step_title).text = title
        row.findViewById<TextView>(R.id.setup_step_why).text = whyText(requirement)
        row.findViewById<ImageView>(R.id.setup_step_icon).apply {
            setImageResource(iconRes(requirement))
            imageTintList = ColorStateList.valueOf(
                if (state == RequirementState.MISSING) themeColor(R.attr.vtWarning) else themeColor(R.attr.vtInk),
            )
        }

        val stateLabel = getString(stateRes(state))
        row.findViewById<TextView>(R.id.setup_step_state).apply {
            text = stateLabel
            setTextColor(stateColor(state))
        }
        row.contentDescription = getString(R.string.setup_row_a11y, title, stateLabel)

        row.findViewById<Button>(R.id.setup_step_action).apply {
            visibility = if (state == RequirementState.DONE) View.GONE else View.VISIBLE
            setOnClickListener { open(requirement) }
        }
        bindConfirm(row.findViewById(R.id.setup_step_confirm), requirement, state)
        return row
    }

    /** Only autostart gets a confirmation, because only autostart cannot be read back. */
    private fun bindConfirm(confirm: Button, requirement: SetupRequirement, state: RequirementState) {
        if (requirement != SetupRequirement.AUTOSTART) {
            confirm.visibility = View.GONE
            return
        }
        val confirmed = state == RequirementState.CONFIRMED_BY_USER
        confirm.visibility = View.VISIBLE
        confirm.setText(if (confirmed) R.string.action_not_switched_on else R.string.action_confirm)
        confirm.setOnClickListener {
            store.writeAutostartConfirmed(!confirmed)
            render()
        }
    }

    private fun open(requirement: SetupRequirement) = when (requirement) {
        SetupRequirement.MICROPHONE -> requestMicrophone()
        SetupRequirement.OVERLAY -> startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()),
        )
        SetupRequirement.BATTERY -> BackgroundAccess.openBatterySettings(this)
        SetupRequirement.AUTOSTART -> BackgroundAccess.openAutostartSettings(this)
    }

    private fun requestMicrophone() {
        if (micRequested && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            BackgroundAccess.openAppSettings(this)
            return
        }
        micRequested = true
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
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
        SetupRequirement.BATTERY -> getString(R.string.setup_battery_why)
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
        RequirementState.UNCONFIRMED -> themeColor(R.attr.vtInkMuted)
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
        private const val REQUEST_MIC = 42
    }
}
