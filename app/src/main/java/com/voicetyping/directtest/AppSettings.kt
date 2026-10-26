package com.voicetyping.directtest

import android.content.Context
import android.content.SharedPreferences

/** The only recording interactions presented to the user. */
enum class RecordingMode { HOLD_TO_RECORD, TAP_TO_TOGGLE }

enum class BubbleVisibilityMode { ALWAYS_SHOW, WHEN_KEYBOARD_VISIBLE_EXPERIMENTAL }

/** Colour themes offered in Settings. Amber ships as the default. */
enum class AppTheme { AMBER, LAGOON, SIGNAL }

/** User-owned configuration. API keys are deliberately absent from logs and display summaries. */
data class AppSettings(
    val recordingMode: RecordingMode = RecordingMode.HOLD_TO_RECORD,
    val bubbleVisibilityMode: BubbleVisibilityMode = BubbleVisibilityMode.ALWAYS_SHOW,
    val theme: AppTheme = AppTheme.AMBER,
    val apiKey: String = "",
    val refinementModel: String = DEFAULT_REFINEMENT_MODEL,
) {
    companion object {
        const val DEFAULT_REFINEMENT_MODEL = "gpt-5.4"
    }
}

/** Pure codec so defaults and compatibility behavior can be tested without Android framework state. */
object AppSettingsCodec {
    const val KEY_RECORDING_MODE = "recording_mode"
    const val KEY_BUBBLE_VISIBILITY_MODE = "bubble_visibility_mode"
    const val KEY_THEME = "theme"
    const val KEY_API_KEY = "api_key"
    const val KEY_REFINEMENT_MODEL = "refinement_model"

    fun decode(values: Map<String, String?>): AppSettings = AppSettings(
        recordingMode = runCatching { RecordingMode.valueOf(values[KEY_RECORDING_MODE].orEmpty()) }
            .getOrDefault(RecordingMode.HOLD_TO_RECORD),
        bubbleVisibilityMode = runCatching {
            BubbleVisibilityMode.valueOf(values[KEY_BUBBLE_VISIBILITY_MODE].orEmpty())
        }.getOrDefault(BubbleVisibilityMode.ALWAYS_SHOW),
        theme = decodeTheme(values[KEY_THEME]),
        apiKey = values[KEY_API_KEY]?.trim().orEmpty(),
        refinementModel = values[KEY_REFINEMENT_MODEL]?.trim().orEmpty()
            .ifEmpty { AppSettings.DEFAULT_REFINEMENT_MODEL },
    )

    /** Shared by the store's cheap theme-only read, so an unknown value falls back the same way. */
    fun decodeTheme(value: String?): AppTheme =
        runCatching { AppTheme.valueOf(value.orEmpty()) }.getOrDefault(AppTheme.AMBER)

    fun encode(settings: AppSettings): Map<String, String> = mapOf(
        KEY_RECORDING_MODE to settings.recordingMode.name,
        KEY_BUBBLE_VISIBILITY_MODE to settings.bubbleVisibilityMode.name,
        KEY_THEME to settings.theme.name,
        KEY_API_KEY to settings.apiKey.trim(),
        KEY_REFINEMENT_MODEL to settings.refinementModel.trim()
            .ifEmpty { AppSettings.DEFAULT_REFINEMENT_MODEL },
    )
}

/** Private app preferences; values never leave this process unless a future configured client uses them. */
class AppSettingsStore(context: Context) {
    private val apiKeyVault = ApiKeyVault(context)
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun read(): AppSettings = AppSettingsCodec.decode(
        mapOf(
            AppSettingsCodec.KEY_RECORDING_MODE to preferences.getString(AppSettingsCodec.KEY_RECORDING_MODE, null),
            AppSettingsCodec.KEY_BUBBLE_VISIBILITY_MODE to preferences.getString(AppSettingsCodec.KEY_BUBBLE_VISIBILITY_MODE, null),
            AppSettingsCodec.KEY_THEME to preferences.getString(AppSettingsCodec.KEY_THEME, null),
            AppSettingsCodec.KEY_API_KEY to apiKeyVault.read(),
            AppSettingsCodec.KEY_REFINEMENT_MODEL to preferences.getString(AppSettingsCodec.KEY_REFINEMENT_MODEL, null),
        ),
    )

    fun save(settings: AppSettings): Boolean {
        val encoded = AppSettingsCodec.encode(settings)
        preferences.edit()
            .putString(AppSettingsCodec.KEY_RECORDING_MODE, encoded.getValue(AppSettingsCodec.KEY_RECORDING_MODE))
            .putString(AppSettingsCodec.KEY_BUBBLE_VISIBILITY_MODE, encoded.getValue(AppSettingsCodec.KEY_BUBBLE_VISIBILITY_MODE))
            .putString(AppSettingsCodec.KEY_THEME, encoded.getValue(AppSettingsCodec.KEY_THEME))
            .remove(AppSettingsCodec.KEY_API_KEY)
            .putString(AppSettingsCodec.KEY_REFINEMENT_MODEL, encoded.getValue(AppSettingsCodec.KEY_REFINEMENT_MODEL))
            .apply()
        return apiKeyVault.write(encoded.getValue(AppSettingsCodec.KEY_API_KEY))
    }

    /**
     * Read before super.onCreate so a window opens in the right theme. Deliberately avoids
     * [read], which would unlock the Keystore to decrypt an API key nobody asked for yet.
     */
    fun readTheme(): AppTheme =
        AppSettingsCodec.decodeTheme(preferences.getString(AppSettingsCodec.KEY_THEME, null))

    fun clearApiKey() = apiKeyVault.clear()

    companion object { private const val PREFERENCES_NAME = "voicetyping_settings" }
}
