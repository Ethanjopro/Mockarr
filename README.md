# Mockarr

An Android app that plays back road routes through Android's built-in **mock location** developer feature. Pick points on a map, generate a realistic road route, press Play — your device's reported location drives the route realistically, visible in apps like Google Maps.

Built on a fully open stack: Kotlin, Jetpack Compose, MapLibre, OpenStreetMap tiles, and OSRM routing. No API keys required.

> Mock locations are an OS-sanctioned Android testing facility (Developer Options → "Select mock location app"). You are responsible for how you use this tool.

## Status

Early development. See [PLAN.md](PLAN.md) for the full project plan and milestone roadmap.

- [x] **M0** — Project skeleton, module structure, CI
- [ ] **M1** — Mock location walking skeleton
- [ ] **M2** — Map, waypoints, OSRM routing
- [ ] **M3** — Simulation engine + playback service
- [ ] **M4** — Persistence + settings
- [ ] **M5** — Hardening + polish
- [ ] **M6** — Open-source readiness

## Building

Requirements: JDK 17+ and the Android SDK (Android Studio's bundled versions work).

```sh
./gradlew build
```

## Module structure

```
:app                  Compose UI, ViewModels, navigation, playback service
:core:model           Shared data types and geo math (pure Kotlin)
:core:simulation      Route playback engine (pure Kotlin, fully unit-testable)
:core:routing         RouteProvider abstraction + OSRM implementation
:core:mocklocation    Android mock location provider plumbing
:core:data            Room database + DataStore settings
```

## License

TBD — all rights reserved until a license is chosen (planned before open-sourcing).
