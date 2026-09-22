package com.voicetyping.directtest

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import java.io.File

/**
 * Writes a screenshot and also publishes a copy under Download/voicetyping-shots so it can be
 * pulled off a device that does not allow `run-as`. Use `adb shell rm -r` on that folder, or the
 * Files app, once the captures have been collected.
 */
object ScreenshotStore {

    private const val FOLDER = "Download/voicetyping-shots"
    private const val FRAME_SETTLE_MS = 400L

    fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // waitForIdleSync returns before the next frame reaches the surface, so without this the
        // capture can be the previous state's frame.
        instrumentation.waitForIdleSync()
        Thread.sleep(FRAME_SETTLE_MS)
        instrumentation.waitForIdleSync()
        val context: Context = instrumentation.targetContext
        val file = File(context.filesDir, "screenshots/$name.png")
        file.parentFile?.mkdirs()
        assertTrue(
            "screenshot $name was not written",
            UiDevice.getInstance(instrumentation).takeScreenshot(file),
        )
        publish(context, name, file)
    }

    private fun publish(context: Context, name: String, file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, FOLDER)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { output -> output.write(file.readBytes()) }
    }
}
