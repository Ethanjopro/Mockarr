---
name: Mockarr
description: Drive a fake route through the real world — a map-first Android app with a quiet indigo dashboard.
colors:
  night-indigo: "#3949AB"
  night-indigo-on: "#FFFFFF"
  night-indigo-container: "#DEE0FF"
  night-indigo-container-on: "#0A1B6B"
  night-indigo-dark: "#B9C3FF"
  night-indigo-dark-on: "#0A1B6B"
  night-indigo-dark-container: "#2A3A9F"
  night-indigo-dark-container-on: "#DEE0FF"
  signal-teal: "#1E6B4E"
  signal-teal-container: "#A6F2CD"
  signal-teal-container-on: "#002114"
  signal-teal-dark: "#8BD6B2"
  signal-teal-dark-container: "#0F4F38"
  signal-teal-dark-container-on: "#A6F2CD"
  amber-hold: "#8A5A00"
  amber-hold-container: "#FFDEA8"
  amber-hold-container-on: "#2B1A00"
  amber-hold-dark: "#FFBB58"
  amber-hold-dark-container: "#5F4100"
  amber-hold-dark-container-on: "#FFDEA8"
  error: "#BA1A1A"
  error-container: "#FFDAD6"
  error-container-on: "#410002"
  error-dark: "#FFB4AB"
  error-dark-container: "#93000A"
  error-dark-container-on: "#FFDAD6"
  paper: "#FAF8FF"
  paper-on: "#1A1B21"
  paper-on-variant: "#45464F"
  surface-container-lowest: "#FFFFFF"
  surface-container-low: "#F4F3FA"
  surface-container: "#EEEDF4"
  surface-container-high: "#E8E7EF"
  surface-container-highest: "#E2E1E9"
  outline: "#767680"
  outline-variant: "#C6C5D0"
  asphalt: "#121319"
  asphalt-on: "#E3E1E9"
  asphalt-on-variant: "#C6C5D0"
  asphalt-container-lowest: "#0D0E13"
  asphalt-container-low: "#1A1B21"
  asphalt-container: "#1E1F25"
  asphalt-container-high: "#292A30"
  asphalt-container-highest: "#34343B"
  outline-dark: "#90909A"
  map-route: "#3949AB"
  map-route-casing: "#FFFFFF"
  map-route-fallback: "#B8741A"
  map-position: "#3949AB"
  map-hold-pin: "#E0901E"
  map-stop-start: "#1A7D52"
  map-stop-via: "#3949AB"
  map-stop-end: "#1A1B21"
  map-wait-badge: "#F0A422"
  map-selection: "#5C6BC0"
  map-route-dark: "#9FA8FF"
  map-route-casing-dark: "#121319"
  map-position-dark: "#B9C3FF"
  map-hold-pin-dark: "#FFBB58"
  map-stop-start-dark: "#6FD3A4"
  map-stop-via-dark: "#7A88E6"
  map-stop-end-dark: "#E3E1E9"
  map-selection-dark: "#DEE0FF"
typography:
  display:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "36sp"
    fontWeight: 400
    lineHeight: 1.22
  headline:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "28sp"
    fontWeight: 700
    lineHeight: 1.33
    fontFeature: "tnum"
  title:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: 1.43
    letterSpacing: "0.1sp"
  body:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "0.5sp"
  label:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "11sp"
    fontWeight: 500
    lineHeight: 1.45
    letterSpacing: "0.5sp"
rounded:
  control: "12dp"
  card: "16dp"
  sheet: "28dp"
  full: "9999dp"
spacing:
  space1: "4dp"
  space2: "8dp"
  space3: "12dp"
  space4: "16dp"
  inset: "20dp"
  space6: "24dp"
  space8: "32dp"
  map-edge: "12dp"
  touch-target: "48dp"
  sheet-max-width: "640dp"
