---
description: Verify Mockarr changes on the Android emulator like a human tester — boot, drive the UI via scripts/emu.sh, capture screenshot evidence, and report findings with severity triage. Use whenever a change needs emulator verification, when asked to "verify", "test on the emulator", or before declaring a user-visible change done.
argument-hint: "[feature or screen to verify]"
---

# Emulator verification playbook

All device interaction goes through `scripts/emu.sh` (full adb path and the
`mockarr_test` AVD are baked in). Never call bare `adb`; never launch the
emulator binary yourself.

## Setup
1. `scripts/emu.sh boot` — run via Bash `run_in_background` (boot takes ~1–2
   min; the script blocks until `sys.boot_completed`). Never foreground-sleep.
2. `scripts/emu.sh install` then `scripts/emu.sh mockallow` (grants the
   mock-location appop; without it holds/playback silently fail).
3. Launch the app from the drawer or `monkey`-free: `scripts/emu.sh tapon Mockarr`.
4. When finished: `scripts/emu.sh kill`. Never TaskStop the boot task — that
   kills the emulator child process.

## Interaction rules (hard-won — do not rediscover these)
- **Dialogs render ~1 s after the triggering tap.** Screenshot first
  (`scripts/emu.sh shot check.png`), confirm the dialog is up, then tap.
- `find`/`tapon` match substrings in text AND content-desc — `find "Play"` can
  hit hint text containing "Play". Prefer longer unique strings, or read the
  full `ui` dump and tap explicit coordinates.
- zsh does not word-split command substitutions: never `tap $COORDS`; use
  `tapon`, or extract x/y with awk.
- The route-creator card collapses: Save and the expanded controls need the
  chevron tapped first.
- Text fields: `scripts/emu.sh type "text"` (spaces handled); focus the field
  with a tap first.
- Verify mock state empirically when it matters: poll
  `dumpsys location` through `scripts/emu.sh` shell access for the mock
  provider's coordinates — screenshots alone don't prove zero-leak behavior.

## Verification passes (per feature)
Run the passes that apply; light features need only the first.
1. **Primary flow** — the feature's happy path, screenshot at each state
   change; check loading/empty/error states exist where relevant.
2. **Dark theme** — `adb shell cmd uimode night yes` (via emu.sh's ADB), sweep
   the changed screens, then `night no`.
3. **Font scale 1.3** — `adb shell settings put system font_scale 1.3`, check
   for clipped/truncated labels on changed screens, restore `1.0`.
4. **Rotation** — landscape once through the changed screens if layout changed.
5. **Logcat** — scan for exceptions/ANRs from `dev.mockarr.app` during the run.

## Report format
State that evidence came from the emulator (vs physical hardware). Then:

```
### Verified: <feature>
Evidence: <screenshot paths>
- [Blocker] <problem + impact>        ← broken flow, crash, mock leak
- [High] <problem + impact>           ← wrong behavior, clipped/unusable UI
- [Medium] <problem + impact>
- [Nit] <minor polish>
```

Describe problems and their impact, not prescriptions ("the ETA overlaps the
banner at font scale 1.3" — not "change the padding to 8dp"). No findings in a
category → omit it. Never invent a finding; "clean pass" is a valid result.
