# Contributing to VoiceTyping

Thanks for your interest. This is a small Android project, so the process is deliberately light.

## Before you start

Open an issue first for anything beyond a bug fix or a small cleanup. A short discussion up front avoids work that cannot be merged — especially for new permissions, new dependencies, or changes to how the overlay interacts with other apps.

## Setting up

You need JDK 17 and the Android SDK (platform 35). Android Studio installs both.

```bash
git clone https://github.com/<your-fork>/VoiceTyping.git
cd VoiceTyping
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

The app needs the microphone and the display-over-other-apps permission, so the interesting parts cannot be verified in an emulator alone. Test the floating control on a real device. Instrumentation tests (`./gradlew connectedDebugAndroidTest`) are run manually against a connected device; CI does not run them.

If you want to exercise AI refinement without an OpenAI key, run the local server described in the README (`node server/server.mjs` plus `adb reverse tcp:8787 tcp:8787`). Put your provider key in `server/.env.local`, which is gitignored.

## Making a change

1. Fork the repository and branch off `main`. Branch names follow `area/short-description`, for example `ui/signal-console-redesign`.
2. Keep the change focused. One concern per pull request — it gets reviewed and merged much faster.
3. Match the surrounding code. Same naming, same idiom, same comment density. The project favours deleting code over adding it, and plain code over clever code.
4. When you replace something, remove what it replaced in the same commit. Leaving the old path next to the new one is not safer; it is two things to maintain.
5. Add a test for non-trivial logic. Unit tests live in `app/src/test/`; several existing ones use Robolectric.

## Commit messages

Short, lowercase, imperative subject with a type prefix:

```
feat: show floating mic with keyboard
fix: reset bubble after copied feedback
tune: shorten copied-text drag delay
```

`git log` shows the types in use. No AI attribution trailers.

## Pull requests

Describe what changed and why, and say which device and Android version you tested on. CI runs unit tests, Android lint and a debug build on every pull request; all three must pass before review. A maintainer reviews and merges — please do not force-push after review has started, as it makes the diff hard to follow.

## Things that will not be merged

- Committed secrets, API keys, `local.properties`, or signing keystores.
- Accessibility Service or input-method (IME) implementations. The app deliberately avoids both.
- New runtime permissions without a clear justification in an issue.
- Edits to `sources/` or `archive/`. Both are read-only reference material.
- Anything that logs transcribed text or sends it anywhere other than the configured refinement endpoint.

## Security

Do not open a public issue for a vulnerability. Email the maintainer instead and allow time for a fix before disclosing.

## Licence

By contributing, you agree that your contribution is licensed under the Apache License 2.0, the same licence as the project.