components:
  button-primary:
    backgroundColor: "{colors.night-indigo}"
    textColor: "{colors.night-indigo-on}"
    rounded: "{rounded.full}"
    height: "40dp"
    padding: "0 24dp"
  button-outlined:
    backgroundColor: "transparent"
    textColor: "{colors.night-indigo}"
    rounded: "{rounded.full}"
    height: "40dp"
    padding: "0 24dp"
  button-text:
    backgroundColor: "transparent"
    textColor: "{colors.night-indigo}"
    rounded: "{rounded.full}"
    height: "40dp"
    padding: "0 12dp"
  chip-filter:
    backgroundColor: "transparent"
    textColor: "{colors.paper-on-variant}"
    rounded: "{rounded.control}"
    height: "32dp"
    padding: "0 16dp"
  chip-filter-selected:
    backgroundColor: "{colors.surface-container-highest}"
    textColor: "{colors.paper-on}"
    rounded: "{rounded.control}"
    height: "32dp"
    padding: "0 16dp"
  fab-tonal:
    backgroundColor: "{colors.night-indigo-container}"
    textColor: "{colors.night-indigo-container-on}"
    rounded: "{rounded.full}"
    size: "40dp"
  sheet:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.paper-on}"
    rounded: "{rounded.sheet}"
    padding: "0 20dp"
  strip-neutral:
    backgroundColor: "{colors.surface-container-high}"
    textColor: "{colors.paper-on-variant}"
    typography: "{typography.title}"
    height: "40dp"
    padding: "8dp 20dp 0"
  strip-ready:
    backgroundColor: "{colors.signal-teal-container}"
    textColor: "{colors.signal-teal-container-on}"
    typography: "{typography.title}"
    height: "40dp"
    padding: "8dp 20dp 0"
  strip-accent:
    backgroundColor: "{colors.night-indigo-container}"
    textColor: "{colors.night-indigo-container-on}"
    typography: "{typography.title}"
    height: "40dp"
    padding: "8dp 20dp 0"
  strip-hold:
    backgroundColor: "{colors.amber-hold-container}"
    textColor: "{colors.amber-hold-container-on}"
    typography: "{typography.title}"
    height: "40dp"
    padding: "8dp 20dp 0"
  strip-error:
    backgroundColor: "{colors.error-container}"
    textColor: "{colors.error-container-on}"
    typography: "{typography.title}"
    height: "40dp"
    padding: "8dp 20dp 0"
  stat-card:
    backgroundColor: "{colors.surface-container-lowest}"
    textColor: "{colors.paper-on}"
    rounded: "{rounded.card}"
    shadow: "4dp"
    padding: "0"
  button-pill:
    backgroundColor: "{colors.night-indigo}"
    textColor: "{colors.night-indigo-on}"
    rounded: "{rounded.full}"
    height: "56dp"
    padding: "0 24dp"
  card:
    backgroundColor: "{colors.surface-container}"
    textColor: "{colors.paper-on}"
    rounded: "{rounded.card}"
    padding: "12dp"
  search-field:
    backgroundColor: "{colors.surface-container-lowest}"
    textColor: "{colors.paper-on}"
    rounded: "{rounded.control}"
    height: "56dp"
    padding: "0 16dp"
  stop-disc:
    backgroundColor: "{colors.map-stop-via}"
    textColor: "{colors.night-indigo-on}"
    rounded: "{rounded.full}"
    size: "28dp"
  map-pill:
    backgroundColor: "{colors.surface-container-lowest}"
    textColor: "{colors.paper-on}"
    rounded: "{rounded.full}"
    size: "48dp"
    shadow: "6dp"
  popover:
    backgroundColor: "{colors.surface-container-lowest}"
    textColor: "{colors.paper-on}"
    rounded: "{rounded.card}"
    shadow: "8dp"
    padding: "0"
---

# Design System: Mockarr

## Overview

**Creative North Star: "The Quiet Dashboard"**

Mockarr looks like a car's instrument cluster at night: neutral surfaces, one indigo
signal, big bold numerals, and state shown as a tinted band on a card that floats over
the map. The map is the product; every piece of chrome either serves the drive in progress
or recedes. Colour appears where it means something — the route, the position, the primary
action, the state strip — and nowhere else; weight and lift (bold type, soft shadows, a
80dp Start) carry the energy instead.

The system is Material 3 in structure (sheet, chips, snackbars, pills) with a hand-authored
indigo scheme on every API level; dynamic colour is off so the map palette never fights a
wallpaper. Dark is designed as its own set, not an inversion: the evening-on-the-couch
scene is the primary one. **Strava's Record screen is the layout, matched exactly** (brief
3, 2026-08-28): a stat card with a coloured strip floating above a separate sheet whose
peek is the action row; Pause is one full-width pill that splits into End drive + Resume. Only
the palette and the components are Material. Confirmed anti-references: the pre-2026-08
card stack, Strava orange, hero numerals, a bottom navigation bar, iOS wheel pickers and
Cancel/Done headers.

**Key Characteristics:**
- Restrained colour: neutral ground, indigo accent, semantic state tints kept separate.
- Card over sheet: the stat card (strip + trio) floats above the sheet and rides its edge.
- Value-over-label stat cells in equal-weight trios; bold tabular figures; no hero number.
- Soft lift: the card, the sheet and every control floating over the map (white pills,
  popovers, markers) carry a low shadow; nothing inside the sheet does.
- One radius family: 12 / 16 / 28 dp and full pills.
- No navigation bar; chrome recedes during playback (search, 3D toggle, action row hide —
  the card stays).

## Colors

A cool-neutral ground with a single indigo voice; state colours are semantic and never
borrow the accent.

