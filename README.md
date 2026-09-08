# Mockarr

**Drive a fake route through the real world.** Mockarr is an Android app built on Android's built-in **mock location** developer feature: pick points on a map, generate a realistic road route, press Play — and your device's reported location drives that route with human-like acceleration, corner slow-downs, wait times at stops, and GPS-style noise, visible in any app that reads location (including Google Maps).

Stack: **Kotlin · Jetpack Compose · MapLibre · OpenStreetMap · Geoapify (routing + search, with OSRM and Photon as fallback) · AWS Terrain Tiles · OpenFreeMap** — no accounts; one optional API key (`secrets.properties`, see ADR 0003), without which the app uses the public servers. Two commitments hold whatever else changes: **no anti-detection features**, and **nothing phones home without being disclosed in-app**.

> **Source-available.** This repository is public so the code and its history can be read. It is licensed under [PolyForm Strict 1.0.0](LICENSE) (noncommercial use only; no redistribution, no modification). See [License](#license).

| Route planning | Playback | Setup checklist | Settings |
|---|---|---|---|
| ![Route](fastlane/metadata/android/en-US/images/phoneScreenshots/1.png) | ![Playback](fastlane/metadata/android/en-US/images/phoneScreenshots/2.png) | ![Setup](fastlane/metadata/android/en-US/images/phoneScreenshots/3.png) | ![Settings](fastlane/metadata/android/en-US/images/phoneScreenshots/4.png) |

## Features

- **Road-route playback** — tap two or more points; OSRM finds the road route; the simulation engine drives it with per-segment speeds, kinematic braking before turns, and a graceful stop (never a teleport)
- **Realistic GPS** — Gaussian position jitter, plausible accuracy/speed/bearing and terrain elevation (Open-Meteo) on every fix; tune or disable it in Settings
- **Place search** — find and add stops by name (Photon geocoding), ranked around what you're looking at
- **Wait times at stops** — give any stop a dwell; the drive pulls up, waits out the countdown, and moves on — and you can change a coming stop's wait mid-drive
- **Off-road endings** — points beyond the road grid get dotted walking connectors at a believable pace instead of a snap to the nearest asphalt
- **Route builder** — drag to move stops, undo/redo, reverse, and save; start from your real location or from a held spot
- **Pin mode** — long-press the map to hold your location anywhere, with a thumbstick to nudge it; "stay at destination" keeps holding after a drive
- **Background playback** — foreground service with pause/resume/stop from the notification; survives screen-off
- **Saved routes** — replay past routes fully offline
- **Speed control** — 0.25×–4× live during playback from the run box's speed pill (wait timers stay real-time)

## How it works

Android's Developer Options include **"Select mock location app"** — an OS-sanctioned testing facility. Once Mockarr is selected there, it registers platform *test providers* (gps, network, fused) via `LocationManager` and feeds them simulated fixes. Google Play services' fused location provider honors these, so consumer apps follow along. Nothing is patched, hooked, or rooted.

> **Fair use**: mock locations exist for app testing. What you do with a mocked location in third-party apps is your responsibility; some services' terms prohibit location spoofing. Mockarr deliberately contains no anti-detection features.

## Install

- **Google Play** — in preparation (ADR 0002; runbook in `docs/release/play-launch.md`)
- **From source** (below)

## Building

Requirements: JDK 17+ and the Android SDK (Android Studio's bundled versions work fine).

```sh
git clone https://github.com/Ethanjopro/Mockarr.git
cd Mockarr
./gradlew build           # compile + unit tests + lint + detekt
./gradlew :app:installDebug   # install on a connected device/emulator
```

First-run setup on the device: enable Developer Options (tap Build number 7×), then Developer Options → **Select mock location app** → Mockarr. The in-app checklist walks you through it.

## Routing backend

Routing and search go through Geoapify when the build has a `GEOAPIFY_KEY` (git-ignored `secrets.properties`, see `docs/adr/0003-backend-providers.md`), with the public OSRM/Photon servers as automatic fallback. Without a key the app uses the public servers only, which route driving only. There is no in-app server setting.

## Architecture

```
:app                  Compose UI, ViewModels, navigation, MockSessionService, Hilt wiring
:core:model           Shared data types + geo math (pure Kotlin)
:core:simulation      Route playback engine (pure Kotlin, deterministic, fully unit-tested)
:core:routing         RouteProvider abstraction, Geoapify + OSRM clients, Geoapify + Photon geocoders, Terrarium elevation, polyline6 codec, fallback
:core:mocklocation    Mock location providers + setup-status detection
:core:data            Room (saved routes) + DataStore (settings)
```

Key seams are interfaces (`RouteProvider`, `MockLocationController`), so alternative routing backends or mock sinks slot in without touching callers. See [PLAN.md](PLAN.md) for the full design and [PROGRESS.md](PROGRESS.md) for development history.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Issues are welcome — especially device-specific reports (mock location behavior varies by OEM). Pull requests are not accepted: the licence below does not permit derived works, and keeping all copyright in one place keeps a later relicensing possible.

## Attribution

Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors · Routing and search powered by [Geoapify](https://www.geoapify.com), with [OSRM](https://project-osrm.org) and [Photon](https://photon.komoot.io) (komoot) as fallback · Elevation from [Terrain Tiles on AWS](https://registry.opendata.aws/terrain-tiles/) (Mapzen) · Rendering by [MapLibre](https://maplibre.org) · Tiles by [OpenFreeMap](https://openfreemap.org)

## License

**Source-available, not open source.** Mockarr is licensed under the [PolyForm Strict License 1.0.0](LICENSE): you may read, build and run it for noncommercial purposes; you may not distribute it, modify it, or make new works based on it. The reasoning is in [ADR 0004](docs/adr/0004-source-available-licence.md). Copyright © 2026 Ethan Jones.
