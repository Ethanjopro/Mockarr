# Search R&D — bringing place search to industry speed and relevance

Status: research, 2026-08-28 (session 15i). Implementation is the next round; §6 is its plan.
Inputs: `MapSearchViewModel.kt`, `PhotonGeocoder.kt`, `MapSearchBar` (`MapScreen.kt`), Photon
`docs/api-v1.md`, provider docs (fetched 2026-08-28), Strava `hud-042`, Google/Apple Maps.

## 1. Baseline — what the app does today, measured

**Pipeline.** `MapSearchBar` → `MapSearchViewModel.setQuery` (min 3 chars, 350 ms debounce,
cancels in-flight) → `PhotonGeocoder.search(q, bias, limit=6)` over Retrofit/OkHttp with a
250 ms `MinIntervalInterceptor` → `distinctBy { name }` → rows "Name, City, State, Country ·
distance". Bias anchor order: **mocked position → device last-known real location → camera**.
No `zoom`, `bbox`, `lang`, `location_bias_scale`, no recents, no cache, no highlighting.

**Server latency** (`curl`, host machine, 3 runs each, bias 37.42/-122.08):

| query | first result at 3 chars | full query | server time |
|---|---|---|---|
| starbucks | Stanford (3 chars) | Starbucks, Mountain View ✓ | 0.51–0.57 s |
| 25th ave | 25th Avenue, Oakland | West 25th Avenue, San Mateo ✗ (SF one exists) | 0.50–0.53 s |
| oakland | Oak (Morgan Hill) | Oakland ✓ | 0.51 s |
| 1 market st | "1 m, Frankfurt am Main" ✗ | SoFA Market, San Jose ✗ | 0.63–0.71 s |
| goldn gate (typo) | Golden Gate Bridge ✓ | Golden Gate Bridge ✓ | 0.52–0.76 s |

Photon's floor is ~0.5 s per request from this network regardless of query; typo tolerance
is good; house numbers ("1 market st") are weak; prefix queries at 3 chars are mostly noise.

**End-to-end on the emulator** (`emu.sh record`, 10 fps frame sheet, typing "starbucks"):
the list appears ≈1.2 s after the last keystroke (350 ms debounce + ~550 ms server + parse
+ layout). Perceived: "type, wait, list pops in" — nothing happens while typing.

**Relevance bug found.** With the map on Mountain View, results were *Starbucks, Ukiah, CA ·
5.8 mi*, *Starbucks, Ukiah, California · 7.1 mi*, *Willits*, *Lakeport* — the emulator's
stale last-known *real* fix (≈Ukiah) outranked the camera. For a mock-location app the user
almost never cares where the phone physically is; the map viewport is the intent. The
anchor order must become **mocked → camera → real**. The same screenshot shows the
`distinctBy { name }` dedupe missing near-duplicates ("Ukiah, CA" vs "Ukiah, California").

**Bias knobs, measured** (`lat/lon` = SF, `zoom` 12 vs 16, `location_bias_scale` 0.4 vs 0.1):
`zoom=16` pulls "1455 Market St." and street-level hits into the top 3 for "1 market st";
"25th ave" ranks the SF street first at any zoom once the anchor is SF. `zoom` matters more
than `location_bias_scale`; the default (12) is right for a city view, 16 for a street view
— i.e. pass the **camera zoom**.

## 2. What "industry standard" looks like (Google Maps / Apple Maps / Strava)

- **Typeahead from the first character**, results reshuffle on every keystroke; nothing
  blocks on a debounce longer than ~150–250 ms, and stale responses are discarded.
- **Instant local answers**: recents, favourites and previously seen results render at
  0 ms; network results merge in underneath (Google shows recents before you type).
- **Viewport-first ranking** with an explicit "Search this area" escape and the result's
  distance from the viewport centre (or from "you" when following).
- **Structured rows**: primary name, secondary line (street · city), a type glyph (café,
  street, city, address), distance right-aligned, the matched prefix bolded.
- **Keyboard Search** jumps to the best hit and drops a pin; a result tap centres and
  offers the next action (here: "Add stop" / "Hold here").