### Primary
- **Night Indigo** (`{colors.night-indigo}` light / `{colors.night-indigo-dark}` dark): the
  only brand colour. Primary buttons, the route line, the position marker, selected chips,
  the "Driving" strip container, links. It is used on well under 10% of any screen.

### Secondary
- **Signal Teal** (`{colors.signal-teal}` / `{colors.signal-teal-dark}`): "mocking works" —
  the Ready strip, the start marker. Never used for buttons.

### Tertiary
- **Amber Hold** (`{colors.amber-hold}` / `{colors.amber-hold-dark}`): "you are parked
  somewhere" — Holding and Waiting strips, the hold pin and its wobble range, and live wait
  chips. Never a button (session 40: one button system).
- **Error** (`{colors.error}` / `{colors.error-dark}`): not-set-up strip, routing failures.

### Neutral
- **Paper** (`{colors.paper}`) and **Asphalt** (`{colors.asphalt}`): the two grounds. Both
  themes carry the full Material surface-container ramp (lowest → highest); sheets sit on
  `surface-container-low`, neutral strips on `surface-container-high`, selected list rows
  on `surface-container-high`, progress tracks on `surface-container-highest`.
- **On-surface / on-surface-variant** (`{colors.paper-on}` / `{colors.paper-on-variant}`
  and the asphalt pair): primary and secondary text. Labels in stat cells and section
  headers are always on-surface-variant.
- **Map palette** (`map-*` tokens): every colour the MapLibre layers, marker bitmaps, wait
  chips and route thumbnails draw with, in both themes. The route line has a casing in the
  ground colour so it reads on any basemap tone.

### Named Rules
**The One Signal Rule.** Indigo is the only colour allowed to mean "act here" or "this is
you". If a second element on the screen also wants attention, it uses a state tint or it
waits.

**The State Is a Band Rule.** Session state (ready / driving / waiting / holding / paused /
error) is communicated by the strip's container colour and one line of copy — never by a
new card, never by an icon alone. Idle prompts ("Plan a route", "Building a route") are not
state: the card is hidden until there is something to report. The band never shows raw coordinates: while a hold's
place name resolves, the previous line stays; if the lookup fails it reads "Holding at
dropped pin".

**The Palette Travels Rule.** Map colours are never literals: they come from `MapPalette`
in `ui/theme/Theme.kt`, and anything that bakes a colour into a bitmap or cache includes
the palette in its key so a theme switch re-renders.

## Typography

**Display Font:** Roboto (system)
**Body Font:** Roboto (system)
**Label/Mono Font:** none — numerals use tabular figures of the same face

**Character:** deliberately neutral. On an Operate surface the type does not perform; the
hierarchy is carried by weight and size steps, by the quiet sentence-case labels under every
value, and by small uppercase captions over sections.

### Hierarchy
- **Display** (400, 36sp): reserved; not used on any current screen.
- **Headline** (700, 28sp, tabular figures): stat values — time left, distance, speed,
  stop count. Always paired with a Label under it. The strip's line and pill labels are
  Title at bold.
- **Title** (`titleMedium`, 16sp at bold): the status strip copy, dialog titles, pill labels
  and list-card titles — the code's actual role everywhere (the 14sp/500 this line used to
  name was never used; corrected in session 41).
- **Body** (400, 16sp / 14sp small): sheet hints, list rows, dialog text. Measure is
  bounded by the sheet inset, never wider than ~60ch.
- **Label** (`labelMedium`, sentence case, on-surface-variant): the word under every stat
  value ("Time left", "Distance"). **Captions** (`labelSmall`, UPPERCASE): section headers
  ("STOPS · 3", "SPEED", "START FROM"), marked as headings for TalkBack. Chip text at 14sp in
  sentence case.

