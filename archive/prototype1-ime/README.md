# Prototype 1 — IME direct-insert (archived)

This folder preserves the Prototype 1 direct-mode IME implementation, moved out
of the active Android source sets so Prototype 2 (clipboard mode) ships without
an input method.

## What it tried

A floating bubble opened a short in-memory recorder, staged the fixed text
`Hello World from App Name` in `PendingInsertStore`, asked Android to show the
input-method picker, and had `VoiceTypingInputMethodService` commit the text
through the temporarily selected `VoiceTyping Insert` IME. `ImeStatus`,
`InputFieldSafety`, and `PendingInsertDispatcher` guarded the dispatch.

## Why archived

Prototype 2 uses a lower-friction flow instead: bubble → record → Copy text to
the clipboard → user pastes manually (with an experimental global drag from the
bubble where the destination app supports text drops). No IME service,
input-method picker, secure-settings read, or pending dispatch remains in the
active app.

## Contents

- `src/ImeStatus.kt` — enabled/selected IME queries.
- `src/InputFieldSafety.kt` — ordinary-text-field guard.
- `src/PendingInsertStore.kt` — staged fixed text with TTL and one-way claim.
- `src/PendingInsertDispatcher.kt` — single commit + no-replay logic.
- `src/VoiceTypingInputMethodService.kt` — temporary IME that performed the insert.
- `res-xml/input_method.xml` — IME `android.view.im` metadata.
- `tests/` — `InputFieldSafetyTest`, `PendingInsertDispatcherTest`,
  `PendingInsertStoreTest` as they existed for Prototype 1.

`RmsMeter` and the waveform view were not IME-specific and remain active.
