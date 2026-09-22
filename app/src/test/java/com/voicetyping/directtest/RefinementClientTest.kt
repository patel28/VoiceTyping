package com.voicetyping.directtest

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefinementClientTest {
    @Test fun normalizesBaseUrl() {
        assertEquals("http://127.0.0.1:8787", RefinementClient.normalizeBaseUrl("http://127.0.0.1:8787/"))
        assertEquals("http://127.0.0.1:8787", RefinementClient.normalizeBaseUrl("  http://127.0.0.1:8787  "))
        assertEquals(RefinementClient.DEFAULT_BASE_URL, RefinementClient.normalizeBaseUrl(null))
        assertEquals(RefinementClient.DEFAULT_BASE_URL, RefinementClient.normalizeBaseUrl("   "))
    }

    @Test fun requestBodyPostsTextAndLocale() {
        val body = RefinementClient.buildRequestJson("hello world", "en-US")
        val json = JSONObject(body)
        assertEquals("hello world", json.getString("text"))
        assertEquals("en-US", json.getString("locale"))
    }

    @Test fun parsesRefinedTextVariants() {
        assertEquals("Hello world.", RefinementClient.parseRefinedText("{\"text\":\"Hello world.\"}"))
        assertEquals("Hello world.", RefinementClient.parseRefinedText("{\"refinedText\":\"Hello world.\"}"))
        assertEquals("Hello world.", RefinementClient.parseRefinedText("{\"refined_text\":\"  Hello world.  \"}"))
        assertNull(RefinementClient.parseRefinedText("{\"text\":\"   \"}"))
        assertNull(RefinementClient.parseRefinedText(""))
        assertNull(RefinementClient.parseRefinedText("not json"))
    }

    @Test fun picksFirstUsableTranscript() {
        assertEquals("hello", SpeechTranscripts.pickBest(listOf(null, "  ", "hello", "world")))
        assertEquals("trimmed", SpeechTranscripts.pickBest(listOf("  trimmed  ")))
        assertNull(SpeechTranscripts.pickBest(listOf(null, "   ", "")))
        assertNull(SpeechTranscripts.pickBest(emptyList()))
        assertNull(SpeechTranscripts.pickBest(null))
    }

    @Test fun defaultConfigUsesLocalhost() {
        val config = RefinementClient.configForTesting()
        assertEquals("http://127.0.0.1:8787", config.baseUrl)
        assertEquals("/v1/refine", RefinementClient.REFINE_PATH)
        assertTrue(config.connectTimeoutMs in 1_000..15_000)
        assertTrue(config.readTimeoutMs in 1_000..20_000)
    }
}
