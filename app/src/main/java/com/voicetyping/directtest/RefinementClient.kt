package com.voicetyping.directtest

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Properties

/** Thrown when refinement cannot produce a replacement string. Caller copies the raw transcript. */
class RefinementException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Endpoint configuration loaded from assets/app-settings.properties. */
data class RefinementConfig(
    val baseUrl: String,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val maxChars: Int,
)

/**
 * Minimal refinement client over [HttpURLConnection] and org.json only.
 * Posts {text, locale} to POST {baseUrl}/v1/refine and returns the refined string.
 * Never logs transcript text or secrets; only lengths and status codes.
 */
object RefinementClient {
    const val SETTINGS_ASSET = "app-settings.properties"
    const val REFINE_PATH = "/v1/refine"
    const val DEFAULT_BASE_URL = "http://127.0.0.1:8787"
    const val DEFAULT_CONNECT_TIMEOUT_MS = 5_000
    const val DEFAULT_READ_TIMEOUT_MS = 8_000
    const val DEFAULT_MAX_CHARS = 2_000
    const val MAX_RESPONSE_BYTES = 32_768

    private const val TAG = "RefinementClient"

    fun loadConfig(context: Context): RefinementConfig {
        val props = Properties()
        runCatching {
            context.assets.open(SETTINGS_ASSET).use { props.load(it) }
        }
        val baseUrl = normalizeBaseUrl(props.getProperty("refine.baseUrl", DEFAULT_BASE_URL))
        val connectTimeout = props.getProperty("refine.connectTimeoutMs")?.toIntOrNull()
            ?.coerceIn(1_000, 15_000) ?: DEFAULT_CONNECT_TIMEOUT_MS
        val readTimeout = props.getProperty("refine.readTimeoutMs")?.toIntOrNull()
            ?.coerceIn(1_000, 20_000) ?: DEFAULT_READ_TIMEOUT_MS
        val maxChars = props.getProperty("refine.maxChars")?.toIntOrNull()
            ?.coerceIn(200, 8_000) ?: DEFAULT_MAX_CHARS
        return RefinementConfig(
            baseUrl = if (baseUrl.isEmpty()) DEFAULT_BASE_URL else baseUrl,
            connectTimeoutMs = connectTimeout,
            readTimeoutMs = readTimeout,
            maxChars = maxChars,
        )
    }

    fun configForTesting(
        baseUrl: String = DEFAULT_BASE_URL,
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
        maxChars: Int = DEFAULT_MAX_CHARS,
    ): RefinementConfig = RefinementConfig(
        baseUrl = normalizeBaseUrl(baseUrl),
        connectTimeoutMs = connectTimeoutMs,
        readTimeoutMs = readTimeoutMs,
        maxChars = maxChars,
    )

    @Throws(RefinementException::class)
    fun refine(config: RefinementConfig, rawText: String, locale: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) throw RefinementException("empty transcript")
        if (trimmed.length > config.maxChars) throw RefinementException("transcript exceeds limit")
        val safeLocale = locale.trim().ifEmpty { "en-US" }
        val body = buildRequestJson(trimmed, safeLocale)
        val url = runCatching { URL(config.baseUrl + REFINE_PATH) }.getOrElse {
            throw RefinementException("bad base url", it)
        }
        val connection = runCatching { url.openConnection() as HttpURLConnection }.getOrElse {
            throw RefinementException("connection failed", it)
        }
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = config.connectTimeoutMs
            connection.readTimeout = config.readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            runCatching {
                connection.outputStream.use { it.write(bytes) }
            }.getOrElse {
                throw RefinementException("write failed", it)
            }
            val status = runCatching { connection.responseCode }.getOrElse {
                throw RefinementException("no response", it)
            }
            if (status !in 200..299) {
                Log.w(TAG, "refine failed status=" + status + " inLen=" + trimmed.length)
                throw RefinementException("server status=" + status)
            }
            val responseBody = readBounded(connection.inputStream, MAX_RESPONSE_BYTES)
            val refined = parseRefinedText(responseBody)
            if (refined.isNullOrBlank()) {
                Log.w(TAG, "refine empty result outLen=" + responseBody.length)
                throw RefinementException("empty refined text")
            }
            if (refined.length > config.maxChars * 2) {
                Log.w(TAG, "refine oversized outLen=" + refined.length)
                throw RefinementException("refined text too long")
            }
            return refined
        } catch (e: RefinementException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "refine failed class=" + e.javaClass.simpleName)
            throw RefinementException("refine failed", e)
        } finally {
            connection.disconnect()
        }
    }

    fun normalizeBaseUrl(raw: String?): String {
        if (raw.isNullOrBlank()) return DEFAULT_BASE_URL
        return raw.trim().trimEnd('/')
    }

    fun buildRequestJson(text: String, locale: String): String {
        return JSONObject().put("text", text).put("locale", locale).toString()
    }

    /** Accepts {"text": ...} plus common variants; returns trimmed text or null. */
    fun parseRefinedText(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
            val json = JSONObject(body)
            val candidates = listOf("text", "refinedText", "refined_text", "result")
            for (key in candidates) {
                if (json.has(key) && !json.isNull(key)) {
                    val value = json.optString(key, "").trim()
                    if (value.isNotEmpty()) return value
                }
            }
            null
        }.getOrNull()
    }

    private fun readBounded(stream: InputStream, maxBytes: Int): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4_096)
        var total = 0
        stream.use { input ->
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                if (total + read > maxBytes) {
                    out.write(buf, 0, maxBytes - total)
                    break
                }
                out.write(buf, 0, read)
                total += read
            }
        }
        return out.toString(StandardCharsets.UTF_8.name())
    }
}

/** Pure transcript selection shared by the bubble service and unit tests. */
object SpeechTranscripts {
    /** Returns the first non-blank trimmed match, or null when none is usable. */
    fun pickBest(matches: List<String?>?): String? {
        if (matches.isNullOrEmpty()) return null
        for (candidate in matches) {
            val trimmed = candidate?.trim()
            if (!trimmed.isNullOrEmpty()) return trimmed
        }
        return null
    }
}
