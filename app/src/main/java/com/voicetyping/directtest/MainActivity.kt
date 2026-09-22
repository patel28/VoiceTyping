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
import androidx.core.net.toUri
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
    private lateinit var micIcon: ImageView
    private lateinit var micStatus: TextView
    private lateinit var micAction: Button
    private lateinit var overlayIcon: ImageView
    private lateinit var overlayStatus: TextView
    private lateinit var overlayAction: Button
    private lateinit var primaryAction: Button
    private lateinit var stopAction: Button
    private lateinit var modeSummary: TextView
    private lateinit var stepGesture: TextView
    private lateinit var privacyBody: TextView
    private lateinit var privacyChevron: ImageView
    private lateinit var apiKeyMissingWarning: View
    private lateinit var sandboxHelper: TextView
    private var privacyExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = AppSettingsStore(this)
        setContentView(R.layout.activity_main)

        signalRail = findViewById(R.id.signal_rail)
        readinessIcon = findViewById(R.id.readiness_icon)
        readinessHeadline = findViewById(R.id.readiness_headline)
        micIcon = findViewById(R.id.mic_icon)
        micStatus = findViewById(R.id.mic_status)
        micAction = findViewById(R.id.mic_action)
        overlayIcon = findViewById(R.id.overlay_icon)
        overlayStatus = findViewById(R.id.overlay_status)
        overlayAction = findViewById(R.id.overlay_action)
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
        findViewById<LinearLayout>(R.id.privacy_row).setOnClickListener { togglePrivacy() }
        micAction.setOnClickListener { requestMicrophone() }
        overlayAction.setOnClickListener { openOverlaySettings() }
        stopAction.setOnClickListener { stopBubble() }

        applyEdgeToEdgeInsets()
        applyPrivacyExpansion()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) refresh()
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
            micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
            overlayGranted = Settings.canDrawOverlays(this),
            serviceRunning = OverlayBubbleService.isRunning,
            resultCopied = ClipboardStateStore.read() != null,
            mode = settings.recordingMode,
            apiKeySaved = settings.apiKey.isNotBlank(),
        )
    }

    /** Everything the screen is allowed to claim, read once so the views cannot disagree. */
    data class Readiness(
        val micGranted: Boolean,
        val overlayGranted: Boolean,
        val serviceRunning: Boolean,
        val resultCopied: Boolean,
        val mode: RecordingMode,
        val apiKeySaved: Boolean = false,
    )

    @VisibleForTesting
    fun render(readiness: Readiness) {
        val ready = readiness.micGranted && readiness.overlayGranted

        bindPermissionRow(micIcon, micStatus, micAction, readiness.micGranted)
        bindPermissionRow(overlayIcon, overlayStatus, overlayAction, readiness.overlayGranted)
        apiKeyMissingWarning.visibility = if (readiness.apiKeySaved) View.GONE else View.VISIBLE

        signalRail.render(railStates(ready, readiness.resultCopied))
        bindHeadline(ready, readiness.serviceRunning, readiness.resultCopied)
        bindPrimaryAction(readiness.micGranted, readiness.overlayGranted, readiness.serviceRunning)

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
            getColor(if (readiness.resultCopied) R.color.success_mint else R.color.ink_muted),
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

    private fun bindPermissionRow(icon: ImageView, status: TextView, action: Button, granted: Boolean) {
        status.setText(if (granted) R.string.perm_ready else R.string.perm_missing)
        status.setTextColor(getColor(if (granted) R.color.success_mint else R.color.warning_amber_text))
        icon.imageTintList = android.content.res.ColorStateList.valueOf(
            getColor(if (granted) R.color.ink else R.color.warning_amber),
        )
        action.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun bindHeadline(ready: Boolean, running: Boolean, copied: Boolean) {
        val headline: Int
        val icon: Int
        val tint: Int
        when {
            !ready -> {
                headline = R.string.state_setup_required
                icon = R.drawable.ic_warning
                tint = R.color.warning_amber
            }
            copied -> {
                headline = R.string.rail_copied
                icon = R.drawable.ic_check
                tint = R.color.success_mint
            }
            running -> {
                headline = R.string.state_active
                icon = R.drawable.ic_mic
                tint = R.color.signal_blue
            }
            else -> {
                headline = R.string.state_ready
                icon = R.drawable.ic_check
                tint = R.color.success_mint
            }
        }
        readinessHeadline.setText(headline)
        readinessIcon.setImageResource(icon)
        readinessIcon.imageTintList = android.content.res.ColorStateList.valueOf(getColor(tint))
    }

    private fun bindPrimaryAction(micGranted: Boolean, overlayGranted: Boolean, running: Boolean) {
        when {
            running -> primaryAction.visibility = View.GONE
            !micGranted -> {
                primaryAction.visibility = View.VISIBLE
                primaryAction.setText(R.string.action_allow_mic)
                primaryAction.setOnClickListener { requestMicrophone() }
            }
            !overlayGranted -> {
                primaryAction.visibility = View.VISIBLE
                primaryAction.setText(R.string.action_allow_overlay)
                primaryAction.setOnClickListener { openOverlaySettings() }
            }
            else -> {
                primaryAction.visibility = View.VISIBLE
                primaryAction.setText(R.string.action_start_bubble)
                primaryAction.setOnClickListener { startBubble() }
            }
        }
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

    private fun requestMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        } else {
            refresh()
        }
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()),
        )
    }

    private fun startBubble() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicrophone()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
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

    companion object {
        private const val REQUEST_MIC = 41
    }
}
