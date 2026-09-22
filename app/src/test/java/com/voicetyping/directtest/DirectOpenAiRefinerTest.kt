package com.voicetyping.directtest

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectOpenAiRefinerTest {
    @Test
    fun requestContainsExpectedResponsesApiFieldsAndTranscriptContext() {
        val request = JSONObject(
            DirectOpenAiRefiner.buildRequestJson(
                model = "gpt-test",
                transcript = "hello there",
                locale = "en-US",
            ),
        )

        assertEquals("gpt-test", request.getString("model"))
        assertFalse(request.getBoolean("store"))
        assertEquals("none", request.getJSONObject("reasoning").getString("effort"))
        assertEquals(DirectOpenAiRefiner.MAX_OUTPUT_TOKENS, request.getInt("max_output_tokens"))
        val instructions = request.getString("instructions")
        assertTrue(instructions.contains("Preserve its language"))
        assertTrue(instructions.contains("complete a trailing unfinished sentence"))
        assertTrue(instructions.contains("only when its intended completion is unambiguous"))
        assertTrue(instructions.contains("Never invent facts, names, numbers, commitments, or ideas"))
        assertTrue(instructions.contains("Use the surrounding sentence and domain context"))
        assertTrue(instructions.contains("'commit' over 'comment'"))
        assertTrue(instructions.contains("preserve clear words"))
        assertTrue(instructions.contains("leave genuinely ambiguous words unchanged"))
        assertTrue(instructions.contains("Return only the refined text"))
        assertTrue(request.getString("input").contains("Locale: en-US"))
        assertTrue(request.getString("input").contains("Transcript:\nhello there"))
    }

    @Test
    fun parsesTopLevelOutputText() {
        assertEquals(
            "Clean text.",
            DirectOpenAiRefiner.parseResponseText("""{"output_text":" Clean text. "}"""),
        )
    }

    @Test
    fun parsesNestedOutputTextContentAndIgnoresOtherParts() {
        val body = """{
            "output": [
              {"type":"reasoning","content":[{"type":"reasoning_text","text":"hidden"}]},
              {"type":"message","content":[
                {"type":"output_text","text":"Clean "},
                {"type":"output_text","text":"text."}
              ]}
            ]
        }"""

        assertEquals("Clean text.", DirectOpenAiRefiner.parseResponseText(body))
    }

    @Test
    fun malformedOrMissingResponseTextReturnsNull() {
        assertNull(DirectOpenAiRefiner.parseResponseText("not-json"))
        assertNull(DirectOpenAiRefiner.parseResponseText("""{"output":[]}"""))
    }

    @Test
    fun retriesOnlyTransientFailures() {
        assertTrue(DirectOpenAiRefiner.isRetryable(DirectOpenAiFailure.NETWORK_ERROR))
        assertTrue(DirectOpenAiRefiner.isRetryable(DirectOpenAiFailure.RATE_LIMITED))
        assertTrue(DirectOpenAiRefiner.isRetryable(DirectOpenAiFailure.SERVER_ERROR))
        assertFalse(DirectOpenAiRefiner.isRetryable(DirectOpenAiFailure.HTTP_ERROR))
        assertFalse(DirectOpenAiRefiner.isRetryable(DirectOpenAiFailure.INVALID_INPUT))
    }
}
