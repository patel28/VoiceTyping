package com.voicetyping.directtest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import com.google.android.material.appbar.MaterialToolbar

/** Recording control, plus optional AI refinement kept behind a collapsed advanced section. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var store: AppSettingsStore
    private lateinit var modeGroup: RadioGroup
    private lateinit var holdOption: RadioButton
    private lateinit var tapOption: RadioButton
    private lateinit var bubbleVisibilityGroup: RadioGroup
    private lateinit var keyboardVisibleOption: RadioButton
    private lateinit var themeGroup: RadioGroup
    private lateinit var themeLagoonOption: RadioButton
    private lateinit var themeSignalOption: RadioButton
    private lateinit var setupStatus: TextView
    private lateinit var apiKeyField: EditText
    private lateinit var modelField: EditText
    private lateinit var visibilityButton: Button
    private lateinit var advancedContent: LinearLayout
    private lateinit var advancedChevron: ImageView
    private lateinit var saveStatus: LinearLayout
    private lateinit var saveStatusIcon: ImageView
    private lateinit var saveStatusText: TextView
    private var apiKeyVisible = false
    private var advancedExpanded = false
    private var appliedTheme = AppTheme.AMBER

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedTheme = AppSettingsStore(this).readTheme()
        setTheme(appliedTheme.styleRes())
        super.onCreate(savedInstanceState)
        store = AppSettingsStore(this)
        setContentView(R.layout.activity_settings)

        modeGroup = findViewById(R.id.mode_group)
        holdOption = findViewById(R.id.mode_hold)
        tapOption = findViewById(R.id.mode_tap)
        bubbleVisibilityGroup = findViewById(R.id.bubble_visibility_group)
        keyboardVisibleOption = findViewById(R.id.bubble_visibility_keyboard)
        themeGroup = findViewById(R.id.theme_group)
        themeLagoonOption = findViewById(R.id.theme_lagoon)
        themeSignalOption = findViewById(R.id.theme_signal)
        setupStatus = findViewById(R.id.background_setup_status)
        apiKeyField = findViewById(R.id.api_key_field)
        modelField = findViewById(R.id.model_field)
        visibilityButton = findViewById(R.id.api_key_visibility)
        advancedContent = findViewById(R.id.advanced_content)
        advancedChevron = findViewById(R.id.advanced_chevron)
        saveStatus = findViewById(R.id.save_status)
        saveStatusIcon = findViewById(R.id.save_status_icon)
        saveStatusText = findViewById(R.id.save_status_text)

        findViewById<MaterialToolbar>(R.id.settings_toolbar).setNavigationOnClickListener { finish() }

        holdOption.text = optionText(R.string.mode_hold, R.string.mode_hold_detail)
        holdOption.contentDescription = optionContentDescription(
            R.string.mode_hold,
            R.string.mode_hold_detail,
            R.string.a11y_gesture_hold_diagram,
        )
        tapOption.text = optionText(R.string.mode_tap, R.string.mode_tap_detail)
        tapOption.contentDescription = optionContentDescription(
            R.string.mode_tap,
            R.string.mode_tap_detail,
            R.string.a11y_gesture_tap_diagram,
        )
        tintDiagram(holdOption)
        tintDiagram(tapOption)

        val settings = store.read()
        modeGroup.check(
            if (settings.recordingMode == RecordingMode.TAP_TO_TOGGLE) tapOption.id else holdOption.id,
        )
        modeGroup.setOnCheckedChangeListener { _, _ -> hideSaveStatus() }
        bubbleVisibilityGroup.check(
            if (settings.bubbleVisibilityMode == BubbleVisibilityMode.WHEN_KEYBOARD_VISIBLE_EXPERIMENTAL) {
                keyboardVisibleOption.id
            } else {
                R.id.bubble_visibility_always
            },
        )
        bubbleVisibilityGroup.setOnCheckedChangeListener { _, _ -> hideSaveStatus() }

        themeGroup.check(
            when (settings.theme) {
                AppTheme.LAGOON -> R.id.theme_lagoon
                AppTheme.SIGNAL -> R.id.theme_signal
                AppTheme.AMBER -> R.id.theme_amber
            },
        )
        themeGroup.setOnCheckedChangeListener { _, _ -> hideSaveStatus() }

        apiKeyField.setText(settings.apiKey)
        apiKeyField.transformationMethod = PasswordTransformationMethod.getInstance()
        modelField.hint = AppSettings.DEFAULT_REFINEMENT_MODEL
        modelField.setText(settings.refinementModel)

        visibilityButton.setOnClickListener { toggleApiKeyVisibility() }
        findViewById<Button>(R.id.api_key_clear).setOnClickListener {
            apiKeyField.setText("")
            store.clearApiKey()
            hideSaveStatus()
        }
        findViewById<LinearLayout>(R.id.advanced_row).setOnClickListener { toggleAdvanced() }
        findViewById<Button>(R.id.save_action).setOnClickListener { save() }

        findViewById<LinearLayout>(R.id.background_setup_row).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        applyEdgeToEdgeInsets()
        applyAdvancedExpansion()
    }

    override fun onResume() {
        super.onResume()
        bindSetupRow()
    }

    /** How much of the checklist is left, read on every resume because the setup screen changes it. */
    private fun bindSetupRow() {
        val status = SetupStatus(
            micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
            overlayGranted = Settings.canDrawOverlays(this),
            batteryExempt = BackgroundAccess.isBatteryExempt(this),
            hasVendorAutostart = BackgroundAccess.hasVendorAutostartList(),
            autostartConfirmed = store.readAutostartConfirmed(),
        )
        val complete = SetupChecklist.isComplete(status)
        setupStatus.text = if (complete) {
            getString(R.string.settings_setup_row_detail)
        } else {
            getString(
                R.string.setup_progress,
                SetupChecklist.doneCount(status),
                SetupChecklist.requirements(status).size,
            )
        }
        setupStatus.setTextColor(
            if (complete) themeColor(R.attr.vtInkMuted) else themeColor(R.attr.vtWarningText),
        )
    }

    private fun applyEdgeToEdgeInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = findViewById<LinearLayout>(R.id.settings_root)
        val toolbar = findViewById<MaterialToolbar>(R.id.settings_toolbar)
        val scroll = findViewById<ScrollView>(R.id.settings_scroll)
        val toolbarTop = toolbar.paddingTop
        val scrollBottom = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.updatePadding(left = bars.left, right = bars.right)
            toolbar.updatePadding(top = toolbarTop + bars.top)
            scroll.updatePadding(bottom = scrollBottom + maxOf(bars.bottom, keyboard))
            insets
        }
    }

    /** Title in bold over a smaller, muted detail line: one label, one radio, one tap target. */
    private fun optionText(titleRes: Int, detailRes: Int): CharSequence {
        val title = getString(titleRes)
        val detail = getString(detailRes)
        return SpannableStringBuilder("$title\n$detail").apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val detailStart = title.length + 1
            setSpan(
                RelativeSizeSpan(0.84f),
                detailStart,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(
                ForegroundColorSpan(themeColor(R.attr.vtInkMuted)),
                detailStart,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun optionContentDescription(titleRes: Int, detailRes: Int, diagramRes: Int) =
        "${getString(titleRes)}. ${getString(detailRes)} ${getString(diagramRes)}"

    private fun tintDiagram(option: RadioButton) {
        TextViewCompat.setCompoundDrawableTintList(option, ColorStateList.valueOf(themeColor(R.attr.vtInk)))
    }

    private fun toggleApiKeyVisibility() {
        apiKeyVisible = !apiKeyVisible
        apiKeyField.transformationMethod = if (apiKeyVisible) {
            HideReturnsTransformationMethod.getInstance()
        } else {
            PasswordTransformationMethod.getInstance()
        }
        apiKeyField.setSelection(apiKeyField.length())
        visibilityButton.setText(if (apiKeyVisible) R.string.action_hide else R.string.action_show)
    }

    private fun toggleAdvanced() {
        advancedExpanded = !advancedExpanded
        applyAdvancedExpansion()
    }

    private fun applyAdvancedExpansion() {
        advancedContent.visibility = if (advancedExpanded) View.VISIBLE else View.GONE
        advancedChevron.rotation = if (advancedExpanded) 180f else 0f
        findViewById<LinearLayout>(R.id.advanced_row).contentDescription = getString(
            R.string.advanced_title,
        ) + ", " + getString(if (advancedExpanded) R.string.a11y_collapse else R.string.a11y_expand)
    }

    /** Explicit save with inline feedback; nothing is written silently on the way out. */
    private fun save() {
        val mode = if (modeGroup.checkedRadioButtonId == tapOption.id) {
            RecordingMode.TAP_TO_TOGGLE
        } else {
            RecordingMode.HOLD_TO_RECORD
        }
        val chosenTheme = when (themeGroup.checkedRadioButtonId) {
            themeLagoonOption.id -> AppTheme.LAGOON
            themeSignalOption.id -> AppTheme.SIGNAL
            else -> AppTheme.AMBER
        }
        val saved = store.save(
            AppSettings(
                recordingMode = mode,
                bubbleVisibilityMode = if (
                    bubbleVisibilityGroup.checkedRadioButtonId == keyboardVisibleOption.id
                ) {
                    BubbleVisibilityMode.WHEN_KEYBOARD_VISIBLE_EXPERIMENTAL
                } else {
                    BubbleVisibilityMode.ALWAYS_SHOW
                },
                theme = chosenTheme,
                apiKey = apiKeyField.text.toString(),
                refinementModel = modelField.text.toString(),
            ),
        )
        if (saved && OverlayBubbleService.isRunning) {
            startService(
                Intent(this, OverlayBubbleService::class.java)
                    .setAction(OverlayBubbleService.ACTION_REFRESH_VISIBILITY),
            )
        }
        showSaveStatus(saved)
        // Recreating is the only way an open window picks up a different theme. Do it last so
        // the saved state is already written and the bubble has been told.
        if (saved && chosenTheme != appliedTheme) recreate()
    }

    private fun showSaveStatus(saved: Boolean) {
        val message = getString(if (saved) R.string.save_success else R.string.save_error)
        val color = getColor(if (saved) R.color.success_mint else R.color.recording_coral)
        saveStatusIcon.setImageResource(if (saved) R.drawable.ic_check else R.drawable.ic_warning)
        saveStatusIcon.imageTintList = ColorStateList.valueOf(color)
        saveStatusText.text = message
        saveStatusText.setTextColor(color)
        saveStatus.visibility = View.VISIBLE
        saveStatus.announceForAccessibility(message)
    }

    private fun hideSaveStatus() {
        saveStatus.visibility = View.INVISIBLE
    }
}
