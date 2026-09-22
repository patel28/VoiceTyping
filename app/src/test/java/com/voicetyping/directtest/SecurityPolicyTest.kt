package com.voicetyping.directtest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SecurityPolicyTest {
    @Test fun apiKeyIsEmptyByDefaultEncryptedAtRestAndAbsentFromAssets() {
        val settings = File("src/main/java/com/voicetyping/directtest/AppSettings.kt").readText()
        val vault = File("src/main/java/com/voicetyping/directtest/ApiKeyVault.kt").readText()
        val assets = File("src/main/assets/app-settings.properties").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(settings.contains("val apiKey: String = \"\""))
        assertTrue(vault.contains("AndroidKeyStore"))
        assertTrue(vault.contains("AES/GCM/NoPadding"))
        assertFalse(assets.contains("OPENAI_API_KEY"))
        assertFalse(assets.contains("sk-"))
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }

    @Test fun directProviderTransportIsHttpsAndNeverLogsSecrets() {
        val client = File("src/main/java/com/voicetyping/directtest/DirectOpenAiRefiner.kt").readText()
        assertTrue(client.contains("https://api.openai.com/v1/responses"))
        assertFalse(client.contains("android.util.Log"))
        assertFalse(client.contains("println("))
    }
}
