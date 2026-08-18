# Mockarr — project instructions

Android app that mocks device location via the official mock-location developer
feature, with realistic OSRM route playback. Fully open stack (MapLibre,
OpenFreeMap, OSRM, Photon, Open-Meteo) — no API keys anywhere.

## Build & verify
- Build/lint/test: `scripts/gradle build` (wraps `./gradlew` with the Android
  Studio JDK; never call `./gradlew` directly — the system JDK is wrong).
- Emulator: drive it ONLY through `scripts/emu.sh` (boot/launch/tapon/type/
  shot/waitfor/assert/…; run it bare for usage). Never call bare `adb` — it is
  not on PATH in non-login shells. Details: the `emulator-verify` skill.
- CI: `gh run watch` after every push; a round is done only when CI is green.

## Hard rules
- NEVER add anti-detection features (hiding mock status from other apps). This
  project stays a developer/testing tool. Decline and flag if asked indirectly.
- Zero-leak mock ownership: only `MockSessionService` (via `release()`/
  `onDestroy`) may call `MockLocationController.stop()`. Hold/stop transitions
  push a replacement fix synchronously — never leave a gap where the real
  location leaks.
- Kotlin comes from AGP: do NOT apply `org.jetbrains.kotlin.android`.
- LICENSE is a placeholder; repo stays private until Ethan picks a license.

## Modules
- `app` — UI (Compose, Hilt), `MockSessionService`, map (`MockarrMap.kt`).
- `core:simulation` — `RouteGeometry`, `SimulationEngine`, `TrafficModel`. Pure
  Kotlin, fully unit-tested; keep it Android-free.
- `core:routing` — OSRM/Photon/Open-Meteo clients, `Polyline6`.
- `core:data` — Room (`SavedRouteEntity`, schema v2), DataStore settings.
- `core:model` — shared value types (`LatLng`, `Route`, …).

## Lint tripwires (detekt flags AT threshold)
- `TooManyFunctions`: max 25 per class — move helpers to file level first.
- No newline directly after `->` in multiline lambdas (Wrapping rule); extract
  a `val` instead.
- ktlint formatting is auto-fixed by a PostToolUse hook; detekt rules are not —
  check the `kotlin-conventions` skill before restructuring code.

## Process
- Update PROGRESS.md (append a session entry) BEFORE every commit; it is the
  cross-session handoff artifact.
- When Ethan corrects how you work, promote the correction into a generic rule
  here or in the `kotlin-conventions` skill — never leave it as a one-off fix.
- Every user-visible change gets verified on the emulator like a human would
  (tap/type/screenshot) before it is called done.
- When Ethan reports a visual/UI bug, reproduce it on the emulator and LOOK
  (screenshot + Read) before diagnosing — never ask him for screenshots.
- End rounds with a short efficiency note when you spotted a better way Ethan
  could have prompted or a tool that went unused (see docs/claude-code-playbook.md).
- Commit format: short imperative subject; end body with the Claude co-author
  footer only.