### Named Rules
**The Value Over Label Rule.** A number never appears alone: its label sits directly under
it (Strava's trio), centred, and the three cells of a trio share one baseline. A trio with
nothing to count is not shown — never placeholders for undefined numbers.

**The No Hero Rule.** Stat trios are equal weight. No single numeral is enlarged to
dominate the card.

## Layout

Phone portrait first, one column. The map fills the window edge to edge behind
everything; the status bar inset is applied by the app shell, the navigation-bar inset by
the sheet (and by pushed screens). There is no navigation bar.

**Short windows restructure, they don't stretch** (adapt, session 41). When the window is
under 480dp tall and at least 600dp wide (a phone on its side; `rememberSidePanelWidth()` in
`MapAdaptive.kt`, from the Material window size classes), the search field, card and sheet
become a **start-side panel** (half the width, at most 420dp). The FAB stack and the
thumbstick move onto the open map to its right, and fits, padded centring and the follow
camera add the panel to their start padding, so the drive and the held pin stay in the
clear. Tall windows (every portrait phone, tablets) keep the bottom sheet, capped at
`sheetMaxWidth` and centred. **Pushed screens** (Saved routes, Settings, Setup) are a centred
column of the same maximum width, never 1200dp-wide rows. The overlay and pushed screens pad
the horizontal safe-drawing insets (a side cutout, side 3-button navigation). **Large text:**
the action row and stop rows set minimum heights, not fixed ones, and map markers (stop
numbers, wait chips) scale with the font size up to 1.3×.

- **Stat card** floats `{spacing.map-edge}` above the sheet's top edge and inset from the
  sides, 16dp corners: the status strip on top, the trio (and progress while driving)
  below. It stays through playback and hides behind the sheet when it expands.
- **Bottom sheet** (`{spacing.inset}` horizontal inset, 28dp top corners, drag handle) with
  two states: *peek* — the action row (Mode · Start · Add route), or Pause / End drive + Resume
  while driving, measured at runtime and never assumed — and *expanded* — the same plus a
  scrollable options list — *Save route* first when a road route is loaded (reads *Saved*
  once it is), the drive switches, then *Saved routes ›* and *All settings ›*. The peek is
  the screen's resting state. The sheet is draggable from anywhere on it, the
  handle (36dp of layout, 48dp of touch) toggles peek ↔ expanded — two ways in, because a
  swipe alone is neither discoverable nor accessible. **While driving (Playing, Paused,
  Waiting) the sheet is inert**: swipe is disabled and the detail block measures zero, so
  the peek — Pause, or End drive + Resume — is the whole sheet and a drag never lifts it into
  an empty band. **Peek rhythm:** content sits under the 36dp handle (or, with no handle
  while driving, `{spacing.inset}` under the sheet edge) and ends `space3` above the
  navigation-bar inset, which the sheet applies once for peek and detail together; only
  the three-slot action row has a fixed height — the start-choice pills and the playback
  pills take their natural height. The fully expanded sheet stops at 60% of the scaffold
  **and** `map-edge` under the stat card at its highest lift, so the trio is never eaten.
  The strip carries no expand glyph
  (Ethan, session 16): its trailing slot is the speed chip while driving and nothing else.
- **Top overlays** sit `{spacing.map-edge}` from the edges: the search field full-width,
  then the FAB stack aligned to the right edge (3D toggle, locate / follow), 8dp apart —
  every FAB is a **map pill**. MapLibre's attribution "i" sits bottom-left (`map-edge` in, 8dp
  above the overlay stack — sheet peek, card or pills, whichever is highest), tinted
  `MapPalette.attribution` (onSurfaceVariant at 60 %); the MapLibre logo is off. Discreet, but it
  must stay visible in every state — OpenStreetMap/OpenFreeMap terms, not decoration. The builder's tools row (clear · save · reverse · undo · redo) is five map
  pills bottom-centre, riding the stat card's top edge (Strava `map-352`).
- **Spacing rhythm** is the 4dp grid: 4 / 8 / 12 / 16 / 24 / 32, with 20dp as the sheet and
  card content inset and 12dp as the map-edge gutter.
- **Landscape / compact height**: the sheet keeps its peek and scrolls its detail column;
  Material centres the sheet at its max width, and the floating overlays (stat card,
  builder tools) cap at the same `{spacing.sheet-max-width}` so they never sprawl behind
  the centred sheet. Expanded-width side panel is a planned adaptation, not yet built.
- **Touch targets** are 48dp minimum, including list rows and search results.

## Elevation & Depth

Tonal layering inside the sheet, one soft lift over the map. Surfaces step through the
container ramp (card on `surface-container-lowest`, sheet on `surface-container-low`,
strips on `-high`, selected rows on `-high`, tracks on `-highest`); the map itself is the
lowest layer. Everything that floats over the basemap reads as an object on it: the stat
card (4dp), the sheet (8dp), the white **map pills** (6dp), **popovers** (8dp) and the
marker bitmaps (a baked 3dp blur) — Strava's builder is the reference (`map-352`). Chips,
strips and anything inside the sheet never get a shadow. Material's default elevation is
kept for snackbars; dialogs float at `popoverElevation` on a 16dp card.

### Named Rules
**The Soft Lift Rule.** A shadow means "this floats over the map". The stat card, the
sheet, map pills, popovers and markers cast one — always the low value in `Tokens`
(`cardElevation` / `sheetElevation` / `floatingElevation` / `popoverElevation`). Inside the
sheet, hierarchy comes from container tone and the 28/16/12dp corner, never a shadow.
**In dark**, a near-black shadow on the near-black basemap disappears (1.05:1, audit session
41), so floating objects use `MockarrTheme.colors.floating` — lighter than the sheet
(`#292A30`, M3's tonal lift) — rimmed by a 1dp `floatingOutline` (`#6E7079`, ≥3:1 on the dark
map). Light keeps white plus shadow and no rim.

## Shapes

