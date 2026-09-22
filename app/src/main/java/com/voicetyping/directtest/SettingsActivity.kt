package com.voicetyping.directtest

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.text.method.HideReturnsTransformationMethod
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Private, explicit controls for the only two supported recording experiences. */
class SettingsActivity : AppCompatActivity() {
    private lateinit var store: AppSettingsStore
    private lateinit var apiKeyField: EditText
    private lateinit var modelField: EditText
    private lateinit var modeGroup: RadioGroup
    private var apiKeyVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AppSettingsStore(this)
        val settings = store.read()
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun text(value: String, size: Float, color: Int = R.color.ink) = TextView(this).apply {
            this.text = value; textSize = size; setTextColor(getColor(color))
        }
        fun card(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(getColor(R.color.white)); cornerRadius = dp(18).toFloat()
            }
        }
        fun margin(bottom: Int) = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(bottom) }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(getColor(R.color.surface))
        }
        page.addView(text("Settings", 28f).apply { setTypeface(typeface, Typeface.BOLD) }, margin(4))
        page.addView(text("Control how VoiceTyping records and refines speech.", 14f).apply {
            alpha = .68f; setPadding(0, 0, 0, dp(18))
        }, margin(8))

        val recordingCard = card()
        recordingCard.addView(text("Recording mode", 17f).apply { setTypeface(typeface, Typeface.BOLD) })
        recordingCard.addView(text("Choose the interaction that feels most natural.", 13f).apply {
            alpha = .7f; setPadding(0, dp(4), 0, dp(8))
        })
        modeGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        fun modeButton(label: String, description: String, mode: RecordingMode) = RadioButton(this).apply {
            id = View.generateViewId(); tag = mode; text = "$label\n$description"; textSize = 15f
            setTextColor(getColor(R.color.ink)); setPadding(0, dp(7), 0, dp(7))
        }
        val hold = modeButton("Hold to record", "Hold the floating mic while speaking; release to finish.", RecordingMode.HOLD_TO_RECORD)
        val tap = modeButton("Tap to toggle", "Tap once to start recording and once again to finish.", RecordingMode.TAP_TO_TOGGLE)
        modeGroup.addView(hold); modeGroup.addView(tap)
        modeGroup.check(if (settings.recordingMode == RecordingMode.HOLD_TO_RECORD) hold.id else tap.id)
        recordingCard.addView(modeGroup)
        page.addView(recordingCard, margin(12))

        val refineCard = card()
        refineCard.addView(text("Refinement", 17f).apply { setTypeface(typeface, Typeface.BOLD) })
        refineCard.addView(text("Optional personal configuration for text refinement.", 13f).apply {
            alpha = .7f; setPadding(0, dp(4), 0, dp(10))
        })
        refineCard.addView(text("API key", 13f).apply { setTypeface(typeface, Typeface.BOLD) })
        apiKeyField = EditText(this).apply {
            hint = "Optional API key"; setText(settings.apiKey)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            setSingleLine(true); setPadding(dp(14), dp(8), dp(14), dp(8))
            contentDescription = "API key"
        }
        refineCard.addView(apiKeyField, margin(6))
        val keyActions = LinearLayout(this).apply { gravity = Gravity.END; orientation = LinearLayout.HORIZONTAL }
        keyActions.addView(Button(this).apply { text = "Show"; isAllCaps = false; setOnClickListener { toggleApiKeyVisibility(this) } })
        keyActions.addView(Button(this).apply { text = "Clear"; isAllCaps = false; setOnClickListener {
            apiKeyField.setText(""); store.clearApiKey()
        } })
        refineCard.addView(keyActions, margin(4))
        refineCard.addView(text("Your key is encrypted on this device and sent only to OpenAI. Direct mobile keys can still be extracted from a compromised device; production accounts should use a server.", 12f, R.color.warning_amber).apply {
            setPadding(0, dp(4), 0, dp(14))
        })
        refineCard.addView(text("Refinement model", 13f).apply { setTypeface(typeface, Typeface.BOLD) })
        modelField = EditText(this).apply {
            hint = AppSettings.DEFAULT_REFINEMENT_MODEL; setText(settings.refinementModel)
            inputType = InputType.TYPE_CLASS_TEXT; setSingleLine(true)
            contentDescription = "Refinement model"
        }
        refineCard.addView(modelField)
        page.addView(refineCard, margin(18))

        page.addView(Button(this).apply {
            text = "Save settings"; isAllCaps = false; setTextColor(getColor(R.color.white))
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.signal_blue))
            setOnClickListener {
                if (save()) {
                    Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@SettingsActivity, "API key could not be secured on this device", Toast.LENGTH_LONG).show()
                }
            }
        })
        setContentView(ScrollView(this).apply { addView(page) })
    }

    override fun onPause() { save(); super.onPause() }

    private fun toggleApiKeyVisibility(button: Button) {
        apiKeyVisible = !apiKeyVisible
        apiKeyField.transformationMethod = if (apiKeyVisible) HideReturnsTransformationMethod.getInstance()
        else PasswordTransformationMethod.getInstance()
        apiKeyField.setSelection(apiKeyField.length())
        button.text = if (apiKeyVisible) "Hide" else "Show"
    }

    private fun save(): Boolean {
        val selected = modeGroup.findViewById<RadioButton>(modeGroup.checkedRadioButtonId)
        return store.save(AppSettings(
            recordingMode = selected?.tag as? RecordingMode ?: RecordingMode.HOLD_TO_RECORD,
            apiKey = apiKeyField.text.toString(),
            refinementModel = modelField.text.toString(),
        ))
    }
}
