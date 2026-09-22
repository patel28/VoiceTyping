# VoiceTyping production UI/UX plan

Audience: professionals who want fast dictation without replacing their keyboard. The product's single job is **speak → polish → paste**.

## Design direction

- Ink `#15191F`, cloud `#F5F7FA`, signal blue `#2864DC`, recording coral `#E75555`, success mint `#2A9D72`, quiet slate `#667085`.
- Android system typography for predictable rendering and accessibility.
- Signature: the floating control is a compact live instrument—mic, timer/pulse, refining activity, copied check—not a generic branded bubble.
- Main screen prioritizes readiness and one action. Technical development details move behind Settings.
- Drag guidance uses a non-touchable translucent veil with a chat/paste cue. It must never become the drop target or steal editor focus.

## Work list

- [x] Move clean project to `/home/alex/AndroidStudioProjects/VoiceTyping`.
- [x] Initialize Git and commit the working prototype baseline.
- [x] Rename project/app to VoiceTyping and add adaptive + monochrome icon.
- [x] Add production main screen with readiness cards and clear primary action.
- [x] Add Settings with Hold to record / Tap to start-stop modes.
- [x] Add optional user API key (empty by default), masked controls, secure-at-rest storage and model setting.
- [x] Use direct OpenAI refinement when a user key exists; keep local proxy and raw-transcript fallback.
- [x] Implement both recording gesture modes without accidental bubble movement.
- [x] Preserve the external editor as far as Android permits: non-focusable overlays, no activity launch, no forced keyboard changes.
- [x] Improve drag affordance with a safe full-screen dim cue and chat icon; keep manual Paste primary because arbitrary apps may reject global drops.
- [x] Add/update unit, UI, permission and security tests.
- [x] Run unit tests, lint and assemble; install/launch the production UI build before the device disconnected.
- [ ] Reconnect a device for the final manual cross-app drag/refocus gesture check.
- [x] Commit the production UI/UX feature set.

## Platform boundary

Without IME or Accessibility, Android does not allow this app to programmatically refocus or paste into an arbitrary external editor. `FLAG_NOT_FOCUSABLE` prevents our overlay from taking focus, but the destination app still owns caret/focus and may reject global drag/drop. VoiceTyping therefore preserves focus where possible, copies automatically, offers a visual drag cue, and always keeps normal long-press Paste as the reliable fallback.
