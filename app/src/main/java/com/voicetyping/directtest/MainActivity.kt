package com.voicetyping.directtest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Setup and readiness screen. One primary action at a time: whatever the next real requirement
 * is. The screen only claims what it can observe, so the pipeline stops it cannot see from here
 * are drawn as stops still ahead rather than as live telemetry.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settingsStore: AppSettingsStore
    private lateinit var signalRail: SignalRailView
    private lateinit var readinessIcon: ImageView
    private lateinit var readinessHeadline: TextView
    private lateinit var setupCard: View
    private lateinit var setupCardIcon: ImageView
    private lateinit var setupCardBody: TextView
    private lateinit var setupCardProgress: TextView
    private lateinit var primaryAction: Button
    private lateinit var stopAction: Button
    private lateinit var modeSummary: TextView
    private lateinit var stepGesture: TextView
    private lateinit var privacyBody: TextView
    private lateinit var privacyChevron: ImageView
    private lateinit var apiKeyMissingWarning: View
    private lateinit var sandboxHelper: TextView
    private var privacyExpanded = false
    private var appliedTheme = AppTheme.AMBER

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedTheme = AppSettingsStore(this).readTheme()
        setTheme(appliedTheme.styleRes())
        super.onCreate(savedInstanceState)
        settingsStore = AppSettingsStore(this)
        setContentView(R.layout.activity_main)

        signalRail = findViewById(R.id.signal_rail)
        readinessIcon = findViewById(R.id.readiness_icon)
        readinessHeadline = findViewById(R.id.readiness_headline)
        setupCard = findViewById(R.id.setup_card)
        setupCardIcon = findViewById(R.id.setup_card_icon)
        setupCardBody = findViewById(R.id.setup_card_body)
        setupCardProgress = findViewById(R.id.setup_card_progress)
        primaryAction = findViewById(R.id.primary_action)
        stopAction = findViewById(R.id.stop_action)
        modeSummary = findViewById(R.id.mode_summary)
        stepGesture = findViewById(R.id.step_gesture)
        privacyBody = findViewById(R.id.privacy_body)
        privacyChevron = findViewById(R.id.privacy_chevron)
        apiKeyMissingWarning = findViewById(R.id.api_key_missing_warning)
        sandboxHelper = findViewById(R.id.sandbox_helper)

        findViewById<ImageButton>(R.id.open_settings).setOnClickListener { openSettings() }
        findViewById<LinearLayout>(R.id.mode_summary_row).setOnClickListener { openSettings() }
        apiKeyMissingWarning.setOnClickListener { openSettings() }
        setupCard.setOnClickListener { openSetup() }
        findViewById<LinearLayout>(R.id.privacy_row).setOnClickListener { togglePrivacy() }
        stopAction.setOnClickListener { stopBubble() }

        applyEdgeToEdgeInsets()
        applyPrivacyExpansion()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        // Settings can change the theme while this screen is stopped; a recreate is the only
        // way an already-inflated window picks the new one up.
        if (settingsStore.readTheme() != appliedTheme) {
            recreate()
            return
        }
        refresh()
        openSetupOnFirstRun()
    }

    /**
     * Nothing works until the checklist is done, so a first run opens it instead of leaving the
     * user to find it. Marked seen by [SetupActivity] itself, so this happens exactly once.
     */
    private fun openSetupOnFirstRun() {
        if (settingsStore.readSetupSeen()) return
        openSetup()
    }

    private fun applyEdgeToEdgeInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = findViewById<LinearLayout>(R.id.main_root)
        val scroll = findViewById<ScrollView>(R.id.main_scroll)
        val scrollBottom = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            scroll.updatePadding(bottom = scrollBottom + maxOf(bars.bottom, keyboard))
            insets
        }
    }

    private fun refresh() = render(readReadiness())

    private fun readReadiness(): Readiness {
        val settings = settingsStore.read()
        return Readiness(
            serviceRunning = OverlayBubbleService.isRunning,
            resultCopied = ClipboardStateStore.read() != null,
            mode = settings.recordingMode,
            apiKeySaved = settings.apiKey.isNotBlank(),
            setup = SetupStatus(
                micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED,
                overlayGranted = Settings.canDrawOverlays(this),
                batteryExempt = BackgroundAccess.isBatteryExempt(this),
                hasVendorAutostart = BackgroundAccess.hasVendorAutostartList(),
                autostartConfirmed = settingsStore.readAutostartConfirmed(),
            ),
        )
    }

    /** Everything the screen is allowed to claim, read once so the views cannot disagree. */
    data class Readiness(
        val serviceRunning: Boolean,
        val resultCopied: Boolean,
        val mode: RecordingMode,
        val apiKeySaved: Boolean = false,
        val setup: SetupStatus = SetupStatus(
            micGranted = true,
            overlayGranted = true,
            batteryExempt = true,
            hasVendorAutostart = false,
            autostartConfirmed = false,
        ),
    )

    @VisibleForTesting
    fun render(readiness: Readiness) {
        val ready = SetupChecklist.blocking(readiness.setup).isEmpty()

        bindSetupCard(readiness.setup)
        apiKeyMissingWarning.visibility = if (readiness.apiKeySaved) View.GONE else View.VISIBLE

        signalRail.render(railStates(ready, readiness.resultCopied))
        bindHeadline(ready, readiness.serviceRunning, readiness.resultCopied)
        bindPrimaryAction(ready, readiness.serviceRunning)

        stopAction.visibility = if (readiness.serviceRunning) View.VISIBLE else View.GONE
        modeSummary.text = getString(R.string.mode_summary, getString(modeLabel(readiness.mode)))
        stepGesture.text = getString(
            if (readiness.mode == RecordingMode.TAP_TO_TOGGLE) {
                R.string.step_gesture_tap
            } else {
                R.string.step_gesture_hold
            },
        )
        sandboxHelper.setText(
            if (readiness.resultCopied) {
                R.string.sandbox_helper_copied
            } else {
                R.string.sandbox_helper_idle
            },
        )
        sandboxHelper.setTextColor(
            if (readiness.resultCopied) getColor(R.color.success_mint) else themeColor(R.attr.vtInkMuted),
        )
    }

    /**
     * Listening and Refining happen inside the overlay service while another app is in front, so
     * this screen never pretends to watch them live. A fresh clipboard result is proof the whole
     * pipeline ran, and only then are those stops marked complete.
     */
    private fun railStates(ready: Boolean, copied: Boolean): List<StageState> = when {
        copied -> listOf(
            StageState.DONE,
            StageState.DONE,
            StageState.DONE,
            StageState.DONE,
            StageState.CURRENT,
        )
        ready -> listOf(
            StageState.DONE,
            StageState.CURRENT,
            StageState.AHEAD,
            StageState.AHEAD,
            StageState.AHEAD,
        )
        else -> listOf(
            StageState.CURRENT,
            StageState.AHEAD,
            StageState.AHEAD,
            StageState.AHEAD,
            StageState.AHEAD,
        )
    }

    /**
     * The card is the whole checklist in one line: hidden once nothing is left, and warning
     * coloured only while something the floating mic cannot run without is missing. Autostart
     * sitting unconfirmed is worth saying, but it is not a reason to shout.
     */
    private fun bindSetupCard(status: SetupStatus) {
        val outstanding = SetupChecklist.outstanding(status)
        if (outstanding.isEmpty()) {
            setupCard.visibility = View.GONE
            return
        }
        val blocking = SetupChecklist.blocking(status).isNotEmpty()
        val tint = if (blocking) themeColor(R.attr.vtWarning) else themeColor(R.attr.vtAccent)
        val names = outstanding.joinToString(", ") { getString(setupLabel(it)) }

        setupCard.visibility = View.VISIBLE
        setupCardIcon.setImageResource(if (blocking) R.drawable.ic_warning else R.drawable.ic_info)
        setupCardIcon.imageTintList = android.content.res.ColorStateList.valueOf(tint)
        setupCardBody.text = getString(R.string.setup_card_body, names)
        setupCardProgress.text = getString(
            R.string.setup_progress,
            SetupChecklist.doneCount(status),
            SetupChecklist.requirements(status).size,
        )
        setupCard.contentDescription = getString(
            R.string.setup_card_a11y,
            getString(R.string.setup_card_title),
            setupCardBody.text,
        )
    }

    private fun setupLabel(requirement: SetupRequirement) = when (requirement) {
        SetupRequirement.MICROPHONE -> R.string.setup_mic_title
        SetupRequirement.OVERLAY -> R.string.setup_overlay_title
        SetupRequirement.BATTERY -> R.string.setup_battery_title
        SetupRequirement.AUTOSTART -> R.string.setup_autostart_title
    }

    private fun bindHeadline(ready: Boolean, running: Boolean, copied: Boolean) {
        val headline: Int
        val icon: Int
        val tint: Int
        when {
            !ready -> {
                headline = R.string.state_setup_required
                icon = R.drawable.ic_warning
                tint = themeColor(R.attr.vtWarning)
            }
            copied -> {
                headline = R.string.rail_copied
                icon = R.drawable.ic_check
                tint = getColor(R.color.success_mint)
            }
            running -> {
                headline = R.string.state_active
                icon = R.drawable.ic_mic
                tint = themeColor(R.attr.vtAccent)
            }
            else -> {
                headline = R.string.state_ready
                icon = R.drawable.ic_check
                tint = getColor(R.color.success_mint)
            }
        }
        readinessHeadline.setText(headline)
        readinessIcon.setImageResource(icon)
        readinessIcon.imageTintList = android.content.res.ColorStateList.valueOf(tint)
    }

    private fun bindPrimaryAction(ready: Boolean, running: Boolean) {
        if (running) {
            primaryAction.visibility = View.GONE
            return
        }
        primaryAction.visibility = View.VISIBLE
        primaryAction.setText(if (ready) R.string.action_start_bubble else R.string.action_finish_setup)
        primaryAction.setOnClickListener { if (ready) startBubble() else openSetup() }
    }

    private fun modeLabel(mode: RecordingMode) =
        if (mode == RecordingMode.TAP_TO_TOGGLE) R.string.mode_tap else R.string.mode_hold

    private fun togglePrivacy() {
        privacyExpanded = !privacyExpanded
        applyPrivacyExpansion()
    }

    private fun applyPrivacyExpansion() {
        privacyBody.visibility = if (privacyExpanded) View.VISIBLE else View.GONE
        privacyChevron.rotation = if (privacyExpanded) 180f else 0f
        findViewById<LinearLayout>(R.id.privacy_row).contentDescription = getString(
            R.string.privacy_row_title,
        ) + ", " + getString(if (privacyExpanded) R.string.a11y_collapse else R.string.a11y_expand)
    }

    private fun openSetup() {
        startActivity(Intent(this, SetupActivity::class.java))
    }

    private fun startBubble() {
        // The checklist owns every permission prompt, so a tap that arrives before it is done
        // goes there rather than raising a dialog this screen would have to handle too.
        if (SetupChecklist.blocking(readReadiness().setup).isNotEmpty()) {
            openSetup()
            return
        }
        startForegroundService(
            Intent(this, OverlayBubbleService::class.java).setAction(OverlayBubbleService.ACTION_START),
        )
        refresh()
    }

    private fun stopBubble() {
        stopService(Intent(this, OverlayBubbleService::class.java))
        refresh()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}
