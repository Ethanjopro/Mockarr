---
target: everything (app UI)
total_score: 26
max_score: 40
na_heuristics: 
p0_count: 1
p1_count: 2
timestamp: 2026-09-24T19-04-33Z
slug: app-src-main-kotlin-dev-mockarr-app-ui
---
# Mockarr: impeccable critique (everything), 2026-09-24

**Method:** dual-agent.
- **A:** a design-review sub-agent on the mockarr_test emulator (API 35). 80 captures across light, dark, font scale 1.3, landscape, the notification shade and not-set-up.
- **B:** the bundled detector plus a static scan of the Compose sources. `detect.mjs` is web-only and scanned 0 Kotlin files, so B's findings come from the static read.

## Design Health Score: 26/40 (Acceptable)

| # | Heuristic | Score | Key issue |
|---|---|---|---|
| 1 | Visibility of System Status | 3 | The band is excellent. Gaps: Pause hides the wait countdown, the notification title stays "Driving" while waiting, and End drive could falsely announce "Arrived at …" (fixed). |
| 2 | Match System / Real World | 3 | Plain copy. But "stop" is both a waypoint and a verb, raw map abbreviations leak into the band ("Fwy Eb"), and the mode is "Cycle" while the band says "Cycling" and the verbs say "ride". |
| 3 | User Control and Freedom | 2 | ✕ offered to clear an unchanged route (fixed: ✕ now discards only this session's edits). A search pick forces the builder. "Held spot" permanently prepends a stop. |
| 4 | Consistency and Standards | 3 | The visual system is exemplary. One route shows three durations (9 / 10 / 11 min). The trash glyph means three things in the builder. |
| 5 | Error Prevention | 2 | Start or a hold while not set up crashed the app (fixed). The "My location" start ignores distance (the real location can be about 1,459 mi away). |
| 6 | Recognition Rather Than Recall | 2 | Stops are anonymous ("Stop 2") even when they came from a named search. The popover is three bare glyphs. Gestures are taught once. |
| 7 | Flexibility and Efficiency | 3 | Recents, saved routes, speed, notification actions, Undo/Redo and Drive again. No stop reordering or coordinate entry. |
| 8 | Aesthetic and Minimalist Design | 3 | Chrome recedes during playback. The builder shows about 11 targets. The thumbstick sits over the held pin. |
| 9 | Error Recovery | 2 | Diagnostic copy is excellent, but the main failure path crashed, so the written error never showed (fixed; the snackbar now carries "Set up"). |
| 10 | Help and Documentation | 3 | Help at each step (idle line, one-stop hint, START FROM help, Setup how-tos). Gestures are never re-explained. |
| **Total** | | **26/40** | **Acceptable**: a solid core, dragged down by the edges |

## Specificity verdict

**The structure is borrowed; the meaning is Mockarr's own.**
- **Borrowed:** the skeleton is Strava's Record screen. The idle screen is the least authored screen in the app.
- **Mockarr's own:**
  - the wobble circle on the true position while the dot jitters at the reported one
  - the band's vocabulary ("Holding at Elm Street · Stop holding")
  - "Stopped — your real location is live again"
  - START FROM, Drive again, and the live wait chip
- **Missed:** the central tension, *what other apps see* versus *where I really am*, has no lasting visual form at idle.

Deterministic scan: the detector ran but scanned 0 files, because it only reads web sources. B's static read found no absolute-ban violations. Colours all come from the theme, and there are no literal colours in screens.

## What's working
1. **The band as the single source of truth.** One surface, five tones, one line and one verb, echoed by the notification.
2. **Copy that states consequences.** "Mocking isn't set up — playback won't move your location", the START FROM help line, and the release snackbar.
3. **Chrome that leaves.** Playback fades the search and FABs, the landscape side panel works, the dark theme is designed, and font scale 1.3 holds.

## Priority issues
- **[P0] Start or a hold crashed when mocking isn't set up.**
  - `ForegroundServiceDidNotStartInTimeException`: the service stopped before calling `startForeground`.
  - FIXED on sight: it now goes foreground first, then fails into the "Set up" snackbar.
- **[P1] The builder's ✕ was a hidden "clear route", and a search pick forces the builder.**
  - The ✕ half is FIXED: no dialog when nothing changed, otherwise "Discard your changes?", which restores the stops.
  - The search-pick half remains.
- **[P1] "Held spot" / "My location" silently rewrite the route and ignore distance.**
  - `addWaypoint(origin, atStart = true)` makes a saved route unsaved, and every Drive again starts from the old hold.
  - The filled default, "Start of route", is the option that jumps.
- **[P2] One route, three durations** (list 9 / card 10 / Time left 11). It needs one estimator and one rounding rule.
- **[P2] Stops are anonymous.** Carry the searched or reverse-geocoded name into the list, band, wait dialog and notification.

## Fixed on sight (session 42)
- **Crash:** P0 not-set-up crash, on both the Start and hold paths.
- **Snackbar:** the not-selected error snackbar offers "Set up" and ✕, and times out (it used to have only Dismiss, and stayed up indefinitely).
- **Arrival:** no false "Arrived" after an End drive that follows an earlier arrival (arrival tick de-duplicated).
- **Builder ✕:** leaves silently when nothing changed; otherwise "Discard changes" restores this session's stops. History resets when Edit route opens.
- **Camera:**
  - Route fits clear the right-hand FAB column (80 dp end padding).
  - Edit route reframes an off-screen route.
- **Pills:** 16 dp sides and an ellipsis, not a silent word drop ("Discard changes" rendered as "Discard").
- **TalkBack:**
  - Sliders say their name once and their value once.
  - Map pills announce as buttons.
  - The 3D pill isn't read twice.
  - Stop-row trash buttons are named per stop.
- **Stays:** the popover clock uses full secondary ink, and the sheet shows it as plain text rather than a disabled button.
- **Rotation and keyboard:**
  - The mode picker and the saved-route rename survive rotation.
  - The saved list pads for the IME.
  - The mode tag grows with large text.

## Persona red flags
- **Jordan (first-timer):**
  - The crash was the first-minute killer (fixed).
  - At idle, "Add route" competes with "tap the map to add stops".
  - The ✕ fright is fixed.
- **Casey (one-handed):**
  - The popover can open against the search bar.
  - The collapsed notification shows no time left.
  - The thumbstick hides the held pin.
- **Sam (TalkBack):**
  - There's no gesture-free way to place a stop at an arbitrary spot.
  - Move stop still needs a drag or a tap.
- **Priya (QA tester):**
  - Runs aren't repeatable: Held spot mutates the route, and the estimates disagree.
  - 4× reports 52–66 mph on service lanes with no warning.

## Minor observations
- **Builder layout:**
  - With the sheet expanded, the builder tools float over the stops. The camera deliberately doesn't refit when the sheet grows.
  - The speed popover wraps 3 + 2.
- **Icon reuse:**
  - trash (clear route / remove stop)
  - the squiggle (save / wobble)
  - the crosshair (locate / follow / wobble)
  - the clock (wait / rush hour)
- **Code (B):**
  - `MarkerTracker` recomposes per frame.
  - `playbackState` is read in the `MapScreen` body.
  - The dark sheet edge is at 1.07:1.
  - The saved-routes search field has no border.
  - `isSystemInDarkTheme` is called outside the theme.
  - `uppercase()` has no locale.
  - `splitRouteName` is coupled to the ", " template.

## Questions to consider
1. If "believable over impressive" is principle #1, why is the filled START FROM default the option that jumps?
2. Could the idle screen say what other apps currently see?
3. Does the builder need to be a mode at all?
4. Should a drive have an ending, a one-line summary, instead of fading into an amber hold?