One radius family, applied by role: **28dp** for the sheet's top corners, **16dp** for
cards, **12dp** for controls (search field, filter chips), and **full pills** for buttons,
FABs, the drag handle and the numbered stop discs. Map markers are circles with a 2.5dp
ring in the ground colour; the selected marker gains an outer ring in `map-selection`.
Direction chevrons on the route are 10dp, 2dp stroke, drawn in the casing colour.
Off-road connectors (a stop the road network can't reach, off-road setting on) and the
straight-line fallback draw as the same dotted `map-route-fallback` line; the road part
of a route stays solid. 3D mode pitches the camera itself (a gentle 30°, `MockarrMap`'s
`ENTER_3D_TILT_DEGREES`) and shows the style's stock building extrusions — the toggle
must read instantly, not only after a manual two-finger tilt; a hand-set tilt is left alone.

## Components

### Buttons
One system (session 40, Ethan: "different button prompts have different colours and are
formatted differently"). Every **labelled** button is a pill from `ui/theme/Pills.kt` —
`Pill` / `OutlinedPill`, or `ActionPill` / `OutlinedActionPill` for an equal share of a
two-up row — and the pills take **no colour parameter**, so no screen can repaint one.
- **Shape:** full pill (`{rounded.full}`), **56dp** tall everywhere (sheet, dialogs,
  builder, Setup, Saved routes), 24dp horizontal padding, Title bold one-line label that
  shrinks (16 → 12sp) rather than wraps, leading 24dp glyph when the verb has one.
- **Filled (indigo):** the one verb that moves you forward on a surface — Pause, Resume,
  Start of route, Done, Save, Set wait, Start drive, Clear route, Plan a drive.
- **Outlined:** the alternative or the way out, always **left** of the filled one — Cancel,
  End drive, Held spot / My location, Forget it, optional Setup steps.
- **Text:** indigo text, no container — tertiary actions inside rows and lists (Set wait ·
  Remove wait · Move stop, Clear history). The strip's action (Set up, Stop holding, Skip
  wait, Resume drive) is a text button in the **band's own ink**: the band's colour is the
  signal, the button just names the verb.
- **Never red, amber or black.** A destructive verb says what it loses ("Clear route",
  "Delete route", "Remove stop") and carries the trash glyph (`ic_delete`) — Google's own
  Android dialogs work this way. Red is for error *states* only (the not-set-up band,
  routing failures, Setup's missing marks).
- **Labels are verb + object** when the object isn't obvious from where the button sits
  ("End drive", not "Finish"; "Skip wait", not "Skip"), and follow the travel mode where
  the verb does (Start / End drive · walk · ride).
- **Icon-only controls** (map pills, the action row's circles, the stop popover's glyphs)
  are not "button prompts" and keep their own forms; their trash glyph is neutral ink too.
- **Disabled:** Material's 38% alpha; never hidden to signal disabled.

### Dialog
- **Container:** `MockarrDialog` (`ui/theme/Dialogs.kt`) — the stat card floated to the
  centre: its 16dp corner on `surface-container-lowest` at `popoverElevation`, the card's
  own `map-edge` side margins (never the platform dialog width; capped at
  `Tokens.dialogMaxWidth` on tablets), 20dp inset; title `titleMedium` bold, body
  `bodyMedium` on `onSurfaceVariant`.
- **Actions:** the sheet's two-up **56dp pill row** (`ActionPill` / `OutlinedActionPill`,
  `ui/theme/Pills.kt`, equal weights): one outlined way out (**Cancel**, Forget it) on the
  left beside one filled verb (Save, Set wait, Start drive, Clear route) on the right — the
  same row as End drive · Resume, never two small right-aligned buttons and never two flat
  text buttons.
- **Destructive:** the same indigo pill; the label names the loss and `DialogAction.iconRes`
  carries the trash glyph (Clear route). Never `error` fill.

### The Action Row (signature)
Strava's Record screen, matched: the sheet's peek is one 120dp row of three equal slots in
a centred 320dp cluster — Mode (64dp tonal, 28dp icon + `titleSmall` label) · **Start**
(80dp filled indigo FAB, 40dp play) · Add / Edit route (64dp tonal, 28dp icon) — a step
above Strava `hud-048` (≈58/68pt) because M3 glyphs read smaller; circles top-aligned so
each label sits under its own circle. The expanded sheet's detail column is capped at 60%
of the window and scrolls, so the map is never buried. **Start while holding elsewhere**
does not raise a dialog: the row fade-throughs into a "START FROM" caption over two 56dp
pills — **Held spot** or **My location** (outlined) · **Start of route** (filled) — and
returns once one is picked; Back or a map tap cancels. The caption and the pills are all
there is — no help line, no Cancel (Ethan, session 44: too much text). Held spot / My
location is a **drive-in** (session 43, Ethan): the lead-in from there to the first stop is
routed and driven in front of the route, drawn while driving, and never added to it — a
saved route stays saved. The choice only appears when a drive-in makes sense: an origin at
the route's start (≤30 m) or too far to drive in (>80 km, straight line) just starts the
route. In builder mode the row is ✕ (56dp
white circle, hairline) · **Done** (filled pill); Save, Undo, Redo, Reverse and Clear route
are map pills above the card. While driving the row is replaced by the **Pause pill**,
which splits into **End drive** (outlined, left) + **Resume** (filled, right) when paused;
the "1×" speed chip sits in the card's strip. **A drive's end clears its route** (Ethan,
session 44 — the kept route and "Drive again" of session 41 are gone): a natural arrival,
End drive and Stop in the notification all leave the map with just the hold. A saved route
is reloaded from Saved routes. Every new drive starts at **1×**; only a resumed drive keeps
its own pace. Never stack actions vertically in the peek.

### Map Pill
Strava's floating control: a 48dp `floating` circle (white in light; lifted and rimmed in
dark — see the Soft Lift Rule) with the floating shadow and an on-surface glyph (`MapPill` / `MapIconPill` in `ui/theme/MapChrome.kt`).
Disabled = glyph at 38%, never hidden; selected (follow) = indigo fill. Used for the FAB
stack and the builder tools; never inside the sheet. **Builder tools appear as they start to
mean something** (distill, session 41): Undo once there is history, Redo once there is
something to redo, and Clear route · Save · Reverse from two stops. "Never hidden" is about
signalling a disabled state — a tool that applies but can't act yet stays visible and
disabled (Save on a straight-line fallback); a tool that doesn't apply yet isn't shown.

