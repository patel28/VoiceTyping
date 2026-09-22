package com.voicetyping.directtest

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.inputmethodservice.InputMethodService

@SuppressLint("SetTextI18n") // This compatibility instrument intentionally ships in English only.
class VoiceTypingInputMethodService : InputMethodService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var store: PendingInsertStore
    private var activeEditor: EditorInfo? = null
    private var fallbackMessage: TextView? = null
    private var retryButton: Button? = null

    private val attemptInsert = Runnable { attemptPendingInsert() }

    override fun onCreate() {
        super.onCreate()
        store = PendingInsertStore(this)
    }

    override fun onCreateInputView(): View {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
            setBackgroundColor(getColor(R.color.surface))

            addView(TextView(context).also {
                fallbackMessage = it
                it.text = "Preparing direct insert…"
                it.setTextColor(getColor(R.color.ink))
                it.textSize = 15f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(Button(context).also { button ->
                    retryButton = button
                    button.text = "Retry after checking"
                    button.visibility = View.GONE
                    button.setOnClickListener {
                        if (store.retryFailedUncertain() != null) {
                            button.visibility = View.GONE
                            fallbackMessage?.text = "Retrying once…"
                            mainHandler.post(attemptInsert)
                        }
                    }
                })
                addView(Button(context).apply {
                    text = "Switch keyboard"
                    setTextColor(Color.WHITE)
                    setOnClickListener { switchBackOrShowPicker() }
                })
            })
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        activeEditor = attribute
        mainHandler.removeCallbacks(attemptInsert)

        if (InputFieldSafety.isRecorderHost(attribute)) {
            fallbackMessage?.text = "VoiceTyping selected. Returning to your app…"
            return
        }

        val pending = store.read()
        if (pending?.state != PendingInsertStore.State.READY) return

        if (!InputFieldSafety.isOrdinaryTextField(attribute)) {
            store.cancelReady()
            fallbackMessage?.text = "Protected or unsupported field. Nothing inserted."
            mainHandler.postDelayed({ switchBackOrShowPicker() }, 350L)
            return
        }

        mainHandler.postDelayed(attemptInsert, 120L)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!InputFieldSafety.isRecorderHost(info) && InputFieldSafety.isOrdinaryTextField(info)) {
            mainHandler.removeCallbacks(attemptInsert)
            mainHandler.post(attemptInsert)
        }
    }

    override fun onFinishInput() {
        mainHandler.removeCallbacks(attemptInsert)
        activeEditor = null
        super.onFinishInput()
    }

    override fun onUnbindInput() {
        mainHandler.removeCallbacks(attemptInsert)
        activeEditor = null
        super.onUnbindInput()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun attemptPendingInsert() {
        val editor = activeEditor ?: currentInputEditorInfo ?: return
        when (PendingInsertDispatcher.dispatch(store, editor, currentInputConnection)) {
            PendingInsertDispatcher.Result.SENT -> {
                fallbackMessage?.text = "Text sent. Returning to previous keyboard…"
                mainHandler.postDelayed({ switchBackOrShowPicker() }, 250L)
            }
            PendingInsertDispatcher.Result.FAILED_UNCERTAIN -> {
                fallbackMessage?.text = "Insert result is uncertain. Check the field before retrying."
                retryButton?.visibility = View.VISIBLE
                showWindow(true)
            }
            else -> Unit
        }
    }

    private fun switchBackOrShowPicker() {
        if (!switchToPreviousInputMethod()) {
            val imm = getSystemService(InputMethodManager::class.java)
            imm.showInputMethodPicker()
        }
    }
}
