# Mockarr — Progress Report

> **Purpose**: complete, self-contained record of project state. If you (human or AI assistant) have lost context, read this file top to bottom plus [PLAN.md](PLAN.md) and you know everything needed to continue. **Keep this file updated at the end of every work session.**

## What this project is

Android app that plays back road routes through Android's built-in mock location developer feature. User picks points on a map → OSRM generates a road route → playback engine drives the device's mocked location along it realistically (visible in Google Maps etc.). Owner: Ethan Jones (GitHub: Ethanjopro). Solo developer, plans to open-source later. Full design: [PLAN.md](PLAN.md). Repo: https://github.com/Ethanjopro/Mockarr (private).

## Milestone status

| Milestone | Status | Notes |
|---|---|---|
| Plan document | ✅ Done | PLAN.md, commit `2c9632c` |
| M0 — Skeleton + CI | ✅ Done | Commits `7f495f8`, `19e7098`, `bed0cc7`. CI green. |
| M1 — Mock location walking skeleton | ✅ Done | **Verified on emulator incl. Google Maps blue dot at mocked coords.** Physical-device spot-check still worthwhile before M3 (OEM quirks). |
| M2 — Map + waypoints + OSRM | ✅ Done | Emulator-verified: road-following route in Paris + airplane-mode fallback. |
| M3 — Simulation engine + playback service | ⬜ Not started | |
| M4 — Persistence + settings | ⬜ Not started | |
| M5 — Hardening + polish | ⬜ Not started | |
| M6 — Open-source readiness | ⬜ Not started | License still TBD (user chose "decide later"). |

## Key decisions (and why)

- **Fully open stack**: MapLibre + OpenFreeMap tiles + OSRM routing. User explicitly chose this over Google stack (no API keys/billing; open-source friendly). Mocked location still appears in the real Google Maps app — that works at OS level.
- **License deferred**: user chose "decide later". README says "all rights reserved until chosen". Must be decided before open-sourcing (M6).
- **Toolchain**: Gradle 9.5.1, AGP 9.3.1, Kotlin 2.3.21, KSP 2.3.11, Hilt 2.60.1, Compose BOM 2026.08.00, compileSdk/targetSdk 37, minSdk 26. AGP 9 has **built-in Kotlin** — do NOT apply `org.jetbrains.kotlin.android` (it errors). Compose/serialization plugins still applied separately, version = Kotlin version.
- **Architecture**: 6 modules (`:app`, `:core:model`, `:core:simulation`, `:core:routing`, `:core:mocklocation`, `:core:data`), convention plugins in `build-logic/` (`mockarr.android.application`, `mockarr.android.library`, `mockarr.jvm.library` — all auto-apply detekt). Pure-JVM core modules stay Android-free and Hilt-free; Hilt bindings live in `app/src/main/kotlin/dev/mockarr/app/di/`.
- **No `FusedLocationProviderClient.setMockMode`**: would add a Play Services dependency; platform test providers (gps/network/fused) are honored by Play services FLP. Escape hatch documented in PLAN.md §1.3.
- **detekt with detekt-formatting** (bundles ktlint) as the single style tool. Config: `config/detekt/detekt.yml` (MagicNumber off, FunctionNaming ignores @Composable).

## Toolchain gotchas (learned the hard way — don't rediscover)

- **Build JDK**: use Android Studio's bundled JBR: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` before `./gradlew`. System java is 24 (too new to be safe); JBR is 21.
- **Compose BOM 2026.08.00 requires compileSdk 37** (build fails at `checkDebugAarMetadata` with 36).
- **Material icons are NOT bundled with material3** in current BOMs — need explicit `androidx.compose.material:material-icons-core:1.7.8` (not in BOM).
- **GitHub workflow files need the `workflow` OAuth scope** — was granted via `gh auth refresh -h github.com -s workflow` (done 2026-08-13). If pushes touching `.github/workflows/` are rejected, that's why.
- SDK components (platform 37 etc.) auto-download during build; `local.properties` (gitignored) points at `/Users/ethanjones/Library/Android/sdk`.
- Version catalog: `gradle/libs.versions.toml`. build-logic imports the same catalog via its settings file.

## How to build / test / verify

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew build          # compiles all modules, runs unit tests, lint, detekt
./gradlew :app:installDebug   # install on connected device (USB debugging on)
```

CI: `.github/workflows/ci.yml`, runs `./gradlew build` on every push/PR to main. Check with `gh run list`.

## Autonomous emulator testing (set up 2026-08-13 — use this to test without the user)

A dedicated AVD **`mockarr_test`** exists (Pixel 7, API 35, google_apis arm64 — includes Google Maps). The user's other AVDs (Pixel_7, Pixel_7_API35) must not be touched.

```sh
SDK=~/Library/Android/sdk
$SDK/emulator/emulator -avd mockarr_test -no-window -no-audio -no-boot-anim -no-snapshot &   # boots in ~15 s
$SDK/platform-tools/adb wait-for-device   # then poll: adb shell getprop sys.boot_completed == 1
./gradlew :app:installDebug

