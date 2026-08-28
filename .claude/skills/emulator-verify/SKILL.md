---
description: Verify Mockarr changes on the Android emulator like a human tester — boot, drive the UI via scripts/emu.sh, capture screenshot evidence, and report findings with severity triage. Use whenever a change needs emulator verification, when asked to "verify", "test on the emulator", or before declaring a user-visible change done — AND when Ethan reports a visual/UI problem: reproduce and screenshot it yourself before diagnosing; never ask him for screenshots.
argument-hint: "[feature or screen to verify]"
---

# Emulator verification playbook

All device interaction goes through `scripts/emu.sh` (full adb path and the
`mockarr_test` AVD are baked in). Never call bare `adb`; never launch the
emulator binary yourself.

## Setup
1. `scripts/emu.sh boot` — **one emulator per machine.** `boot` reuses a
   running instance ("already booted"), recovers an offline adb, and only
   launches a new AVD when none exists; every wait has a 180 s deadline. Run
   it via Bash `run_in_background` when it actually has to boot (~1–2 min).
   Never foreground-sleep; never launch the emulator binary yourself.
2. `scripts/emu.sh install` then `scripts/emu.sh mockallow` (grants the
   mock-location appop; without it holds/playback silently fail). `install`
   exits 75 with "Gradle busy" while another build holds the lock — wait for
   that build (never queue a second Gradle behind a build agent) and retry.
3. `scripts/emu.sh launch`, then **`scripts/emu.sh settle`** before the first
   tap — the map swallows touches while it warms up.
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
- Map taps fired while the bottom sheet is re-anchoring (right after entering
  builder mode, Done, Stop, or a peek-height change) are swallowed. After any
  sheet/mode change, `scripts/emu.sh shell sleep 1` (or `waitfor` the new
  strip text, then sleep 1) before tapping the map. Two taps on the same spot
  within ~1 s are a MapLibre double-tap zoom, not two stops.
- `find`/`tapon` prefer an exact text/desc match and fall back to substring —
  `tapon "Play"` hits the Play button before hint copy containing the word.
  Still check `waitfor`'s echoed match. There is no navigation bar: open
  screens with `scripts/emu.sh tab Routes|Settings` (drags the Map sheet up and
  taps its row) and `tab Map` (backs out to the root).
- zsh does not word-split command substitutions: never `tap $COORDS`; use
  `tapon`, or extract x/y with awk.
- The Map screen is a floating stat card over one bottom sheet: swipe the
  handle up (`swipe 540 2100 540 600`) to reach the options list (ending in
  Saved routes / All settings), the stop list and the speed chips. Search, 3D
  and the action row hide during playback by design; the card stays.
- Text fields: `scripts/emu.sh type "text"` (spaces handled); focus the field
  with a tap first.
- Verify mock state empirically when it matters:
  `scripts/emu.sh shell dumpsys location | grep -i mock` shows the mock
  provider's coordinates — screenshots alone don't prove zero-leak behavior.
- Logs: `scripts/emu.sh logcat [pattern] [lines]` — grep recent app output
  (e.g. for missing-layer or GL errors) instead of declaring logs unreachable.
- Orientation: `scripts/emu.sh rotate landscape|portrait` (disables
  auto-rotate). Tablet layouts: `scripts/emu.sh resize 2560x1600 320` emulates
  a tablet window without a second AVD; `resize reset` restores. Note: there
  is no navigation bar or rail on any width — don't file that as a bug.
- After `scripts/emu.sh shell pm clear dev.mockarr.app` (fresh-install checks) the
  runtime permissions are gone too: `shell pm grant dev.mockarr.app
  android.permission.ACCESS_FINE_LOCATION` (and `POST_NOTIFICATIONS`) before the
  first hold, or every long-press lands on a system dialog `tapon` cannot reach.
- Non-modal Compose `Popup`s (the stop popover over a marker) are NOT in the
  `ui` dump, so `waitfor`/`tapon` cannot see them — verify with `shot` + Read
  and tap by pixel. Modal popups (the builder `⋯` menu) are dumped normally.
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
3. **Font scale 1.3** — `scripts/emu.sh shell settings put system font_scale 1.3`,
   check for clipped/truncated labels on changed screens, restore `1.0`.
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
