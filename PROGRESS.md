# Mockarr — Progress Report

> **Purpose**: complete, self-contained record of project state. If you (human or AI assistant) have lost context, read this file top to bottom plus [PLAN.md](PLAN.md) and you know everything needed to continue. **Keep this file updated at the end of every work session.**

## What this project is

Android app that plays back road routes through Android's built-in mock location developer feature. User picks points on a map → OSRM generates a road route → playback engine drives the device's mocked location along it realistically (visible in Google Maps etc.). Owner: Ethan Jones (GitHub: Ethanjopro). Solo developer, plans to open-source later. Full design: [PLAN.md](PLAN.md). Repo: https://github.com/Ethanjopro/Mockarr (private).

## Milestone status

| Milestone | Status | Notes |
|---|---|---|
| Plan document | ✅ Done | PLAN.md, commit `2c9632c` |
| M0 — Skeleton + CI | ✅ Done | Commits `7f495f8`, `19e7098`, `bed0cc7`. CI green. |
| M1 — Mock location walking skeleton | 🔨 Code written | **Pending: verification on a physical device** (see M1 acceptance in PLAN.md §10). |
| M2 — Map + waypoints + OSRM | ⬜ Not started | |
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

## M1 device verification procedure (the pending step)

1. Physical phone, USB debugging on, plugged in → `./gradlew :app:installDebug`.
2. On phone: Settings → Developer Options → **Select mock location app** → Mockarr (the in-app Setup checklist deep-links there).
3. In Mockarr: Map tab → **Mock here (debug)** → open Google Maps → blue dot should sit at the Eiffel Tower (48.8584, 2.2945).
4. **Stop** in Mockarr → blue dot returns to real location. No crash when Mockarr is NOT selected as mock app (should show friendly error).

## Session log

### 2026-08-13 — Session 1
- Created repo (private) on Ethanjopro account, pushed initial README.
- Researched Google Maps Platform pricing for user (Routes API: 10K free/month then $5/1K; Maps SDK free) — user chose open stack instead. License: decide later.
- Wrote PLAN.md (full architecture/design/milestones) — reviewed by Plan agent, approved by user.
- **M0 complete**: scaffolded everything (see Key decisions). Fixed compileSdk 36→37 and added material-icons-core along the way. Local build + detekt green, both CI runs green (~8 min each). CI actions bumped to checkout@v7/setup-java@v5/setup-gradle@v6/upload-artifact@v7.
- **M1 code written** (this session, see below): AndroidMockLocationController, SetupStatusRepository, real SetupScreen with deep links, debug "Mock here" pin on MapScreen, Hilt DI module, added `androidx.hilt:hilt-navigation-compose:1.4.0`. Device verification NOT yet done — user needs to plug in a phone.

## Next steps (in order)

1. **Verify M1 on a physical device** (procedure above). Nothing else proceeds until the blue dot moves (PLAN.md M1 gate).
2. M2: MapLibre map + tap waypoints + OSRM route fetch + polyline render (PLAN.md §3, §8).
3. M3: simulation engine + foreground playback service (PLAN.md §4, §5) — the flagship.
