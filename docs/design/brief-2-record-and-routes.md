# Brief 2 — Map tab as Strava's Record screen · Routes tab as Strava's Saved Routes

Status: **confirmed by Ethan 2026-08-28** (shape). Build order: Map tab (Record + builder) first, Routes tab next. Map taps inert outside builder mode. Inherits `DESIGN.md` ("The
Quiet Dashboard") and `brief.md` (sheet + strip system, indigo, no hero numeral). References:
Record `hud-029/030/048/657`, builder `map-346/347/348/349`, Saved Routes `list-045/286/511`,
empty `state-285` — all in `docs/design/refs/strava/`.

## 1. Job and audience
Same user as brief 1. Two surfaces: **Map tab** (the drive: set up, start, watch) and
**Routes tab** (find a saved drive and load it). Mode: Operate.

## 2. Outcome and proof
- Map tab reads like Strava's Record screen at a glance: full-bleed map, a stat card with
  a coloured readiness strip, and one bottom **Action Row** — *Mode* · **Play** · *Add Route*.
- Building a route is a **mode of the same tab**, not a screen: touch behaviour and the
  sheet change in place; Done returns to the Record layout with the route loaded.
- Routes tab reads like Strava's Saved Routes: title bar, search, filter chips, thumbnail
  cards; tapping a card loads it into the Map tab in the Record layout.

## 3. Selected direction (inside the established world — no new identity)

### Map tab — Record layout (default)
Chrome, top to bottom:
- **Search bar** full-width at top-left/top (replaces Strava's chevron — Ethan's call);
  `search-field` token, 12dp radius. Below it the **FAB stack** on the right edge:
  3D/2D, locate.
- **Stat card** = the existing sheet peek: status strip (Not set up / Ready / Holding) over
  a **Stat Trio**: *Time · Distance · Speed* — zeros at rest (Strava's `00:00 · -:-- ·
  0.00`), the loaded route's *Distance · Duration · Stops* once a route is loaded. Strip
  copy names the readiness state exactly as Strava's "GPS Acquired" band does.
- **Action Row** (the signature): three equal-width slots, 96dp tall, centred —
  - left **Mode**: 48dp tonal icon-button + label ("Drive" / "Walk" / "Cycle"); tap opens
    a **Mode picker sheet** (Strava `hud-042`): search-less list with the three profiles;
    Walk/Cycle disabled with the hint "Needs a custom routing server (Settings)" until
    `customServerConfigured`.
  - centre **Play**: 64dp filled indigo FAB with the play glyph; label "Start" under it.
    Disabled (38%) until a route is loaded or a hold + one stop exists.
  - right **Add Route**: 48dp tonal icon-button + label; becomes **"Switch Route"** while a
    route is loaded (Strava does the same) — opens the same builder mode on the current
    route.
- **Drag up** (sheet expanded): the options list Strava shows under its row — rows with
  leading icon, title, trailing value/switch: *Follow camera* (switch), *Stay at
  destination* (switch), *Rush-hour traffic* (switch), *Realistic GPS wobble* (switch, value
  "4.5 m"), *Playback speed* (value "1×"), *Settings ›*. These mirror `SettingsScreen`
  toggles; edits write the same DataStore settings.
- **Map touch in Record layout:** taps do **not** place stops (Strava's Record map is
  inert). Long-press still holds. Marker taps still select a stop (opens its row).

### Map tab — Builder mode (same tab, in place)
Entered from Add Route / Switch Route or by tapping a search result. Nothing navigates:
- Chrome swap: search bar stays; FAB stack stays; the action row is replaced by the
  builder sheet; a **Done** pill (filled) and an **X** (tonal icon-button, exits and
  discards new stops only if the user confirms when >0 unsaved stops) sit as the sheet's
  bottom row. Floating **tool pills** above the sheet on the right (Strava `map-348`):
  *Undo*, *Reverse*, *⋯* (Clear all, Set waits…).
- **Map touch:** taps place stops; marker tap selects; drag-to-move a stop is deferred.
- Sheet peek: Mode chip (Drive) + **Stat Trio** *Distance · Duration · Stops* (zeros at
  start), then the stop list with numbered discs (existing `StopRow`), then Save (text) —
  the illustrated empty state "Tap the map to add stops" when no stops (`map-346`).
- **Done** → Record layout with the route loaded: strip "Ready to drive", trio shows the
  route, Play enabled, right slot reads Switch Route. Done with <2 stops keeps the single
  stop as a hold hint like today.
- Playback (Play) and the playing sheet are unchanged from brief 1; Stop returns to the
  Record layout with the route still loaded.

### Routes tab — Saved Routes layout
- **Top app bar** "Saved Routes" (centre-aligned), trailing **sort** icon (Recent / Longest
  / A–Z) — Strava's edit pencil slot.
- **Search field** ("Search by keyword"), then a **filter chip row**: *All ▾* (profile:
  Driving / Walking / Cycling), *Recent*, *Length*. Scrolls horizontally.
- **Cards**, 16dp radius, `surface-container`: 88dp map thumbnail left; **name** (Title,
  max 2 lines); line 2: profile **chip** ("Driving") · distance · duration; line 3:
  location (city from the geocoded name, on-surface-variant); line 4: "Created today /
  Aug 18". Trailing **⋯** overflow → Rename, Delete (undo snackbar). Tap → load into the
  Map tab (Record layout). Long titles truncate — no three-line titles.
- **Empty state** (`state-285`): illustration (route glyph), "No saved routes yet", one
  line, filled CTA **Plan a drive** → Map tab in builder mode.
- No-match search: "No routes match", chips stay.

**Focal moment:** tapping **Play** in the Record layout — the action row and the search
bar slide away, the nav bar hides, the strip turns indigo, the camera eases to follow.

## 4. Scope and boundaries
- Files: `MapScreen.kt` / `MapSheet.kt` / new `MapActionRow.kt`, `MapModePicker.kt`,
  `MapViewModel` (builder-mode state + options), `SavedRoutesScreen.kt` + view model
  (sort/filter, rename), `strings.xml`, `SavedRouteEntity` (no schema change needed —
  profile and createdAt exist).
- Untouched: playback sheet, zero-leak rules, three-tab IA, Settings/Setup screens (their
  own round), notification.
- Anti-goals: no separate builder screen; no orange; no iOS chrome; no hero numeral.
- Deferred: drag-to-move stops, elevation profile, side panel on expanded widths.

## 5. States and ranges
Record: not set up · idle (no route) · route loaded · holding · playing (existing).
Builder: 0 / 1 / 2–10 stops · routing · routing error · unsaved on exit. Mode picker:
public server (Walk/Cycle locked) · custom server. Routes: 0 · 1–50 cards · search no-match ·
rename · delete + undo. Light/dark, landscape, font scale 1.3, TalkBack labels on the row.

## 6. Interaction and layout (intent)
Action row is part of the sheet peek (measured), so the sheet's peek height grows by 96dp
in Record layout; in builder mode the Done/X row takes its place. Mode picker and sort are
modal bottom sheets. Chip row and card list are lazy. All new controls ≥48dp. Every map
gesture keeps an on-sheet equivalent: builder sheet gets **"Add stop at map centre"** in ⋯.

## 7. Constraints and open decisions
- Play FAB colour: Night Indigo (DESIGN.md One Signal Rule) — Strava's orange Start is the
  anti-reference.
- Open: whether the options list under the row should include *Playback speed* (it is also
  in the playing sheet). Recommendation: yes, as a read-only value that opens the chips.
- Open: sort persistence (DataStore) — recommend yes, one key.
