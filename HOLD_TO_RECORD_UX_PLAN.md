# Hold-to-record FAB UX

## State flow

`IDLE → RECORDING → PROCESSING → COPIED`

- Idle: blue microphone; hold for 250 ms to begin.
- Recording: coral timer; release to finish. Speech is transcribed via SpeechRecognizer (on-device preferred).
- Processing: blue animated dots; covers the bounded final-result wait plus refinement. Refinement failure copies the raw transcript.
- Copied: mint check; text is already on the clipboard. Hold and drag where supported, or use normal Paste. A quick tap resets the bubble for another recording.
- Error: red exclamation with a precise toast; tap to reset.

## Gesture arbitration

- Movement before the hold threshold repositions the bubble and cancels recording initiation.
- After recording begins, movement does not move the bubble; release completes recording.
- In copied state, a stationary hold starts Android global text drag. Unsupported targets retain the clipboard fallback.

## Android requirements

- Overlay service declares `specialUse|microphone` and the normal `FOREGROUND_SERVICE_MICROPHONE` permission.
- The service starts only from the visible setup Activity after `RECORD_AUDIO` is granted.
- Release calls `stopListening()`; a bounded timeout waits for the final result, then the recognizer is destroyed.
- No IME, Accessibility, audio files, or automatic paste. Internet is used only for the local refinement call.