# Select Mockarr as mock location app WITHOUT touching the Settings UI:
adb shell settings put global development_settings_enabled 1
adb shell appops set dev.mockarr.app android:mock_location allow    # deny = simulate not-selected

# Drive the UI: uiautomator dump /sdcard/ui.xml for button bounds, adb shell input tap X Y
# Verify mocking at OS level (should show gps/network/fused with [mock] + the mocked coords):
adb shell dumpsys location | grep -E "provider \[mock\]|last location"
# Screenshots: adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
adb emu kill   # ALWAYS shut down when done
```

M1 verification results (2026-08-13, emulator): all three providers mocked to Eiffel Tower and `dumpsys location` confirmed `[mock]` fixes; **Google Maps showed the blue dot at Tour Eiffel**; Stop removed all test providers (0 left); appop `deny` produced the friendly "not selected" error, no crash. Physical-device spot-check still recommended before M3 (OEM quirks the emulator can't show).

### Human-style UI control (verified 2026-08-13)

**`scripts/emu.sh`** wraps the whole workflow — always drive the emulator through it: `boot`, `kill`, `tap X Y`, `tapon "Button text"` (finds a UI element by text/desc via uiautomator and taps its center), `find "text"`, `swipe X1 Y1 X2 Y2 [ms]`, `type "text"`, `key N` (66=enter, 4=back), `home`, `back`, `drawer`, `shot [file]`, `ui` (dump XML), `install`, `mockallow`/`mockdeny`. The loop that works: act → `shot` → Read the png (or `ui` + grep) → decide next action. Screen is 1080×2400.

Verified end-to-end like a human: app-drawer swipe → tapped the Mockarr icon → tab navigation → Setup checklist → deep link into real Developer Options → swipe-scrolled 9 screens to "Select mock location app" → picked Mockarr in the real settings picker (checklist went green — validates probe detection without appops) → typed "Louvre Museum" into Google Maps search with the keyboard. Gotcha: `tapon` fails (exit 1) when a button isn't on screen — e.g. setup-card action buttons only render while their check is unmet.

## Session log

### 2026-08-13 — Session 1
- Created repo (private) on Ethanjopro account, pushed initial README.
- Researched Google Maps Platform pricing for user (Routes API: 10K free/month then $5/1K; Maps SDK free) — user chose open stack instead. License: decide later.
- Wrote PLAN.md (full architecture/design/milestones) — reviewed by Plan agent, approved by user.
- **M0 complete**: scaffolded everything (see Key decisions). Fixed compileSdk 36→37 and added material-icons-core along the way. Local build + detekt green, both CI runs green (~8 min each). CI actions bumped to checkout@v7/setup-java@v5/setup-gradle@v6/upload-artifact@v7.
- **M1 code written**: AndroidMockLocationController, SetupStatusRepository, real SetupScreen with deep links, debug "Mock here" pin on MapScreen, Hilt DI module, added `androidx.hilt:hilt-navigation-compose:1.4.0`.
- **Emulator testing set up + M1 verified** (user asked for autonomous testing): created `mockarr_test` AVD, full verification pass incl. Google Maps blue dot in Paris — see "Autonomous emulator testing" section. detekt lessons this session: no inline `/* param */` comments (CommentWrapping), `javax` imports go last (ImportOrdering), ReturnCount/LoopWithTooManyJumpStatements limits; Android lint wants `ProviderProperties` constants (safe pre-31 — compile-time inlined).

### 2026-08-13 — Session 2 (M2)

- **M2 complete**: `core:routing` = OsrmRouteProvider (Retrofit 3 + kotlinx converter, @Url full-URL pattern, UA + min-1s-interval interceptors, typed RoutingException incl. 429→RateLimited and 400-body NoRoute), hand-written Polyline6 codec, StraightLineRouteProvider fallback — 12 unit tests incl. MockWebServer. App = MockarrMap composable (MapLibre 13, OpenFreeMap liberty style, GeoJson sources: solid route layer + dashed fallback layer + role-colored waypoint circles, camera auto-fit, initial camera Paris z12 until M5), MapViewModel (500 ms debounce, fallback on failure), MapScreen rewrite (profile chips — walk/bike disabled pending custom server, stats, Clear, disabled Play placeholder), long-press = pin-mock (MockPinViewModel now takes a position). INTERNET permission; MapLibre.getInstance in Application.
- Emulator-verified: two taps in Paris → road-following OSRM route "2.7 km · about 8 min"; airplane mode → error banner + orange dashed straight line. Gotchas: hiltViewModel moved to `androidx.hilt.lifecycle.viewmodel.compose` (1.4.0); detekt LongParameterList needs Composable exemption.

## Next steps (in order)

1. M3: simulation engine + foreground playback service (PLAN.md §4, §5) — the flagship. Do a physical-device spot-check of mocking before/during M3.
2. M4: Room saved routes + DataStore settings, wire OSRM base URL + walk/bike chips to custom server setting.
3. M5/M6: hardening, open-source readiness (PLAN.md §10, §12).
