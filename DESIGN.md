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
  map-stop-start: "#1E8A5A"
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
  button-pill-inverse:
    backgroundColor: "{colors.paper-on}"
    textColor: "{colors.paper}"
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
---

# Design System: Mockarr

## Overview

**Creative North Star: "The Quiet Dashboard"**

Mockarr looks like a car's instrument cluster at night: neutral surfaces, one indigo
signal, big bold numerals, and state shown as a tinted band on a card that floats over
the map. The map is the product; every piece of chrome either serves the drive in progress
or recedes. Colour appears where it means something — the route, the position, the primary
action, the state strip — and nowhere else; weight and lift (bold type, soft shadows, a
64dp Start) carry the energy instead.

The system is Material 3 in structure (sheet, chips, snackbars, pills) with a hand-authored
indigo scheme on every API level; dynamic colour is off so the map palette never fights a
wallpaper. Dark is designed as its own set, not an inversion: the evening-on-the-couch
scene is the primary one. **Strava's Record screen is the layout, matched exactly** (brief
3, 2026-08-28): a stat card with a coloured strip floating above a separate sheet whose
peek is the action row; Pause is one full-width pill that splits into Resume + Finish. Only
the palette and the components are Material. Confirmed anti-references: the pre-2026-08
card stack, Strava orange, hero numerals, a bottom navigation bar, iOS wheel pickers and
Cancel/Done headers.

**Key Characteristics:**
- Restrained colour: neutral ground, indigo accent, semantic state tints kept separate.
- Card over sheet: the stat card (strip + trio) floats above the sheet and rides its edge.
- Value-over-label stat cells in equal-weight trios; bold tabular figures; no hero number.
- Soft lift: the card and the sheet carry a low shadow; nothing else does.
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
  somewhere" — Holding and Waiting strips, the hold pin, wait badges and live wait chips.
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
new card, never by an icon alone. The band never shows raw coordinates: while a hold's
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
hierarchy is carried by weight and size steps and by the uppercase, tracked labels that sit
over every value.

### Hierarchy
- **Display** (400, 36sp): reserved; not used on any current screen.
- **Headline** (700, 28sp, tabular figures): stat values — time left, distance, speed,
  stop count. Always paired with a Label under it. The strip's line and pill labels are
  Title at bold.
- **Title** (500, 14sp): the status strip copy, dialog titles, list-card titles.
- **Body** (400, 16sp / 14sp small): sheet hints, list rows, dialog text. Measure is
  bounded by the sheet inset, never wider than ~60ch.
- **Label** (500, 11sp, +0.5sp tracking, UPPERCASE): the word above every stat value,
  section headers ("STOPS", "SPEED"), chip text at 14sp in sentence case.

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

- **Stat card** floats `{spacing.map-edge}` above the sheet's top edge and inset from the
  sides, 16dp corners: the status strip on top, the trio (and progress while driving)
  below. It stays through playback and hides behind the sheet when it expands.
- **Bottom sheet** (`{spacing.inset}` horizontal inset, 28dp top corners, drag handle) with
  two states: *peek* — the action row (Mode · Start · Add route), or Pause / Resume + Finish
  while driving, measured at runtime and never assumed — and *expanded* — the same plus a
  scrollable options list — *Save route* first when a road route is loaded (reads *Saved*
  once it is), the drive switches, then *Saved routes ›* and *All settings ›*. The peek is
  the screen's resting state. The sheet is always draggable from anywhere on it, the
  handle is a 48dp tap target that toggles peek ↔ expanded, and the stat card's strip
  carries Strava's expand glyph (`hud-030`) — three ways in, because a swipe alone is
  neither discoverable nor accessible.
- **Top overlays** sit `{spacing.map-edge}` from the edges: the search field full-width,
  then the FAB stack aligned to the right edge (3D toggle, locate / follow), 8dp apart.
- **Spacing rhythm** is the 4dp grid: 4 / 8 / 12 / 16 / 24 / 32, with 20dp as the sheet and
  card content inset and 12dp as the map-edge gutter.
- **Landscape / compact height**: the sheet keeps its peek and scrolls its detail column;
  Material centres the sheet at its max width. Expanded-width side panel is a planned
  adaptation, not yet built.
- **Touch targets** are 48dp minimum, including list rows and search results.

## Elevation & Depth

Tonal layering plus one soft lift. Surfaces step through the container ramp (card on
`surface-container-lowest`, sheet on `surface-container-low`, strips on `-high`, selected
rows on `-high`, tracks on `-highest`); the map itself is the lowest layer. The stat card
(4dp) and the sheet (8dp) carry a low ambient shadow so they read as objects over the
basemap — Strava's card is the reference. FABs keep tonal elevation; chips, markers and
strips never get a shadow. Material's default component elevation (dialog, snackbar) is
left as is.

### Named Rules
**The Soft Lift Rule.** Only the stat card and the sheet cast a shadow, and only the low
one in `Tokens` (`cardElevation` / `sheetElevation`). Everything else floating over the map
is distinguished by container tone and its 28/16/12dp corner.

