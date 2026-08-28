# Design brief — Map HUD (and the system it sets)

Status: **confirmed by Ethan 2026-08-28** (shape output). Build starts with tokens + Map HUD.
Inputs: `PRODUCT.md`, `audit-2026-08.md`, `refs/refs.md` (Strava iOS, 42 curated shots).
Mode: **Operate** — the user is doing a task (place stops, play, watch) and then leaves.

## 1. Job and audience
An enthusiast opens Mockarr to make their phone "be somewhere else", sets up a drive in
under a minute, presses Play, and switches to another app. They glance back to check
progress, pause/hold, or nudge. The map is the product; the HUD is the thing they glance at.

## 2. Outcome and proof
Success: cold start → driving in < 1 min; the drive looks real in the other app; a glance
at Mockarr tells them where the drive is and lets them act in one tap.
Real evidence to carry: route distance and OSRM duration, live simulated position, stop
count and wait times, speed multiplier, hold location name, mock-setup status.

## 3. Selected direction

**Visual authority:** new world; the current look is the anti-reference (audit #1).

**Colour strategy — Restrained.** Neutral surfaces; **indigo** is the single accent: the
primary action, the route line, the position marker, selected chips, links. State colours
are semantic and separate from the accent: *ready* (green-teal), *waiting/held* (amber),
*error/not set up* (error role). Both themes designed as sets; the map basemap already
swaps (light liberty / bundled dark). Scene: evening on a couch, phone in hand, other app
open — dark mode will be seen at least as often as light; it is designed first, not derived.

**Type:** Material 3 type scale, Roboto (system) — a brand face is not earned on an Operate
surface. Numerals use `displaySmall`/`headlineMedium` with tabular figures; labels
`labelSmall` uppercase-tracked over values (Strava's label-over-value trio).

**Shape & elevation:** one radius family (M3 `large` 16 for sheets, `medium` 12 for cards,
`full` for pills/FABs); tonal elevation, no drop shadows on the map overlays.

**Structural thesis — one sheet, one strip.**
- A single draggable **bottom sheet** replaces the card stack. Snap points: *peek* (status
  strip + primary action), *half* (stats + actions), *full* (route options, stops, save).
- A **status strip** on the sheet's top edge carries state in colour and one line of copy:
  "Mocking not set up — Fix" (error) · "Ready" (ready) · "Driving" (accent) · "Waiting at
  Stop 2 · 4:52" (amber) · "Holding at 25th Avenue" (amber) · "Paused". Event banners
  ("Arrived", "Route saved") ride the strip, never a new card. Transient confirmations use
  **snackbars**.
- **Stats trio, equal weight** (Ethan's call): *time left · distance left · speed* as three
  label-over-value cells; no hero numeral. Progress is a thin bar under the trio.
- **Speed is a pill** ("1×") that opens a chip row (0.25 / 0.5 / 1 / 2 / 4), not a
  permanent slider.
- **Chrome rules by state:** idle/planning show search bar + sport-neutral filter row and
  the FAB stack (layers/3D · locate); playback hides search and the **bottom navigation**
  (Ethan's call) — the sheet owns the bottom edge; Stop returns the nav. FAB stack always
  on the right edge. "Following" becomes a recenter toggle FAB, not a text button.
- **Map language:** route line in accent with direction chevrons; start = green dot, end =
  chequered flag, vias = numbered accent discs; selected stop gets a halo; wait badge is
  an amber pill; position marker = accent disc with white ring + heading cone; hold marker
  = amber. All derived from theme roles, both modes.
- **Idle sheet** is an illustrated empty state ("Tap the map to add stops") — not a hint
  inside a disabled control bar.
- **Stop options** open inside the sheet (half), not as another card.

**Focal moment:** pressing Play — sheet collapses to peek, nav bar slides away, strip turns
accent, camera eases to follow. This is the one choreographed transition.

## 4. Scope and boundaries
- Target: `MapScreen.kt` + `MapDialogs.kt` + `map/*` overlays; tokens in `theme/`.
- Fidelity: production-ready, both themes, phone portrait first; landscape/expanded width
  get a side panel (sheet becomes a right-hand panel ≥ 600 dp wide class).
- Untouched: three-tab IA; playback semantics; zero-leak mock ownership; copy voice.
- Anti-goals: no anti-detection UI; no Strava orange; no photo thumbnails; no social
  affordances; no iOS chrome (wheel pickers, Cancel/Done headers).
- Pulsing pin: allowed back only as the position marker's heading cone breathing — decide
  in the motion pass.

## 5. States and ranges
Not set up · Idle (0 stops) · 1 stop (hold hint) · Planned (2–10 stops, 0.1–200 mi, wait
0–30 min per stop) · Routing (loading) · Routing error · Playing · Waiting at stop · Paused ·
Holding (from pin or after stop) · Arrived (stay at destination on/off) · Saved
confirmation · Delete confirmation · Landscape / expanded width · Font scale 1.3 · TalkBack.

## 6. Interaction and layout (intent)
Sheet drag with M3 `ModalBottomSheet`-like standard sheet (non-modal, map stays live);
snap animation standard easing; strip colour crossfades; camera eases honour animator
duration scale. Every map gesture has an on-sheet equivalent (Add stop at centre, Hold at
centre, stop list with per-stop options, thumbstick with directional custom actions).
Touch targets ≥ 48 dp; results list scrollable with IME padding.

## 7. Constraints and open decisions
- Material 3 with a **static indigo scheme on every API level; dynamic colour OFF**
  (Ethan's decision 2026-08-28): brand indigo everywhere, map palette never fights a
  wallpaper-derived accent.
- Values in `Tokens` object (spacing 4/8/12/16/24, radii, stroke widths, map palette);
  map layer colours read from `MaterialTheme.colorScheme` via a `MapPalette` mapper.
- Strings externalised as part of the same round (P1 in audit).
- Open: seed hue exactly — propose `#3F51B5`-family indigo tuned for contrast on both the
  light liberty and dark basemaps; final hex chosen during token work with contrast checks
  against both basemaps.
- Open: does Routes tab get the sheet model too (route detail = map + sheet, Strava
  map-363)? Recommended yes, in the Routes round.
