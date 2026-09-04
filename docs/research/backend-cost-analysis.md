# Backend cost analysis — routing, search, elevation, tiles (2026-09-03)

Companion to ADR 0003. Prices and terms were read from the vendors' own pages on 2026-09-03;
everything with a currency sign drifts, so re-check before acting on a number here.

## What the app needs
- **Routing** with per-step or per-segment timing (`RouteLeg.segmentDurationsSeconds` feeds the
  realism engine). One request per stop edit after a 500 ms debounce.
- **Search**: proximity-biased as-you-type after 200 ms, plus reverse geocoding twice per route and
  once per hold.
- **Elevation**: once per route (≤ 100 sampled points) and once per hold. Feeds the fix altitude only.
- **Tiles**: OpenFreeMap through MapLibre — free, unlimited, attribution shown on the map.
- **Freedom to store route results**: saved routes replay offline from Room.

## Usage model
Per session ≈ 6 autocomplete + 3 reverse + 5 route + 2 elevation = 16 requests; 1.5 sessions per
daily active user.

| Scale | Daily active users | Requests/day | Requests/month | route / search / reverse / elevation per month |
|---|---|---|---|---|
| Launch | 100 | ~2,400 | ~72k | 22k / 27k / 13k / 9k |
| Traction | 1,000 | ~24k | ~720k | 225k / 270k / 135k / 90k |
| Hit | 5,000 | ~120k | ~3.6M | 1.1M / 1.35M / 675k / 450k |

## Google Maps Platform — rejected
| | |
|---|---|
| Map | Maps SDK for Android: free and unlimited on mobile. |
| Routes | Compute Routes Essentials $5/1k after 10k free per month (Pro $10/1k, Enterprise $15/1k). |
| Search | Places Autocomplete $2.83/1k after 10k free; Geocoding $5/1k after 10k free; Text Search Essentials $32/1k. |
| Elevation | $5/1k after 5k free. |
| Cost | Launch ≈ $150/mo · Traction ≈ $2,900/mo · Hit ≈ $14k/mo. |
| Terms | Routes and Places content must be displayed on a Google map, so MapLibre + OpenFreeMap (≈ 2,000 lines in `ui/map/`, the palette system, BYO style URL, the dark style) would have to go. Route coordinates may be cached for at most 30 days — offline saved routes become a violation. Requires Google Play Services. |
| Verdict | Most expensive at every scale, forces a map rewrite, forbids a shipped feature. |

## OSM-data providers (results may be stored; ODbL attribution)
| Provider | Free tier | Paid | Launch / Traction / Hit per month | Notes |
|---|---|---|---|---|
| Public OSRM + Photon demo servers | throttled at the operators' discretion | none | $0 / risky / no | No SLA, no commercial terms ("don't send business clients"). Kept as fallback and as the "Public" setting. |
| **Geoapify** (routing + autocomplete + reverse) | 3,000 credits/day, 5 req/s, commercial allowed with "Powered by Geoapify" | API 10 $59 (10k/day) · API 25 $109 · API 50 $179 · API 100 $299 · API 250 $609 | **$0 / $109–179 / $609** | 1 credit per request (route = waypoints − 1); fixed daily credits, no overage bills. Steps carry time/distance/from_index/to_index; `details=route_details` adds speed. Drive/walk/bicycle + more. |
| OpenRouteService (HeiGIT) | 2,500/day, 40k/month | Starter €20 (20k/day, 500k/month) | $0 (at the cap) / €20–? / ? | Academic non-profit; Pelias geocoding. |
| Stadia Maps (Valhalla + Pelias + elevation) | 200k credits/month, **non-commercial only** | Starter $20 (1M) · Standard $80 (7.5M) · Professional $250 (25M) | $20 / $250 / ~$900 | Route and reverse 20 credits, autocomplete 1. Best routing quality of the group; the recorded alternative. |
| Mapbox | 100k directions/month, 500 search sessions, 100k geocodes | $2/1k directions, $3/1k search sessions, $0.75/1k geocodes | ~$12 / ~$385 / ~$2,000 | Directions is OSRM-shaped (annotations, polyline6) — a near URL swap. Terms on use with a non-Mapbox map could not be confirmed from public documents. |
| GraphHopper | 500 credits/day, **non-commercial only** | Basic €69 (5k/day) · Standard €199 · Premium €479 (50k/day) | €69 / €479 / custom | Best per-interval speed detail; logistics-oriented pricing. |
| HERE / TomTom | HERE ~30k transactions/month; TomTom ~2.5k/day non-tile | HERE ~$0.75/1k; TomTom $0.75–6/1k | ~$30 / ~$500 / ~$2,700 (HERE) | Proprietary data with caching restrictions; not pursued. |
| Self-host OSRM + Photon | — | Hetzner 64 GB dedicated ≈ €46–60/mo (North America + Europe car); planet ≈ 128 GB, €100+ | €50–100 flat | Full control, ops on Ethan. Already supported through Settings → Custom URL; the scale-up path. |

## Elevation
| Source | Terms | Cost |
|---|---|---|
| Open-Meteo (previous) | free for **non-commercial** use under 10k/day; commercial plan $29/mo | $0 → $29/mo once Pro ships |
| **AWS Open Data Terrain Tiles** (Mapzen "Terrarium", chosen) | public S3 bucket, no key, no commercial restriction, no SLA | $0 |
| Stadia elevation | part of the credit pool (commercial plans only) | inside the Stadia plan |
| Google Elevation | Google-map-only terms | $5/1k |

Terrarium: `https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png`, 256×256 8-bit
RGB, `elevation = R*256 + G + B/256 − 32768`. One z12 tile ≈ 10 km, so a route needs 1–4 tiles.

## Decision (ADR 0003)
Geoapify default with the public servers as automatic fallback and "Public" setting; Terrarium tiles
for elevation; key in the app for launch; Cloudflare Worker proxy as phase 2; Stadia as the recorded
alternative; Google out. Switch points: Geoapify wins from zero to ~1,000 daily users; past that a
flat-fee self-hosted server wins on cost.
