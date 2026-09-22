package com.voicetyping.directtest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Setup screen: mic, overlay, bubble, and local refinement server status. */
@SuppressLint("SetTextI18n") // This compatibility instrument intentionally ships in English only.
class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var bubbleButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(getColor(R.color.white))
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.signal_blue))
            setOnClickListener { action() }
        }
        fun section(title: String, detail: String): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(getColor(R.color.white))
                cornerRadius = dp(16).toFloat()
            }
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(getColor(R.color.ink))
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = detail
                setTextColor(getColor(R.color.ink))
                alpha = .72f
                textSize = 13f
                setPadding(0, dp(4), 0, 0)
            })
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(24))
            setBackgroundColor(getColor(R.color.surface))
        }
        page.addView(TextView(this).apply {
            text = "VoiceTyping / Clipboard mode"
            setTextColor(getColor(R.color.ink))
            textSize = 25f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        page.addView(TextView(this).apply {
            text = "Quick test · Android 13+"
            setTextColor(getColor(R.color.ink))
            alpha = .65f
            textSize = 14f
            setPadding(0, dp(5), 0, dp(18))
        })

        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(getColor(R.color.ink))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(getColor(R.color.white)); cornerRadius = dp(16).toFloat()
            }
        }
        page.addView(statusText, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        page.addView(button("Allow microphone", ::requestMicrophone), spaced(dp(8)))
        page.addView(button("Open overlay permission", ::openOverlaySettings), spaced(dp(8)))
        bubbleButton = button("Start floating mic", ::toggleBubble)
        page.addView(bubbleButton, spaced(dp(16)))

        page.addView(
            section(
                "HOW IT WORKS",
                "1. Hold the blue button and speak (on-device speech when available).\n" +
                    "2. Release it — transcription is refined, then the green check means copied.\n" +
                    "3. If refinement fails, the raw transcript is copied instead.\n" +
                    "4. Paste normally, or hold and drag the green check where supported.\n\n" +
                    "Quick-tap the green check to record again.",
            ),
            spaced(dp(10)),
        )

        page.addView(
            section(
                "REFINEMENT SERVER",
                "Debug builds refine via http://127.0.0.1:8787.\n" +
                    "Run: node server/server.mjs, then adb reverse tcp:8787 tcp:8787.",
            ),
            spaced(dp(10)),
        )

        page.addView(section("TRY PASTE HERE", "Speak via the bubble, then paste the copied text into this field."), spaced(dp(10)))
        val testField = EditText(this).apply {
            hint = "Long-press here and tap Paste"
            minLines = 2
            gravity = Gravity.TOP
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(getColor(R.color.ink))
            setHintTextColor(getColor(R.color.ink))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(getColor(R.color.white)); cornerRadius = dp(12).toFloat()
                setStroke(dp(1), getColor(R.color.signal_blue))
            }
        }
        page.addView(testField, spaced(dp(10)))
        page.addView(TextView(this).apply {
            text = "Result: your speech, refined when the local server is reachable."
            setTextColor(getColor(R.color.ink)); alpha = .65f; textSize = 12f
            setPadding(dp(2), dp(4), dp(2), 0)
        })

        val scroll = ScrollView(this).apply { addView(page) }
        setContentView(scroll)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) refreshStatus()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) refreshStatus()
    }

    private fun spaced(bottom: Int) = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = bottom }

    private fun requestMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        } else refreshStatus()
    }

    private fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun toggleBubble() {
        val intent = Intent(this, OverlayBubbleService::class.java)
        if (OverlayBubbleService.isRunning) {
            stopService(intent)
        } else {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestMicrophone()
                return
            }
            if (!Settings.canDrawOverlays(this)) {
                openOverlaySettings()
                return
            }
            intent.action = OverlayBubbleService.ACTION_START
            startForegroundService(intent)
        }
        refreshStatus()
    }

    private fun refreshStatus() {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val overlay = Settings.canDrawOverlays(this)
        statusText.text = "MICROPHONE    ${if (mic) "Ready" else "Permission needed"}\n" +
            "OVERLAY             ${if (overlay) "Allowed" else "Permission needed"}\n" +
            "BUBBLE               ${if (OverlayBubbleService.isRunning) "Running" else "Stopped"}"
        if (::bubbleButton.isInitialized) bubbleButton.text = if (OverlayBubbleService.isRunning) "Stop floating mic" else "Start floating mic"
    }

    companion object { private const val REQUEST_MIC = 41 }
}
