package com.voicetyping.directtest

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
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
    private lateinit var statusBadge: View
    private lateinit var readinessIcon: ImageView
    private lateinit var readinessHeadline: TextView
    private lateinit var readinessDetail: TextView
    private lateinit var setupCard: View
    private lateinit var setupCardChip: View
    private lateinit var setupCardIcon: ImageView
    private lateinit var setupCardBody: TextView
    private lateinit var setupCardProgress: TextView
    private lateinit var primaryAction: Button
    private lateinit var stopAction: Button
    private lateinit var modeSummary: TextView
    private lateinit var howBody: View
    private lateinit var howChevron: ImageView
    private lateinit var privacyBody: TextView
    private lateinit var privacyChevron: ImageView
    private lateinit var apiKeyMissingWarning: View
    private lateinit var sandboxHelper: TextView
    private var startAfterSetup = false
    private var howExpanded = false
    private var privacyExpanded = false
    private var appliedTheme = AppTheme.AMBER

    /** The setup run reports back here, so one tap on Start can end with the mic actually up. */
    private val setupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refresh()
        val wanted = startAfterSetup
        startAfterSetup = false
        if (wanted && SetupChecklist.blocking(readReadiness().setup).isEmpty()) startBubble()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedTheme = AppSettingsStore(this).readTheme()
        setTheme(appliedTheme.styleRes())
        super.onCreate(savedInstanceState)
        settingsStore = AppSettingsStore(this)
        // The setup run leaves this screen in the background for minutes at a time, so whether a
        // start was asked for has to survive the process being killed while it is away.
        startAfterSetup = savedInstanceState?.getBoolean(STATE_START_AFTER_SETUP) == true
        setContentView(R.layout.activity_main)

        statusBadge = findViewById(R.id.status_badge)
        readinessIcon = findViewById(R.id.readiness_icon)
        readinessHeadline = findViewById(R.id.readiness_headline)
        readinessDetail = findViewById(R.id.readiness_detail)
        setupCard = findViewById(R.id.setup_card)
        setupCardChip = findViewById(R.id.setup_card_chip)
        setupCardIcon = findViewById(R.id.setup_card_icon)
        setupCardBody = findViewById(R.id.setup_card_body)
        setupCardProgress = findViewById(R.id.setup_card_progress)
        primaryAction = findViewById(R.id.primary_action)
        stopAction = findViewById(R.id.stop_action)
        modeSummary = findViewById(R.id.mode_summary)
        howBody = findViewById(R.id.how_body)
        howChevron = findViewById(R.id.how_chevron)
        privacyBody = findViewById(R.id.privacy_body)
        privacyChevron = findViewById(R.id.privacy_chevron)
        apiKeyMissingWarning = findViewById(R.id.api_key_missing_warning)
        sandboxHelper = findViewById(R.id.sandbox_helper)

        findViewById<ImageButton>(R.id.open_settings).setOnClickListener { openSettings() }
        findViewById<LinearLayout>(R.id.mode_summary_row).setOnClickListener { openSettings() }
        apiKeyMissingWarning.setOnClickListener { openSettings() }
        setupCard.setOnClickListener { openSetup() }
        findViewById<LinearLayout>(R.id.how_row).setOnClickListener { toggleHow() }
        findViewById<LinearLayout>(R.id.privacy_row).setOnClickListener { togglePrivacy() }
        stopAction.setOnClickListener { stopBubble() }

        applyEdgeToEdgeInsets()
        applyHowExpansion()
        applyPrivacyExpansion()
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_START_AFTER_SETUP, startAfterSetup)
    }

    override fun onResume() {
        super.onResume()
        // Settings can change the theme while this screen is stopped; a recreate is the only
        // way an already-inflated window picks the new one up.
        if (settingsStore.readTheme() != appliedTheme) {
            recreate()
            return
        }
        OverlayBubbleService.onRunningChanged = { refresh() }
        refresh()
    }

    override fun onPause() {
        super.onPause()
        OverlayBubbleService.onRunningChanged = null
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
            setup = readSetupStatus(this, settingsStore),
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
            batteryNeedsConfirmation = false,
            batteryConfirmed = false,
            hasVendorAutostart = false,
            autostartConfirmed = false,
        ),
    )

    @VisibleForTesting
    fun render(readiness: Readiness) {
        val ready = SetupChecklist.blocking(readiness.setup).isEmpty()

        bindSetupCard(readiness.setup)
        apiKeyMissingWarning.visibility = if (readiness.apiKeySaved) View.GONE else View.VISIBLE

        bindHeadline(ready, readiness)
        bindPrimaryAction(ready, readiness.serviceRunning)

        stopAction.visibility = if (readiness.serviceRunning) View.VISIBLE else View.GONE
        modeSummary.setText(modeLabel(readiness.mode))
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
        setupCardIcon.imageTintList = ColorStateList.valueOf(tint)
        setupCardChip.backgroundTintList = ColorStateList.valueOf(washOf(tint))
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

    /**
     * The hero says one thing: what the app is doing right now, and what the user does next.
     * Nothing here is inferred - a copied result is the only proof the pipeline ran end to end.
     */
    private fun bindHeadline(ready: Boolean, readiness: Readiness) {
        val headline: Int
        val detail: Int
        val icon: Int
        val tint: Int
        when {
            !ready -> {
                headline = R.string.state_setup_required
                detail = R.string.state_setup_required_detail
                icon = R.drawable.ic_warning
                tint = themeColor(R.attr.vtWarning)
            }
            readiness.resultCopied -> {
                headline = R.string.state_copied
                detail = R.string.state_copied_detail
                icon = R.drawable.ic_check
                tint = getColor(R.color.success_mint)
            }
            readiness.serviceRunning -> {
                headline = R.string.state_active
                detail = gestureHint(readiness.mode)
                icon = R.drawable.ic_mic
                tint = themeColor(R.attr.vtAccent)
            }
            else -> {
                headline = R.string.state_ready
                detail = gestureHint(readiness.mode)
                icon = R.drawable.ic_check
                tint = getColor(R.color.success_mint)
            }
        }
        readinessHeadline.setText(headline)
        readinessDetail.setText(detail)
        readinessIcon.setImageResource(icon)
        readinessIcon.imageTintList = ColorStateList.valueOf(tint)
        statusBadge.backgroundTintList = ColorStateList.valueOf(washOf(tint))
    }

    private fun gestureHint(mode: RecordingMode) =
        if (mode == RecordingMode.TAP_TO_TOGGLE) R.string.step_gesture_tap else R.string.step_gesture_hold

    /** A badge or chip carries the state colour at low opacity, so the glyph stays the loud part. */
    private fun washOf(tint: Int) = ColorUtils.setAlphaComponent(tint, 56)

    private fun bindPrimaryAction(ready: Boolean, running: Boolean) {
        if (running) {
            primaryAction.visibility = View.GONE
            return
        }
        primaryAction.visibility = View.VISIBLE
        primaryAction.setText(if (ready) R.string.action_start_bubble else R.string.action_set_up_and_start)
        primaryAction.setOnClickListener {
            if (ready) startBubble() else openSetup(autoRun = true, startAfter = true)
        }
    }

    private fun modeLabel(mode: RecordingMode) =
        if (mode == RecordingMode.TAP_TO_TOGGLE) R.string.mode_tap else R.string.mode_hold

    private fun toggleHow() {
        howExpanded = !howExpanded
        applyHowExpansion()
    }

    private fun applyHowExpansion() {
        howBody.visibility = if (howExpanded) View.VISIBLE else View.GONE
        howChevron.rotation = if (howExpanded) 180f else 0f
        findViewById<LinearLayout>(R.id.how_row).contentDescription = getString(
            R.string.how_it_works_title,
        ) + ", " + getString(if (howExpanded) R.string.a11y_collapse else R.string.a11y_expand)
    }

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

    private fun openSetup(autoRun: Boolean = false, startAfter: Boolean = false) {
        startAfterSetup = startAfter
        setupLauncher.launch(
            Intent(this, SetupActivity::class.java).putExtra(SetupActivity.EXTRA_AUTO_RUN, autoRun),
        )
    }

    private fun startBubble() {
        // The checklist owns every permission prompt, so a tap that arrives before it is done
        // goes there rather than raising a dialog this screen would have to handle too.
        if (SetupChecklist.blocking(readReadiness().setup).isNotEmpty()) {
            openSetup(autoRun = true, startAfter = true)
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

    private companion object {
        const val STATE_START_AFTER_SETUP = "start_after_setup"
    }
}
