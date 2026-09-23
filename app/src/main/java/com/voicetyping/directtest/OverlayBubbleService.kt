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
import android.graphics.Color
import android.graphics.PixelFormat
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
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

/** Visible recording shortcut. Transcribes with SpeechRecognizer, refines, then copies. */
@SuppressLint("ClickableViewAccessibility", "InflateParams")
class OverlayBubbleService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var appSettingsStore: AppSettingsStore
    private var bubble: BubbleControlView? = null
    private var bubbleLayoutParams: WindowManager.LayoutParams? = null
    private var imeObserver: View? = null
    /** Raw observer value, kept separately from the 200 ms debounced UI visibility value. */
    private var observedImeVisible = false
    private var imeVisible = false
    private var dragGuide: View? = null
    private var firstUseHint: View? = null
    private var copiedDragInProgress = false
    private var state = BubbleFlowState.IDLE
    private var speechRecognizer: SpeechRecognizer? = null
    private var waitingForFinal = false
    private var finalDelivered = false
    private var lastPartial: String? = null
    private var recordingStartedAt = 0L
    private var recordingSessionMode = RecordingMode.HOLD_TO_RECORD
    private var refineThread: Thread? = null

    private val hideBubbleAfterIme = Runnable {
        imeVisible = false
        applyBubbleVisibility()
    }

    /** The check is feedback, not a second interaction state that blocks the next recording. */
    private val clearCopiedFeedback = Runnable {
        resetTerminalFeedbackForNextInput()
    }

    private val recordingTick = object : Runnable {
        override fun run() {
            if (state != BubbleFlowState.RECORDING) return
            updateBubbleAppearance()
            val elapsed = SystemClock.elapsedRealtime() - recordingStartedAt
            if (elapsed >= MAX_RECORDING_MS) finishRecordingAndProcess()
            else mainHandler.postDelayed(this, TIMER_TICK_MS)
        }
    }

    private val finalResultTimeout = Runnable {
        if (state != BubbleFlowState.PROCESSING || finalDelivered) return@Runnable
        val fallback = lastPartial?.trim()?.takeIf { it.isNotEmpty() }
        if (fallback != null) {
            finalDelivered = true
            startRefinementAndCopy(fallback)
        } else {
            setError(R.string.error_no_speech)
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
                setError(R.string.error_no_speech)
            } else {
                if (state != BubbleFlowState.PROCESSING) {
                    state = BubbleFlowState.PROCESSING
                    updateBubbleAppearance()
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
        appSettingsStore = AppSettingsStore(this)
        if (Settings.canDrawOverlays(this)) attachBubble() else stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_REFRESH_VISIBILITY -> {
                imeObserver?.let(ViewCompat::requestApplyInsets)
                applyBubbleVisibility()
            }
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
        state = if (ClipboardStateStore.read() != null) BubbleFlowState.COPIED else BubbleFlowState.IDLE
        attachImeObserver()
        val view = BubbleControlView(this)
        view.visibility = if (shouldShowBubble()) View.VISIBLE else View.GONE
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
            bubbleLayoutParams = params
            updateBubbleAppearance()
        } catch (_: SecurityException) {
            stopSelf()
        } catch (_: WindowManager.BadTokenException) {
            stopSelf()
        }
    }

    /**
     * A fully transparent, non-touchable overlay sits behind the IME and receives its insets.
     * This is best-effort: floating and some OEM keyboards report no IME inset.
     */
    private fun attachImeObserver() {
        if (imeObserver != null) return
        val observer = View(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        ViewCompat.setOnApplyWindowInsetsListener(observer) { _, insets ->
            onImeVisibilityChanged(insets.isVisible(WindowInsetsCompat.Type.ime()))
            insets
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 0f
        }
        runCatching {
            windowManager.addView(observer, params)
            imeObserver = observer
            ViewCompat.requestApplyInsets(observer)
        }
    }

    private fun onImeVisibilityChanged(visible: Boolean) {
        val resetForNewKeyboardSession = BubbleVisibilityRules.shouldResetForNewKeyboardSession(
            mode = appSettingsStore.read().bubbleVisibilityMode,
            wasImeVisible = observedImeVisible,
            isImeVisible = visible,
            state = state,
        )
        observedImeVisible = visible
        mainHandler.removeCallbacks(hideBubbleAfterIme)
        if (visible) {
            imeVisible = true
            if (resetForNewKeyboardSession) resetTerminalFeedbackForNextInput()
            applyBubbleVisibility()
        } else {
            mainHandler.postDelayed(hideBubbleAfterIme, IME_HIDE_DEBOUNCE_MS)
        }
    }

    private fun shouldShowBubble(): Boolean {
        val mode = appSettingsStore.read().bubbleVisibilityMode
        if (mode == BubbleVisibilityMode.WHEN_KEYBOARD_VISIBLE_EXPERIMENTAL && imeObserver == null) {
            return true
        }
        return BubbleVisibilityRules.shouldShow(mode, imeVisible, state)
    }

    private fun applyBubbleVisibility() {
        val view = bubble ?: return
        view.visibility = if (shouldShowBubble()) View.VISIBLE else View.GONE
        if (view.visibility == View.VISIBLE && state == BubbleFlowState.IDLE) {
            bubbleLayoutParams?.let(::showFirstUseHintIfNeeded)
        } else if (view.visibility != View.VISIBLE) {
            removeFirstUseHint()
        }
    }

    private fun installTouchFlow(view: BubbleControlView, params: WindowManager.LayoutParams) {
        var downX = 0
        var downY = 0
        var rawX = 0f
        var rawY = 0f
        var moved = false
        var dragStarted = false
        var gestureMode = RecordingMode.HOLD_TO_RECORD

        val beginRecording = Runnable {
            if (RecordingGestureRules.shouldArmHold(gestureMode, state) && !moved) {
                startRecording(gestureMode)
            }
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
            if (dragStarted) {
                copiedDragInProgress = true
                showCopiedDragGuide()
            } else {
                showToast(R.string.drag_unsupported)
            }
        }

        // A global drag can finish after the source receives ACTION_UP. The source is the
        // reliable place to observe ACTION_DRAG_ENDED, so the visual guide never becomes a
        // drop target itself.
        view.setOnDragListener { _, event ->
            if (event.action == android.view.DragEvent.ACTION_DRAG_ENDED) {
                copiedDragInProgress = false
                dragStarted = false
                removeCopiedDragGuide()
            }
            true
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
                    gestureMode = currentRecordingMode()
                    view.isPressed = true
                    if (state == BubbleFlowState.IDLE) view.setArmed(true)
                    when (state) {
                        BubbleFlowState.IDLE -> if (RecordingGestureRules.shouldArmHold(gestureMode, state)) {
                            view.postDelayed(beginRecording, HOLD_TO_RECORD_MS)
                        }
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
                    view.setArmed(false)
                    when {
                        RecordingGestureRules.shouldStopOnRelease(state) -> finishRecordingAndProcess()
                        state == BubbleFlowState.COPIED && !moved && !dragStarted &&
                            event.actionMasked == MotionEvent.ACTION_UP -> resetForNextRecording()
                        state == BubbleFlowState.ERROR && !moved && event.actionMasked == MotionEvent.ACTION_UP ->
                            resetForNextRecording()
                        state == BubbleFlowState.IDLE && !moved && event.actionMasked == MotionEvent.ACTION_UP -> {
                            view.performClick()
                            if (RecordingGestureRules.shouldStartOnRelease(gestureMode, state, moved)) {
                                startRecording(gestureMode)
                            }
                            else showToast(R.string.toast_hold_required)
                        }
                    }
                    if (!copiedDragInProgress) dragStarted = false
                    true
                }

                else -> true
            }
        }
    }

    /**
     * This window is deliberately NOT_TOUCHABLE: it is a visual affordance only, so Android
     * continues to deliver global drag/drop events to the app underneath it. It shades the screen
     * edges only, so the destination field the user is aiming at stays readable, and it can
     * neither receive focus nor become a drop target.
     */
    private fun showCopiedDragGuide() {
        if (dragGuide != null) return
        val guide = LayoutInflater.from(this).inflate(R.layout.overlay_drag_strip, null)
        val guideParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        runCatching {
            windowManager.addView(guide, guideParams)
            dragGuide = guide
        }
    }

    private fun removeCopiedDragGuide() {
        val guide = dragGuide ?: return
        dragGuide = null
        runCatching { windowManager.removeView(guide) }
    }

    /** The gesture hint appears beside the control on first use only, then never again. */
    private fun showFirstUseHintIfNeeded(bubbleParams: WindowManager.LayoutParams) {
        val preferences = getSharedPreferences(UI_PREFERENCES_NAME, MODE_PRIVATE)
        if (preferences.getBoolean(KEY_FIRST_USE_HINT_SHOWN, false)) return
        preferences.edit { putBoolean(KEY_FIRST_USE_HINT_SHOWN, true) }
        val hint = LayoutInflater.from(this).inflate(R.layout.overlay_first_use_hint, null) as TextView
        hint.setText(
            if (currentRecordingMode() == RecordingMode.TAP_TO_TOGGLE) {
                R.string.hint_first_use_tap
            } else {
                R.string.hint_first_use_hold
            },
        )
        val hintParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleParams.x + dp(BUBBLE_SIZE_DP) + dp(8)
            y = bubbleParams.y + dp(14)
        }
        runCatching {
            windowManager.addView(hint, hintParams)
            firstUseHint = hint
        }
        mainHandler.postDelayed(Runnable { removeFirstUseHint() }, FIRST_USE_HINT_MS)
    }

    private fun removeFirstUseHint() {
        val hint = firstUseHint ?: return
        firstUseHint = null
        runCatching { windowManager.removeView(hint) }
    }

    private fun startRecording(mode: RecordingMode = currentRecordingMode()) {
        if (state != BubbleFlowState.IDLE) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setError(R.string.error_mic_permission)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setError(R.string.error_speech_unavailable)
            return
        }
        val recognizer = createSpeechRecognizerOrNull()
        if (recognizer == null) {
            setError(R.string.error_speech_unavailable)
            return
        }
        val intent = buildRecognizerIntent(mode)
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
            setError(R.string.error_mic_blocked)
            return
        } catch (_: IllegalStateException) {
            destroyRecognizer()
            setError(R.string.error_mic_blocked)
            return
        } catch (_: IllegalArgumentException) {
            destroyRecognizer()
            setError(R.string.error_speech_unavailable)
            return
        }
        recordingStartedAt = SystemClock.elapsedRealtime()
        recordingSessionMode = mode
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

    private fun buildRecognizerIntent(mode: RecordingMode): Intent {
        val completeSilenceMs = if (mode == RecordingMode.TAP_TO_TOGGLE) {
            TAP_COMPLETE_SILENCE_MS
        } else {
            HOLD_COMPLETE_SILENCE_MS
        }
        val possibleSilenceMs = if (mode == RecordingMode.TAP_TO_TOGGLE) {
            TAP_POSSIBLE_SILENCE_MS
        } else {
            HOLD_POSSIBLE_SILENCE_MS
        }
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            // These are endpointing hints. Some vendor recognition services may ignore them,
            // but Google speech services honour them on most supported devices.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, completeSilenceMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, possibleSilenceMs)
        }
    }

    private fun finishRecordingAndProcess() {
        if (state != BubbleFlowState.RECORDING) return
        mainHandler.removeCallbacks(recordingTick)
        // Release the microphone; the final result arrives asynchronously.
        runCatching { speechRecognizer?.stopListening() }
        state = BubbleFlowRules.release(state)
        waitingForFinal = true
        updateBubbleAppearance()
        mainHandler.postDelayed(finalResultTimeout, FINAL_RESULT_TIMEOUT_MS)
    }

    private fun startRefinementAndCopy(rawTranscript: String) {
        val existing = refineThread
        if (existing != null && existing.isAlive) runCatching { existing.interrupt() }
        val localeTag = Locale.getDefault().toLanguageTag()
        val worker = Thread({
            val settings = appSettingsStore.read()
            val outcome = if (settings.apiKey.isNotBlank()) {
                refineDirectWithRetries(settings, rawTranscript, localeTag)
            } else {
                val config = runCatching { RefinementClient.loadConfig(applicationContext) }
                    .getOrElse { RefinementClient.configForTesting() }
                try {
                    RefinementOutcome(RefinementClient.refine(config, rawTranscript, localeTag), refined = true)
                } catch (_: RefinementException) {
                    RefinementOutcome(rawTranscript, refined = false)
                } catch (_: Exception) {
                    RefinementOutcome(rawTranscript, refined = false)
                }
            }
            mainHandler.post { completeCopy(outcome.text, outcome.refined) }
        }, "voicetyping-refine-copy")
        refineThread = worker
        worker.isDaemon = true
        worker.start()
    }

    private fun refineDirectWithRetries(
        settings: AppSettings,
        rawTranscript: String,
        localeTag: String,
    ): RefinementOutcome {
        for (attempt in 0 until DIRECT_MAX_ATTEMPTS) {
            when (val result = DirectOpenAiRefiner.refine(
                apiKey = settings.apiKey,
                model = settings.refinementModel,
                rawTranscript = rawTranscript,
                locale = localeTag,
                connectTimeoutMs = DIRECT_CONNECT_TIMEOUT_MS,
                readTimeoutMs = DIRECT_READ_TIMEOUT_MS,
            )) {
                is DirectOpenAiRefineResult.Success -> return RefinementOutcome(result.text, refined = true)
                is DirectOpenAiRefineResult.Failure -> {
                    if (!DirectOpenAiRefiner.isRetryable(result.reason) || attempt == DIRECT_MAX_ATTEMPTS - 1) break
                    try {
                        Thread.sleep(DIRECT_RETRY_DELAYS_MS[attempt])
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }
        return RefinementOutcome(rawTranscript, refined = false)
    }

    private fun completeCopy(finalText: String, wasRefined: Boolean) {
        if (state != BubbleFlowState.PROCESSING) return
        mainHandler.removeCallbacks(finalResultTimeout)
        val copiedText = InsertionText.normalize(finalText)
        if (copiedText == null) {
            setError(R.string.error_no_speech)
            return
        }
        val copied = runCatching {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("VoiceTyping", copiedText))
            ClipboardStateStore.markCopied(copiedText)
        }.isSuccess
        waitingForFinal = false
        destroyRecognizer()
        state = BubbleFlowRules.processingFinished(copied)
        updateBubbleAppearance()
        if (copied) mainHandler.postDelayed(clearCopiedFeedback, COPIED_FEEDBACK_MS)
        if (copied && wasRefined) showToast(R.string.toast_copied)
        else if (copied) showToast(R.string.toast_copied_raw)
        else showToast(R.string.error_copy_failed)
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
        mainHandler.removeCallbacks(finalResultTimeout)
        mainHandler.removeCallbacks(recordingTick)
        destroyRecognizer()
        refineThread?.let { runCatching { it.interrupt() } }
        refineThread = null
        finalDelivered = false
        lastPartial = null
        mainHandler.removeCallbacks(clearCopiedFeedback)
        state = BubbleFlowRules.reset(state)
        ClipboardStateStore.clear()
        updateBubbleAppearance()
        showToast(
            if (currentRecordingMode() == RecordingMode.TAP_TO_TOGGLE) {
                R.string.toast_ready_tap
            } else {
                R.string.toast_ready_hold
            },
        )
    }

    /**
     * Return the control to its mic state without touching Android's primary clipboard. The
     * system Paste action remains available after this visual feedback has disappeared.
     */
    private fun resetTerminalFeedbackForNextInput() {
        if (state != BubbleFlowState.COPIED && state != BubbleFlowState.ERROR) return
        mainHandler.removeCallbacks(clearCopiedFeedback)
        state = BubbleFlowRules.reset(state)
        ClipboardStateStore.clear()
        updateBubbleAppearance()
    }

    private fun setError(@StringRes messageRes: Int) {
        mainHandler.removeCallbacks(recordingTick)
        mainHandler.removeCallbacks(finalResultTimeout)
        waitingForFinal = false
        destroyRecognizer()
        refineThread?.let { runCatching { it.interrupt() } }
        refineThread = null
        mainHandler.removeCallbacks(clearCopiedFeedback)
        state = BubbleFlowState.ERROR
        updateBubbleAppearance()
        showToast(messageRes)
    }

    @StringRes
    private fun speechErrorMessage(code: Int): Int {
        return when (code) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.error_mic_permission
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                R.string.error_no_speech
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                R.string.error_speech_unavailable
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.error_speech_busy
            else -> R.string.error_speech_failed
        }
    }

    private fun updateBubbleAppearance() {
        val view = bubble ?: return
        val recording = state == BubbleFlowState.RECORDING
        val elapsed = if (recording) SystemClock.elapsedRealtime() - recordingStartedAt else 0L
        val mode = if (recording) recordingSessionMode else currentRecordingMode()
        view.render(state, mode, elapsed)
        applyBubbleVisibility()
    }

    private fun showToast(@StringRes messageRes: Int) =
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()

    private fun currentRecordingMode(): RecordingMode = appSettingsStore.read().recordingMode

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        refineThread?.let { runCatching { it.interrupt() } }
        refineThread = null
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        bubbleLayoutParams = null
        imeObserver?.let { runCatching { windowManager.removeView(it) } }
        imeObserver = null
        removeCopiedDragGuide()
        removeFirstUseHint()
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
            .setContentText(getString(R.string.bubble_notification_text))
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.bubble_notification_stop),
                    stopIntent,
                ).build(),
            )
            .build()
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.bubble_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class RefinementOutcome(val text: String, val refined: Boolean)

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        const val ACTION_START = "com.voicetyping.directtest.action.START_BUBBLE"
        const val ACTION_STOP = "com.voicetyping.directtest.action.STOP_BUBBLE"
        const val ACTION_REFRESH_VISIBILITY =
            "com.voicetyping.directtest.action.REFRESH_BUBBLE_VISIBILITY"
        private const val TAG = "OverlayBubble"
        private const val CHANNEL_ID = "voice_bubble"
        private const val NOTIFICATION_ID = 7421
        private const val BUBBLE_SIZE_DP = 62
        private const val MOVE_SLOP_DP = 10
        private const val HOLD_TO_RECORD_MS = 250L
        private const val HOLD_TO_DRAG_MS = 300L
        private const val MAX_RECORDING_MS = 120_000L
        private const val TAP_COMPLETE_SILENCE_MS = 10_000L
        private const val TAP_POSSIBLE_SILENCE_MS = 7_500L
        private const val HOLD_COMPLETE_SILENCE_MS = 3_000L
        private const val HOLD_POSSIBLE_SILENCE_MS = 1_500L
        private const val FINAL_RESULT_TIMEOUT_MS = 3_500L
        private const val DIRECT_CONNECT_TIMEOUT_MS = 7_000
        private const val DIRECT_READ_TIMEOUT_MS = 20_000
        private const val DIRECT_MAX_ATTEMPTS = 4
        private val DIRECT_RETRY_DELAYS_MS = longArrayOf(400L, 1_000L, 2_000L)
        private const val TIMER_TICK_MS = 250L
        private const val FIRST_USE_HINT_MS = 4_500L
        private const val COPIED_FEEDBACK_MS = 1_250L
        private const val IME_HIDE_DEBOUNCE_MS = 200L
        private const val UI_PREFERENCES_NAME = "voicetyping_ui"
        private const val KEY_FIRST_USE_HINT_SHOWN = "first_use_hint_shown"
    }
}