## Shapes

One radius family, applied by role: **28dp** for the sheet's top corners, **16dp** for
cards, **12dp** for controls (search field, filter chips), and **full pills** for buttons,
FABs, the drag handle and the numbered stop discs. Map markers are circles with a 2.5dp
ring in the ground colour; the selected marker gains an outer ring in `map-selection`.
Direction chevrons on the route are 10dp, 2dp stroke, drawn in the casing colour.

## Components

### Buttons
- **Shape:** full pill (`{rounded.full}`), 40dp tall, 24dp horizontal padding.
- **Primary:** Night Indigo fill, white text; one per surface (Start, Pause/Resume). Carries
  a leading 24dp icon when the verb has one.
- **Pill (playback):** 56dp tall, full width or half of a two-up row, Title bold label +
  24dp icon. Primary = indigo (Pause, Resume); **inverse** = `inverseSurface` fill with
  `inverseOnSurface` text (Finish) — Strava's black Finish, in the theme's own ink.
- **Outlined:** transparent, 1dp `outline` stroke, indigo text — a secondary verb beside a
  primary in dialogs.
- **Text:** indigo text, no container — tertiary actions in rows (Save · Undo · Clear) and
  the strip's action (Fix, Stop).
- **Disabled:** Material's 38% alpha; never hidden to signal disabled.

### The Action Row (signature)
Strava's Record screen, matched: the sheet's peek is one 96dp row of three equal slots —
Mode (48dp tonal icon + label) · **Start** (64dp filled indigo FAB + label) · Add / Edit
route (48dp tonal icon + label). In builder mode the row is ✕ (tonal) · **Save**
(outlined, enabled once a road route exists) · **Done** (filled) — Strava keeps Save in
the builder sheet (`map-348`). While driving the row is replaced by the **Pause pill**,
which splits into **Resume** (primary) + **Finish** (inverse) when paused; the "1×" speed
chip sits in the card's strip. Never stack actions vertically in the peek.

### Motion (one authored moment)
Play is the only choreographed transition, built from three reusable pieces in
`ui/Motion.kt`: top chrome (search bar) slides up and fades (200ms out / 300ms in),
floating controls (3D toggle, builder pills, thumbstick) scale from 80% with a fade, and
the sheet peek swaps content with a Material fade-through (90ms out, 300ms in from 96%) —
the action row becomes the Pause pill. The card's strip crossfades to indigo, its trio
animates in, and the camera eases; Finish reverses everything. Pause splits the pill into
Resume + Finish with the same fade-through. Compose animations follow the system
"Remove animations" setting on their own; MapLibre camera moves are gated by
`rememberSystemAnimationsEnabled()` and cut instead of easing when it is off.

### Stat Card (signature)
Strava's "run box": a 16dp-corner `surface-container-lowest` card with the soft lift,
floating `map-edge` above the sheet. **Status strip** on top: a 48dp-min band of bold Title
copy, centred when alone, with an optional trailing text action (Fix / Stop) or the speed
chip while driving. Container and content colours crossfade between the five tones
(neutral / ready / accent / hold / error) — a colour animation on one surface, never a
swap of components. Below it the **Stat Trio** when there is something to count.

### Stat Trio
Three equal `weight(1f)` centred columns; Headline value (700, tabular) over its Label
(on-surface-variant). Under it while driving, a 4dp `LinearProgressIndicator` with a
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
  Results drop as a 16dp card with 48dp-min rows and a scroll cap of 280dp.
- **Dialog text fields:** Material outlined, single line.

### Navigation
- No navigation bar. The Map is the root; **Saved routes** and **All settings** are rows
  at the bottom of the sheet's drag-up list and open as pushed screens with a centred top
  bar and a back arrow; Setup opens from Settings or the strip's Fix action. System back
  always returns to the map.

### Map markers
- **Stop discs:** 11dp radius, numbered, bold label; start in `map-stop-start`, vias in
  `map-stop-via`, destination in `map-stop-end` with the ring colour as its text. Wait
  badge: 4.5dp amber dot at the top-right. Selected: an extra `map-selection` ring.
- **Position:** 8dp `map-position` circle with a 3dp ground-colour ring. **Hold pin:** 9dp
  `map-hold-pin` circle, same ring. **Wait chip:** rounded pill, 11dp bold text with a
  clock glyph; ground-toned by default, amber while the wait is live.

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
- **Don't** add a second card above the sheet; there is one card and one sheet.
- **Don't** enlarge one stat into a hero numeral.
- **Don't** use Strava orange, or any second saturated accent beside indigo.
- **Don't** put a shadow on anything but the card and the sheet.
- **Don't** add a navigation bar or tabs; everything is reachable from the sheet.
- **Don't** ever show raw coordinates in the strip or the notification.
- **Don't** add iOS chrome — wheel pickers, Cancel/Done header pairs, Cupertino switches.
- **Don't** hard-code a colour, radius or spacing literal in a screen; extend `Tokens` or
  the theme instead.
