# ADR 0003 — Backend providers: Geoapify by default, public servers as fallback, Terrarium elevation

Date: 2026-09-03 · Status: **accepted** (Ethan: "go with Geoapify, but if this goes badly get ready
for a rollback" — see Rollback).

## Context
ADR 0002 left the routing/search/elevation provider open until the production rollout. The defaults
(`router.project-osrm.org`, `photon.komoot.io`) are demo instances with no commercial terms and
discretionary throttling; Open-Meteo's free elevation API is non-commercial only. Ethan asked for a
cost comparison, was open to a fixed monthly fee, and asked whether Google Maps Platform should be
used now that paying is acceptable. Full analysis: `docs/research/backend-cost-analysis.md`.

What the code needs from a backend: per-step or per-segment timing for the realism engine
(`RouteLeg.segmentDurationsSeconds`), proximity-biased typeahead search plus reverse geocoding, a
terrain altitude per fix, and the freedom to **store route results indefinitely** (saved routes
replay offline).

Usage model: ≈ 16 requests per session (6 autocomplete, 3 reverse, 5 route, 2 elevation);
100 daily users ≈ 72k requests/month, 1,000 ≈ 720k.

## Decision
1. **Google Maps Platform is rejected.** Its Routes/Places content must be displayed on a Google map
   (MapLibre + OpenFreeMap would have to go), route coordinates may be cached for at most 30 days
   (offline saved routes become a violation), it needs Play Services, and it is the most expensive
   option at every scale (≈ $150/mo at 100 daily users, ≈ $2,900/mo at 1,000).
2. **Geoapify is the default for routing, autocomplete and reverse geocoding**
   (`GeoapifyRouteProvider`, `GeoapifyGeocoder` in `core:routing`). Free plan: 3,000 credits/day,
   5 req/s, commercial use allowed with a "Powered by Geoapify" attribution; paid from $59/mo
   (10k/day) to $299/mo (100k/day), fixed daily credits with no overage billing. OSM data, so
   stored routes are fine. Steps carry `time`/`distance`/`from_index`/`to_index` (+ `speed` with
   `details=route_details`), which the provider spreads across geometry segments. Walking and
   cycling work without a custom server.
3. **The public OSRM/Photon servers stay as the automatic fallback and as the "Public" option.**
   `SwitchingRouteProvider` / `SwitchingGeocoder` try the managed provider and fall through on any
   failure other than "no route"; with no key configured they go straight to the public servers.
   Settings → Routing server offers Mockarr (default) / Public / Custom URL; Custom keeps today's
   behaviour (any OSRM at a URL).
4. **Elevation comes from AWS Open Data Terrain Tiles** ("Terrarium" PNG tiles by Mapzen/AWS,
   `s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png`): free, keyless, no commercial
   restriction, elevation = R×256 + G + B/256 − 32768. `TerrariumElevationProvider` decodes tiles
   with a small pure-Kotlin PNG reader (8-bit RGB/RGBA, non-interlaced) and caches them. Open-Meteo
   is removed.
5. **The Geoapify key ships in the app** (`secrets.properties` → `BuildConfig.GEOAPIFY_KEY`; CI secret
   `GEOAPIFY_KEY`; empty key = public servers only, so clean clones build and run). Abuse exposure
   is quota, not money: a leaked key burns the day's credits and users fall to the fallback. Mitigation:
   dashboard quota alerts, a reserve key, rotation by app update. **Phase 2**, if abuse or a provider
   swap warrants it: a Cloudflare Worker proxy (free tier, 100k req/day) holding the key with per-IP
   limits and Play Integrity, which also allows server-side provider swaps with no app update.
6. **Stadia Maps is the recorded alternative** if Geoapify's routing quality disappoints in closed
   testing: $20/mo Starter (commercial), Valhalla routing, own elevation API; a one-class swap.

Tiles stay on OpenFreeMap (free, unlimited, attribution shown on the map since ADR 0002).

## Prices and terms as checked on 2026-09-03
| Provider | Free | Paid | Notes |
|---|---|---|---|
| Geoapify | 3,000 credits/day, commercial with attribution | $59 / $109 / $179 / $299 / $609 per month for 10k–250k credits/day | 1 credit per request; route = waypoints − 1 |
| OpenRouteService | 2,500/day, 40k/month | from €20/mo (20k/day) | academic; launch scale sits at the cap |
| Stadia Maps | 200k credits/mo, non-commercial only | $20 / $80 / $250 per month | route & reverse 20 credits, autocomplete 1 |
| Mapbox | 100k directions/mo | $2/1k directions, $3/1k search sessions | OSRM-shaped API; non-Mapbox-map terms unconfirmed |
| GraphHopper | 500/day, non-commercial only | €69 / €199 / €479 per month | logistics pricing |
| Google | 10k/5k/1k free events per SKU | Routes $5/1k, Autocomplete $2.83/1k, Elevation $5/1k | Google-map-only, 30-day cache limit |
| Self-host OSRM + Photon | — | ≈ €50–100/mo (Hetzner 64–128 GB) | the scale-up path via the Custom URL setting |
| Open-Meteo | non-commercial, < 10k/day | $29/mo commercial | replaced by Terrarium tiles |

## Rollback
- Runtime: Settings → Routing server → Public; or ship a build with an empty `GEOAPIFY_KEY`.
- Release: the switch is one commit (this ADR, the classes, the binding) — `git revert` restores the
  previous default; saved routes are provider-neutral and survive either way.
- Watch during closed testing: routing failures in the Geoapify dashboard, tester-reported search
  misses, per-step timing visibly worse than OSRM on the Redwood City test drive.

## Consequences
- `core:routing`: `GeoapifyRouteProvider`, `GeoapifyGeocoder`, `TerrariumElevationProvider` + `Png`,
  `BackendMode`, `SwitchingRouteProvider`, `SwitchingGeocoder`; `OpenMeteoElevationClient` deleted;
  `dedupe()` shared from `Geocoder.kt`.
- `core:data`: `MockarrSettings.publicServersOnly` + `backendMode`; `SettingsRepository.setPublicServersOnly`.
- `app`: `BuildConfig.GEOAPIFY_KEY`, `BackendConfig(managedAvailable)`, `RoutingModule` chain,
  Settings → Routing server dialog gains the Mockarr/Public toggle, walking/cycling unlock when the
  managed backend is on, About credits and the privacy page list the new providers.
- CI: `GEOAPIFY_KEY` secret → env; absent = public-only build, still green.
- Superseded parts of ADR 0002 §"Deferred": the backend decision.

## Amendment — 2026-09-03: no user-facing backend setting
Decision 3's Settings → Routing server (Mockarr / Public / Custom URL) is removed. An end user cannot
do anything useful with it, and it advertised a self-hosting path nobody is on. What stays: the
managed provider first, the public OSRM/Photon servers as *automatic* fallback on any failure
(now including "no route" — the public OSRM's `snapping=any` reaches stops Geoapify refuses, and the
off-road stitcher takes it from there), public-only when the build has no key. Consequences:
`BackendMode`, `MockarrSettings.osrmBaseUrl` / `publicServersOnly` / `backendMode` /
`profilesUnlocked()`, the connection test and the dialog are deleted; walking/cycling are available
exactly when the build has a key (`BackendConfig.managedAvailable`). Rollback is build-time only:
ship with an empty `GEOAPIFY_KEY`, or `git revert`. Self-hosting remains the scale-up path in the
cost table, but behind a code change, not a setting.
