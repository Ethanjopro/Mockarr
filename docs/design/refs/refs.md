# Design references

Input for `docs/design/brief.md`. Target feel: consumer nav-app, map-first, restrained enough
for a developer tool. **Palette constraint (Ethan): indigo, not Strava orange.**

## Source
Strava iOS, Mobbin capture Jul 2026 — 709 screens in `Strava ios Jul 2026/` (git-ignored,
600 MB, third-party). Triaged 2026-08-28 to 42 curated webps named `<category>-<mobbin index>`.
The curated set was removed from the tree on 2026-09-08 when the repo went public (ADR 0004:
Mobbin captures are third-party material); the table below is the record of what each frame
informed. The frames live locally in the git-ignored folder. Categories: **hud** (Record screen), **map** (map + sheet),
**list** (route cards), **settings**, **state** (empty / error / confirm), **numeral**.

## What to take · what to leave
**Take:** the sheet model (peek → expanded, drag handle, map stays visible); one big numeral
with a tiny label; a status *strip* on top of the stat card that changes colour with state
(amber acquiring → green ready → blue event → yellow stopped); a single saturated primary
action with secondary actions as outlined/tonal; FAB stack on the map's right edge (layers /
3D / locate); route line with direction chevrons; start = green dot, end = chequered flag;
list cards with a map thumbnail on the left and a chip + one metadata line; icon-grid
settings with the current value as a subtitle; section headers in small caps; empty states
with one illustration, one sentence, one CTA; a dark toast for transient success; a
destructive sheet with the destructive action isolated.

**Leave:** orange (→ indigo); social/feed/subscription surfaces; iOS chrome (Cancel/Done
header pairs, wheel pickers, Cupertino switches) → translate to M3 top app bar, dialogs,
Material switches; heatmap/segment/3D-flyover features; photo thumbnails on route cards.

## Curated references

