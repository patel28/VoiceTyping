package com.voicetyping.directtest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Archived Prototype 2 tap/modal recorder. Not compiled by the active app. */
@SuppressLint("SetTextI18n") // This compatibility instrument intentionally ships in English only.
class RecorderActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null
    private val recording = AtomicBoolean(false)
    private var startedAt = 0L
    private var copied = false
    private lateinit var timer: TextView
    private lateinit var status: TextView
    private lateinit var waveform: SignalWaveformView
    private lateinit var copyButton: Button

    private val tick = object : Runnable {
        override fun run() {
            if (!recording.get()) return
            val elapsed = System.currentTimeMillis() - startedAt
            timer.text = String.format(Locale.US, "%02d.%01d", elapsed / 1000, (elapsed / 100) % 10)
            if (elapsed >= MAX_RECORDING_MS) {
                stopCapture()
                status.text = "Recording stopped\nTap Copy text, then paste in your app."
            } else {
                mainHandler.postDelayed(this, 100)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        copied = savedInstanceState?.getBoolean(KEY_COPIED) == true
        buildUi()
        if (copied) showCopiedUi()
    }

    override fun onResume() {
        super.onResume()
        if (!copied && !recording.get()) startCapture()
    }

    override fun onPause() {
        stopCapture()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_COPIED, copied)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        stopCapture()
        startService(Intent(this, OverlayBubbleService::class.java).setAction(OverlayBubbleService.ACTION_SHOW))
        super.onDestroy()
    }

    @Deprecated("The prototype deliberately uses the classic callback for its Android 13+ test flow")
    override fun onBackPressed() {
        cancelAndClose()
    }

    private fun buildUi() {
        window.setBackgroundDrawableResource(android.R.color.transparent)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
            background = roundedBackground(Color.rgb(245, 247, 250), 24)
            elevation = dp(16).toFloat()
        }
        root.addView(text("VoiceTyping", 22f, Color.rgb(21, 25, 31), true))
        status = text("Listening…\nSpeak now", 15f, Color.rgb(80, 89, 101))
        status.setLineSpacing(dp(3).toFloat(), 1f)
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        timer = text("00.0", 44f, Color.rgb(231, 85, 85), true).apply { typeface = Typeface.MONOSPACE }
        root.addView(timer, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) })
        waveform = SignalWaveformView(this)
        root.addView(waveform, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(6) })
        root.addView(
            text("Test output\n${ClipboardStateStore.OUTPUT_TEXT}", 13f, Color.rgb(80, 89, 101)).apply {
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedBackground(Color.rgb(235, 240, 248), 12)
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) },
        )
        copyButton = primaryButton("Copy text") { copyAndFinish() }
        root.addView(copyButton, fullWidthButton().apply { topMargin = dp(17) })
        root.addView(secondaryButton("Cancel") { cancelAndClose() }, fullWidthButton().apply { topMargin = dp(5) })
        setContentView(root)
        window.setLayout(dp(360), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun startCapture() {
        if (recording.get()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Microphone permission is required"
            return
        }
        val sampleRate = 16_000
        val minBytes = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBytes <= 0) {
            status.text = "Microphone is unavailable"
            return
        }
        val bufferBytes = maxOf(minBytes, sampleRate * 2 / 5)
        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes,
            )
        }.getOrNull()
        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder?.release()
            status.text = "Could not start microphone capture"
            return
        }
        audioRecord = recorder
        try {
            recorder.startRecording()
        } catch (_: IllegalStateException) {
            recorder.release()
            audioRecord = null
            status.text = "Could not start microphone capture"
            return
        }
        recording.set(true)
        startedAt = System.currentTimeMillis()
        mainHandler.post(tick)
        status.text = "Listening…\nSpeak now"
        audioThread = Thread({
            val samples = ShortArray(bufferBytes / 2)
            while (recording.get()) {
                val count = try {
                    recorder.read(samples, 0, samples.size)
                } catch (_: IllegalStateException) {
                    -1
                }
                if (count > 0 && recording.get()) {
                    val rms = RmsMeter.level(samples, count)
                    mainHandler.post { if (recording.get()) waveform.addLevel(rms) }
                }
            }
            samples.fill(0)
        }, "voicetyping-audio-capture").apply { isDaemon = true; start() }
    }

    private fun copyAndFinish() {
        if (copied) return
        copied = true
        stopCapture()
        val output = ClipboardStateStore.OUTPUT_TEXT
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("VoiceTyping", output))
        ClipboardStateStore.markCopied(output)
        showCopiedUi()
        mainHandler.postDelayed({ if (!isFinishing) finish() }, FINISH_DELAY_MS)
    }

    private fun showCopiedUi() {
        status.text = "Copied\nReturn to your app and Paste."
        status.setTextColor(Color.rgb(42, 157, 114))
        copyButton.isEnabled = false
        copyButton.text = "Copied"
    }

    private fun cancelAndClose() {
        stopCapture()
        finish()
    }

    private fun stopCapture() {
        if (!recording.getAndSet(false)) return
        mainHandler.removeCallbacks(tick)
        val recorder = audioRecord
        audioRecord = null
        if (recorder != null) {
            runCatching { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() }
            runCatching { recorder.release() }
        }
        val thread = audioThread
        audioThread = null
        // AudioRecord.stop unblocks read; join briefly so its in-memory sample array is zeroed before return.
        if (thread != null && thread !== Thread.currentThread()) runCatching { thread.join(350) }
    }

    private fun primaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(Color.rgb(40, 100, 220))
        setOnClickListener { action() }
    }

    private fun secondaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(Color.rgb(21, 25, 31))
        backgroundTintList = ColorStateList.valueOf(Color.rgb(229, 233, 239))
        setOnClickListener { action() }
    }

    private fun fullWidthButton() = LinearLayout.LayoutParams(-1, dp(48))

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun roundedBackground(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_RECORDING_MS = 10_000L
        private const val FINISH_DELAY_MS = 900L
        private const val KEY_COPIED = "copied"
    }
}