- **States**: empty (recents), searching (inline spinner, list stays), no match ("No
  places match — try the city name"), offline (recents only, one-line notice).
- **Provider etiquette**: sessions/tokens for billing, attribution text, minimum interval.

## 3. Provider comparison (docs read 2026-08-28)

| provider | key | autocomplete | bias / bbox | POI+address quality | cost | notes |
|---|---|---|---|---|---|---|
| **Photon public** (komoot) | none | yes, built for it | `lat/lon` + `zoom` + `location_bias_scale`, `bbox`, `layer`, `osm_tag`, `lang` | OSM: POIs good, house numbers patchy | free; "reasonable use, extensive usage throttled or banned" | ~0.5 s floor; no SLA |
| **Photon self-hosted** | none | same | same | same | server cost (~€10–20/mo region extract) | latency under our control; fits the OSRM-style "custom server" setting |
| Nominatim (OSM) | none | **forbidden** ("must not implement autocomplete client-side"), 1 req/s | `viewbox` | OSM | free | usable only for submit-on-enter; not a fit |
| Stadia Maps (Pelias) | yes | yes (Autocomplete v1, 20 credits/req) | focus point, bbox, layers, lang | OSM + OpenAddresses + WOF | free tier 200k credits/mo **non-commercial**; $20/mo Starter for commercial | attribution required |
| MapTiler Geocoding | yes | `autocomplete=true` | `proximity`, `bbox`, `language`, `types`, `fuzzyMatch` | OSM | free tier exists (limits on pricing page) | attribution "© MapTiler © OSM" |
| geocode.earth (Pelias) | yes | yes | focus, bbox | OSM + OA + WOF | from $100/mo (150k req) | 2-week trial; open-source discounts |
| Mapbox Search Box | token | suggest/retrieve sessions | `proximity`, `bbox`, `types` | best-in-class POIs | per session; free tier | **Mapbox maps only** per ToS — incompatible with MapLibre/OpenFreeMap |
| Google Places (New) | key | session tokens, 5 predictions | `locationBias`/`locationRestriction` | best | per session; monthly credit | must show Google logo / Google map — incompatible |

Fit with ADR 0001 (no keys today, monetisation open): Photon stays the default. If a key
ever ships, **Stadia** (Pelias, permissive attribution, MapLibre-friendly) is the only
candidate that improves house numbers without map-vendor lock-in. Mapbox and Google are
ruled out by their display terms, not by price.

## 4. Recommendation

Keep Photon; fix the client. Ranked by user-visible impact:

1. **Anchor = mocked → camera → real** (bug). Pass `zoom = camera.zoom` and `bbox` of the
   viewport when the user asks to "search this area"; keep `location_bias_scale` default.
2. **Feel instant**: debounce 200 ms, min 2 chars; a **prefix cache** (results for "star"
   filtered client-side while "starb" is in flight, LRU 32 queries per session); discard
   responses whose query no longer matches the field; keep the previous list visible while
   searching (spinner in the field, not a blank card).
3. **Recents** in DataStore (last 10 selected results + their coordinates); shown on focus
   before typing, matched by prefix while typing; a saved-route name match joins the same
   list with a route glyph ("Saved route").
4. **Structured rows**: name / secondary (street · city — Photon's `street`, `city`,
   `state` fields are already parsed), type glyph from `osm_key`/`osm_value` (amenity →
   POI, highway → street, place → city, house number → address), distance from the
   anchor, matched prefix bold (`AnnotatedString`).
5. **Dedupe** by `(name, city)` and by proximity (<50 m) rather than full display name.
6. **Result actions**: tap → centre + builder mode as now, plus a second-line chip
   "Add stop" · "Hold here" (Strava's dropped-pin sheet, `map-393`).
7. **Photon server URL in Settings** beside the OSRM one (`Routing server` tile pattern),
   with the same live Test; drop `MinIntervalInterceptor` to 100 ms once self-hosted.
8. **`Geocoder` interface** in `core:routing` (`search`, `reverse`) so a keyed provider
   (Stadia) can be added later under its own ADR without touching the UI.

Non-goals: offline search (no local index; recents cover the common case), voice, category
browsing.

## 5. Success criteria for the implementation round

- Cached-prefix results render within one frame; network results < 900 ms P50 on the
  emulator (from ~1.2 s).
- The 5 baseline queries with the map on Mountain View / SF put the nearby hit in the top 3.
- No duplicate rows for the same place; rows never exceed two lines.
- Recents appear on focus; selecting one is a no-network action.
- Unit tests: URL builder (`zoom`, `bbox`, `lang`), anchor priority, prefix-cache filter,
  dedupe, recents merge order.

## 6. Implementation plan (next round)

1. `core:routing`: `Geocoder` interface; `PhotonGeocoder` gains `zoom`, `bbox`, `lang`,
   returns `GeocodingResult(name, secondary, kind, position)`; URL-builder tests.
2. `MapSearchViewModel`: anchor order, 200 ms / 2 chars, prefix cache, stale-response
   guard, recents (`SettingsRepository` or a small `RecentSearchesStore` in `core:data`),
   saved-route matches via `SavedRoutesRepository`.
3. `MapSearchBar` / new `MapSearchComponents.kt`: structured rows, glyphs, highlighting,
   spinner-in-field, empty/offline states, "Search this area" chip when the camera moved
   since the last query.
4. Settings: "Search server" tile + dialog (reuse the routing-server dialog composables).
5. Emulator verification: the 5 queries, recents flow, offline (airplane mode via
   `emu.sh shell svc wifi disable`), light/dark; measure again with `emu.sh record`.
