# Mockarr — Project Plan

An Android app that plays back road routes through Android's OS-sanctioned **mock location** developer facility. Pick points on a map, generate a realistic road route, press Play — the device's reported location drives the route realistically (acceleration, slowing for turns, GPS-like noise), visible in apps like Google Maps.

**Stack decisions (locked):** Kotlin, Jetpack Compose, Material 3, MapLibre + OpenFreeMap tiles (no API keys), OSRM routing (public demo server by default, custom server URL in settings). No Google APIs. License: TBD before open-sourcing.

Items marked **[VERIFY]** should be checked against current API/library state at implementation time.

## 1. Mock location mechanics (core, de-risk first)

### Manifest
- `ACCESS_MOCK_LOCATION` permission declaration (with `tools:ignore="MockLocation,ProtectedPermissions"`) — never granted at runtime, but declaring it is what makes Mockarr appear in Developer Options → "Select mock location app". Keep in main manifest.
- `ACCESS_FINE_LOCATION` (runtime) — required on API 34+ to start a location-type foreground service; also lets the map show real position pre-playback.

### `MockLocationController` (`:core:mocklocation`)
- Interface: `start(): MockStartResult (Ok | NotSelectedAsMockApp | ProviderError)`, `push(fix)`, `stop()`.
- `start()`: for `GPS_PROVIDER`, `NETWORK_PROVIDER`, and (API 31+) `FUSED_PROVIDER`: `addTestProvider(...)` (ProviderProperties overload on 31+), `setTestProviderEnabled(p, true)`. Catch `SecurityException` → NotSelectedAsMockApp (canonical "not selected in Developer Options" signal). Catch `IllegalArgumentException` (OEM quirks); defensively `removeTestProvider` first.
- `push()`: one `Location` per provider per tick with **mandatory fields or the fix is silently dropped**: `latitude/longitude`, `accuracy`, `time = System.currentTimeMillis()`, `elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()`; plus `speed`, `bearing`, `altitude`, and on O+ `bearingAccuracyDegrees` / `speedAccuracyMetersPerSecond` / `verticalAccuracyMeters` (Play services plausibility checks favor complete fixes).
- `stop()`: teleport-free (engine decelerates to 0 first), then per-provider `setTestProviderEnabled(false)` + `removeTestProvider` in individual try/catches. Never leave stale test providers registered — they freeze the device's real location.
- **Play services note:** Google Maps reads the Fused Location Provider, which honors platform mock providers when a mock app is selected. Do NOT use `FusedLocationProviderClient.setMockMode` (pulls in Google Play Services dependency; redundant on current Play services). Keep the controller behind an interface so a Play-services variant could ship as an optional flavor if field reports demand it. **[VERIFY]** current Play services behavior.
- Cadence: 1 Hz default (0.5–5 Hz configurable). Keep pushing the same fix (fresh timestamps) while paused — silent providers time out and consumers fall back to WiFi/cell location.

## 2. Onboarding / setup checklist UX

`SetupChecklistScreen` + `SetupStatusRepository`, re-evaluated on every resume:

| Check | Detection |
|---|---|
| Developer Options on | `Settings.Global.DEVELOPMENT_SETTINGS_ENABLED == 1` |
| Mockarr selected as mock app | Trial `addTestProvider` + immediate remove in try/catch (OEM-proof); cheaper secondary: `AppOpsManager.unsafeCheckOpNoThrow("android:mock_location", ...)` **[VERIFY]** public constant availability |
| Notifications (33+) | `NotificationManagerCompat.areNotificationsEnabled()` |
| Battery exemption (optional) | `PowerManager.isIgnoringBatteryOptimizations` |

Each row deep-links: `ACTION_APPLICATION_DEVELOPMENT_SETTINGS` with illustrated 3-step guide; if Dev Options off entirely, `ACTION_DEVICE_INFO_SETTINGS` + "tap Build number 7×" (try/catch, fallback `ACTION_SETTINGS`); `POST_NOTIFICATIONS` via ActivityResult; battery via `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`. Gate the Play button on the first two checks; slim warning banner otherwise.

