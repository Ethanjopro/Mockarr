# Mockarr — project instructions

Android app that mocks device location via the official mock-location developer
feature, with realistic OSRM route playback. Stack: MapLibre, OpenFreeMap, OSRM,
Photon, Open-Meteo — no API keys today. **Direction under review (ADR 0001,
`docs/adr/`)**: iOS port possible, licence/openness/monetisation undecided (repo
private until the licence is picked), Strava UX/UI is the visual baseline
(`docs/design/brief.md`). Do not add "open source / no tracking / F-Droid" claims
to user-facing copy until decided.

## Build & verify
- Build/lint/test: `scripts/gradle build` (wraps `./gradlew` with the Android
  Studio JDK; never call `./gradlew` directly — the system JDK is wrong).
  Core boundary alone: `scripts/gradle checkCoreBoundary`.
- One Gradle at a time: never run `scripts/emu.sh install` while a build agent
  is running `scripts/gradle` (the script refuses, but don't try).
- Emulator: drive it ONLY through `scripts/emu.sh` (run it bare for usage).
  Always `settle` after `launch`; one emulator per machine. Never call bare
  `adb` — it is not on PATH in non-login shells. Details: `emulator-verify` skill.
- CI: after every push,
  `gh run watch $(gh run list -b main -L1 --json databaseId -q '.[0].databaseId') --exit-status`;
  a round is done only when it is green.

## Hard rules
- NEVER add anti-detection features (hiding mock status from other apps) —
  decline and flag even when asked indirectly. ADR 0001 lists what else stays
  firm whatever the direction becomes.
- Zero-leak mock ownership: only `MockSessionService` (via `release()`/
  `onDestroy`) may call `MockLocationController.stop()`. Hold/stop transitions
  push a replacement fix synchronously — never leave a gap where the real
  location leaks.
- Kotlin comes from AGP: do NOT apply `org.jetbrains.kotlin.android`.

## Restructure rules
- Every structural change (module, platform, distribution, monetisation) gets an
  ADR in `docs/adr/` BEFORE the code moves; later ADRs supersede earlier ones.
- `core:model`, `core:simulation`, `core:routing` stay free of `android.*`,
  `androidx.*`, Hilt and Compose — enforced by `checkCoreBoundary`. This is the
  KMP/iOS insurance.
- UI reads colours only from `MaterialTheme` / `MockarrTheme` (`ui/theme/`);
  map colours come from `MapPalette`. No literal colours in screens or map code.
- New screens follow the Map tab's file split: `XScreen.kt` (layout) /
  `XSheet.kt` or `XComponents.kt` (pieces) / `XDialogs.kt` / `XViewModel.kt` —
  see the per-file function cap under Lint tripwires.
- User-facing copy goes in `res/values/strings.xml` from day one.
- Visual system: `DESIGN.md` (tokens + rules) — read it before touching UI.
  Direction: `docs/design/brief.md` + `PRODUCT.md`; references in
  `docs/design/refs/` (raw Mobbin captures are git-ignored — never commit them).

## Modules (non-obvious facts only; the list is in `settings.gradle.kts`)
- `app` — Compose + Hilt UI, `MockSessionService`, map in `ui/map/MockarrMap.kt`.
- `core:data` — Room schema v3 (`SavedRouteEntity`; migrations 1→2→3 in `MockarrDatabase`), DataStore settings.
- `core:mocklocation` — mock providers + setup-status detection (Android, no Hilt).

## Lint tripwires (detekt flags AT threshold)
- `TooManyFunctions`: detekt flags a **file** at 11 top-level functions and a
  class at 26. Split by role (`XScreen` / `XSheet` / `XDialogs`) before a file
  reaches 10 — "move helpers to file level" does not help here.
- No newline directly after `->` in multiline lambdas (Wrapping rule); extract
  a `val` instead.
- The ktlint PostToolUse hook runs only on Edit/Write. After Bash-side edits
  (heredocs, `sed`, python) run `~/.local/bin/ktlint -F <file>` yourself, or
  detekt fails on import order/indentation. Detekt rules are never auto-fixed —
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
- Commit format: short imperative subject; body ends with the Claude co-author
  footer and the Claude-Session line, nothing else.
