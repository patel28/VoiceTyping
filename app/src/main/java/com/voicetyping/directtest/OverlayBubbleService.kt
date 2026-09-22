package com.voicetyping.directtest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import java.util.Locale

/** Visible hold-to-record shortcut. Transcribes with SpeechRecognizer, refines, then copies. */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class OverlayBubbleService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var state = BubbleFlowState.IDLE
    private var speechRecognizer: SpeechRecognizer? = null
    private var waitingForFinal = false
    private var finalDelivered = false
    private var lastPartial: String? = null
    private var recordingStartedAt = 0L
    private var processingFrame = 0
    private var refineThread: Thread? = null

    private val recordingTick = object : Runnable {
        override fun run() {
            if (state != BubbleFlowState.RECORDING) return
            updateBubbleAppearance()
            val elapsed = SystemClock.elapsedRealtime() - recordingStartedAt
            if (elapsed >= MAX_RECORDING_MS) finishRecordingAndProcess()
            else mainHandler.postDelayed(this, TIMER_TICK_MS)
        }
    }

    private val processingTick = object : Runnable {
        override fun run() {
            if (state != BubbleFlowState.PROCESSING) return
            processingFrame = (processingFrame + 1) % 3
            updateBubbleAppearance()
            mainHandler.postDelayed(this, PROCESSING_TICK_MS)
        }
    }

    private val finalResultTimeout = Runnable {
        if (state != BubbleFlowState.PROCESSING || finalDelivered) return@Runnable
        val fallback = lastPartial?.trim()?.takeIf { it.isNotEmpty() }
        if (fallback != null) {
            finalDelivered = true
            startRefinementAndCopy(fallback)
        } else {
            setError("No speech recognized. Try again.")
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            SpeechTranscripts.pickBest(matches)?.let { lastPartial = it }
        }

        override fun onResults(results: Bundle?) {
            if (finalDelivered) return
            if (state != BubbleFlowState.PROCESSING && state != BubbleFlowState.RECORDING) return
            finalDelivered = true
            mainHandler.removeCallbacks(finalResultTimeout)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val transcript = SpeechTranscripts.pickBest(matches) ?: lastPartial?.trim()?.takeIf { it.isNotEmpty() }
            if (transcript.isNullOrEmpty()) {
                setError("No speech recognized. Try again.")
            } else {
                if (state != BubbleFlowState.PROCESSING) {
                    state = BubbleFlowState.PROCESSING
                    processingFrame = 0
                    updateBubbleAppearance()
                    mainHandler.post(processingTick)
                }
                startRefinementAndCopy(transcript)
            }
        }

        override fun onError(error: Int) {
            if (finalDelivered) return
            if (state != BubbleFlowState.PROCESSING && state != BubbleFlowState.RECORDING) return
            // After stopListening we still wait briefly for a final result; otherwise fail fast.
            if (state == BubbleFlowState.PROCESSING && waitingForFinal) {
                val fallback = lastPartial?.trim()?.takeIf { it.isNotEmpty() }
                if (fallback != null && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    // Keep waiting for the timeout to prefer any pending final result.
                    return
                }
            }
            finalDelivered = true
            mainHandler.removeCallbacks(finalResultTimeout)
            setError(speechErrorMessage(error))
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForegroundForOverlayAndMicrophone()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Settings.canDrawOverlays(this)) attachBubble() else stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_START, null -> if (bubble == null && Settings.canDrawOverlays(this)) attachBubble()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundForOverlayAndMicrophone() {
        val notification = buildNotification()
        val microphone = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                microphone or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification, microphone)
        }
    }

    private fun attachBubble() {
        if (bubble != null) return
        val view = object : AppCompatTextView(this) {
            override fun performClick(): Boolean {
                super.performClick()
                return true
            }
        }.apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundResource(android.R.drawable.btn_default)
            elevation = dp(8).toFloat()
        }
        val params = WindowManager.LayoutParams(
            dp(BUBBLE_SIZE_DP),
            dp(BUBBLE_SIZE_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(180)
        }
        installTouchFlow(view, params)
        try {
            windowManager.addView(view, params)
            bubble = view
            state = if (ClipboardStateStore.read() != null) BubbleFlowState.COPIED else BubbleFlowState.IDLE
            updateBubbleAppearance()
        } catch (_: SecurityException) {
            stopSelf()
        } catch (_: WindowManager.BadTokenException) {
            stopSelf()
        }
    }

    private fun installTouchFlow(view: TextView, params: WindowManager.LayoutParams) {
        var downX = 0
        var downY = 0
        var rawX = 0f
        var rawY = 0f
        var moved = false
        var dragStarted = false

        val beginRecording = Runnable {
            if (!moved && state == BubbleFlowState.IDLE) startRecording()
        }
        val beginCopiedDrag = Runnable {
            if (moved || dragStarted || state != BubbleFlowState.COPIED) return@Runnable
            val copied = ClipboardStateStore.read() ?: return@Runnable
            dragStarted = runCatching {
                view.startDragAndDrop(
                    ClipData.newPlainText("VoiceTyping", copied),
                    View.DragShadowBuilder(view),
                    null,
                    View.DRAG_FLAG_GLOBAL,
                )
            }.getOrDefault(false)
            if (!dragStarted) showToast("Drag is not supported here. Use Paste instead.")
        }

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = params.x
                    downY = params.y
                    rawX = event.rawX
                    rawY = event.rawY
                    moved = false
                    dragStarted = false
                    view.isPressed = true
                    when (state) {
                        BubbleFlowState.IDLE -> view.postDelayed(beginRecording, HOLD_TO_RECORD_MS)
                        BubbleFlowState.COPIED -> view.postDelayed(beginCopiedDrag, HOLD_TO_DRAG_MS)
                        else -> Unit
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (state == BubbleFlowState.RECORDING || dragStarted) return@setOnTouchListener true
                    val dx = (event.rawX - rawX).toInt()
                    val dy = (event.rawY - rawY).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(MOVE_SLOP_DP)) {
                        if (!moved) {
                            view.removeCallbacks(beginRecording)
                            view.removeCallbacks(beginCopiedDrag)
                        }
                        moved = true
                        params.x = downX + dx
                        params.y = downY + dy
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(beginRecording)
                    view.removeCallbacks(beginCopiedDrag)
                    view.isPressed = false
                    when {
                        state == BubbleFlowState.RECORDING -> finishRecordingAndProcess()
                        state == BubbleFlowState.COPIED && !moved && !dragStarted &&
                            event.actionMasked == MotionEvent.ACTION_UP -> resetForNextRecording()
                        state == BubbleFlowState.ERROR && !moved && event.actionMasked == MotionEvent.ACTION_UP ->
                            resetForNextRecording()
                        state == BubbleFlowState.IDLE && !moved && event.actionMasked == MotionEvent.ACTION_UP ->
                            showToast("Hold the button to record")
                    }
                    dragStarted = false
                    true
                }

                else -> true
            }
        }
    }

    private fun startRecording() {
        if (state != BubbleFlowState.IDLE) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setError("Microphone permission is required")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setError("Speech recognition is unavailable")
            return
        }
        val recognizer = createSpeechRecognizerOrNull()
        if (recognizer == null) {
            setError("Could not start speech recognition")
            return
        }
        val intent = buildRecognizerIntent()
        speechRecognizer = recognizer
        waitingForFinal = false
        finalDelivered = false
        lastPartial = null
        ClipboardStateStore.clear()
        try {
            recognizer.setRecognitionListener(recognitionListener)
            recognizer.startListening(intent)
        } catch (_: SecurityException) {
            destroyRecognizer()
            setError("Microphone access was blocked")
            return
        } catch (_: IllegalStateException) {
            destroyRecognizer()
            setError("Could not start the microphone")
            return
        } catch (_: IllegalArgumentException) {
            destroyRecognizer()
            setError("Speech recognition is unavailable")
            return
        }
        recordingStartedAt = SystemClock.elapsedRealtime()
        state = BubbleFlowRules.hold(state)
        updateBubbleAppearance()
        mainHandler.post(recordingTick)
    }

    private fun createSpeechRecognizerOrNull(): SpeechRecognizer? {
        // Prefer the on-device recognizer; fall back to the system recognizer.
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                    return SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                }
            }
        }
        return runCatching { SpeechRecognizer.createSpeechRecognizer(this) }.getOrNull()
    }

    private fun buildRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
    }

    private fun finishRecordingAndProcess() {
        if (state != BubbleFlowState.RECORDING) return
        mainHandler.removeCallbacks(recordingTick)
        // Release the microphone; the final result arrives asynchronously.
        runCatching { speechRecognizer?.stopListening() }
        state = BubbleFlowRules.release(state)
        waitingForFinal = true
        processingFrame = 0
        updateBubbleAppearance()
        mainHandler.post(processingTick)
        mainHandler.postDelayed(finalResultTimeout, FINAL_RESULT_TIMEOUT_MS)
    }

    private fun startRefinementAndCopy(rawTranscript: String) {
        val existing = refineThread
        if (existing != null && existing.isAlive) runCatching { existing.interrupt() }
        val localeTag = Locale.getDefault().toLanguageTag()
        val worker = Thread({
            val config = runCatching { RefinementClient.loadConfig(applicationContext) }
                .getOrElse { RefinementClient.configForTesting() }
            val finalText = try {
                RefinementClient.refine(config, rawTranscript, localeTag)
            } catch (_: RefinementException) {
                rawTranscript
            } catch (_: Exception) {
                rawTranscript
            }
            mainHandler.post { completeCopy(finalText) }
        }, "voicetyping-refine-copy")
        refineThread = worker
        worker.isDaemon = true
        worker.start()
    }

    private fun completeCopy(finalText: String) {
        if (state != BubbleFlowState.PROCESSING) return
        mainHandler.removeCallbacks(processingTick)
        mainHandler.removeCallbacks(finalResultTimeout)
        val trimmed = finalText.trim()
        if (trimmed.isEmpty()) {
            setError("No speech recognized. Try again.")
            return
        }
        val copied = runCatching {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("VoiceTyping", trimmed))
            ClipboardStateStore.markCopied(trimmed)
        }.isSuccess
        waitingForFinal = false
        destroyRecognizer()
        state = BubbleFlowRules.processingFinished(copied)
        updateBubbleAppearance()
        if (copied) showToast("Copied. Paste, or hold and drag the green button.")
        else showToast("Copy failed. Tap the red button to try again.")
    }

    private fun destroyRecognizer() {
        mainHandler.removeCallbacks(finalResultTimeout)
        waitingForFinal = false
        val recognizer = speechRecognizer
        speechRecognizer = null
        if (recognizer != null) {
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
        }
    }

    private fun resetForNextRecording() {
        mainHandler.removeCallbacks(processingTick)
        mainHandler.removeCallbacks(finalResultTimeout)
        mainHandler.removeCallbacks(recordingTick)
        destroyRecognizer()
        refineThread?.let { runCatching { it.interrupt() } }
        refineThread = null
        finalDelivered = false
        lastPartial = null
        state = BubbleFlowRules.reset(state)
        ClipboardStateStore.clear()
        updateBubbleAppearance()
        showToast("Ready. Hold to record.")
    }

    private fun setError(message: String) {
        mainHandler.removeCallbacks(recordingTick)
        mainHandler.removeCallbacks(processingTick)
        mainHandler.removeCallbacks(finalResultTimeout)
        waitingForFinal = false
        destroyRecognizer()
        refineThread?.let { runCatching { it.interrupt() } }
        refineThread = null
        state = BubbleFlowState.ERROR
        updateBubbleAppearance()
        showToast(message)
    }

    private fun speechErrorMessage(code: Int): String {
        return when (code) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required"
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "No speech recognized. Try again."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                "Speech service unavailable. Try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                "Speech language is unavailable"
            else -> "Speech recognition failed. Try again."
        }
    }

    private fun updateBubbleAppearance() {
        val view = bubble ?: return
        val visual = when (state) {
            BubbleFlowState.IDLE -> BubbleVisual("HOLD", getColor(R.color.signal_blue), 10f, "Hold to record")
            BubbleFlowState.RECORDING -> {
                val seconds = (SystemClock.elapsedRealtime() - recordingStartedAt).coerceAtLeast(0L) / 1000f
                BubbleVisual(String.format(Locale.US, "%.1f", seconds), getColor(R.color.recording_coral), 16f, "Recording. Release to finish")
            }
            BubbleFlowState.PROCESSING -> BubbleVisual(
                ".".repeat(processingFrame + 1),
                getColor(R.color.signal_blue),
                18f,
                "Processing recording",
            )
            BubbleFlowState.COPIED -> BubbleVisual("✓", getColor(R.color.success_mint), 25f, "Copied. Hold and drag, or paste manually. Tap to record again")
            BubbleFlowState.ERROR -> BubbleVisual("!", getColor(R.color.recording_coral), 24f, "An error occurred. Tap to reset")
        }
        view.text = visual.label
        view.textSize = visual.textSize
        view.backgroundTintList = ColorStateList.valueOf(visual.color)
        view.contentDescription = visual.contentDescription
    }

    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        refineThread?.let { runCatching { it.interrupt() } }
        refineThread = null
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        isRunning = false
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground failed")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, OverlayBubbleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.bubble_notification_title))
            .setContentText("Hold the floating button to record; release to copy.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop bubble", stopIntent).build())
            .build()
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.bubble_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class BubbleVisual(
        val label: String,
        val color: Int,
        val textSize: Float,
        val contentDescription: String,
    )

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        const val ACTION_START = "com.voicetyping.directtest.action.START_BUBBLE"
        const val ACTION_STOP = "com.voicetyping.directtest.action.STOP_BUBBLE"
        private const val TAG = "OverlayBubble"
        private const val CHANNEL_ID = "voice_bubble"
        private const val NOTIFICATION_ID = 7421
        private const val BUBBLE_SIZE_DP = 62
        private const val MOVE_SLOP_DP = 10
        private const val HOLD_TO_RECORD_MS = 250L
        private const val HOLD_TO_DRAG_MS = 450L
        private const val MAX_RECORDING_MS = 10_000L
        private const val FINAL_RESULT_TIMEOUT_MS = 3_500L
        private const val TIMER_TICK_MS = 100L
        private const val PROCESSING_TICK_MS = 160L
    }
}