## 3. Route generation (`:core:routing`)

- `RouteProvider` interface: `suspend fun route(waypoints, profile): Result<Route>`; profiles DRIVING / WALKING / CYCLING. `Route(points, legs, distanceMeters, durationSeconds)` lives in `:core:model` so the simulation engine never knows OSRM exists. Ship a `StraightLineRouteProvider` fallback (great-circle) for routing failures and the walking-skeleton milestone.
- **OSRM impl**: `GET {baseUrl}/route/v1/{profile}/...` with `overview=full&geometries=polyline6&annotations=distance,duration`. Hand-written polyline6 decoder (~40 lines, unit-tested; no dependency). Per-segment target speed = annotation distance/duration (clamp 0.5–42 m/s; reuse previous speed on zero-duration segments). Public demo server reliably serves driving only — grey out walk/bike unless custom server set **[VERIFY]**.
- **HTTP**: Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization (most contributor-recognizable stack). Interceptors: descriptive `User-Agent: Mockarr/<version> (+repo URL)` (demo-server etiquette), min-1s rate limit, 429/5xx → typed `RouteError.RateLimited/ServerError` with "demo server busy — try again or set your own server" UX. Base URL read from DataStore at request time via URL-rewriting interceptor.

## 4. Simulation engine (`:core:simulation` — pure Kotlin/JVM, zero Android deps)

`SimulationEngine(clock: SimClock, random: Random(seed), params)` exposing `StateFlow<PlaybackState> (Idle|Playing|Paused|Stopping|Finished)` and `Flow<SimulatedFix>`; API: `load(route, speedMultiplier)`, `play/pause/resume`, `stop()` (decelerate-to-zero, teleport-free), `setSpeedMultiplier(0.25x–4x live)`.

- **Precompute on load** (`RouteGeometry`): cumulative haversine distances; per-segment target speeds; **turn caps** — at each vertex, entry speed capped by turn angle (`vTurn = max(2.0, vSeg * (1 − θ/180 × 0.9))`: 90° ≈ half speed, hairpins crawl); **backward braking pass** so approaches obey `v² ≤ vTarget² + 2·decel·d`.
- **Tick loop** (coroutine, injected dispatcher, distance advanced by measured clock delta not nominal tick): approach allowed speed with accel ≈ 2 m/s² / decel ≈ 3 m/s²; interpolate along polyline; low-pass-filtered bearing; **Gaussian jitter** σ ≈ 2–4 m applied to the *reported* position only (no accumulated drift), reported accuracy 3–8 m; emit `SimulatedFix`.
- **Tests**: fake `SimClock` + `runTest` + Turbine; invariants: playback time ≈ OSRM duration ±10% at 1×, speed never exceeds target + ε, no physically impossible jumps, total distance = route length. Seedable RNG for determinism.

## 5. Foreground service (`PlaybackService` in `:app`)

- Manifest: `foregroundServiceType="location"`, permissions `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`. API 34+: needs fine location granted + foreground-eligible start; use `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_LOCATION)`; catch `ForegroundServiceStartNotAllowedException`.
- Service owns the session: holds engine + controller, collects fixes → `push()`, under a `PARTIAL_WAKE_LOCK` during playback (Doze-proof; acceptable for explicit user sessions).
- Notification: ongoing, low-importance channel, route name + progress, Pause/Resume/Stop actions via `PendingIntent.getService` handled in `onStartCommand`.
- UI ↔ service via singleton `PlaybackSessionRepository` (StateFlow of state + latest fix) — no Binder plumbing.
- Teardown: graceful `engine.stop()` with 5 s timeout → `controller.stop()` → `stopForeground(STOP_FOREGROUND_REMOVE)` → release wakelock.

