# Prototype 2 — Clipboard mode

## Goal

Provide a low-permission Android flow: floating bubble → record → fixed test text copied → return to the original app → user pastes. No IME, Accessibility, Internet, stored audio, or automatic cross-app editing.

## Work

1. Archive Prototype 1 IME/pending-dispatch implementation outside Android source sets.
2. Remove the IME service and metadata from the active manifest.
3. Simplify setup to microphone, overlay, and bubble state only.
4. Replace recorder completion with clipboard copy and automatic return.
5. Add an experimental global text drag from the bubble after copying, with normal Paste as fallback.
6. Verify unit tests, lint, APK assembly, packaged permissions/components, wireless installation, clipboard content, launch, and drag behavior where the destination app supports text drops.

## Test output

`Hello World from App Name`
