# Claude prompt — VoiceTyping production UI/UX redesign

You are redesigning and implementing the Android UI for an existing app in this repository. Work in the existing project; inspect the code and git diff before editing. Preserve all working behavior and tests. Do not replace working domain, recording, overlay, encryption, networking, or clipboard logic merely to make the UI easier to rewrite.

## Product

VoiceTyping is a low-permission Android 13+ voice-writing utility for professionals. A small floating microphone works above any app:

1. The user focuses a destination text field in another app.
2. They either hold the floating mic and release, or tap once to start and tap again to stop.
3. Android speech recognition creates a transcript.
4. Optional AI refinement cleans the writing.
5. The result is copied automatically.
6. The user pastes normally, or tries hold-and-drag where the destination app supports Android drag/drop.

It is deliberately **not** an IME and uses no Accessibility service. Do not add either one. Do not add login, analytics, ads, contacts, location, storage, or notification permission. Existing microphone and display-over-other-apps permissions are the product's trust boundary.

## Single design job

Make setup, readiness, recording state, and recovery unmistakable within three seconds. The UI must feel like a precise voice instrument, not a settings demo, generic Material dashboard, fintech app, or marketing page.

## Visual direction

Use a restrained **field-recorder / signal-console** aesthetic:

- Ink: `#15191F`
- Canvas: `#F5F7FA`
- Signal blue: `#2864DC`
- Recording coral: `#E75555`
- Success mint: `#2A9D72`
- Warning amber: `#B7791F`
- White: `#FFFFFF`

Use Android system typography for reliable offline rendering. Establish a disciplined type scale with strong numeric/timer treatment, compact utility labels, and highly readable body copy. Avoid excessive rounded cards, gradients, glassmorphism, giant hero text, decorative illustrations, and emoji controls.

The signature element is a thin vertical **live signal rail** that communicates the real state sequence: `Permission → Ready → Listening → Refining → Copied`. It should be functional status communication, not decoration. Spend visual boldness here and keep the rest quiet.

Support light and dark themes, dynamic text sizing, visible focus, 48dp minimum touch targets, TalkBack labels, edge-to-edge insets, and reduced-motion preferences. Never communicate state by color alone.

## Main screen information architecture

Do not show five equally important buttons. Create one clear primary action based on current state.

### Header

- Compact VoiceTyping wordmark and a short trust line: “Voice to polished text. Paste anywhere.”
- Settings icon in the app bar.

### Readiness instrument

- Show the live signal rail.
- Show microphone and overlay permission as two compact rows with meaningful icons, status text, and one contextual action.
- If a permission is missing, the primary action resolves the next missing requirement.
- When both are ready, the primary action becomes “Start floating mic”.
- When the service is running, show a calm active state and “Stop floating mic” as a secondary/destructive action.
- Recording mode should be visible as a small summary, not another main CTA.

### First-use instruction

Replace long explanatory cards with a compact three-step visual instruction:

`Focus a field → Speak from the floating mic → Paste`

Use plain language and show the selected gesture, either “Hold and release” or “Tap to start and stop”. Keep the privacy explanation behind a concise expandable row.

### Paste sandbox

Keep a small multiline test field titled “Try it here”. After copy success, its helper text should say exactly what to do: “Long-press the field and choose Paste.” Do not imply guaranteed automatic insertion.

## Settings screen

- Standard app bar with Back and “Settings”.
- “Recording control” should be an accessible two-option selector with simple gesture diagrams:
  - Hold and release
  - Tap to start / tap to stop
- Explain that a recording can run for up to 2 minutes and that some Android speech services may stop after a long silence.
- Make AI refinement a clearly optional, collapsed “Advanced: AI refinement” section.
- API key defaults empty, is masked, and retains Show/Hide and Clear actions.
- Keep the security warning, but rewrite it compactly: “Stored encrypted on this device. For production, use a server-managed key.”
- Model remains editable and defaults to `gpt-5.4`.
- Use an explicit “Save changes” action with inline success/error feedback. Do not rely only on a toast.

## Floating control state machine

Design the 62dp floating control and any transient overlay for these exact states. Preserve its non-focusable behavior so the destination field and keyboard are not intentionally dismissed.

- **Idle:** microphone icon; concise gesture hint only on first use.
- **Pressed/arming:** tactile scale or ring response; hold threshold is 250ms.
- **Recording:** coral, strong mic/stop symbol, elapsed timer; no tiny text inside the circle.
- **Processing:** blue progress motion with accessible static alternative.
- **Copied:** mint check; announce “Copied”; tapping resets for the next recording.
- **Error:** warning mark plus a clear recoverable message; tapping resets.

For hold-and-drag after copy, keep the underlying destination visible. Do not cover the whole page with an opaque treatment. Prefer a subtle edge vignette or top instruction strip with the existing chat/drop icon. State honestly that drag/drop is app-dependent and ordinary long-press Paste is the reliable fallback.

## Copy and error writing

Use consistent verbs:

- “Listening…”
- “Refining…”
- “Copied — long-press the field to paste”
- “No speech detected — try again”
- “Speech service unavailable — check Google speech services”
- “Refinement unavailable — raw transcript copied”

Never use vague “Something went wrong” copy.

## Engineering constraints

- Existing package: `com.voicetyping.directtest`
- Kotlin, Android Views, current Material/AppCompat dependencies
- `minSdk 33`, `compileSdk/targetSdk 35`
- Do not add network-loaded fonts or runtime design dependencies without justification.
- Prefer XML resources, styles, selectors, dimensions, and strings over large programmatic view construction. Refactor UI code carefully while preserving event handlers and state reads.
- Move visible copy into string resources.
- Do not expose `SettingsActivity`; it intentionally remains non-exported.
- Do not weaken encrypted API-key storage.
- Do not add permissions or components without explicit approval.
- Preserve instrumentation selectors or update the tests with equivalent semantic assertions.
- Preserve any user changes already present in the working tree.

## Required workflow and output

1. Inspect `MainActivity.kt`, `SettingsActivity.kt`, `OverlayBubbleService.kt`, resources, manifest, and tests.
2. Show a compact plan first: tokens, screen hierarchy, signal-rail behavior, floating-control states, and an ASCII wireframe for the main and settings screens.
3. Critique the plan for generic AI-design patterns and revise it before coding.
4. Implement the approved direction in the existing project.
5. Build and run unit, lint, and instrumentation tests.
6. Capture emulator screenshots for the main screen, settings screen, missing-permission state, ready state, recording state, processing state, copied state, and error state.
7. Report changed files, test results, and any behavior that still requires real-device/manual speech testing.

Do not redesign functionality or invent automatic paste. This pass is about trustworthy hierarchy, legible state, professional polish, and a UI that accurately explains what Android allows.