## 6. Architecture & build

**Modules (lean; feature modules deliberately deferred):**
```
:app                  Compose UI, ViewModels, navigation, PlaybackService, Hilt wiring
:core:model           LatLng, Route, SimulatedFix, PlaybackState — pure Kotlin
:core:simulation      engine — pure JVM, the unit-test crown jewel
:core:routing         RouteProvider, OsrmRouteProvider, polyline6, StraightLine fallback
:core:mocklocation    MockLocationController, SetupStatusRepository
:core:data            Room DB, DataStore SettingsRepository
```

- **DI: Hilt** (ecosystem default → lowest contributor onboarding cost); pure modules stay Hilt-free, bindings live in `:app/di`.
- **MVVM**: ViewModel per screen, `StateFlow<UiState>` + sealed events, coroutines/Flow throughout.
- **Navigation Compose** with type-safe `@Serializable` routes; single activity.
- **Map**: MapLibre Android SDK ~11.x wrapped in `AndroidView` composable (check maturity of community maplibre-compose wrapper first **[VERIFY]**). Default style: **OpenFreeMap "liberty"** (`https://tiles.openfreemap.org/styles/liberty`) — free, keyless; do NOT default to raster tile.openstreetmap.org (usage policy). Style URL configurable. OSM attribution rendered — mandatory.
- **Build**: Kotlin 2.x, AGP 8.x, Compose BOM, version catalog (`gradle/libs.versions.toml`), convention plugins in `build-logic/`. minSdk 26, targetSdk current. Libraries: Retrofit 2.11, OkHttp 4.12, kotlinx.serialization 1.7+, Room 2.6+ (KSP), DataStore 1.1, Hilt 2.5x, coroutines 1.9+, Turbine. **[VERIFY]** exact versions at scaffold time.

## 7. Persistence (`:core:data`)

- **Room**: `SavedRouteEntity(id, name, createdAt, profile, distanceM, durationS, encodedPolyline6, annotationsJson)` — store encoded polyline + annotations blob (routes read whole; trivial migrations; offline replay needs no network). `FavoritePlaceEntity(id, name, lat, lon)`. DAOs return `Flow`.
- **Preferences DataStore**: `osrmBaseUrl`, `tileStyleUrl`, `defaultProfile`, `speedMultiplier`, `jitterEnabled`, `jitterSigmaM`, `tickHz`.

## 8. Screens

1. **MapScreen** (start): tap adds numbered waypoint (long-press remove; drag if MapLibre supports **[VERIFY]**, else move-mode). ≥2 points auto-fetch route (500 ms debounce), draw polyline. Bottom sheet: profile chips, stats, speed slider, Play. Playback: live marker, camera-follow toggle, progress, Pause/Stop. Setup-incomplete banner. No search in v1 (future `PlaceProvider`).
2. **SavedRoutesScreen**: list, swipe-to-delete + undo, tap → load; "Save current route" in map bottom sheet.
3. **SettingsScreen**: OSRM URL (+ test-connection button), tile style URL, profile, tick rate, jitter, About/attribution.
4. **SetupScreen**: the §2 checklist; auto-shown on first launch and on Play with failing checks.

## 9. Testing & CI

- Unit tests concentrated in `:core:simulation`/`:core:routing`: geo math fixtures, polyline6 round-trips, braking invariants, playback property tests, `FakeRouteProvider` with canned OSRM JSON.
- Thin instrumented: Room DAOs; emulator smoke test that controller surfaces NotSelectedAsMockApp cleanly.
- **detekt** (with detekt-formatting bundling ktlint — one tool) + compose ruleset.
- GitHub Actions on PR/main: JDK 17, gradle caching, `detekt lintDebug testDebugUnitTest assembleDebug`, upload reports on failure. Later: tag-triggered release APK workflow.

## 10. Milestones (each demoable on a real device)

