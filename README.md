# VoiceTyping

Android 11+ voice typing companion for professionals:

`floating control → native speech recognition → optional GPT-5.4 cleanup → clipboard → Paste`

It does not replace the keyboard and does not use Accessibility. Android `SpeechRecognizer` handles transcription, preferring on-device recognition when available, so transcription itself has no OpenAI cost.

## Use

1. Open **VoiceTyping**, allow microphone and display-over-other-apps access, then start the floating mic.
2. In **Settings**, choose **Hold to record** or **Tap to toggle**.
3. Speak, wait for the mint check, then use normal Paste. Hold the mint check to try global drag/drop; the dim chat cue is visual only and cannot force apps that reject text drops.

The overlay and drag guide are non-focusable; VoiceTyping never opens a keyboard picker or activity while recording. This preserves the external app's editor focus as far as Android permits. Without IME or Accessibility, an app cannot forcibly refocus or paste into another app, so normal Paste remains the reliable fallback.

## Refinement modes

- **Personal key:** Enter an optional OpenAI API key in Settings. It is empty by default, encrypted at rest using Android Keystore, never logged, and sent only to the OpenAI Responses API. The model setting defaults to `gpt-5.4` with reasoning effort `none`.
- **Local development server:** With no personal key, the app tries `http://127.0.0.1:8787/v1/refine`. Run `node server/server.mjs` and `adb reverse tcp:8787 tcp:8787`. The server reads ignored `server/.env.local` and keeps the provider key off the APK.
- **Offline-safe fallback:** If refinement is unavailable, the raw Android transcript is still copied.

Direct mobile API keys can be extracted from a compromised device. Personal-key mode is for owner-operated testing; a production multi-user release should use an authenticated HTTPS backend.

## Android versions

Runs on Android 11 (API 30) and newer. Two things only take effect on later releases: the
clipboard is marked sensitive, so the dictated text stays out of the paste preview, from
Android 13; and the special-use foreground-service type is declared from Android 14.

## Permissions

The manifest declares Internet, microphone, overlay, base foreground-service, microphone foreground-service, and foreground-service special-use permissions. Only `RECORD_AUDIO` prompts at runtime. There is no Accessibility, storage, contacts, location, broad package enumeration, clipboard-specific, analytics, login, or notification permission. The narrow `android.speech.RecognitionService` visibility declaration is required by Android 11+ for native recognition.

Some banking apps intentionally hide overlays; the bubble disappearing there is expected. See [BANKING_TEST_CHECKLIST.md](BANKING_TEST_CHECKLIST.md). Never test with real PINs, OTPs or payments.

## Build and verification

Use JDK 17 and Android SDK 35:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Earlier IME and modal-recorder prototypes remain under `archive/` and are outside active source sets.

## Contributing

Pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first — it covers the setup, the commit convention, and the few things that will not be merged. CI runs `testDebugUnitTest lintDebug assembleDebug` on every pull request.

## Licence

Licensed under the Apache License 2.0. See [LICENSE](LICENSE).

Copyright 2026 Ganesh Patel.
