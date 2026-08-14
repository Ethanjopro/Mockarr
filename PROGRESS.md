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
| M3 — Simulation engine + playback service | ✅ Done | Emulator-verified: Google Maps blue dot drives the route; screen-off survival; notification controls. |
| M4 — Persistence + settings | ✅ Done | Emulator-verified: offline replay after force-stop; settings + test-connection live. |
| M5 — Hardening + polish | ✅ Done | First-run auto-setup, 4-item checklist, map warning banner; nav restore bug fixed. |
| M6 — Open-source readiness | ✅ Done | Docs/templates/fastlane in place. **Only the license decision remains (user's call).** |

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

### 2026-08-13 — Session 2 (M3)

- **M3 complete**: `core:simulation` = RouteGeometry (cumulative distances, per-segment speeds from annotations w/ uniform fallback, turn caps `v=max(2, cruise×(1−θ/180×0.9))`, backward braking pass) + SimulationEngine (cold single-collector `fixes` Flow driving the tick loop; SimClock-injected dt; pause/resume/stop/multiplier 0.25–4×; Box-Muller jitter on reported position only) — **10 unit tests** under virtual time (duration envelope, speed/accel limits, corner slow-down, no-teleport stop, determinism). App = PlaybackSessionRepository (UI↔service bridge, route handoff via `pendingRoute` since routes exceed intent-extra limits), PlaybackService (FGS type location, wakelock w/ 6 h cap, notification with Pause/Resume/Stop actions + progress, graceful teardown), PlaybackViewModel, playback UI (progress card, speed slider, camera-follow toggle, live blue dot layer), runtime permission flow (fine location + notifications 33+).
- Emulator-verified end-to-end: permission dialogs → playback → **Google Maps blue dot drove Av. de la Grande Armée to the Arc de Triomphe** with direction beam (bearing working); fused fixes showed vel≈6.7 m/s, jittered accuracy; **screen-off 25 s: still moving**; notification Pause → vel=0.0 (held, jitter-only wander); notification Stop → 0 mock providers left.
- Notes: uiautomator dumps can go stale while permission dialogs animate in — re-dump or tap visible coords from a screenshot; system dialog sequence = location then notifications, launcher callback fires once after both.

### 2026-08-13 — Session 2 (M4)

- **M4 complete**: `core:data` = Room (SavedRouteEntity: polyline6 + legs JSON blob, v1, exportSchema off) + SavedRoutesRepository (save/delete/restore/toRoute) + SettingsRepository (Preferences DataStore → hot StateFlow via injected app scope; osrmBaseUrl/tileStyleUrl/tickHz/jitter/sigma/defaultProfile; `customServerConfigured` gates walk/bike chips). App: Save dialog on map, SavedRoutesScreen (list, load→RouteHandoff singleton→MapViewModel, delete w/ Undo snackbar), full SettingsScreen (URL fields w/ Apply + **Test connection** hitting a Berlin hop, realism sliders, default profile), MockarrMap takes dynamic styleUrl (re-adds layers on style reload), PlaybackService reads SimulationParams from settings, RouteProvider base URL reads settings live.
- Emulator-verified: save → force-stop → airplane mode → reopen → route listed → loads → **plays offline** (3 mock providers, no network); Settings renders; "✓ Server responded with a route" from Test connection.
- Deviation from plan: FavoritePlace entity/UI deferred (waypoint reuse is low-value until there's a geocoder; long-press is taken by pin-mock).

### 2026-08-13 — Session 2 (M5)

- **M5 complete**: SetupStatus grew notificationsEnabled + batteryOptimizationExempt (platform APIs only, no androidx in core:mocklocation). SetupScreen now 4 cards — the two hard requirements plus Notifications (in-place POST_NOTIFICATIONS request on 33+, settings deep link below) and Battery exemption (ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS + dontkillmyapp pointer). First-run: MockarrApp auto-navigates to Setup once per launch when `readyToMock` is false. MapScreen shows a warning banner (with Fix action) when setup incomplete; clears on resume.
- **Bug found & fixed**: bottom-nav used saveState/restoreState — Setup (pushed within the Map tab stack) was captured in the saved state, so tapping "Map" restored Setup on top forever. Fix: pop Setup before tab navigation.
- **Emulator facts learned**: `pm clear` does NOT unselect the mock-location app; on userdebug emulator images the *default* appop mode for mock_location behaves as allowed (debuggable build relaxation) — use `appops set ... deny` to simulate "not selected", `allow` to select. The secure setting `mock_location_app` was null on this image.
- Emulator-verified: deny + fresh launch → auto-opens checklist (accurate per-item state) → notification request flips card to ✓ → Map tab shows warning banner → `allow` + resume → banner gone.

### 2026-08-13 — Session 2 (M6)

- **M6 complete**: README overhauled (screenshots, features, how-it-works + fair-use note, build instructions, OSRM self-host guide, architecture, attribution, license-TBD), CONTRIBUTING.md (style/architecture/test rules, emulator appops cheatsheet, pre-license contribution note), CODE_OF_CONDUCT.md (Contributor Covenant), LICENSE placeholder (all rights reserved until chosen), issue templates (bug asks device/OS/Play-services — mock behavior is OEM-specific — plus feature) + PR template, fastlane F-Droid metadata (title/descriptions/changelog + 4 real phone screenshots from emulator verification).

### 2026-08-14 — Session 3 (cleanup pass)

- **/simplify sweep** (4 parallel review agents: reuse/simplification/efficiency/altitude), all fixes emulator-smoke-tested:
  - New shared helpers: `app/ui/Formatting.kt` (route summary, timestamp, profile labels, MockStartResult→message), `PlaybackState?.progressOrZero` (core:model), `RoutingProfile.fromNameOrDefault`, `LocationManager.registerTestProvider` (core:mocklocation), `MockarrSettings.normalizeBaseUrl` + range constants, `RouteGeometry.brakingLimit`.
  - Dead code removed: `PlaybackState.Idle` (unreachable), `PlaybackSessionRepository.activeRoute` (never read), unused deps (compose tooling, room-ktx, lifecycle-viewmodel-compose), `TopLevelDestination.routeClass`.
  - Encapsulation: `pendingRoute` is now private behind `requestStart()`/`consumePendingRoute()`; USER_AGENT derives from BuildConfig.VERSION_NAME.
  - Nav generalized: `navigateTopLevel()` pops ANY non-top-level overlay before tab switches (was a SetupDestination special case).
  - Setup status: shared `SetupStatusHolder` singleton with 500 ms cache — one probe at launch instead of two, all screens share one StateFlow.
  - Efficiency: settings sliders commit once per gesture (was a DataStore file write per drag event); notification PendingIntents cached + notify() skipped when progress/paused unchanged (was steady binder spam incl. while paused); map waypoint/route GeoJSON updates split into independent effects.
  - Skipped (noted, deliberate): re-enabling detekt MagicNumber (would churn unit-conversion literals in core math for little clarity), unifying RouteHandoff with the playback mailbox (different observer patterns), initial speed-multiplier handoff to new sessions (latent behavior quirk — a /code-review matter, not cleanup).

### 2026-08-14 — Session 4 (user testing feedback round 1)

- Addressed the user's first hands-on testing notes. All changes emulator-verified (screenshots under the job tmp dir during the session):
  - **Settings simplified**: Routing server, Map tiles, and Default profile hidden from the UI (backend + repository support kept; profile chips on the Map only appear when a custom OSRM server is configured — the demo server is driving-only, so the picker was dead weight). Remaining settings renamed to plain language ("Updates per second", "Realistic GPS wobble", "Wobble amount") with helper text under each.
  - **Distance units**: new `DistanceUnits` (core:model) + `distance_units` setting; default from locale (US/GB/LR/MM → miles, else km). Used in route summaries, playback card, saved-route cards, and the playback notification. Settings has a Miles/Kilometers picker.
  - **Paris camera bug fixed**: camera target+zoom persisted to DataStore on camera-idle (debounced 1 s via `collectLatest`); `MockarrMap` restores it on tab switches AND cold starts (late-arrival guard for async DataStore load). Paris is now only the fresh-install fallback.
  - **Numbered waypoint markers**: SymbolLayer with white bold numbers over the circle markers ("Noto Sans Bold" glyphs — render fine on the OpenFreeMap liberty style).
  - **Pin-mock made visible + clearer**: purple map marker at the held position; copy renamed from "pin-mocking" to "Holding your location at …"; hint text rewritten. Playback now stops any active pin-hold first (one mock source at a time).
  - **Undo button** next to Clear (removes last waypoint, refetches route).
  - **Place search on the Map tab**: `NominatimGeocoder` in core:routing (shared `UserAgentInterceptor` + `MinIntervalInterceptor` extracted from OsrmRouteProvider; 1.1 s min interval + UA per Nominatim policy). Search bar → up to 5 results → tap flies camera there (zoom 14).
  - **Tab-transition fix**: MapView now uses `textureMode(true)` — the default SurfaceView sat opaque on top during navigation crossfades ("map stays on screen too long"); texture mode composites into the fade correctly.
  - **Default-profile race fixed**: MapViewModel read `settings.value.defaultProfile` at construction, racing the async DataStore load; it now follows the settings flow until the user picks a profile by hand.
- Emulator facts learned: `scripts/emu.sh` assumes `adb` resolvable — background shells here need `$HOME/Library/Android/sdk/platform-tools/adb` full path; killing a background task kills child processes (the emulator!) — launch it with `nohup ... & disown`.
- Verified live: OSRM demo routes across water via ferries (Dover→Calais = 86 km, code Ok); impossible crossings (SF→Honolulu) return NoRoute → dashed straight-line fallback.
- Deferred/answered-only (user's notes that need no code): notifications are optional for background playback (foreground service runs without POST_NOTIFICATIONS; the permission only makes the progress notification visible — checklist already marks it "Recommended"); on route finish/stop the mock providers are removed and the device reverts to its real location (a "hold at destination" toggle is a possible future feature); emulator pinch-zoom needs Cmd/Ctrl+drag, double-tap zoom also works — not an app bug; long-press tooltips on settings noted as a future idea.

## PLAN COMPLETE — remaining items are the user's

1. **License decision** (GPL-3.0 vs Apache-2.0 vs other) — swap LICENSE, update README/CONTRIBUTING, then the repo can go public.
2. **Physical-device spot-check** (recommended before any release/announcement): install via `./gradlew :app:installDebug`, run the in-app setup checklist, play a route, watch Google Maps follow. Emulator can't show OEM battery-killer quirks.
3. Optional future work (from PLAN.md future ideas): joystick mode, GPX import/export, multi-stop UI polish, favorite places, geocoder search, tag-triggered release workflow with signing.
