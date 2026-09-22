# VoiceTyping Clipboard Prototype

Android 13+ compatibility APK for one focused question: can a floating voice shortcut transcribe speech, refine it, and copy the result that the user pastes manually, without IME, Accessibility, clipboard auto-read, or replacing the user's keyboard?

## Test flow

1. Install the debug APK and open **VoiceTyping Direct Test**.
2. Allow microphone access and overlay display.
3. Start the local refinement server: `node server/server.mjs` (reads `server/.env.local`), then `adb reverse tcp:8787 tcp:8787`.
4. Tap **Start floating mic**.
5. Hold the blue **HOLD** bubble and speak for up to 10 seconds. The bubble turns coral and shows elapsed time. Speech uses `SpeechRecognizer`, preferring the on-device recognizer with system fallback.
6. Release the bubble. `stopListening()` releases the mic; a bounded final-result timeout waits for transcription. Animated dots indicate processing; a mint check means the refined text (or raw transcript on server failure) was copied.
7. In the focused field, long-press and tap **Paste**.
8. Quick-tap the mint check to reset for another recording.
9. Experimental drag: hold the mint check, then drag it onto a field that accepts Android text drops. Drag does not work in every app; clipboard Paste remains the fallback.

Transcripts are sent as `{text, locale}` to `POST /v1/refine` (base URL from `app/src/main/assets/app-settings.properties`, default `http://127.0.0.1:8787`). The app never logs transcript text or secrets. Debug builds allow localhost cleartext only; release keeps cleartext disabled.

## Permissions

The manifest declares Internet, microphone, overlay, base foreground-service, microphone foreground-service, and foreground-service special-use permissions. `FOREGROUND_SERVICE_MICROPHONE` is a normal manifest permission required by Android 14+; the only microphone runtime prompt remains `RECORD_AUDIO`. `INTERNET` is a normal permission for the local refinement call. The app has no Accessibility, storage, contacts, location, broad package enumeration, clipboard-specific, analytics, login, or notification permission. Android's one narrowly scoped `android.speech.RecognitionService` visibility declaration is included because Android 11+ requires it for `SpeechRecognizer`.

Some banking apps use Android's secure overlay-hiding behavior, so the bubble may disappear while those apps are foregrounded. Use [BANKING_TEST_CHECKLIST.md](BANKING_TEST_CHECKLIST.md) to distinguish overlay, sideloading, and installed-app effects. Never use real PINs, OTPs, credentials, or payments during this test.

Prototype 1's IME direct-insert implementation is preserved outside the active source sets under [archive/prototype1-ime](archive/prototype1-ime/README.md).
The previous modal clipboard recorder is preserved under [archive/prototype2-tap-recorder](archive/prototype2-tap-recorder/README.md).

## Refinement server

Dependency-free `server/server.mjs` (Node built-ins only). Put the key in `server/.env.local` as `OPENAI_API_KEY="sk-..."`; quoted or unquoted values both work. The configurable defaults are `OPENAI_REFINEMENT_MODEL=gpt-5.4`, reasoning `none`, and port `8787`. Endpoints: `GET /health`, `POST /v1/refine`. The server calls OpenAI `POST https://api.openai.com/v1/responses` with `store: false` and short output, retrying 429/5xx/network up to three times after the initial request with increasing non-blocking delay. Request-size limits apply; secrets and transcript text are never logged.

## Build

Use JDK 17, Android SDK 35, and `./gradlew testDebugUnitTest lintDebug assembleDebug`. Device-only instrumentation requires a connected Android 13+ device or emulator.
