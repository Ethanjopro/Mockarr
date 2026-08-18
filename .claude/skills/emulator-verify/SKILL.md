---
description: Verify Mockarr changes on the Android emulator like a human tester — boot, drive the UI via scripts/emu.sh, capture screenshot evidence, and report findings with severity triage. Use whenever a change needs emulator verification, when asked to "verify", "test on the emulator", or before declaring a user-visible change done — AND when Ethan reports a visual/UI problem: reproduce and screenshot it yourself before diagnosing; never ask him for screenshots.
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
3. `scripts/emu.sh launch` starts the main activity directly.
4. When finished: `scripts/emu.sh kill`. Never TaskStop the boot task — that
   kills the emulator child process.

## Interaction rules (hard-won — do not rediscover these)
- **Wait for UI states with `scripts/emu.sh waitfor "text" [timeout]`** — it
  polls until the text/desc appears and echoes what it matched (check that
  echo: substring matches can hit the wrong node). `assert "text"` checks the
  current screen the same way; both support `"a|b"` alternation. Never
  hand-roll sleep loops for dialogs.
- If a dialog needs tapping without waitfor, remember it renders ~1 s after
  the triggering tap: screenshot first, confirm it's up, then tap.
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
- Logs: `scripts/emu.sh logcat [pattern] [lines]` — grep recent app output
  (e.g. for missing-layer or GL errors) instead of declaring logs unreachable.
- Orientation: `scripts/emu.sh rotate landscape|portrait` (disables
  auto-rotate). Tablet layouts: `scripts/emu.sh resize 2560x1600 320` emulates
  a tablet window without a second AVD; `resize reset` restores. Note: phone
  landscape keeps the bottom bar BY DESIGN (Material switches to a rail only
  when window height is non-compact) — don't file that as a bug.
- Compose merged semantics look "wrong" in `ui` dumps: the focusable
  checkable row shows an empty content-desc while an inert child carries the
  label/description. That IS the correct TalkBack pattern (the reader
  concatenates the merged subtree on focus) — don't file it as a regression.

## Verification passes (per feature)
Run the passes that apply; light features need only the first.
1. **Primary flow** — the feature's happy path, screenshot at each state
   change; check loading/empty/error states exist where relevant.
2. **Dark theme** — `scripts/emu.sh night on`, sweep the changed screens,
   then `scripts/emu.sh night off`.
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