### Popover
Strava's builder menu and its tap-a-point callout: a 16dp `surface-container-lowest` card
with the popover shadow and a **caret** on its anchor (`MapPopover`), sitting above the
anchor and flipping below when there is no room. The card hugs its widest row (intrinsic
width, 280dp cap). Rows (`PopoverRow`) are label-left, glyph-right, 48dp min, hairline
dividers between actions only — never directly under a header; a destructive row comes
last, in the same ink as the others. The **stop popover** (`StopPopover`) rides the selected marker
on every camera frame and is **symbols only**: one row of three 48dp icon buttons —
*Move* (four-way arrows, `ic_open_with`) · *Wait* (clock; hold-tinted once a wait is set,
greyed on the destination while "Stay at destination" is on — still tappable, and a tap
explains in a snackbar that the destination already holds) · *Remove* (neutral trash,
last). No header: the selected disc says which stop, and the buttons'
descriptions carry the words ("Wait · 5 min", "Stop 2 options") for TalkBack. While a
drive is playing only the clock shows — the route's shape is fixed mid-drive, but a coming
stop's wait can still change. Outside tap and Back dismiss. Move puts the strip in "Drag
Stop 2 or tap the map to move it — Cancel"; the next map tap or a drag relocates it.

### Motion (one authored moment)
Play is the only choreographed transition, built from three reusable pieces in
`ui/Motion.kt`: top chrome (search bar) slides up and fades (200ms out / 300ms in),
floating controls (3D toggle, builder pills, thumbstick) scale from 80% with a fade, and
the sheet peek swaps content with a Material fade-through (90ms out, 300ms in from 96%) —
the action row becomes the Pause pill. The card's strip crossfades to indigo, its trio
animates in, and the camera eases; End drive reverses everything. Pause splits the pill into
End drive + Resume with the same fade-through. Compose animations follow the system
"Remove animations" setting on their own; MapLibre camera moves are gated by
`rememberSystemAnimationsEnabled()` and cut instead of easing when it is off.

**The drive's continuous motion** (not a moment, so not counted against the budget) lives in
`ui/map/MapPuck.kt` and `MapRouteShade.kt`. The **puck** is two points on one source (a
`kind` property splits them): the **wobble range** — a see-through `position` disc (15 %
fill, 1dp rim at 40 %) whose radius is 2σ of the GPS wobble in true metres (95 % of the
wobbled fixes land inside), floored at 16dp so it never hides under the dot — centred on the
**true** route position, and the accent **dot** on top at the **reported** (wobbled) fix. So
the dot jitters inside a circle that glides steadily down the road, and the follow camera
tracks the circle, not the jitter. No wobble (off, or σ 0) → no circle. The held pin gets
the same circle in `map-hold-pin`. Fixes arrive at the engine's tick rate; dot and circle
**glide** between them over the real fix interval (150–1500ms, linear) from wherever they
were last drawn, so an early fix shortens a glide rather than snapping; a jump over 200m
(resume, restart) sets it directly. The **road shade** moves in the same frames:
`routeTravelled` behind the puck feathering into the accent over 0.4 % of the line, and the
dark theme's `routeGlow` goes out behind it the same way. Nothing pulses (session 40: the
heading beam and its breath read as noise and went). Reduce-motion: the puck snaps.