| file | Strava screen | why · what Mockarr surface it informs |
|------|---------------|----------------------------------------|
| hud-029 / hud-030 | Record idle: "Acquiring GPS" → "GPS Acquired" | Status strip over the stat card changes tint with readiness; three zero stats set the layout before anything moves. → Map **idle** and **not-set-up** states: same card, strip reads "Mocking not set up" / "Ready". |
| hud-033 | Recording, map expanded | Compact 3-stat card (time · pace · distance) floats over a live map; one full-width Pause. → **Playing** HUD baseline. |
| hud-032 | Recording, stats expanded | Giant pace numeral, tiny label, distance second, progress bars. → the expanded HUD / "big numeral" mode. |
| hud-034 | "Split 1 complete!" banner | Event banner rides on top of the stat card in a contrasting tint, never a separate card. → **Waiting at stop** and "arrived" moments. |
| hud-037 | Stopped: yellow strip, "Go!", Resume + Finish | Paused state recolours the strip; two actions, primary filled + secondary dark. → **Held / paused** state. |
| hud-042 | Sport picker sheet | Search + favourites row + grouped list over the map. → pattern for any picker over the map (saved routes, profiles). |
| hud-048 | Record sheet expanded | Action row on top, settings list below, in one draggable sheet. → the map bottom sheet's expanded state hosting route options and stop options. |
| hud-657 | Record idle, dark | Dark card on dark basemap; green status still legible. → dark HUD tokens. |
| hud-373 | 3D flyover playback | Three big numerals over the map, scrubber with a **1× speed pill**. → playback speed control as a pill, not a slider. |
| hud-702 / hud-703 | Live Activity widget: Run / Stopped | Same stat trio with a coloured header in a lock-screen widget. → the foreground-service notification layout. |
| map-346 | Route builder empty state | "Tap the map to add points" as an illustrated sheet, not a hint in a control bar. → **idle Map** sheet. |
| map-347 | Route builder zero stats | Distance / Elevation / Est. time as label-over-value trio, Save disabled. → route-creator card layout. |
| map-348 | Route builder with route | Pill tools (⋯ / reverse / undo) on the map edge; stats + Save in the sheet. → **route planned** state. |
| map-349 | Save route sheet | Stats, name field with edit glyph, visibility segmented, two toggles, Cancel/Confirm. → Save dialog → sheet. |
| map-361 | "We can't route to this point" toast | Dark toast at top over the map; sheet unchanged. → routing error state. |
| map-352 | Route builder `⋯` menu | White 16dp card with a **caret** on the pill, label-left/glyph-right rows, hairline dividers, red "Delete all"; white shadowed tool pills `⋯ · sketch · undo · redo` bottom-centre. → `MapPopover` + `MapPill` (session 16). |
| map-361 (raw 361) | Manual-mode tools | Same pill family over a route with vias as small ringed discs; ✕ top-left, FAB stack top-right all white with shadow. → floating-control family. |
| map-363 | Route detail | Map top half, sheet with title, chip, stat line, icon-action row (Save / Offline / Edit / Start). → opening a saved route. |
| map-392 / map-656 | Maps tab (light / dark) | Search bar with sport chip, filter chips, FAB stack, Create Route FAB, peek card. → chrome layout for the Map tab. |
| map-278 | Maps tab with route card + counter | Floating route card + "147 Routes" sheet handle. → peek sheet showing the current route. |
| map-046 | "Use this Route?" dialog | Contextual dialog over the map with two named actions. → "Start from held location?" dialog. |
| map-047 | Route loaded on Record map | Route line with direction chevrons, green start dot, chequered end. → route line + marker language. |
| map-393 / map-395 | Dropped pin sheet · too-far warning | Sheet with address, two pill actions; inline warning callout. → long-press hold sheet. |
| map-314 / map-093 | Map layers / Map types sheets | Thumbnail grid pickers with selected outline. → 2D/3D + style picker. |
| list-045 / list-286 | Saved Routes list | Thumbnail left, name, Easy chip, km · m · time, location, "Created today". → **Saved routes** cards. |
| list-511 | Routes with "Use Route" chip | Per-card primary chip. → Play-from-list affordance. |
| list-544 | Activity card | Stat row above a wide map thumbnail. → alternative card layout if names stay long. |
| settings-058 | Record settings icon grid | Icon + name + current value subtitle. → **Settings** top section. |
| settings-065 | Audio cues settings | Small-caps section headers, toggles, value rows, footnotes. → Settings list styling. |
| settings-611 | Settings list | Value-trailing rows ("Metric"), NEW badges, subtitles. → Settings rows. |
| settings-654 | Appearance picker (dark) | Thumbnail radio rows for theme. → a theme setting, if added. |
| settings-668 | Hidden-radius slider | Value as headline above the slider, tick labels below. → wobble / update-rate sliders. |
| state-285 / state-288 | No results · No segments | One illustration, one line, one CTA. → Saved-routes empty state. |
| state-050 | Location permission dialog | System dialog over a dimmed screen. → Setup permission moments. |
| state-353 | "Route reversed" toast | Dark success toast top-left with green check. → Saved confirmation. |
| state-391 | Delete confirmation sheet | Destructive action isolated in its own sheet row. → delete saved route. |
| numeral-044 | Time-of-day stats screen | One giant numeral, small label. → the expanded HUD's "big number" register. |

**Tap a point (Strava help centre, no Mobbin frame):** a callout over the marker with
**Move Point** and **Delete**; Move = the next map tap relocates the point. → `StopPopover`
(Wait · Move stop · Delete) riding the marker.

## Decisions these refs settle (proposed — confirmed in brief.md)
- **Sheet, not cards.** One draggable bottom sheet (peek / half / full) replaces the card
  stack. Status strip on top of the sheet carries state colour; banners ride on the sheet.
- **Numeral hierarchy.** Playing: one big numeral (remaining time or distance), two small.
- **Speed as a pill** (1×) that opens a chip row, not a permanent slider.
- **Chrome rules.** Search + chips visible in idle/planning; hidden in playback. FAB stack
  right edge always. Bottom nav hides during playback (open decision).
- **Marker language.** Route line with chevrons, green start, chequered end, numbered vias.
- **Motion budget.** Sheet drag/snap, strip colour crossfade, camera ease, marker drop.
  Nothing else animates.
