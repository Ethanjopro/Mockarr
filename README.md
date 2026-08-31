# Mockarr

**Drive a fake route through the real world.** Mockarr is an Android app built on Android's built-in **mock location** developer feature: pick points on a map, generate a realistic road route, press Play — and your device's reported location drives that route with human-like acceleration, corner slow-downs, wait times at stops, and GPS-style noise, visible in any app that reads location (including Google Maps).

Stack: **Kotlin · Jetpack Compose · MapLibre · OpenStreetMap · OSRM · Photon · Open-Meteo · OpenFreeMap** — no API keys or accounts needed today. Two commitments hold whatever else changes: **no anti-detection features**, and **nothing phones home without being disclosed in-app**.

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
- **Bring your own server** — point Mockarr at a self-hosted OSRM to unlock walking/cycling profiles, and use any MapLibre style URL for tiles

## How it works

Android's Developer Options include **"Select mock location app"** — an OS-sanctioned testing facility. Once Mockarr is selected there, it registers platform *test providers* (gps, network, fused) via `LocationManager` and feeds them simulated fixes. Google Play services' fused location provider honors these, so consumer apps follow along. Nothing is patched, hooked, or rooted.

> **Fair use**: mock locations exist for app testing. What you do with a mocked location in third-party apps is your responsibility; some services' terms prohibit location spoofing. Mockarr deliberately contains no anti-detection features.

## Install

- **From source** (below) — the project is pre-release; a distribution channel is still to be decided

## Building

Requirements: JDK 17+ and the Android SDK (Android Studio's bundled versions work fine).

```sh
git clone https://github.com/Ethanjopro/Mockarr.git
cd Mockarr
./gradlew build           # compile + unit tests + lint + detekt
./gradlew :app:installDebug   # install on a connected device/emulator
```

First-run setup on the device: enable Developer Options (tap Build number 7×), then Developer Options → **Select mock location app** → Mockarr. The in-app checklist walks you through it.

## Self-hosting OSRM (optional)

The public demo server (`router.project-osrm.org`) is rate-limited and driving-only. To run your own:

```sh
wget https://download.geofabrik.de/europe/monaco-latest.osm.pbf   # pick your region
docker run -t -v $PWD:/data ghcr.io/project-osrm/osrm-backend osrm-extract -p /opt/car.lua /data/monaco-latest.osm.pbf
docker run -t -v $PWD:/data ghcr.io/project-osrm/osrm-backend osrm-partition /data/monaco-latest.osrm
docker run -t -v $PWD:/data ghcr.io/project-osrm/osrm-backend osrm-customize /data/monaco-latest.osrm
docker run -t -i -p 5000:5000 -v $PWD:/data ghcr.io/project-osrm/osrm-backend osrm-routed --algorithm mld /data/monaco-latest.osrm
```

Then set `http://<your-host>:5000` in Mockarr's Settings. Build with `foot.lua`/`bicycle.lua` profiles to unlock walking/cycling.

## Architecture

```
:app                  Compose UI, ViewModels, navigation, MockSessionService, Hilt wiring
:core:model           Shared data types + geo math (pure Kotlin)
:core:simulation      Route playback engine (pure Kotlin, deterministic, fully unit-tested)
:core:routing         RouteProvider abstraction, OSRM client, Photon geocoder, Open-Meteo elevation, polyline6 codec, fallback
:core:mocklocation    Mock location providers + setup-status detection
:core:data            Room (saved routes) + DataStore (settings)
```

Key seams are interfaces (`RouteProvider`, `MockLocationController`), so alternative routing backends or mock sinks slot in without touching callers. See [PLAN.md](PLAN.md) for the full design and [PROGRESS.md](PROGRESS.md) for development history.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Issues and PRs welcome — especially device-specific reports (mock location behavior varies by OEM).

## Attribution

Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors · Routing by [OSRM](https://project-osrm.org) · Search by [Photon](https://photon.komoot.io) (komoot) · Elevation by [Open-Meteo](https://open-meteo.com) · Rendering by [MapLibre](https://maplibre.org) · Tiles by [OpenFreeMap](https://openfreemap.org)

## License

**TBD — all rights reserved until a license is chosen.** Picking one is an explicit pre-release task; until then this source is available for reading and building for personal use.