**Speed rolls** (`ui/screens/RollingText.kt`) — and only speed (session 40, Ethan: time and
distance rolling too was noise): the drive's mph / km/h ticks over like a speedometer wheel
— only the glyphs that changed roll, **up when the number grew and down when it shrank**
(the `StatCell` with `rolls = true` carries its raw magnitude, so the direction is exact,
never parsed from text), each out of its own clipped line box; a change of length rolls the
whole value once. Every other value is a plain `Text` in the same style and 2sp autosize
steps. Tabular figures (`tnum`) are set on the value style so a rolling `1` is as wide as a
`7` and the row never shifts.

**Haptic vocabulary** (`ui/screens/MapHaptics.kt`, plus arrival in `MapSessionFeedback.kt`):
drive start (`GestureThresholdActivate`), a wait beginning and ending, and each intermediate
stop driven straight through (`SegmentTick`); a stop placed on the map (`ContextClick`); the
thumbstick's dead-zone edge (`SegmentFrequentTick`); arrival (`Confirm`). Nothing else
vibrates; the system haptic setting silences all of it.

### Stat Card (signature)
Strava's "run box": a 16dp-corner `surface-container-lowest` card with the soft lift,
floating `map-edge` above the sheet — **in every state, from cold start to Stop**. With
nothing loaded it is the empty card: one neutral band, "Tap the map to add stops ·
long-press to hold". **Status strip** on top: a 48dp-min band of bold Title copy, centred
when alone, with an optional trailing text action (Fix / Stop) or the speed chip while
driving. Container and content colours crossfade between the five tones (neutral / ready
/ accent / hold / error) — a colour animation on one surface, never a swap of components.
**One band, never two:** the card never stacks a second strip; the trio under it is what
says a route is loaded. The end of a drive is its own line — "Arrived at ‹place› · Stop"
(ready tone, one haptic tick, ~4 s) before the band settles into "Holding at ‹place› ·
Stop". Below the strip the **Stat Trio** when there is something to count — while
building too (the builder's peek holds only the hint and the Done row). The card **rides
the sheet**: as the sheet expands (speed chips, options, stops) the card and the builder
pills lift with it, capped under the top chrome — and the sheet in turn stops `map-edge`
under the card — so the trio stays readable while its speed changes and a hold's Stop is
never buried. The only state with no card is a single
placed stop ("Building a route") — unless a search pin is up, when the band names the
place in the accent tone with *Add stop* as its action. A wait names its stop: "Waiting at
Reunion Tower · 0:56" (the stop's number in *your* route when it has no name yet), and the
notification's title says the same. With a route loaded (not driving) the **stats block is
tappable** — it opens the expanded sheet's stop list (`Role.Button`, "Edit the route");
the in-drive stats are inert.

### Stat Trio
Three equal `weight(1f)` centred columns; Headline value (700, tabular; speed rolls — see
Motion) over its Label (on-surface-variant). **One duration everywhere** (session 43): the
card's Duration, the saved list and a drive's opening Time left are the drive's own estimate
(`estimatedDriveSeconds` — traffic, waits and off-road pauses; the engine's speed spread
keeps its total), worded by one rule (`Formatter.duration`: whole minutes rounded up, seconds
under a minute). Under it while driving, a 4dp `LinearProgressIndicator` with a
`surface-container-highest` track and no stop indicator. Hidden entirely when the numbers
are undefined (nothing loaded).

### Chips
- **Filter chip:** 12dp radius, 32dp tall, 16dp padding; unselected is outlined, selected
  is `surface-container-highest` fill with on-surface text. Used for speed presets and wait
  presets. The speed pill in the action row is the same component, selected while the
  chip row is open.

### Cards / Containers
- **Corner:** 16dp. **Background:** `surface-container`. **Shadow:** none (see Elevation).
  **Padding:** 12dp; list cards keep a map thumbnail on the left at 88dp with an 8dp radius.

### Inputs / Fields
- **Search field:** `surface-container-lowest` fill, 12dp radius, no visible border until
  focused (then the indigo outline), trailing search icon or a 24dp progress spinner.
  Results drop as a 16dp `surface-container-lowest` card at popover elevation with 48dp-min
  rows and a scroll cap of 280dp; past four rows the list fades at the bottom. Each row:
  a 24dp `on-surface-variant` glyph by kind (pin = place, signpost = street, house =
  address, skyline = city/region, history = recent), the name with the typed text bold,
  a `bodySmall` "where" line, distance from the viewport right-aligned; one line each,
  ellipsised. An empty focused field shows "Recent · Clear"; no-match and offline read
  as a quiet notice above the list, never a blank card. A pick, Close, or a tap on the
  map clears the field and its list — while the search is up, a map tap only dismisses
  it, never places a stop; recents open only for a focused, empty field — never on launch.
- **Dialog text fields:** Material outlined, single line.

### Navigation
- No navigation bar. The Map is the root; **Saved routes** and **All settings** are rows
  at the bottom of the sheet's drag-up list and open as pushed screens with a centred,
  pinned top bar (tints to `surface-container` as content scrolls under it) and a back
  arrow; Setup opens from Settings or the strip's Fix action, and on first run only when a
  required step is missing. System back always returns to the map.
- **Every map gesture has a row twin** in the sheet (the TalkBack and precision path): a
  "Map" group with "Hold my location at the map centre", and compass nudges as custom
  accessibility actions on the thumbstick. Clickable rows carry a `Role`. ("Add a stop at
  the map centre" was removed at Ethan's request, session 25 — placing a stop currently
  has no gesture-free twin; restore one if accessibility becomes a goal.)

### Map markers
- **Thumbstick (hold only):** a 120dp white pill-family base with the floating shadow and a
  44dp knob that warms from indigo to hold-amber with push strength; 8% dead zone, squared
  response, 20 Hz (`docs/research/thumbstick-rnd.md`).
- **Stop discs:** 11dp radius, numbered, bold label; start in `map-stop-start`, vias in
  `map-stop-via`, destination in `map-stop-end` with the ring colour as its text, on a
  baked soft shadow (3dp blur, 1.5dp down). Wait badge: a 5dp ground-toned clock at the
  top-right of every waited stop — the icon says "waits", never the amount.
  Selected: the disc itself lifts to `map-selection` and grows 2dp (no halo); the number
  flips to whichever ink contrasts. Tapping one opens the stop popover; **dragging one
  moves it** (the map does not pan; the route refetches on drop). The popover's wait row
  reads the current value ("Wait · 5 min").
- **Search pin:** a teardrop in `map-selection` with the stop disc's head (11dp + ring) and
  a small ground-colour dot, its tip on the exact geocoded point of the last search pick,
  over the stop discs and under the mocked location. It centres in the *visible* map
  (between the top chrome and the card/sheet stack), at z17 for a place or address, z16
  for a street, z13 for a city. A pick doesn't open the builder (looking a place up isn't
  editing): tapping the pin, or the band's *Add stop*, drops a stop exactly there, under the
  place's name, and opens it. The pin leaves when it becomes a stop, a new pick replaces it,
  Back dismisses it, a drive starts, or the builder closes.
