package com.voicetyping.directtest

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** Result of one direct OpenAI refinement request. */
sealed class DirectOpenAiRefineResult {
    data class Success(val text: String) : DirectOpenAiRefineResult()
    data class Failure(val reason: DirectOpenAiFailure) : DirectOpenAiRefineResult()
}

enum class DirectOpenAiFailure {
    INVALID_INPUT,
    NETWORK_ERROR,
    HTTP_ERROR,
    RATE_LIMITED,
    SERVER_ERROR,
    INVALID_RESPONSE,
    EMPTY_RESPONSE,
    RESPONSE_TOO_LARGE,
}

/**
 * Makes a single direct request to the OpenAI Responses API.
 *
 * Call [refine] from a background thread supplied by the caller. This class has no Android
 * dependencies and deliberately never logs request or response contents.
 */
object DirectOpenAiRefiner {
    const val ENDPOINT = "https://api.openai.com/v1/responses"
    const val DEFAULT_MAX_TRANSCRIPT_CHARS = 8_000
    const val MAX_API_KEY_CHARS = 4_096
    const val MAX_MODEL_CHARS = 128
    const val MAX_LOCALE_CHARS = 64
    const val MAX_OUTPUT_TOKENS = 160
    const val MAX_RESPONSE_BYTES = 65_536

    private const val INSTRUCTIONS =
        "Clean up this dictated transcript. Preserve its language, meaning, tone, and word choices. " +
            "Fix grammar, casing, punctuation, and obvious speech-recognition errors. You may " +
            "complete a trailing unfinished sentence only when its intended completion is " +
            "unambiguous from the words already spoken; otherwise leave it incomplete. Never " +
            "invent facts, names, numbers, commitments, or ideas. Use the surrounding sentence " +
            "and domain context to correct likely homophones or misrecognized words—for example, " +
            "in Git or software context prefer 'commit' over 'comment' when context supports it. " +
            "Make a word substitution only when context strongly supports the correction; preserve " +
            "clear words and leave genuinely ambiguous words unchanged. Do not answer, explain, " +
            "translate, or summarize. Return only the refined text."

    /** Perform one request. Network work runs synchronously on the caller's thread. */
    fun refine(
        apiKey: String,
        model: String,
        rawTranscript: String,
        locale: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): DirectOpenAiRefineResult {
        val key = apiKey.trim()
        val safeModel = model.trim()
        val transcript = rawTranscript.trim()
        val safeLocale = locale.trim()
        if (key.isEmpty() || key.length > MAX_API_KEY_CHARS ||
            safeModel.isEmpty() || safeModel.length > MAX_MODEL_CHARS ||
            transcript.isEmpty() || transcript.length > DEFAULT_MAX_TRANSCRIPT_CHARS ||
            safeLocale.isEmpty() || safeLocale.length > MAX_LOCALE_CHARS ||
            connectTimeoutMs <= 0 || readTimeoutMs <= 0
        ) {
            return DirectOpenAiRefineResult.Failure(DirectOpenAiFailure.INVALID_INPUT)
        }

        val connection = try {
            URL(ENDPOINT).openConnection() as HttpURLConnection
        } catch (_: Exception) {
            return DirectOpenAiRefineResult.Failure(DirectOpenAiFailure.NETWORK_ERROR)
        }

        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            val requestBytes = buildRequestJson(safeModel, transcript, safeLocale)
                .toByteArray(StandardCharsets.UTF_8)
            connection.outputStream.use { it.write(requestBytes) }

            val status = connection.responseCode
            if (status !in 200..299) {
                DirectOpenAiRefineResult.Failure(
                    when {
                        status == 429 -> DirectOpenAiFailure.RATE_LIMITED
                        status in 500..599 -> DirectOpenAiFailure.SERVER_ERROR
                        else -> DirectOpenAiFailure.HTTP_ERROR
                    },
                )
            } else {
                val responseStream = connection.inputStream
                val responseBody = readBounded(responseStream, MAX_RESPONSE_BYTES)
                    ?: return DirectOpenAiRefineResult.Failure(DirectOpenAiFailure.RESPONSE_TOO_LARGE)
                val refined = parseResponseText(responseBody)
                    ?: return DirectOpenAiRefineResult.Failure(DirectOpenAiFailure.INVALID_RESPONSE)
                if (refined.isBlank()) {
                    DirectOpenAiRefineResult.Failure(DirectOpenAiFailure.EMPTY_RESPONSE)
                } else if (refined.length > DEFAULT_MAX_TRANSCRIPT_CHARS * 2) {
                    DirectOpenAiRefineResult.Failure(DirectOpenAiFailure.RESPONSE_TOO_LARGE)
                } else {
                    DirectOpenAiRefineResult.Success(refined)
                }
            }
        } catch (_: Exception) {
            DirectOpenAiRefineResult.Failure(DirectOpenAiFailure.NETWORK_ERROR)
        } finally {
            connection.disconnect()
        }
    }

    /** Build a Responses API request body; exposed to keep request shape independently testable. */
    fun buildRequestJson(model: String, transcript: String, locale: String): String =
        JSONObject()
            .put("model", model)
            .put("store", false)
            .put("reasoning", JSONObject().put("effort", "none"))
            .put("max_output_tokens", MAX_OUTPUT_TOKENS)
            .put("instructions", INSTRUCTIONS)
            .put("input", "Locale: $locale\n\nTranscript:\n$transcript")
            .toString()

    /** Read the common Responses API output_text field or nested output[].content[].text parts. */
    fun parseResponseText(body: String): String? {
        if (body.isBlank() || body.toByteArray(StandardCharsets.UTF_8).size > MAX_RESPONSE_BYTES) {
            return null
        }
        return runCatching {
            val response = JSONObject(body)
            val direct = response.optString("output_text", "").trim()
            if (direct.isNotEmpty()) return direct

            val output = response.optJSONArray("output") ?: return null
            val pieces = ArrayList<String>()
            for (i in 0 until output.length()) {
                val item = output.optJSONObject(i) ?: continue
                val content = item.optJSONArray("content") ?: continue
                appendOutputText(content, pieces)
            }
            pieces.joinToString("").trim().ifEmpty { null }
        }.getOrNull()
    }

    fun isRetryable(failure: DirectOpenAiFailure): Boolean = when (failure) {
        DirectOpenAiFailure.NETWORK_ERROR,
        DirectOpenAiFailure.RATE_LIMITED,
        DirectOpenAiFailure.SERVER_ERROR -> true
        else -> false
    }

    private fun appendOutputText(content: JSONArray, destination: MutableList<String>) {
        for (i in 0 until content.length()) {
            val part = content.optJSONObject(i) ?: continue
            if (part.optString("type") == "output_text") {
                val text = part.optString("text", "")
                if (text.isNotEmpty()) destination.add(text)
            }
        }
    }

    /** Returns null if reading would exceed [maxBytes]. */
    private fun readBounded(stream: InputStream, maxBytes: Int): String? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4_096)
        var total = 0
        stream.use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (total + count > maxBytes) return null
                output.write(buffer, 0, count)
                total += count
            }
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }
}
