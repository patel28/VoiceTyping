package com.voicetyping.directtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestPolicyTest {
    @Test fun sourceManifestContainsOnlyApprovedSensitivePermissionsAndNoAccessibilityService() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val declared = Regex("<uses-permission[^>]+android:name=\"([^\"]+)\"")
            .findAll(manifest).map { it.groupValues[1] }.toSet()
        val approved = setOf(
            "android.permission.INTERNET",
            "android.permission.RECORD_AUDIO",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "android.permission.FOREGROUND_SERVICE_MICROPHONE",
        )
        assertEquals(approved, declared)
        assertFalse(manifest.contains("AccessibilityService"))
        assertFalse(manifest.contains("android.accessibilityservice"))
        assertFalse(manifest.contains("android.permission.BIND_INPUT_METHOD"))
        assertFalse(manifest.contains("VoiceTypingInputMethodService"))
        assertFalse(manifest.contains("android.view.im"))
        assertFalse(manifest.contains("RecorderActivity"))
        assertFalse(manifest.contains("<activity android:name=\".RecorderActivity\""))
        assertTrue(manifest.contains("android:foregroundServiceType=\"microphone|specialUse\""))
        assertFalse(File("src/main/res/xml/input_method.xml").exists())
    }

    @Test fun manifestAddsNoForbiddenPermissionsQueriesOrNotifications() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val forbidden = listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
        )
        for (permission in forbidden) {
            assertFalse("forbidden permission: $permission", manifest.contains(permission))
        }
        assertTrue("SpeechRecognizer requires its system-service query", manifest.contains("<queries"))
        assertTrue(manifest.contains("android.speech.RecognitionService"))
        assertFalse(manifest.contains("<package android:name="))
        assertFalse("notification permission is not allowed", manifest.contains("POST_NOTIFICATIONS"))
    }

    @Test fun releaseKeepsCleartextDisabledAndDebugAllowsOnlyLocalhost() {
        val main = File("src/main/AndroidManifest.xml").readText()
        assertTrue(main.contains("android:usesCleartextTraffic=\"false\""))
        assertFalse(File("src/main/res/xml/network_security_config.xml").exists())
        val debugConfig = File("src/debug/res/xml/network_security_config.xml")
        assertTrue(debugConfig.exists())
        val debugText = debugConfig.readText()
        assertTrue(debugText.contains("127.0.0.1"))
        assertTrue(debugText.contains("localhost"))
        val debugManifest = File("src/debug/AndroidManifest.xml")
        assertTrue(debugManifest.exists())
        assertTrue(debugManifest.readText().contains("networkSecurityConfig"))
    }

    @Test fun refinementIsConfiguredForLocalhost() {
        val props = File("src/main/assets/app-settings.properties").readText()
        assertTrue(props.contains("refine.baseUrl=http://127.0.0.1:8787"))
        assertTrue(props.contains("/v1/refine") || props.contains("8787"))
    }
}