- **Stop names:** every stop carries a name — a searched place keeps its result's, a tapped
  stop is reverse-geocoded once it settles (the street for a POI tap, as route titles do).
  The sheet's stop row shows the name with its role under it ("Stop 2 · Waits 1 min"); the
  band, the wait dialog ("Wait at Reunion Tower"), TalkBack actions and suggested route
  names use it; a moved stop is looked up again.
- **Position:** 7dp `map-position` circle with a 3dp ground-colour ring, on its see-through
  wobble range (see Motion). **Hold pin:** 9dp
  `map-hold-pin` circle ringed in hold ink (`holdPinRing`: `#8A5A00` light, so the amber pin
  holds 3:1 on the light map; the ground colour in dark). **Wait chip:** amber rounded pill, 11dp bold countdown
  with a clock glyph, above the one stop playback is dwelling at — the amount shows only
  while it counts down.

## Do's and Don'ts

### Do:
- **Do** read every colour from `MaterialTheme.colorScheme` or `MockarrTheme.colors`; map
  code takes a `MapPalette`.
- **Do** put session state in the strip's tone and copy; put transient feedback in a
  snackbar.
- **Do** present numbers as Label-over-Headline cells in equal trios with tabular figures.
- **Do** measure the sheet's peek at runtime and fall back to 200dp until measured.
- **Do** hide non-essential chrome (search, 3D, action row, thumbstick) during playback
  or when its state does not apply — through the `Motion` transitions, never a hard cut.
- **Do** hide the trio rather than show placeholders when its numbers are undefined.
- **Do** keep every touch target at 48dp and every list scrollable with IME padding.

### Don't:
- **Don't** add a second card above the sheet; there is one card and one sheet (popovers
  are transient and anchored, not cards).
- **Don't** enlarge one stat into a hero numeral.
- **Don't** use Strava orange, or any second saturated accent beside indigo.
- **Don't** put a shadow on anything inside the sheet; over the map, only the Soft Lift set.
- **Don't** add a navigation bar or tabs; everything is reachable from the sheet.
- **Don't** ever show raw coordinates in the strip or the notification.
- **Don't** add iOS chrome — wheel pickers, Cancel/Done header pairs, Cupertino switches.
- **Don't** hard-code a colour, radius or spacing literal in a screen; extend `Tokens` or
  the theme instead.
