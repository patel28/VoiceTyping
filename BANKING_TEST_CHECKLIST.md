# Banking and UPI compatibility checklist

Use a non-production test account where available. Do not enter or record passwords, PINs, OTPs, card details, account numbers, or real payment information. Do not initiate a real payment. Capture only the app name, Android version, test state, visible warning text (redact personal details), and outcome.

For each banking or UPI app, begin from a clean baseline and record one row per state. Reopen the app after each settings change. Keep the same app version, device, network, and account state throughout a run.

| # | App state | Overlay | VoiceTyping IME | Selected keyboard | Outcome / exact warning (redacted) |
|---|---|---|---|---|---|
| 1 | APK absent | N/A | N/A | Existing keyboard | |
| 2 | APK installed; overlay permission denied | Denied | Disabled | Existing keyboard | |
| 3 | APK installed; bubble stopped | Allowed | Disabled | Existing keyboard | |
| 4 | APK installed; floating bubble running | Allowed | Disabled | Existing keyboard | |
| 5 | VoiceTyping IME enabled | Allowed | Enabled | Existing keyboard | |
| 6 | During one direct-mode test | Allowed | Enabled | VoiceTyping Insert temporarily selected | |
| 7 | After insertion and keyboard return | Allowed | Enabled | Existing keyboard restored | |
| 8 | IME disabled in system settings | Allowed | Disabled | Existing keyboard | |
| 9 | Overlay permission revoked | Denied | Disabled | Existing keyboard | |
| 10 | APK uninstalled | N/A | N/A | Existing keyboard | |

For each row, record these observations in the outcome cell:

- App opens and login screen works.
- Warning appears; note its wording and whether it names overlays, installed apps, or keyboards.
- Login or payment flow is blocked (do not proceed with a real payment).
- Bubble is hidden while the banking app is foregrounded.
- App requires disabling the keyboard, revoking overlay permission, or uninstalling the prototype.
- Any crash or unexpected behavior.

## Attribution notes

- If state 2 already fails, the installed APK or distribution source may be the trigger. Repeat with a Play Internal Testing install when available before attributing the result to the IME.
- If state 2 passes and state 5 fails, enabling a third-party keyboard is a likely trigger.
- If state 5 passes and state 6 fails, the app may be reacting specifically to the selected keyboard.
- If only state 4 changes behavior, the app may be reacting to overlays. Some apps intentionally hide overlays; that alone is not a crash.
- State 7 checks whether the warning persists after returning to the existing keyboard while the test IME remains enabled.

## Run record

- Device / Android version:
- Banking app and version:
- Install source (sideload / Play Internal Testing):
- Date:
- Tester initials (optional):
