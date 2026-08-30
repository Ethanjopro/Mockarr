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

### 2026-08-14 — Session 5 (user testing feedback round 2)

- Six feedback items, all emulator-verified (including a dumpsys leak matrix):
  - **Zero-leak mock ownership + "Stay at destination"**: all mocking now lives in the foreground service (`PlaybackService` → `MockSessionService`; repository → `MockSessionRepository` with `Idle / Playing / Holding(position, source)` states). End-of-route chain (finish AND Stop-after-deceleration): stay-at-destination ON (default) → hold at endpoint; OFF + pin held before play → revert to the pin; else → release to real location. `MockLocationController.stop()` is called from exactly two places (release path + onDestroy safety). Every hold transition pushes a fix synchronously — dumpsys polling at 0.6 s through both transitions showed mock providers registered throughout and an instant coordinate handoff (route end → exact pin coords, sample 52→53, no real-location sample). Deleted `MockPinViewModel` (its `onCleared` ripped providers out; pin holds now survive backgrounding via the service) and removed the round-4 stop-before-play leak window. Holding mode has its own notification ("Mockarr — holding location", Stop action = release).
  - **Persistent map**: single `MapView` hoisted BEHIND the NavHost in `MockarrApp` (`MapLayer` in MapScreen.kt); Map tab is a transparent controls overlay; other tabs cover the map with opaque `Surface`s. Reverted texture mode → SurfaceView (fast path; lingering bug can't return since the map is never animated/detached). Tab switches keep the exact viewport (pixel-identical screenshots), no style reload. Camera restore is one-shot cold-start only via `SettingsRepository.awaitLoaded()` (kills the snap-back race: the old reactive restore could replay a ~1 s-stale save mid-pan); a user gesture (REASON_API_GESTURE) cancels restore and flips follow-mode off (no more rubber-banding during playback). Render thread pauses while covered (resume = activity-started AND map-visible).
  - **Search**: Nominatim → **Photon** (komoot) — built for autocomplete, biased by `lat/lon` (camera center, seeded from the saved camera): as-you-type suggestions (300 ms debounce, 3-char min, in-flight cancel), results deduped by display name. Focus fix: `clearFocus()` on map tap/gesture/result-select/Close. Attribution updated.
  - **Settings text** bumped one step (titleLarge/bodyLarge/bodyMedium); **Stay at destination** toggle added under "After a route ends".
  - **2D/3D button**: circular button below the search bar toggles the style's `FillExtrusionLayer`s + tilt gestures (flattens tilt when entering 2D); persisted as `map_3d_enabled`; label shows the mode you'll switch TO.
- Verified besides the leak matrix: Times Square autocomplete + camera jump; cold-start restore to the exact searched viewport; 2D strips Manhattan extrusions; post-fling screenshots 2 s apart are pixel-identical (no snap-back).
- Note: a stale Dallas camera value from the previous build's save race was found in DataStore — the new one-shot restore handles it correctly; no new bogus saves observed.

### 2026-08-14 — Session 6 (user testing feedback round 3)

- Five items shipped (sixth — traffic-light stop simulation — deferred by the user; full validated design lives in the session-6 plan file and can be built later without rework):
  - **Banners at the bottom**: the whole status stack (setup warning, holding banner, errors, saved confirmation) now renders directly above the Play/Playback card; search bar + button cluster stay on top.
  - **Saved-route naming**: `PhotonGeocoder.reverse()` (verified endpoint) → Save dialog prefills "«start» to «end», «city»" via reverse-geocoding both route endpoints (lazy, on Save tap; adopts the suggestion only while the user hasn't typed). Verified live: "East Fork Russian River to Vista del Lago Road".
  - **Terrain altitude**: `Route.altitudes` (nullable, 1:1 with points) enriched asynchronously in MapViewModel via new `OpenMeteoElevationClient` (batch ≤100 coords, no key) + `ElevationSampling` (distance-even sampling + interpolation); `RouteGeometry.altitudeAt()` lerps per fix; pin holds fetch a single elevation and upgrade mid-hold. Persisted via Room **v2** (`altitudesJson` column + MIGRATION_1_2 wired in AppModule — no destructive fallback exists, forgetting addMigrations would crash existing installs). Verified live: fixes report 236–240 m through a Mendocino valley instead of the constant 35.
  - **Locate button**: circular target button (new `ic_target.xml`, Material my-location glyph) under the 2D/3D button. Mock active → pans to held pin/playback dot; idle → pans to the device's real location (`getCurrentLocation` API 30+ w/ last-known fallback). Never touches providers (dumpsys: mock count unchanged in both cases).
  - **Time remaining**: `PlaybackState.Playing/Paused` gained `remainingSeconds` (all construction sites are engine-internal); `RouteGeometry` cumulative durations → `remainingDurationSeconds()/multiplier`; shown in the playback card ("· 30 s left") and notification (dedupe key extended with minutes so the text can't go stale).
- New tests: `RouteGeometryEtaAltitudeTest`, `SimulationEngineEtaTest`, `ElevationSamplingTest`, `OpenMeteoElevationClientTest` (MockWebServer, incl. >100-coord chunking).
- detekt: `LongParameterList.ignoreAnnotated` +HiltViewModel/Inject (DI constructors list one dep per param); `TooManyFunctions.thresholdInClasses` 20→26.
- **Correction to the session-5 note**: the "stale Dallas camera" was almost certainly the user's own between-session emulator testing (camera restored to Carrollton, TX again this session) — camera persistence was working as designed, not a bug.

### 2026-08-15 — Session 7 (user testing feedback round 4)

- **Traffic question answered + heuristic shipped**: no free/open router has real traffic (OSRM = static OSM speeds; Google/TomTom/HERE traffic is paid; OSRM's traffic mode needs your own feeds). Approved substitute: `core/simulation/TrafficModel.kt` — deterministic rush-hour curve (weekday 1.50 peaks @ 08/17h, shoulders 1.15–1.25, nights 1.0, weekends flat 1.10; per-minute lerp, continuous across midnight/week boundaries). Applied as `SimulationParams.durationScale` → `RouteGeometry` divides segment speeds pre-clamp, so playback speed, total duration, and ETA all shift together; captured ONCE at Play in the service; never persisted (saved routes keep raw durations). Summary shows "about N min (traffic)" when factor ≥ 1.05. Setting "Rush-hour traffic" default ON. Full test suite (`TrafficModelTest` + geometry/engine scale tests incl. multiplier independence).
- **Hold banner shows place names**: `Holding.placeName` resolved via Photon reverse in the service on every enterHold (pin AND destination holds; identity-guarded). Verified: "Holding at El Verano Avenue" (pin) and "Holding at Park Boulevard" (destination after route end); notification matches; coords only while unresolved.
- **Routes start at the held position**: MapViewModel (now injecting MockSessionRepository) seeds the first waypoint from an active hold — one tap = hold→tap route, playback continues seamlessly (marker 1 at the pin, verified).
- **Expandable route-creator card**: compact bar (ellipsized summary · Play icon button · rotating chevron) that auto-expands when the first waypoint lands and collapses on Clear; expanded column = profile chips + Save/Undo/Clear + slot for future per-route toggles. Setup button removed from the card.
- **Search localization v2**: bias = mocked position (hold/playing fix) → real last-known → camera; each suggestion row shows its distance from that anchor in the user's units. Verified while holding in Palo Alto: Grocery Outlet Palo Alto 0.1 mi first, then Sunnyvale 6.5, Redwood City 7.2, ordered by distance.
- **Setup moved to Settings**: "Mock location setup ›" row at the top of Settings (map's not-ready banner keeps its Fix action).
- **2D keeps building outlines**: the liberty style's flat `building` FillLayer (maxzoom 14) gets its maxZoom raised to 24 in 2D while extrusions hide; original restored in 3D. Verified footprints+outlines at z15.
- **Settings de-worded**: helper paragraphs removed; every setting label opens its description via **long-press** (M3 TooltipBox/PlainTooltip — verified rendering); About notes the gesture.
- Ops: macOS TCC revoked Documents access mid-session (background `claude bg-pty-host` is launchd-parented — iTerm's Full Disk Access doesn't cover it; fixed by granting FDA to `/opt/homebrew/bin/claude` + restarting the bg processes AND `./gradlew --stop` since old Gradle daemons kept the stale denial).

### 2026-08-15 — Session 8 (user testing feedback round 5)

- **Route-from-hold is now a Play-time choice**: automatic seeding reverted; pressing Play while holding (and the route doesn't already start within 30 m of the pin) shows "Start from held location?" — "From held spot" prepends the hold as origin, refetches, and auto-plays when the new route lands ("playWhenRouteReady" effect); "As built" plays unchanged; no dialog when nothing is held. Verified both paths (1.8 mi route grew to 2.7 mi starting at the pin).
- **Creator-card hint no longer truncates** (maxLines 4 when no route; summaries stay at 2).
- **Settings sliders sit under their options**: Stay at destination → Rush-hour traffic → Updates/sec (label+slider) → GPS wobble (switch) → Wobble amount (label+slider).
- **Setup is first-run onboarding**: `setupSeen` flag — the checklist opens exactly once on the very first launch (verified with pm clear; second launch goes straight to the map); the Settings entry is now a FilledTonalButton.
- **Slider ranges**: wobble minimum 0.5 → 0.0 m; updates-per-second slider is log-scaled (1 Hz now sits at ~30% of the track — most travel covers the realistic low end).
- **Search selection zooms to 16** (was 14) — verified landing on the Tour Eiffel footprint.
- **Speed-limit question answered + driver variance shipped**: OSRM's per-segment durations already encode OSM `maxspeed` + road-class defaults, so free explicit-limit fetching (Overpass) would double-count while adding map-matching, unit-parsing, and coverage problems — not worth it. Instead `SimulationParams.speedVarianceFraction` (service passes 0.08, default 0 keeps tests bit-identical) spreads each segment's cruise speed ±8% via the engine's seeded Random inside `RouteGeometry` BEFORE duration sums, so ETAs stay consistent; composes with traffic factor and speed multiplier. Verified live (vel 6.83/6.38/6.18/6.55/5.93 across segments) + unit tests.

### 2026-08-15 — Session 9 (user testing feedback round 6)

- **Saved-routes thumbnails + search**: `RouteThumbnail` Canvas glyph (Polyline6-decoded, ≤64 pts, cos-lat aspect correction, primary-color stroke + green/red end dots) leads each card; search field filters by name (`combine(observeAll, query)`). Verified: the shape glyph matches the real route.
- **Hold-after-route prompt bug fixed**: the play decision now reads `sessionViewModel.session.value` / `viewModel.uiState.value` **at click time** instead of composition-captured vals (stale captures could eat the dialog). Verified route→hold→Play prompts correctly.
- **One held point + one waypoint playable**: Play enables with 1 waypoint while holding; prompt "Route from held location?" → confirm prepends the hold, refetches, auto-plays (verified end-to-end, vel ≈7 m/s from the pin); cancel does nothing.
- **Ripple on the live dot**: `RIPPLE_LAYER` beneath the dot layers, driven per-frame by an `Animatable` loop writing radius/opacity straight into the style (first attempt used `SideEffect` reading the value outside composition — never re-ran; lesson: animation values must be consumed in a frame callback or read during composition). Blue while driving, purple while holding — verified expanding/fading halo across frames.
- **Progress alert**: `progressAlertEnabled/Percent` settings (switch + 10–95% slider) → one-shot high-importance notification "Route N% complete · ETA" per session from the service's fix collector. Verified live: fired exactly at 10% (~28 s into a 0.7 mi drive), never repeated.
- **Leaner collapsed card**: state-aware one-liners ("Tap the map to add stops" / "Add another stop to make a route" / "Play to route from your held spot" / summary); the long-press tip moved into the expanded section.
- **Locate-button delay**: answered (cold `getCurrentLocation` fix takes seconds — expected) + mitigated: two-stage pan (instant last-known, refine only if the fresh fix is >50 m away).
- Emulator ops: prefer `find`→awk x/y (zsh doesn't word-split `$P`); `find "Play"` matches hint TEXT containing "Play" — tap explicit coords for the button; dialogs render ~1 s after taps — screenshot before tapping dialog buttons.

### 2026-08-15 — Session 10 (Claude Code tooling round: research + Tier 1/2 adoption)

- **Research**: five agents studied wesammustafa/Claude-Code-Everything-You-Need-to-Know + ~40 linked resources, then a second pass gathered practitioner evidence (HN/blogs/GitHub; Reddit/X unreachable) and covered Impeccable + model strategy. Full record: `docs/claude-code-playbook.md`. User decisions: keep Fable 5 as the model; audio pings ON; Impeccable fold-in only; Tier 1+2 this round.
- **Adopted** (all verified locally):
  - `CLAUDE.md` — build/emulator/lint/process rules, zero-leak + no-anti-detection invariants.
  - `.claude/settings.json` — allowlist (emu.sh, scripts/gradle, gh run, git read-ops, rg), deny (force-push, rm -rf), five hooks wired.
  - `scripts/gradle` — JDK-pinned gradlew wrapper (verified: Gradle 9.5.1 on the Studio JBR).
  - Skills: `emulator-verify` (full playbook + triaged report format + dark-theme/font-scale passes adapted from Impeccable's android.md), `kotlin-conventions` (paths-gated to `**/*.kt`), `claude-md-review` (audit skill, staleness checks retargeted at gradle/detekt configs).
  - Hooks: `session_start.sh` (injects branch/CI/PROGRESS tail — verified output incl. live CI status), `format_kt.sh` (standalone ktlint 1.5.0 at `~/.local/bin/ktlint`, ~0.9 s, verified fixing a misformatted file), `turn_stamp.sh` + `speak_stop.sh` (speaks only after ≥45 s turns — both paths verified), `speak_notification.sh`. *(Removed 2026-08-28: the audio/turn-stamp hooks and their settings.json entries are gone; current hooks are `session_start.sh` and `format_kt.sh` only.)*
  - `.editorconfig` — pins `intellij_idea` style + disables `function-signature`/`condition-wrapping` + Composable naming exemption. Parity verified: standalone ktlint reports 0 violations on the detekt-clean tree (default `ktlint_official` style had reported 500+ — the pin is load-bearing). Full `scripts/gradle build` green after adding it.
- **Deferred (Tier 3, next tooling round)**: docs-drift audit workflow (refute-by-default), build-fixer/emulator-verifier subagents, `/five`, `emu.sh waitfor/assert`, full Impeccable trial.
- **Rejected with evidence** (see playbook): SuperClaude/BMAD (ceremony + measured context tax), Agent Teams (3–4× tokens solo), mobile-mcp (open Android bugs; raw adb is the proven path — emu.sh stays), Memory/Seq-Thinking/Playwright MCP, Serena (weak Kotlin LSP).

### 2026-08-15 — Session 10 (continued): Tier 3 tooling trials

- **emu.sh extensions** (all verified live incl. failure paths): `launch` (am start), `waitfor "text" [timeout]` (polls, echoes the matched attribute, `"a|b"` alternation), `assert`, `matchtext`, and real usage text. The match-echo + alternation came from the emulator-verifier trial's friction feedback — the feedback-capture loop working as designed.
- **Subagents**: `.claude/agents/build-fixer.md` (distilled Gradle failures, never edits tests to pass) and `emulator-verifier.md` (PASS/FAIL + triaged findings + screenshots only). Verifier trialed live: 3-tab smoke pass PASSed with screenshot evidence; it independently spotted the dark-UI/light-map split.
- **`/five`** command (`.claude/commands/five.md`) — root cause must name a file/line or config key.
- **Docs-drift workflow** (`.claude/workflows/progress-audit.js`, invoke `/progress-audit`): scan→refute-by-default against the repo. First real run: 15 agents, 54 claims, exactly 1 stale claim (the playbook's own "deferred Tier 3" line — corrected), 0 false positives.
- **Impeccable trial → KEEP**: installed via `npx impeccable install` (web-only `scripts/` gitignored, ~2.9M dead weight for native). Code audit scored the app **13/20** with findings our linters can't model — backlog for a future round: [P1] Settings tooltip descriptions unreachable by TalkBack + unmerged switch rows; [P1] dark theme pairs dark chrome with the always-light map style; [P2] `MapScreen.kt:184` collects `latestFix` at composition → whole overlay recomposes per tick (read `.value` in the locate onClick instead); [P2] ripple ignores Remove-animations; [P2] no window-size-class adaptivity; [P3] search rows ~44dp, locale-unsafe `%.4f` coords, thumbnail/route color mismatch under Material You. Rubric friction (expected): fights hard-coded MapLibre canvas hex and wants top app bars — overruled deliberately.

### 2026-08-16 — Session 11 (user feedback round 7 + audit backlog P1/P2)

- **Real-basemap route thumbnails**: new `RouteThumbnails` (app/ui/map) renders each saved route on the live map style via MapLibre `MapSnapshotter` (88 dp, was a 64 dp flat glyph), route + end dots Canvas-drawn over the snapshot via `MapSnapshot.pixelForLatLng` (NOT `latLngToPixel` — that API doesn't exist in 13.5.0). Memory LRU(16) → in-flight `Deferred` de-dupe → PNG disk cache in `cacheDir/route_thumbs` (key: id+createdAt+styleHash+size, so light/dark coexist; `pruneExcept` on screen visit survives snackbar Undo) → `Semaphore(2)`-capped snapshotter with 10 s timeout. Old Canvas glyph kept as loading placeholder + offline fallback. No Room change. Verified: real streets + route on cards, delete→Undo keeps thumb, dark-theme regeneration.
- **Route line hugs the road**: root cause was NOT OSRM (we already send `overview=full&geometries=polyline6`) — MapLibre's default GeoJsonSource options (tolerance 0.375, maxZoom 18) re-simplified the line client-side when overzoomed. Fix: `GeoJsonOptions().withMaxZoom(22).withTolerance(0f)` on ROUTE/FALLBACK sources + zoom-interpolated line width (3→11 px, z10→z19). Verified: line follows the road bend at high zoom.
- **Stops snap to the road**: OSRM's `waypoints[].location` now parsed (lon,lat!) into `Route.snappedWaypoints`; `displayWaypoints()` (MapViewModel file-level, unit-tested) draws snapped markers when they match the request (size guard covers in-flight fetches), raw taps otherwise. Pin now meets the line end. Tests: OSRM fixture (order + malformed-entry skip), helper fallbacks.
- **Finish-zoom fix**: the follow camera ratcheted (z15 floor applied per fix) and kept easing past playback end. Now the floor applies once when follow engages (`followEngaged` flag, re-armed on toggle); later fixes pan-only — user pinch-zoom sticks, no lurch at route end. Verified through a full playback.
- **Pulsing pin removed** (user request; retry in a later UI pass): `RippleAnimator`, ripple source/layer, and consts deleted from MockarrMap; conventions-skill example rewritten. Also moots the audit's "[P2] ripple ignores Remove-animations". Logcat clean (no missing-layer errors).
- **[P1] Dark map style**: `effectiveStyleUrl()` (app/ui/map/MapStyles.kt, unit-tested) swaps the default liberty style for OpenFreeMap `dark` when in dark theme (custom URLs respected); applied at MapScreen + thumbnails. Also fixed latent `flatBuildingMaxZoom` cache never resetting across style reloads, and deleted dead `applyTileStyleUrl`. Verified: theme flip live-reloads basemap both ways.
- **[P1] Settings a11y (Option A — tooltips kept)**: switch rows are now whole-row `toggleable(role=Switch)` merged targets with `contentDescription = "label. description"`; sliders got contentDescription+stateDescription. Lesson (in emulator-verify skill): `ui` dumps show merged Compose semantics as an inert child under an empty-desc focusable row — that IS the correct TalkBack shape, not a bug. Bonus fixes found during verification: `TooltipBox` swallows a `weight()` passed via its modifier — switches had sat mid-screen since the session-7 tooltip change; weight moved to a Spacer (switches now trail properly) + rows got 48 dp min height (touch-target floor).
- **[P2] latestFix recomposition leak**: MapScreen no longer collects `latestFix` at composition for the locate button — read `.value` at click time (the documented pattern).
- **[P2] Adaptivity**: `NavigationSuiteScaffold` (new BOM-managed `material3-adaptive-navigation-suite` dep) replaces Scaffold+NavigationBar — bottom bar on phones, nav rail on tablets. Verified via new `emu.sh resize 2560x1600 320` (rail + map alive); phone landscape keeps the bottom bar BY DESIGN (M3: rail only when height non-compact).
- **App module got its first unit tests** (11: thumbnails helpers, MapStyles, displayWaypoints). Gotcha captured in conventions skill: AGP's built-in Kotlin does NOT auto-substitute `kotlin-test`→`kotlin-test-junit` like the kotlin.jvm core modules — without `testImplementation(libs.kotlin.test.junit)` the tests compile but never run.
- **emu.sh grew `logcat`, `rotate`, `resize`** — all three born from verifier friction this round (it couldn't check logs or orientation); emulator-verify skill updated to match.
- **Playbook reformatted** to headed prose (tables removed) for plaintext readability.
- Feedback item "route menu context for the current action" was praise — no change; deferred by user: pin/pulse revisit in a larger UI pass.

### 2026-08-17 — Session 12 (user feedback round 8)

- **Dark map reworked as a bundled dark liberty** (feedback: hosted dark style was "a disaster" — no 3D, no building names, hard to see). Root cause: OpenFreeMap's `dark` style has only 47 layers — zero fill-extrusion layers, no POI/house-number labels; liberty (111 layers) is their only style with them. Fix: `scripts/make_dark_style.py` fetches liberty and recolors every layer via curated per-category rules (amber motorway hierarchy retained, dark water/landcover, light text on dark halos, extrusion + flat building colors, `raster-brightness-max` for the shaded-relief raster which can't be recolored) → `app/src/main/assets/liberty_dark.json`; `DEFAULT_TILE_STYLE_URL_DARK` now `asset://liberty_dark.json` (custom URLs untouched; `asset://` works in both the MapView and MapSnapshotter, so thumbnails went dark too). Script fails loudly on unknown layers so upstream drift is caught at regeneration. Iteration finding: the `road_area_pattern` pedestrian sprite glared white on piers/plazas — pattern dropped for a flat walkable tone (script supports prop deletion via `None`). Verified: labels/POIs at all zooms, 2D↔3D toggle now visibly works in dark (extrusions vs outlined flat footprints — layer IDs identical to liberty so `applyMapMode` needed no change), route line/markers/playback dot contrast, dark thumbnails, live light↔dark reload both ways.
- **Status-bar overlap fixed** (regression, not an emulator bug): Round 7's `NavigationSuiteScaffold` swap dropped Scaffold's `innerPadding` — the app's ONLY inset handling. Content Box now gets `windowInsetsPadding(WindowInsets.safeDrawing.only(Top))` (suite already insets its own bar/rail and places content clear of the bottom). Verified portrait, landscape, and tablet-rail (`resize 2560x1600`).
- **Overlapping waypoint markers render atomically**: the CircleLayer+SymbolLayer pair drew all circles beneath all numbers (label A landed on circle B). Replaced with ONE SymbolLayer whose icons are per-waypoint bitmaps (circle+stroke+number baked via Canvas at `LocalDensity`, same colors/sizes), `iconAllowOverlap`+`iconIgnorePlacement`, `symbolSortKey` = index so the later stop wins. Verified: two overlapping stops each keep their number inside their own circle.
- **Progress alert feature removed** (user request): settings fields/keys/setters (`SettingsRepository`), ViewModel wrappers, Settings UI block + range consts, service alert channel/locals/`postProgressAlert`/consts all deleted; `onCreate` now `deleteNotificationChannel("progress_alerts")` so upgraded installs shed the stale channel. DataStore keys simply orphan (harmless).
- Emulator note: transient "System UI isn't responding" ANR dialog + a Photon "Connection reset" right after boot — both emulator-environment noise, not app bugs (retry succeeded; logcat clean of dev.mockarr exceptions).

### 2026-08-18 — Session 13 (process: see-it-yourself rule)

- **User directive**: Claude must see what it's emulating visually on its own — never ask Ethan for screenshots. Promoted to CLAUDE.md (reproduce+LOOK before diagnosing reported visual bugs) and the emulator-verify skill trigger (applies to triaging Ethan's reports, not just verifying changes).
- `emu.sh night on|off` added (system dark mode) — the one visual-loop step that previously needed raw adb; skill's dark-theme pass updated to use it. Full cold loop smoke-tested: boot → night on → launch → shot → image read → kill.

### 2026-08-18 — Session 14 (h+min durations, waypoint wait times, held-dialog rework)

- **Hours+minutes durations**: `Formatting.kt` consolidated on one `formatDurationShort(seconds)` ("45 s" / "5 min" / "1 h" / "1 h 15 min", ceil-based); `routeSummaryText` renders through it ("about 2 h 3 min (traffic)") and gained `extraSeconds` so `Route.summaryText` folds waypoint waits into the estimate. `formatTimeRemaining` deleted — callers append " left". **Latent bug fixed deliberately**: old hour branch printed "1 h 60 min left" for 7199 s (per-piece ceil); total-minutes-first yields "2 h left". First `FormattingTest` added (10 cases). detekt note: file-level TooManyFunctions (11/file) forced the consolidation — the file was at 12.
- **Tappable waypoints with wait times** (the big one):
  - `core:model`: new `Waypoint(position, waitSeconds)`; `Route.waypointWaitsSeconds: List<Int>` (empty = none, aligned `legs.size + 1`); new `PlaybackState.Dwelling(progress, remainingSeconds, waitSecondsLeft)`.
  - `core:simulation`: `RouteGeometry` derives `dwellStops` from leg boundaries (guarded: waits AND leg segments must align with geometry, else silently no stops) and zeroes `allowedVertexSpeeds` at dwell vertices — the existing backward braking pass makes the car brake into each stop with no new physics. `SimulationEngine` gains a Dwelling tick branch; countdown lives in a FIELD (`dwellSecondsLeft`) so pause/resume during a dwell doesn't reset it; **dwell time scales with the speed multiplier** (slider = fast-forward); ETA = cruise + remaining dwell everywhere, so the countdown never lies. 7 new engine tests + 4 geometry tests; existing suite passes untouched (empty waits ⇒ identical arrays).
  - Persistence: Room **v3** (`waypointsJson` + `waypointWaitsJson`, nullable ALTER TABLE like v2); save writes snapped waypoints + waits, load restores them — this also fixed the old endpoints-only reconstruction (saved 5-stop routes used to reload as 2 waypoints). Migration verified by installing over an Aug-14 database.
  - App: `UiState.waypoints` is now `List<Waypoint>`; `removeWaypoint`/`setWaypointWait` (wait edits patch the in-hand route via file-level `Route.withWaits` — NO refetch, geometry unchanged); tap detection = first `queryRenderedFeatures` in the repo (`WaypointMarkers.kt`: ±16 dp rect on `WAYPOINT_LAYER`, max SORT_KEY wins = topmost marker); marker bitmaps grew an amber wait badge (badge state is part of the `addImage` key — the cache is name-keyed). New `MapDialogs.kt` hosts `WaypointOptionsDialog` (no wait actions on the destination — "Stay at destination" covers that), `WaypointWaitDialog` (preset chips 1/5/15/30 + custom minutes; **FlowRow** — a plain Row shredded the 4th chip's label one char per line), and `StartChoiceDialog`. Playback card shows "Waiting 1 min · …"; notification appends "· waiting" (dedup key's paused Boolean widened to a 3-state ordinal). Waypoint taps are builder-mode only.
  - detekt headroom moves: MapViewModel was at 25/26 class functions — `currentReal`, `mockedPosition`, `currentTrafficFactor` moved to file level to fit the 3 new mutators; marker helpers moved to `WaypointMarkers.kt` for the same file-level rule.
- **Held-location prompt reworked**: three stacked options ("Start from held location" / "Play route as built" / "Cancel") via a Column in the confirm slot; body copy normalized; sibling "Route from held location?" dialog reworded to match. Scrim-dismiss still cancels.
- Emulator-verified end-to-end (over-install migration incl. pre-existing routes, 2 h 3 min summary, stop dialogs incl. destination variant, wait playback with notification "waiting" + multiplier-scaled countdown, save→kill→load with badge intact, inert markers during playback, all three held-dialog paths). Verifier friction noted: `emu.sh` lacks `force-stop` and a `dumpsys` passthrough — candidates for next tooling round.

### 2026-08-18 — Session 15 (user feedback round 9: camera fit, stop menu, highlight, wait chips, thumbstick)

- **Smart placement fit** (user picked over full removal): the unconditional `LaunchedEffect(routePoints)` bounds-fit in MockarrMap (zoomed in hard after every tap) is DELETED; all automatic framing now flows through the one camera-command channel so nothing races. `CameraCommand` became a sealed interface — `Center` (panTo/search, old behavior), `FitRoute` (saved-route load, full fit), `EnsureVisible` (route fetch while building: no-op when every point is already inside the padded viewport, else pan/zoom-OUT only, zoom clamped to `min(fitZoom, currentZoom)`). Map-side logic lives in new `app/ui/map/CameraCommands.kt` (MockarrMap.kt was at the 11-function file cap).
  - **Verifier-found bug → root cause was overlay-blind padding, not bounds**: the fit placed route extremes 120 raw px from the screen edges, but the map runs edge-to-edge BEHIND the search bar (~310 px) and card+nav stack (~500 px) — a loaded route's far stop sat "in view" underneath the bottom nav. Fix: asymmetric dp-based fit padding (32 side / 120 top / 180 bottom, × density), used identically by the fit and the in-view check (so a just-fitted route is stable). Reproduced + verified fixed on-emulator (all stops framed between the overlays; adding a stop still never zooms in).
- **Waypoint options menu → non-blocking bottom card**: `WaypointOptionsDialog` replaced by `WaypointOptionsCard` in the bottom stack above `RouteCreatorCard` ("Stop N" + wait summary + X; "Set wait"/"Remove wait"/"Remove stop"). Map stays interactive; `onMapTap` is dismiss-first (open menu → a map tap closes it instead of adding a stop; reads `selectedWaypoint.value` at click time), marker tap toggles selection. `WaypointWaitDialog` stays modal on purpose. All waypoint mutators already force-cleared `_selectedWaypoint`, so the card self-dismisses on undo/clear/remove with zero extra wiring.
- **Selected-marker highlight**: marker bitmaps gain a blue selection ring; every variant reserves ring headroom so the bitmap size (= center anchor) never shifts. Icon cache key gains `-sel` (same rule as `-wait`). Draw order: new `RANK_KEY` property feeds `symbolSortKey` (`index`, `+1000` when selected) — `SORT_KEY` stays untouched as the hit-test identity.
- **Wait-time clock chips over markers**: new `WaitChips.kt` renders a pill (hand-drawn clock glyph + text) above every waited stop via a second SymbolLayer (`iconAnchor=bottom`, offset in bitmap px × density — plumbed density into layer setup). Static chips show `formatDurationShort`; during a dwell the chip goes amber with a live m:ss countdown (`formatChipCountdown` — `formatDurationShort` is minute-granular, useless live). Countdown chips mint a new image name per second: a `remember(style)` set tracks live icons and stale ones are `removeImage`d (same-name re-adds show stale bitmaps — marker-badge lore).
  - Engine surface: `DwellStop` + `PlaybackState.Dwelling` gained `waypointIndex` (`-1` default = additive; RouteGeometry already iterated waypoint k — it just carries it through). `MockSessionViewModel.dwell: StateFlow<DwellInfo?>` maps the state flow with `distinctUntilChanged` (≈1 emission/s). Tests: geometry index single+multi-stop ordering, engine index at stop/across pause-resume/start-waypoint.
  - Unit-test gotcha captured: top-level `Color.argb()` initializers crash app-module unit tests (android.jar stubs throw on class load) — chip colors are literal ARGB ints.
- **Virtual thumbstick (hold mode)**: right-center overlay (new `ThumbstickOverlay.kt`), enabled only while Holding and not playing, greyed (α 0.38, gestures off) otherwise. 5 Hz tick loop reads knob state per pass → bearing (`atan2(x,-y)`) + deflection → `nudgeMeters` scales by meters-per-pixel at camera zoom/lat (screen-constant feel; clamp 0.3–1000 m/s) → `GeoMath.destination` → repository.
  - **Zero-leak nudge channel**: repository `MutableSharedFlow<LatLng>(DROP_OLDEST)` + `requestHoldMove` (no-op unless Holding) + `holdMoved` (keeps source, nulls placeName). Both collectors live INSIDE `enterHold`'s holdJob so they die with the hold: move collector pushes the updated fix immediately (keepalive re-pushes it), `collectLatest` settle collector re-resolves elevation + place name 1.5 s after nudging stops (one geocode per burst, verified — banner shows raw coords while dragging, resolved name ~2 s after release). `rememberedPin` needed NO change (read from the live Holding at play start). Purple dot repaints free (driven by `Holding.position`).
  - Keep-in-view: map-side effect keyed on `pinPosition` — when the pin crosses the outer 20% margin, `easeCamera` re-centers (400 ms). Stationary pins never trigger it.
  - `MapViewModel.camera` exposes the idle camera as a PROPERTY (class sits at the 25-function cap). Thumbstick math is file-level + unit-tested (bearing quadrants, zoom-halving doubles meters, both clamps, dt/deflection proportionality) — mind the clamps when writing such tests: at z15–16 the raw speed exceeds the cap, so scaling asserts must use z17+.
- Emulator-verified (verifier agent + follow-up fix pass): all five features PASS incl. dark theme; countdown observed 1:11→1:07 while the other chip stayed static; stick greys during playback. Verifier friction: the app logs no HTTP lines, so geocode cadence had to be verified behaviorally — a debug-loggable OkHttp interceptor would help future verifications.

### 2026-08-30 — Session 21 (thumbstick: stale zoom)

- **The stick's zoom was stale** (`docs/research/thumbstick-rnd.md`): the persisted camera
  was seeded into `lastKnownCamera`, which nothing read (`camera` exposed `cameraSaves`), so a
  cold-start nudge ran at `DEFAULT_NUDGE_ZOOM` (z15) until the first pan; and zoom was only
  sampled on camera *idle*, so it lagged pinches and follow/keep-in-view moves. Now
  `MockarrMap` has an `onCameraMove` callback (MapLibre `addOnCameraMoveListener`, every
  frame) and `MapViewModel.cameraChanged(camera, idle)` feeds `liveCamera` (what `camera`
  exposes, seeded from DataStore) on every frame and `cameraSaves` (debounced DataStore
  write) only on idle. `saveCamera` / `lastKnownCamera` are gone.
- Verified on the emulator: cold start restored a street-level camera; long-press → hold;
  2 s full push moved the pin one street-width (screen-relative pace) with the camera
  following mid-nudge — the old fixed z15 would have thrown it ≈1.3 km. Logcat clean.

### 2026-08-30 — Session 22 (Ethan's five follow-ups: Finish label, banner, undo/redo, dialogs)

- **Finish mid-route says where you are**: `SimulationEngine.stoppedBeforeArrival` (set by
  `stop()` unless the run is already dwelling at the destination); `HoldSource.STOPPED`;
  `MockSessionService.onEngineEnded(stoppedEarly)` holds as STOPPED instead of DESTINATION.
  Strip + notification: "Holding where you stopped" (`strip_holding_stopped`) until the name
  resolves; the notification's literals moved into strings.xml (`notification_holding_pending`).
  `SimulationEngineStopTest` covers stop-early / run-to-end / stop-during-destination-wait.
  Verified: Pause → Finish mid-drive → "Holding where you stopped" (strip + shade), then the
  place name; a route driven to the end still reads "Holding at destination".
  Gotcha: `svc wifi disable` + `svc data disable` freezes the pre-geocode strip text for a
  screenshot (`resolveHoldName` runs once per hold); re-enable after.
- **One "Ready to drive", not two**: `holdingText()` returns null while a hold's name
  resolves, so `MapScreen` latched the previous primary ("Ready to drive") while the
  ungated secondary emitted the same text. Rule in `MapStatCard.kt`: `visibleSecondary()`
  — the second band is never the first band's text (also applied to the latched exit copy).
  `StatCardStripsTest`. Verified with network off (name never resolves): one green under the
  amber hold band, still one after nudges, none doubled on release.
- **Undo / Redo in the route maker**: `BuilderHistory` (`ui/screens/BuilderHistory.kt`, pure,
  50 snapshots) holds before-edit stop lists; a marker drag pushes once at its first frame.
  `MapViewModel` routes every edit through `mutate(refetch, record, transform)` — reset,
  snapshot, patch-or-refetch (wait-only diffs patch via `samePlaces`, an emptied list drops
  the elevation job); undo/redo replay without recording; `clearWaypoints` = `mutate { empty }`;
  history clears on builder close and saved-route load. Fourth pill `ic_redo` (Material
  mirror of undo); `builder_undo_cd` is just "Undo". `BuilderHistoryTest`.
  **Function budget**: `MapViewModel` was already at 25 (the plan's "23" undercounted), so
  `placeFirstStop`/`prependWaypoint` folded into `addWaypoint(point, atStart)` (a placed stop
  always opens the builder; `atStart` inserts the hold origin and leaves it) and the two-line
  `moveWaypoint` inlined into the tap dispatch (`interaction.takeMove()` on every idle tap —
  harmless when nothing is pending). Still 25.
  Verified: undo ×2 / redo ×2 restore stops + line; a drag undoes in one step; trash → Discard
  → undo brings all back; a new stop after undo disables Redo; reopen → both disabled; row of
  four pills centred. Nit seen: the restored third leg took ~1–2 s to redraw once.
- **Dialogs share one primitive**: `MockarrDialog` (`ui/theme/Dialogs.kt`, `DialogAction` in
  its own file — detekt `MatchingDeclarationName`): `BasicAlertDialog` → 16 dp
  `surfaceContainerLowest` card at `popoverElevation`, bold `titleMedium`, body on
  `onSurfaceVariant`, `inset` padding, then outlined **Cancel** + filled pill primary
  (`destructive` → `error`/`onError`). Discard, Wait, Route-from-hold, Save, Rename and Server
  migrated; "Keep editing" (`builder_keep`) gone — Cancel everywhere. DESIGN.md: Elevation
  note + new `### Dialog`. Captures: `docs/design/audit-2026-08/dialog-*-{light,dark}.webp`
  (before; discard/rename/server added this session) and `dialog-*-v2-*.webp` (after).
  Verified all six in light + dark: red Discard, Set/Save disabled on blank, chips wrap,
  keyboard clears the buttons, server Test fits. Logcat clean; mock providers released.

## PLAN COMPLETE — remaining items are the user's

1. **License decision** (GPL-3.0 vs Apache-2.0 vs other) — swap LICENSE, update README/CONTRIBUTING, then the repo can go public.
2. **Physical-device spot-check** (recommended before any release/announcement): install via `./gradlew :app:installDebug`, run the in-app setup checklist, play a route, watch Google Maps follow. Emulator can't show OEM battery-killer quirks.
3. Optional future work (from PLAN.md future ideas): joystick mode, GPX import/export, multi-stop UI polish, favorite places, geocoder search, tag-triggered release workflow with signing.

### 2026-08-30 — Session 23 (tooling audit, stop-list "more" cues, Save pill)

- **Tooling audit (answered from the 13 transcripts on disk, 08-13 → 08-30)**: no marketplace
  plugin is installed for Mockarr — every `installed_plugins.json` entry is scoped to the Oath
  project; our tooling is the project-local skills/agents/hooks. Usage: `build-fixer` 22×,
  `emulator-verifier` 9×, `kotlin-conventions` 8×, `/simplify` 4×, `emulator-verify` 3×,
  `impeccable` 1× (+3 `/impeccable`), `progress-audit` 1×, `/five` **0×**. Actions: removed
  the two Impeccable hooks from the git-ignored `settings.local.json` (its detector matches
  only `.tsx/.css/.html`, so on Kotlin it cost a Node start per edit and a Stop hook for
  nothing, and left 6 Node crash traces when the ignored `scripts/` were missing); retired
  `/five` (`.claude/commands/five.md` + playbook lines). No standing audit — recount in ~2
  months with the same transcript script (count `tool_use` by name / `Skill.skill` /
  `Agent.subagent_type` / `<command-name>`).
- **Stop list "there's more" cues** (`BuilderDetails`): lists longer than 3 stops cap at
  **3.5 rows** (`STOP_LIST_PEEK_ROWS`), the bottom half-row fades to transparent
  (`Modifier.bottomFade`, DstIn alpha mask — background-agnostic, in `MapBuilder.kt`), the
  header counts (`"STOPS · 7"`, `sheet_stops_header` takes `%1$d`) and a `labelSmall`
  caption `"Scroll for N more"` (`sheet_stops_more`, `StopListMoreCaption`) sits under the
  list; N comes from a `derivedStateOf` over `layoutInfo` (last *fully* visible row), so fade
  and caption vanish at the end. ≤3 stops keep the exact fit. Fade is half a row (24 dp) —
  a full-row fade dimmed Stop 3's badge on the first pass.
- **Save is a map pill**: `BuilderTools` is now clear · **save** · reverse · undo · redo
  (`MapPill` with `ic_save.xml`, spinner while naming, disabled without a real route —
  `canSave`/`saving`/`onSave`); the sheet's action row is ✕ · Done only (`BuilderPeek` lost
  `saving`/`onSave`; `builder_save` → `builder_save_cd` "Save route"). DESIGN.md's tools-row
  line updated to five pills.
- Emulator-verified (verifier agent, fresh install, light + dark): 7-stop list shows
  "STOPS · 7", faded half row, "Scroll for 4 more" → scrolled to the end nothing fades; 3-stop
  list exact; save pill dimmed at 0 stops, enabled when routed, opens the Save dialog with the
  suggested name, route lands in Routes; sheet row is ✕ · Done.

### 2026-08-26 — Session 14 (UI pass, Phase 0: audit)

- **Decision**: full professional UI pass, consumer nav-app feel, audit → code directly (no
  mockup step). Ethan researching references on Mobbin before the brief is written.
- **Phase 0 delivered, no source changes**: `docs/design/audit-2026-08.md` (critique 25/40,
  code audit 12/20, findings by theme), 40 light/dark state screenshots as webp in
  `docs/design/audit-2026-08/`, `docs/design/refs/refs.md` stub for the Mobbin collection.
  Published as an artifact for phone review.
- **Top findings**: no visual identity (dynamic-color slate + Google-blue map + green/red pins +
  forest-green launcher); playback HUD lacks hierarchy; landscape playback card covers the map;
  overlay cards stack with equal weight; map gestures have no TalkBack path; all copy inline.
- **Bugs spotted along the way** (not fixed): dark-mode thumbnails cached without theme key
  render route-only; Setup says "All set" beside a red ✗ optional step; Save dialog can produce
  `" LoopTransverse…"` (prefilled name + typed text). Battery-optimisation step unsatisfiable on
  the AVD.
- **emu.sh gaps from the verifier**: no `shell` passthrough; `tapon` substring matching hits
  attribution/hint text; tab taps drift with IME up.
- **Next**: Ethan fills refs.md → `docs/design/brief.md` → Theme/Tokens → surface rounds
  (map HUD → dialogs → saved routes → settings → setup).

### 2026-08-28 — Session 15 (UI pass, Phase 1a: refs + brief)

- **Housekeeping**: removed the macOS audio hooks (`speak_stop.sh`, `speak_notification.sh`,
  `turn_stamp.sh` + their settings.json entries; playbook paragraph dropped).
- **PRODUCT.md** written via `/impeccable init` (enthusiasts first; realism + openness;
  three-tab IA and API 26 floor preserved; name/icon and distribution left undecided).
- **Strava refs**: Ethan dropped 709 Mobbin screenshots (Strava iOS Jul 2026, 600 MB) —
  folder git-ignored. Triaged with 36 indexed contact sheets + 4 agents → 42 curated webps
  in `docs/design/refs/strava/`, logged with whys in `refs/refs.md`.
  Binding constraint: **indigo, not orange**.
- **`/impeccable shape map-hud`** → `docs/design/brief.md` (draft). Ethan's calls: three
  equal stats (no hero numeral), bottom nav hides during playback, restrained colour
  (neutral sheets, indigo accent). Direction: one sheet + status strip replaces the card
  stack; speed as a pill; chrome rules per state; theme-derived map palette.
- **Open for Ethan**: dynamic colour on/off (recommend off); exact indigo seed; whether
  Routes tab adopts the sheet model.
- **Next**: confirm brief → `/impeccable document`-style token pass (Theme.kt + Tokens +
  MapPalette, static indigo scheme, strings.xml) → Map HUD round with before/after
  emulator screenshots.

### 2026-08-28 — Session 15b (UI pass, Round A+B: tokens + Map HUD)

- **Tokens** (`ui/theme/`): static indigo M3 scheme, light + dark authored as sets, dynamic
  colour OFF (brief). `MockarrColors` adds *ready* (teal) and *hold* (amber) roles plus a
  `MapPalette` (ARGB ints) that MockarrMap layers, marker bitmaps, wait chips and route
  thumbnails all read — zero hard-coded map colours remain. `Tokens` holds spacing/radii.
  `MarkerStyle(density, palette)` bundles the bitmap inputs (detekt LongParameterList).
  Thumbnail cache key now includes the palette hash (fixes dark thumbnails showing the
  light overlay). DayNight window theme + `values-night` (no white flash on dark launch).
- **Map layers**: route casing under the line, direction chevrons (`SymbolLayer` along the
  line), palette re-applied in place on theme change.
- **Map HUD rebuilt** (`MapScreen.kt`, new `MapSheet.kt`, `MapPermissions.kt`): one
  `BottomSheetScaffold` sheet replaces the card stack. Status strip (tinted band + handle)
  carries state: Plan a drive / Ready / Driving / Waiting at stop N · m:ss / Holding at X /
  Paused / Not set up (Fix). Peek: planning → Distance/Duration/Stops trio + Play; playing →
  Time left / Distance left / Speed trio, progress bar, Pause·Stop·1× pill. Expanded: stop
  list with numbered discs (start green, destination dark, vias indigo) + per-stop wait
  actions when selected; speed chips 0.25–4×. Snackbars replace the saved/error cards.
  Search results: 48 dp rows, scrollable, IME padding. Follow = toggle FAB during playback.
  Thumbstick only composed while holding.
- **Chrome**: bottom navigation hides during playback (`NavigationSuiteType.None`), sheet
  takes the navigation-bar inset then; search + 3D hide too.
- **Strings**: every Map-surface string in `strings.xml` (tabs, strip, sheet, dialogs).
  Settings/Setup/Routes screens still inline — their rounds.
- **Verified on emulator** (API 34 AVD, light + dark, portrait + landscape): idle, one stop,
  planned, playing, paused, speed chips, holding. Bug found and fixed during verification:
  after a configuration change while playing, `partialExpand()` ran against a 0-height
  peek anchor and the sheet vanished → fallback peek height + gate on measured peek.
- **Known / deferred**: first map tap after a cold launch is sometimes swallowed (pre-
  existing?); expanded-width side panel (sheet is centred/max-width in landscape for now);
  end marker is a dark numbered disc, not a chequered flag; notification layout untouched.
- **Next rounds**: Saved Routes (cards → sheet model, thumbnails), Settings (top app bar,
  icon grid), Setup (top app bar, optional-step glyph), then motion pass + adapt pass.

### 2026-08-28 — Session 15c (re-baseline for the raised ambition + emulator hardening)

- **Direction change recorded, not enacted**: Android first / iOS possible, openness and
  monetisation under review, Strava UX/UI baseline, restructuring expected. Captured in
  `docs/adr/0001-direction-2026-08.md` (new ADR mechanism, `docs/adr/README.md`), and the
  claims marked *under review* in `CLAUDE.md`, `PLAN.md`, `PRODUCT.md`. README and
  CONTRIBUTING left as-is until real decisions land. Firm regardless: no anti-detection,
  no hidden telemetry.
- **Restructure rules** added to CLAUDE.md (ADR before structural change; `core:*`
  Android-free; theme-only colours; Screen/Sheet/Dialogs/ViewModel split; strings.xml).
  `checkCoreBoundary` Gradle task (root `build.gradle.kts`, wired into `check` of
  core:model/simulation/routing) fails on any `android.*`/`androidx.*` import — negative
  test confirmed; configuration-cache safe.
- **Emulator lock-up root-caused and fixed** (`scripts/emu.sh`): `boot` launched a second
  emulator when one was running (→ adb "device offline"), its waits had no deadline, and
  `install` queued silently behind a build agent's Gradle lock. Now: `boot` reuses/recovers
  (180 s deadline), `install` exits 75 "Gradle busy" instead of hanging and refuses without
  an online device, plus `settle` (wait for first frame — fixes the swallowed first tap),
  `tab <Map|Routes|Settings>`, `shell` passthrough, and `find`/`tapon` prefer exact matches.
  emulator-verify skill + verifier agent updated ("one emulator per machine").
- **Answered**: closing the MacBook lid suspends the session, Gradle and the emulator —
  keep it plugged in with `caffeinate -dims` (or the Battery → Options setting) for
  unattended rounds. Suggested commands: `/claude-md-review`, `/progress-audit`,
  `/impeccable document` (DESIGN.md from the new tokens), `/impeccable init` re-run.
- **Next**: wait for Ethan's Strava layout brief → `/impeccable shape <surface>` per screen.
- **CLAUDE.md reviewed and tightened** (`/claude-md-review`): fixed the stale detekt rule
  (file cap of 11 top-level functions is what bites, not the class cap), the commit-footer
  rule (Claude-Session line is required), the positioning contradiction with PRODUCT.md;
  added the ktlint-hook-only-on-Edit gotcha, the one-Gradle-at-a-time rule, the concrete
  `gh run watch` invocation; Modules trimmed to non-obvious facts (+ `core:mocklocation`).
- **DESIGN.md written** (`/impeccable document`, scan mode): "The Quiet Dashboard" north star,
  Night Indigo / Signal Teal / Amber Hold, six named rules, the Action Row and Status Strip
  as signature components; sidecar `.impeccable/design.json` with rendered primitives.
  `/progress-audit` fixed three stale claims (Room schema v3, `MockSessionService` in the
  README, removed hooks noted) and recovered a PROGRESS entry that had been appended in
  `docs/design/refs/strava/` by mistake.

### 2026-08-28 — Session 15d (Brief 2, Map tab as Strava's Record screen)

- **Brief 2** (`docs/design/brief-2-record-and-routes.md`, shape → confirmed): Map tab
  mirrors Strava's Record screen; Routes tab mirrors Saved Routes (next round). Ethan's
  calls: left button = travel-mode picker; route building is a **mode on the same tab**;
  map taps inert outside builder mode; ⋯ overflow on route cards.
- **Record layout**: search bar stays at top; the sheet peek is the stat card (strip +
  Time·Distance·Speed placeholders, or the loaded route's Distance·Duration·Stops) and the
  **Action Row** — Drive (mode picker sheet; Walk/Cycle locked until a custom OSRM server)
  · 64 dp indigo Start · Add route / Edit route. Drag-up shows the **Options** list
  (Follow camera, Stay at destination, Rush-hour traffic, GPS wobble, All settings ›)
  writing the same DataStore settings.
- **Builder mode**: Add route flips the map in place — taps place stops, floating Undo /
  Reverse / ⋯ (Add stop at map centre, Clear all) pills above the sheet, builder trio +
  stop list + Save, ✕ (discard dialog when stops exist) and Done back to Record with the
  route loaded. Search results fly there and open the builder.
- **Code**: `MapSearchViewModel` (search moved out of `MapViewModel`, which was at the
  detekt class cap), `MapOptionsViewModel`, `MapActionRow.kt`, `MapBuilder.kt`;
  `MapViewModel` gained `builderMode`, `reverseWaypoints`, `addWaypointAtCamera`.
  9 new Material glyph drawables; 36 new strings.
- **Verified on emulator** (light + dark): idle Record, mode picker, builder empty/route,
  Done → Route ready, Start → playing (nav hidden), Stop → Holding at destination. Bug
  found and fixed: tool pills were composed inside the holding guard. Verifier lesson (now
  in the skill): map taps during the sheet's re-anchor animation are swallowed — wait ~1 s
  after any mode change; two quick taps on one spot are a MapLibre double-tap zoom.
- **Next**: Routes tab (Saved Routes layout) per brief 2.

### 2026-08-28 — Session 15e (Brief 2, Routes tab as Strava's Saved Routes)

- **Routes tab rebuilt** (`SavedRoutesScreen.kt` + new `SavedRoutesComponents.kt`,
  `SavedRoutesFormatting.kt`): centred "Saved routes" top bar with a sort menu (Most recent /
  Longest first / A to Z), keyword field, filter chips (All ▾ travel mode · Most recent ·
  Longest first), 16 dp cards: thumbnail, two-line title, mode pill · distance · duration,
  place, "Created today / yesterday / Aug 18", ⋯ overflow → Rename dialog / Delete with
  undo. Empty state with a **Plan a drive** CTA that opens the Map tab in builder mode;
  "No routes match" when a filter/search hides everything.
- Names split on the last comma (`splitRouteName`) so titles stop wrapping to three lines
  and the city becomes the place line; `createdWhen` buckets dates. Both unit-tested
  (`SavedRoutesFormattingTest`, 4 tests). `filterAndSort` is a file-level pure function.
- `SavedRouteDao.update` + `SavedRoutesRepository.rename` added (no schema change).
- Verified on emulator light + dark: list, sort menu, overflow, rename dialog, no-match.
  Fixed during verification: the mode tag was a disabled chip (read as broken) → outlined
  read-only pill.
- **Brief 2 complete.** Next candidates: Settings + Setup screens (top app bars, icon grid),
  motion pass (Play transition), adapt pass (expanded widths).
- **Watch item — ANR seen once** on the Routes tab during verification (trace
  `anr_2026-08-28-03-41-05`, unreadable on the AVD), coinciding with a `night on` theme flip
  while every thumbnail was regenerating for the new palette key on a freshly booted
  emulator. Not reproduced in two further attempts (sort change + theme flip; theme flip
  alone). Hypothesis: two `MapSnapshotter` instances created on Main during a configuration
  change on a cold GL context. If it recurs: move snapshotter creation off the first frame
  after recreation, or drop `MAX_CONCURRENT_SNAPSHOTS` to 1.
- **emu.sh fix**: `find`'s exact-match-first rewrite aborted under `pipefail` whenever the
  exact grep missed, so substring matches ("More actions…") silently returned nothing since
  session 15c. `|| true` on both greps; verified `find 'More actions'` → coords.

### 2026-08-28 — Session 15f (Settings + Setup screens)

- **Settings rebuilt** (`SettingsScreen.kt` + new `SettingsComponents.kt`): centred top
  bar; Strava-style **tile grid** (Mock location Ready/Not set up → Setup, Units toggle,
  Travel mode → picker, Routing server → dialog); small-caps sections PLAYBACK / GPS /
  ROUTING / ABOUT with icon rows; sliders as label + trailing value + slider; descriptions
  visible (tooltips removed). **Routing-server dialog restores the custom-OSRM entry** the
  README promises but the screen had lost (URL + live Test + "Use public server").
  About shows the version from PackageManager.
- **Setup rebuilt** (`SetupScreen.kt`): top bar with back arrow (bottom Back button gone);
  readiness strip (teal "All set" / error "N required steps left") replaces the contradictory
  "All set" beside a red ✗; required steps get an error "!" mark and a filled action,
  optional ones a neutral ring and an "Optional" tag.
- All Settings/Setup strings externalised; 9 new glyph drawables.
- Verified on emulator (light + dark): Settings top/scrolled, server dialog, Setup ready
  and with the mock-app step denied ("1 required step left", error mark, filled action).
  The emulator instance from the ANR session had died meanwhile; `boot` recovered cleanly.
- **All four surfaces now follow DESIGN.md.** Next: motion pass (Play transition, strip
  crossfade already in), adapt pass (expanded widths / landscape side panel), notification
  layout, then the a11y follow-ups from the audit (map-gesture equivalents exist; TalkBack
  sweep pending).

### 2026-08-28 — Session 15g (motion pass)

- **One authored moment — Play** (`ui/Motion.kt`): search bar slides up + fades, 3D toggle
  scales out while the Follow toggle crossfades in, the sheet peek fade-throughs from the
  action row to the stat trio, the bottom navigation bar slides down (the sheet follows
  its edge), strip crossfade and camera ease as before; Stop reverses. Builder pills and
  the thumbstick now scale in/out instead of popping.
- **Navigation**: `NavigationSuiteScaffold` replaced by a hand-rolled `AdaptiveNavigation`
  (bar on compact width, rail otherwise) because the suite can only switch layout types
  with no motion. Behaviour otherwise unchanged.
- **Reduce motion** (audit P3): MapLibre camera eases (`CameraCommands`, follow, keep-in-
  view) now cut instead of easing when the system animator scale is 0
  (`rememberSystemAnimationsEnabled`); Compose animations already honour it.
- **emu.sh `record <secs> [out.mp4]`** + an ffmpeg tile recipe in the usage text: motion is
  verified from frame sheets, not guesses. DESIGN.md gained a Motion section.

### 2026-08-28 — Session 15h (Record-tab parity: no nav bar, floating card, Pause split)

- **Ethan's adjustments** (plan approved): match Strava's Record screen exactly. The
  "impeccable options" that fought it were DESIGN.md / `.impeccable/design.json` rules from
  brief 1 (one merged sheet, Flat Over Map, label-over-value quiet numerals) — rewritten,
  not re-run: Card over sheet · Soft Lift (card 4dp / sheet 8dp) · Value over Label (bold
  28sp) · no navigation bar. Indigo-only stays. `docs/design/brief-3-record-parity.md`.
- **Navigation bar removed** (`MockarrApp.kt`): Map is the root; Saved routes / All
  settings are rows at the end of the sheet's drag-up list and push screens with a back
  arrow (`SavedRoutesScreen`, `SettingsScreen` gained `onBack`); `AdaptiveNavigation` and
  `Motion.bottomChrome*` deleted. `OpaqueScreen` pads the navigation inset *inside* the
  Surface (first cut left a strip of map under the gesture bar).
- **Floating stat card** (`MapStatCard.kt`): strip + trio (+ progress while driving) in a
  `surface-container-lowest` card riding `map-edge` above the sheet; strip-only when nothing
  is loaded (no `—` placeholders) and in builder mode (its trio stays in the sheet). The
  speed `1×` chip sits in the strip while driving. `StatusStrip`/`StatTrio`/`DriveProgress`
  moved here; `MapSheet.kt` keeps `PlaybackControls`, `stripFor`, `PlaybackStats`.
- **Sheet peek = action row**, or the **Pause pill** (56dp) that splits into Resume +
  Finish (inverse surface) via fade-through + `animateContentSize`; Finish = Stop. The peek
  block now measures the drag handle and the navigation inset itself (`sheetDragHandle =
  null`, `BottomSheetDefaults.DragHandle` inside the measured Column) — the default handle
  outside the measured block left the row half off-screen.
- **Hold banner never shows coordinates**: `Holding.nameFailed`; `stripFor` returns null
  while the name resolves and `MapScreen` keeps the last strip (`SideEffect`); service
  `resolveHoldName` with a 5 s `withTimeoutOrNull` → "Holding at dropped pin" on failure;
  notification text follows the same rule. Frame sheet from `emu.sh record`: "Plan a
  drive" → "Holding at Amphitheatre Parkway", no coordinate frame.
- **emu.sh `tab`** now means "open that screen": Map = BACK until the action row / Pause
  shows; Routes/Settings = drag the sheet up, `waitfor` the row, tap. Skill doc updated.
- Verified light + dark: idle, builder, loaded, playing, paused, Finish, expanded list,
  Routes/Settings pushed + back, hold. Watch: `tab Map` leaves the sheet expanded if it
  was; map taps then hit the options switches — collapse first (`swipe 540 1030 540 2300`).

### 2026-08-28 — Session 15i (sheet drag, Save discoverability)

- **"Hard to pull up the options" — reproduced and fixed.** `emu.sh record` of a 1.8 s,
  450 px drag: the sheet did not move a pixel until release, then snapped open. Cause: the
  detail column was only composed once `targetValue == Expanded`, so at rest the sheet's
  content was the peek + 16 dp and the Expanded anchor sat 16 dp above the peek — nothing
  to drag towards; flings survived, slow drags felt dead. Fix: details always composed,
  `clipToBounds()` + an opaque peek block replace the old "rows show through the gap"
  guard. Plus a 48 dp tap-to-toggle handle (`SheetHandle`, TalkBack custom action) and
  Strava's expand glyph on the stat card strip (`ic_expand`) when not driving.
  Frame sheet after: the sheet follows the finger from the first frame.
- **Saving routes** was builder-only, behind the drag, as a text button under the stop
  list. Now: **Save** outlined pill in the builder row (✕ · Save · Done) and a **Save
  route** row at the top of the options list whenever a road route is loaded, reading
  **Saved** (disabled, check glyph) once it is; `UiState.routeSaved` is set by
  `saveRoute` and `loadSavedRoute` and cleared in `scheduleRouteFetch` (every edit).
- Verified: slow drag, handle tap, chevron; builder Save → dialog → snackbar; Done →
  "Saved" row; light. Search R&D doc is the next commit (`docs/research/search-rnd.md`).
- **Search R&D** (`docs/research/search-rnd.md`): measured Photon at a ~0.5 s floor per
  request and ~1.2 s keystroke-to-list end to end; found a relevance bug — the anchor
  order (mocked → *real* → camera) ranked Ukiah Starbucks first while the map sat on
  Mountain View; `zoom` matters more than `location_bias_scale`; provider table (Nominatim
  forbids autocomplete, Mapbox/Google display terms rule them out, Stadia is the only
  keyed upgrade path). Recommendation: keep Photon, fix the client (anchor order, 200 ms /
  2 chars, prefix cache, recents, structured rows, dedupe, search-server setting,
  `Geocoder` interface). PRODUCT.md's stale "three-tab IA" line replaced.


### 2026-08-28 — Session 16 (route maker: Strava builder parity)

- **Strava study** from the local Mobbin capture (frames 346–362) + the help centre:
  white shadowed pills for every floating control, `⋯` menu as a caret popover
  (label-left / glyph-right, red destructive row), tap-a-point callout with **Move Point /
  Delete**. Findings in `docs/design/refs/refs.md` (new curated `map-352`, `map-361`).
- **Map chrome** (`ui/theme/MapChrome.kt`): `MapPill` / `MapIconPill` (48 dp white circle,
  `Tokens.floatingElevation`), `MapPopover` (Compose `Popup`, custom position provider that
  centres on a window-pixel anchor, flips below when there is no room, caret stays on the
  anchor; `modal` = focusable + dismiss-on-outside) and `PopoverRow`. FAB stack, builder
  tools (now bottom-centre: `⋯` · reverse · undo) and the builder ✕ use them. Markers get
  a baked shadow (`WaypointMarkers.kt`, bitmap headroom grows with it). The strip's ⤢
  glyph is gone (`ic_expand.xml` deleted); the trailing slot is the speed chip only.
- **Stop popover** (`MapStopPopover.kt`): tapping a marker shows disc + name + wait, then
  *Wait here… / Move stop / Delete stop* (no Wait on the destination). It rides the
  marker: `MarkerTracker` (`ui/map/MarkerTracker.kt`) republishes the selected marker's
  window point on every camera frame → `MapInteraction.selectedMarkerScreen`. Non-modal, so
  a pan keeps it glued and a map tap deselects; Back closes it. The sheet no longer expands
  on marker tap; the stop list keeps Wait / Move / Delete as the TalkBack path.
- **Move stop**: `MapInteraction.beginMove` → strip "Tap the map to move Stop 2 · Cancel"
  → next map tap `MapViewModel.moveWaypoint` (wait kept, route refetched; `movedTo` helper
  + `MovedWaypointTest`). Every stop edit calls `interaction.reset()`.
- **Start splits in place** (`ActionRow(choice = StartChoice)`): with a hold away from the
  route start, Start fade-throughs the row into **Held spot** (primary) · **Route start**
  (inverse) pills, exactly like Pause → Resume/Finish; picking, Back, a map tap or a pan
  restores the row. `StartChoiceDialog` and its strings are gone. `ActionPill` moved from
  MapSheet to MapActionRow and is shared.
- **detekt**: `MapViewModel` hit the 26-function class cap → the transient state
  (selection, marker point, move, start choice) lives in `MapInteraction` (`viewModel.
  interaction`); `OptionsList` & co moved to `MapOptionsList.kt` to keep MapActionRow under
  the file cap. Reminder: a non-focusable `Popup` is invisible to `uiautomator` — verify
  the stop popover by screenshot, not `waitfor`.
- **DESIGN.md**: Soft Lift rule now covers pills, popovers and markers; new Map Pill and
  Popover sections; Action Row documents the Start choice; "three ways in" → two.
- Verified on the emulator, light + dark: pills/menu/popover, Wait → badge, Move (strip +
  relocation + refetch), Delete, popover flips below and rides a pan, map tap dismisses,
  Start split → Back → Route start → Driving, font scale 1.3 on the split row, logcat clean.

### 2026-08-28 — Session 17 (route maker follow-ups)

- **Ethan's review of session 16, all eleven items:** selected marker = the disc lifts to
  `map-selection` (+2 dp, no halo; `contrastInk` picks the legible number ink, tested);
  popover rows 44 dp / narrower; "START THE DRIVE FROM" caption over the two Start pills;
  the stat card is **hidden** for "Plan a drive" / "Building a route" (`StripModel.hidden`,
  animated); action-row buttons 56 / 72 dp (Strava `hud-048`); builder tools are now
  🗑 · reverse · undo (trash asks via `DiscardRouteDialog`, builder stays open;
  `addWaypointAtCamera` and the ⋯ menu are gone); Saved routes lost its chip row and the
  mode filter (sort stays in the app bar); **Save waits for the place name** (spinner in
  the builder Save pill and the options row, `MapViewModel.suggestName()` with a 4 s
  timeout, `SaveRouteDialog` no longer swaps the field); hold pin + playback dot are the
  top map layers; **first-run hint** = one-time `SheetHintPopover` on the sheet handle
  (`sheetHintSeen` in DataStore, read via `awaitLoaded` so existing users never see a
  flash; the handle tap also marks it seen).
- **Thumbstick R&D** (`docs/research/thumbstick-rnd.md`): the stutter was 5 Hz ticks
  (44 px hops, ≈ 42 m at z17) plus a linear curve. Now 20 Hz, 8 % dead zone + squared
  response, 38 dp travel (base 120 / knob 44), 180 px/s, 0.1–300 m/s clamps, knob tints
  indigo → hold-amber with push, base is a shadowed white pill. Dead zone = no fix pushed.
  Tests updated (`ThumbstickHelpersTest`).
- **Policy**: cheap/free tooling over open-source; free non-OSS APIs OK when they
  noticeably help — PRODUCT.md, CLAUDE.md, ADR 0001 addendum.
- Verified on the emulator (fresh `pm clear` install; light + dark; font 1.3): Setup →
  back → hint popover → Got it; idle map without a card; builder empty without a card;
  trash pill → confirm; selected disc recoloured + tight popover; Save spinner → dialog
  with the place name; hold on stop 1 → amber pin over the disc; Start caption; nudge
  moved the pin and re-resolved the strip; Saved routes without chips; logcat clean.
  Gotcha recorded in the skill: `pm clear` wipes runtime permissions — `pm grant`
  location + notifications before holding, or every long-press hits a system dialog.

### 2026-08-29 — Session 18 (route maker, third round)

- **Row higher**: handle block 48 → 36 dp (touch target unchanged), circles top-aligned
  like Strava, row 100 dp so the Start label fits at font 1.3; Start-choice caption centred.
- **"Plan a drive" flash after a hold — fixed**: the card animated out *after* its strip had
  switched to the hidden idle line. `MapScreen` now feeds `StatCard` the last *visible*
  `StripModel` whenever the current one is hidden/null, so enter/exit fades show the real
  previous state. Verified with a 20 fps `emu.sh record` frame dump: Holding → fading
  Holding → gone.
- **Wait visibility**: the chip above a waited marker already existed (`WaitChips`); it now
  sits 24 dp up and the popover row reads "Wait · 5 min" (header shows "Waits 5 min").
- **Drag a stop**: `ui/map/MarkerDragHandler.kt` — a `MapView` touch listener that owns any
  gesture starting on a marker (`waypointIndexAt`): tap → select, drag past the platform
  slop → `MapViewModel.moveStop(settled = false)` per frame, drop → `settled = true` (refetch).
  The map never pans during a marker drag; disabled while playing. Move-mode strip copy:
  "Drag Stop 2 or tap the map to move it".
- **Sheet cap**: the expanded sheet stops at 60 % of the window (details column max =
  60 % − peek), the stop list scrolls inside.
- **Finish** clears the route from the map (`clearWaypoints`); stay-at-destination hold
  unaffected.
- Verified on the emulator (light; dark row unchanged from session 17): row, chip + copy,
  drag (marker followed, map static, route refetched), 7-stop expanded sheet at 60 %,
  Finish → empty map, release recording, font 1.3. Gotcha (again): zsh does not word-split
  `$p` — `for p in "200 700"; tap $p` fails; loop over one coordinate at a time.

### 2026-08-29 — Session 19 (route maker, fourth round — Ethan's session-18 review)

- **Wait only worked at the start stop — fixed** (`MapViewModel.scheduleRouteFetch`): the fetch
  re-applied waits from a `waypoints` snapshot taken *before* the network call, so a wait set
  while a fetch was in flight (the usual case right after dropping a stop) was clobbered in
  `route.waypointWaitsSeconds` while the chip/popover still showed it. Waits now come from
  live state inside `updateAndGet` on both the success and fallback branches. No ViewModel
  test: its seven Hilt deps are concrete classes (Photon, DataStore…) and there is no mock
  library — verified on the emulator instead (dwell at stop 2: "Waiting at stop 2 · 1:10").
- **Wait chip ~60 dp above the disc — fixed**: MapLibre `icon-offset` is in dp (× icon-size),
  not sprite pixels; `MockarrMap` multiplied by density. Lift is now `WAIT_CHIP_LIFT_DP = 18`
  (disc radius 13.5 + gap). The amber wait badge inside the disc is gone (`waitBadge` removed
  from `MapPalette` / both themes).
- **Marker hit box = drawn disc**: `waypointIndexAt` no longer uses `queryRenderedFeatures`
  (which matched the 40 dp icon quad with shadow headroom, ≈ 2.7× the disc). New pure
  `hitWaypoint()` in `ui/map/MarkerHitTest.kt` (radius fill + ring, + grow for the selected
  stop; selected wins overlaps, then higher index) with `MarkerHitTestTest`. `MockarrMap`
  passes one `hitTest` lambda to both `MarkerDragHandler` and the click listener.
- **Route zoom**: `ensureVisible` (round 9's smart fit) never zoomed *in*. It now refits — in
  or out — when a point is out of view **or** the route is a speck (`fit.zoom − camera.zoom >
  1.5`); otherwise the camera still stays put, so nearby taps don't slam the zoom.
- **Holding + route state stacked**: `secondaryStripFor()` in `MapSheet.kt`; `StatCard` takes
  `secondary` and renders it under the primary strip (latched through its exit animation).
  A hold with a route loaded shows amber "Holding at X · Stop" over green "Ready to drive" /
  "Route ready" and the trio.
- **Duration cut-off**: `StatTrio` values autosize 28 → 18 sp (`TextAutoSize.StepBased`,
  same pattern as `ActionPill`); "1 h 10 min" fits at font 1.3 in the Record card and the
  builder peek.
- **Pull tab**: `SheetHandle` draws its own 32×4 dp `outlineVariant` pill — the M3
  `DragHandle` carries ~22 dp of padding and was clipped inside the 36 dp block.
- **Action row**: 64 / 80 dp circles, 28 / 40 dp glyphs, `titleSmall` labels, three slots in
  a centred 320 dp cluster, row 120 dp (108 clipped "Start" at font 1.3). Measured on
  `hud-048` (480 px ≈ 390 pt): Strava ≈ 58 / 68 pt circles with ≈ 28 pt glyphs — ours run
  one step larger because M3 glyphs/labels read smaller at equal circle sizes. DESIGN.md
  numbers (row, Start, 60 % sheet cap) corrected.
- Verified on the emulator (light + dark, font 1.0 + 1.3, fresh install): idle row + pill;
  3-stop route refit on fetch; wait via popover → chip on the disc, no badge, duration 7 →
  12 min; tap 20 dp beside a disc = map tap (added a stop), tap on the disc = popover;
  hold beside stop 2 → stacked card; 4× playback dwelt at stop 2; 35 + 30 min waits →
  "1 h 10 min" fits at 1.3; Saved routes in dark; logcat clean.
- Gotchas: long-pressing *on* a disc opens the popover (the drag handler owns marker
  touches) — hold by long-pressing the map beside it. The popover follows its marker, so a
  tap queued before a camera refit lands on the map and clears the selection. `waitfor`
  returned 1 immediately for a strip that appears later in playback — poll `ui` in a loop.

### 2026-08-29 — Session 20 (route maker, fifth round — Ethan's session-19 review)

- **Stop list capped at 3 rows** (`BuilderDetails`): `StopRow` is a fixed 48 dp (+ 40 dp
  action strip when selected); the list sits in its own `verticalScroll` column capped at
  `3 × 48 (+ 40)` dp nested inside the sheet's detail scroll, and auto-scrolls to the
  selected stop. Constants `STOP_LIST_VISIBLE_ROWS / STOP_ROW_HEIGHT / STOP_ACTIONS_HEIGHT`.
- **Sheet pick = highlight only**: `MapInteraction.select(index, showPopover)` + `popoverHidden`
  flow; the sheet passes `showPopover = false`, a marker tap keeps the default. The popover
  gate in `MapScreen` adds `!popoverHidden`. `MapInteractionTest` covers it.
- **Destination + Stay at destination**: `StopPopover`/`StopRow` take `stayAtDestination`
  (`options.stayAtDestination`). Last stop + Stay on → greyed "Stays at destination" row;
  otherwise the normal Wait row (the destination used to hide it). Because the engine rests
  at the destination (`RouteGeometry.dwellVertices` skips it), `MockSessionService` now times
  a destination wait itself: `onEngineEnded` → `holdAtDestinationFor()` holds as DESTINATION
  for `wait / speedMultiplier` (repository remembers the last multiplier) and then continues
  the pin → release chain — only if that hold is still the live one (`holdJob === timed`).
- **Idle tap places the first stop**: `MapViewModel.placeFirstStop` = builder on + add;
  `onMapTap`'s idle branch calls it. `sheet_idle_body` copy updated.
- **Amber "Held spot" pill** in the start-choice row (`holdContainer` / `onHoldContainer`);
  DESIGN.md's Amber Hold bullet lists it as the one button wearing the colour.
- **Speed chips vs a new drive (pre-existing, fixed on the way)**: `MockSessionViewModel`'s
  multiplier outlived a drive while every new `SimulationEngine` started at 1×, so the chip
  read "4×" over a 1× drive. The service now seeds `initialSpeedMultiplier` from
  `repository.speedMultiplier` (the same value the destination wait is scaled by).
- Stop list follow-ups from the first emulator pass: the auto-scroll only moves when the
  picked row is out of view (it used to push Start off for a visible Stop 2), and the inner
  list scrolls only while the sheet is Expanded — a drag-expand used to spend the gesture on
  the nested list and open it scrolled to the last stop.
- Verified on the emulator (light + dark for the pill; fresh install): idle tap → builder
  with Start placed; 7-stop route → 3-row list scrolling inside the sheet; sheet row pick →
  marker grows, no popover, row actions in the sheet; Destination popover → greyed "Stays at
  destination" with Stay on, "Wait here…" with Stay off; 1-min destination wait at 4× →
  "Holding at destination" → named → released after ≈15 s (provider override removed only
  at release); Held-spot pill amber over the amber Holding strip.
- Gotchas: the stat card hides behind an *expanded* sheet — collapse it before polling strip
  text, or the poll is blind. `for p in "x y"; set -- $p` does not split in zsh either.
  A tap at y≈300 to "dismiss a popover" lands in the search field.
- `/simplify` pass (same session): the destination wait moved **into the engine** —
  `RouteGeometry.dwellVertices` now includes the last waypoint and `SimulationEngine.tick`
  finishes only once no dwell is running, so the last stop gets the real `Dwelling` state
  (countdown in the card, Pause/Resume, live multiplier, ETA consistent with the sheet); the
  service-side timer, its two constants and `destinationWaitSeconds` are gone. The playback
  multiplier has one owner (`MockSessionRepository.speedMultiplier` StateFlow, clamped to the
  engine's range; the ViewModel delegates). `MapInteraction.popoverWaypoint` replaces the
  `popoverHidden` flag. The stop list is a `LazyColumn` (intrinsic row heights, ≈3 rows via
  `Tokens.touchTarget`, `animateScrollToItem` only for off-screen rows). `stopStays()` next to
  `stopName()` is the one home for the "Stays at destination" rule; both surfaces render one
  wait control with `enabled = !stays`. Idle-sheet body copy reverted (the title already says
  "Tap the map to add stops").
- Dwell copy: the last stop's wait reads "Waiting at destination · m:ss" (`strip_waiting_destination`);
  `RouteGeometry.DwellStop.isDestination` → `PlaybackState.Dwelling.isDestination`, built by one
  `dwelling()` helper in the engine.

### 2026-08-30 — Session 21 (Ethan's round: search, pills, marker clock, caption)
- `impeccable` skill: still installed (`.claude/skills/impeccable/`) — the question came up
  because the roster is long; nothing was removed.
- **"Scroll for N more" caption removed** (`StopListMoreCaption`, `sheet_stops_more`); the
  half-row `bottomFade` stays as the only "it scrolls" cue.
- **Marker clock badge** (`WaypointMarkers.drawWaitBadge`): a waited stop wears a 5 dp
  ground-toned clock at its top-right, baked into the disc bitmap inside the existing
  shadow/grow headroom (anchor unchanged; icon name gains `-wait`). `updateWaitChips` now
  emits **one** chip — the amber live countdown above the dwelling stop — so the wait
  *amount* is visible only while it counts down. `drawClockGlyph` is shared by both.
- **"Buttons disappear when a marker is placed" — reproduced as the inverse**: the
  post-placement `EnsureVisible` fit used a fixed 180 dp bottom padding while the real
  bottom stack (sheet peek ≈194 dp + card + builder pills + edges) is ≈350 dp on the
  emulator, so the fit parked stops *under* the pill row (a tap on stop 2 opened the Clear
  dialog). Fix: `MapScreen` computes the overlay height (`peekHeight + card + pills`) and
  reports it via `MapInteraction.setOverlayBottom`; `MockarrMap` passes it to
  `FitPadding` (`CameraCommands.kt`), whose bottom = max(180 dp, overlay + 24 dp
  clearance). Read through `rememberUpdatedState` so a taller overlay never re-runs the
  last fit. Verified: four placements keep every stop above the pills.
- Verified on the emulator (light + dark): badge on Start after "Wait here… → 1 min", no
  chip; Play → amber "0:59" chip over Start while "Waiting at stop 1 · 0:58"; Finish →
  hold → Stop.
- **Search like a nav app** (`search-rnd.md` §6 steps 1–3, scope chosen by Ethan: ranking +
  rows + feel). `PhotonGeocoder`: `zoom` param, structured `GeocodingResult(name, secondary,
  kind, city)` from `osm_key/osm_value`, dedupe by name+city / name+secondary / 50 m,
  serialization errors → `Result.failure`; `PhotonGeocoderTest` (MockWebServer). New
  `core:data` `RecentSearchesStore` (own `recent_searches` DataStore, JSON list of 10).
  `MapSearchLogic.kt` holds the pure half — `searchAnchor` (mocked → camera → real),
  `biasZoom`, `PrefixCache`, `matchingRecents`, `mergeSuggestions` — with
  `MapSearchLogicTest`. `MapSearchViewModel`: 2 chars / 200 ms, local answers (recents +
  cached prefix) render before the network, stale guard, `Status` enum replaces the
  hard-coded English error strings. `MapSearchComponents.kt` (new file; `MapScreen.kt` was
  at the cap): glyph rows, bold match, "Recent · Clear" header, notice line. Five new
  Material glyph drawables.
- **Measured surprise**: the R&D doc's "pass the camera zoom" made things *worse* for
  streets — `zoom=16` sent "25th ave" to Phoenix; 12 is the sweet spot for every baseline
  query (curl matrix in `search-rnd.md` §7). Bias zoom is clamped to 8–12.
- Bug found on the first pass: after a pick, the recents flow re-rendered the cached prefix
  list over the map; `listHidden` (pick/Close → until focus or a keystroke) fixes it.
- Verified on the emulator: "starb" → Mountain View 1.0 mi first; "25th ave" → San Mateo
  14 mi; "oakland" → Oakland CA 28 mi; "goldn gate" → bridge, one row per place; pick →
  list gone, builder on; refocus → "Recent · Clear" with Starbucks; airplane mode → "Search
  failed — check your connection" over the still-selectable recent; dark rows fine.
- Deviations from the plan: items 1–3 went in one commit (the caption removal and the fit
  fix both touched `MapScreen.kt`); Photon `lang` skipped (400 on unsupported codes).