- **M0 — Skeleton + CI**: modules, catalog, convention plugins, Hilt, nav shell (4 empty screens), detekt, green Actions. ✅ `./gradlew build` passes; app opens to empty map.
- **M1 — Mock location walking skeleton (the de-risker)**: `:core:mocklocation` + SetupScreen + debug "Mock here" button pinning a hardcoded coordinate. ✅ Google Maps on a physical device shows the blue dot at the fake spot; Stop restores reality; unselected-mock-app path shows friendly prompt, no crash. **Nothing proceeds until this passes.**
- **M2 — Map + waypoints + OSRM**: MapLibre + OpenFreeMap, tap waypoints, OSRM fetch with error/rate-limit handling, polyline render, straight-line fallback. ✅ Two taps → drawn road route with sane stats; airplane mode → proper error state.
- **M3 — Simulation engine + playback service (the flagship)**: full engine + unit suite, PlaybackService + notification + wakelock, camera follow, pause/resume/stop, speed multiplier. ✅ 5 km urban route drives realistically in Google Maps (corner slowdowns, ≈ OSRM duration at 1×); survives screen-off and 10 min background; Stop never teleports.
- **M4 — Persistence + settings**: Room routes/places, SavedRoutes + Settings screens wired to DataStore. ✅ Save → force-stop → replay offline; custom OSRM URL works.
- **M5 — Hardening + polish**: first-run flow, battery guidance, OEM/edge cases, process-death recovery, icon, visual polish. ✅ New user goes cold-start → driving in < 1 min from in-app instructions only; stable on Pixel + Samsung across API 26/31/34+.
- **M6 — Open-source readiness**: see §12. ✅ A stranger can clone, build, contribute from repo docs alone; F-Droid metadata validates.

## 11. Risks & mitigations

| Risk | Mitigation |
|---|---|
| OSRM demo down/rate-limited/driving-only | Typed errors + retry UX; custom URL setting; offline replay of saved routes; document docker self-host; straight-line fallback |
| Pre-31: no platform FUSED mocking | Mock gps+network (FLP follows on most devices); document limitation; optional Play-services flavor as escape hatch |
| Google Maps fuses WiFi/cell over mock | Continuous emission (never stale), plausible accuracy/speed/bearing; in-app FAQ (airplane mode + WiFi off); honest docs — unfixable without root |
| OEM service-killing / addTestProvider quirks | Wakelock + battery exemption + dontkillmyapp link; remove-then-add; per-provider try/catch |
| API 34+ FGS prerequisites | Fine-location in setup checklist; catch start exceptions with actionable message |
| Play Store policy gray zone for mock apps | Primary distribution: GitHub Releases + F-Droid; developer-testing framing; Play optional/last |
| Silently dropped fixes | Always set `time` + `elapsedRealtimeNanos`; unit-test the Location mapper |
| Users spoofing third-party apps | First-run disclaimer (OS testing feature, user responsibility); never add anti-detection features |

## 12. Open-source readiness (M6)

- README: hero GIF of route playback, feature list, install (F-Droid badge placeholder + Releases APK), build instructions, self-hosted OSRM guide, architecture diagram (module graph), attribution (OSM, OSRM, MapLibre, OpenFreeMap).
- LICENSE placeholder: "TBD — all rights reserved until chosen" so no implicit license is granted pre-decision; choosing (likely GPL-3.0 vs Apache-2.0 — GPL is F-Droid-friendliest and blocks proprietary forks) is an explicit pre-release task.
- CONTRIBUTING.md (setup, detekt, PR checklist), CODE_OF_CONDUCT.md, `.github/ISSUE_TEMPLATE/` (bug template with device/OS/Play-services fields — mock behavior is device-specific — plus feature template), PR template.
- `fastlane/metadata/android/en-US/` structure (title, descriptions, `phoneScreenshots/`, `changelogs/`) — what F-Droid consumes; reproducible release signing docs.
- Screenshots: map + route, playback, setup checklist, settings.
