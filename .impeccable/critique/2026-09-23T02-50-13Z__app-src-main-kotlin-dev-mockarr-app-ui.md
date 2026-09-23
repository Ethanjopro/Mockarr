---
target: everything (app UI)
total_score: 27
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
timestamp: 2026-09-23T02-50-13Z
slug: app-src-main-kotlin-dev-mockarr-app-ui
---
# Mockarr — impeccable critique (everything), 2026-09-22

Method: dual-agent (A: design-review sub-agent on the emulator · B: detector + static-scan sub-agent). A: mockarr_test AVD (API 35), 57 screenshots, light / dark / font scale 1.3 / landscape. B: bundled detector plus a static scan of 59 Compose files.

## Design Health Score — 27/40 (Acceptable, top of the band)

| # | Heuristic | Score | Key issue |
|---|---|---|---|
| 1 | Visibility of System Status | 3 | The band is excellent and a live region. But "Arrived" shows for only 4 s, Start showed an unlabelled spinner (fixed), and a set wait is shown only by a small badge. |
| 2 | Match System / Real World | 3 | The voice is plain. But the notification's "Stop" means "end the drive, keep holding" in one state and "release, real location returns" in another. "Held spot" is never defined. |
| 3 | User Control and Freedom | 2 | End drive and natural arrival wipe the route with no Undo or Save offer. The start-from choice has no visible Cancel. |
| 4 | Consistency and Standards | 3 | The button system holds. Drift: two travel-mode controls, "Ready to drive" becomes "Route ready", and the "Drive" tag on route cards looks tappable. |
| 5 | Error Prevention | 2 | The route is lost on finish. Five builder tools show at zero stops. The greyed wait clock does nothing silently. 4× carries into the next drive unannounced. |
| 6 | Recognition Rather Than Recall | 2 | Icon-only stop popover and builder tools. Saved routes and All settings sit below the fold of a sheet you must drag up first. |
| 7 | Flexibility and Efficiency | 3 | Recents, saved routes, draggable stops, speed presets and notification controls. No "drive it again". |
| 8 | Aesthetic and Minimalist Design | 3 | Idle and driving are calm. The builder stacks about 11 controls, and the wait band crowds its text, Skip wait and 4×. |
| 9 | Error Recovery | 3 | Set up deep-links each step and re-checks on return. The error band clipped its own consequence at 1.3 (fixed). |
| 10 | Help and Documentation | 3 | The idle band teaches both gestures and the Setup checklist is concrete. The builder hint named a Start button that isn't there (fixed). |
| **Total** | | **27/40** | **Acceptable** |

## Design specificity verdict

**LLM assessment.** Authored on the map, generic off it.
- **Authored:** the one status band (colour plus one line carries idle, ready, driving, waiting, paused, holding and error). The metre-true wobble circle with the reported dot jittering inside it. Speed-only roll, the amber hold pin and thumbstick, the wait chip over the stop being waited at. "Holding where you stopped" and "Stopped — your real location is live again": no other product would write these lines.
- **Borrowed on purpose:** the layout is Strava's Record screen. It's well executed, but the most visible composition belongs to someone else.
- **Category-interchangeable:** Settings (tile grid plus list), Setup (checklist cards) and Saved routes (thumbnail cards). Dark mode is designed but anonymous.
- **Missed opportunity:** "believable" is the pitch, yet nothing shows what *other apps* see.

**Deterministic scan.**
- **Detector:** `detect.mjs --json` on `app/src/main/kotlin/.../ui` and on `app/src/main/res` exited 0 with `[]`. That means 0 scannable files, not 0 findings: the engine reads HTML/CSS/JSX only.
- **Static scan (substitute evidence), clean areas:** colour discipline (0 stray colour literals in screens), button discipline (0 red buttons, 0 recoloured pills), and 0 clickable rows without a role.
- **Static scan, what it caught that no English-locale screenshot could:**
  - `MapPalette.css()` formatted alpha with the device locale. Every MapLibre colour became invalid CSS (`rgba(…,1,000)`) in comma-decimal languages. This was the top signal.
  - TalkBack gaps: the mode picker had no selected state, Setup's "!" was silent, and Start lost its label while locating.
  - A hard-coded "Route " default name, plus 5 unused strings.
  - No `autoMirrored` on directional glyphs with RTL enabled.
  - The 3D tilt ignored Remove animations.
  - An 866-line `MapScreen()` composable, and unused `material3-adaptive` dependencies.
- **Agreement:** both assessments flagged the TalkBack gaps and the large-font clipping.
- **False positives:** Transparent / alpha-mask / bitmap-shadow colours, zero-and-hairline dp literals, documented sp autosize bounds, error colour used for error states, and Compose animations (the system scales them).

**Visual overlays:** not applicable (native Android, no page to inject into).

## Overall impression
The drive itself is the best thing in the app: the chrome recedes and the dot jitters believably inside its range. The biggest opportunity is the ending. Arrival, or End drive, throws the route away four seconds after the best moment. The second is that the map is still a sighted-and-gestures-only surface.

## What's working
1. **One band for all state.** Colour plus one sentence, announced politely, with its action in the band's own ink. It carries seven states without adding cards (`MapStatCard.kt`, `MapSheet.kt` `stripFor`).
2. **The drive reads as real.** The wobble circle, the gliding dot, travelled-road shading, speed-only roll and receding chrome.
3. **Dark mode and Setup are properly designed.** Dark has its own basemap, route glow and thumbnails. Setup gives exact paths and deep links, and re-checks live on return.

## Priority issues
- **[P1] Finishing a drive throws the route away.**
  - **What:** End drive and natural arrival call `clearWaypoints()` (`MapScreen.kt` ArrivalEffect and the End drive handler). There's no Undo and no Save offer.
  - **Why:** repeating a drive means rebuilding it unless you saved first. The weakest moment of the journey is the last one.
  - **Fix:** keep the route loaded and put "Drive again" (or Save) in the band's action slot. At minimum, show a "Route cleared · Undo" snackbar.
  - **Command:** `/impeccable harden`
- **[P1] TalkBack can't do the map's core tasks.**
  - **What:** four gaps were fixed on sight (map description, mode picker state, Setup "!", Start label). What remains:
    - the stop popover is a non-modal Popup outside the accessibility tree
    - wait / move / remove are reachable without gestures only in the builder's expanded list
    - the action row has nested clickables (two focus stops per slot; still needs a live TalkBack check)
    - section labels ("SPEED", "STOPS · n", "START FROM") aren't headings
    - PopoverRow is 44dp
  - **Command:** `/impeccable audit`
- **[P2] The builder is the densest screen.**
  - **What:** about 11 controls after a search pick; five tools at zero stops, with Reverse enabled at one stop; the greyed destination clock is a silent no-op.
  - **Fix:** reveal tools as they become meaningful (Undo from one stop; Reverse, Save and Clear from two), and have the greyed clock say "Stays at destination".
  - **Command:** `/impeccable distill`
- **[P2] The moments with consequences don't state them.**
  - **Start from "Start of route" while holding elsewhere jumps the reported location**, the teleport the product promises never to do. "Held spot" drives there first. Neither button says so, and there's no visible Cancel.
  - **The notification's "Stop" has two meanings.**
  - **4× carries into the next drive** with only a small chip.
  - **Command:** `/impeccable clarify`
- **[P2] Large fonts and other shapes.** The band's three-line wrap and the switch gap were fixed on sight. What remains:
  - the wait band wraps its countdown at 1.3
  - in landscape the sheet peek takes about half the height and hides the thumbstick and hold pin
  - no window-size classes, with the `material3-adaptive` dependencies unused
  - **Command:** `/impeccable adapt`

## Fixed on sight in this round (CLAUDE.md rule)
1. **Map colours in comma-decimal languages.** `MapPalette.css` now uses `Locale.ROOT`, with a regression test (`MapPaletteCssTest`).
2. **The map's TalkBack description.**
   - Before: MapLibre's generic pinch/scroll text overrode ours.
   - Now: the `MapView` carries our text. It was also corrected: search plus Add stop, or Hold at map centre, are the real gesture-free paths.
3. **The mode picker announces the selected mode** (`selectable`).
4. **Setup's "!" mark says "Not done".**
5. **Start keeps its label while locating.**
6. **The builder's one-stop hint.**
   - Before: it told you to press a Start button that isn't in the builder, and said Start would *hold*.
   - Now: "Or tap Done, then Start, to drive here from where you are."
7. **A route loaded from Saved routes** now drops the sheet and reframes, so all its stops show above the card.
8. **A nudged hold no longer reads "Holding at destination".** It becomes a pin.
9. **The status band takes three lines**, so the not-set-up consequence is never cut.
10. **Settings and sheet switch rows, and slider values,** keep a gap to their text.
11. **The 3D tilt respects Remove animations.**
12. **Back, chevron, undo and redo glyphs mirror in right-to-left layouts.**
13. **The last Material trash glyph now uses `ic_delete`.**
14. **The default route name comes from `strings.xml`.**
15. **The Mock location tile and Setup step** used a "verified" check glyph beside "Not set up"; now a location pin.
16. **3 dead strings removed.**

## Persona red flags
- **Jordan (first-timer):**
  - a search pick lands in builder mode with seven unlabelled controls
  - the stop popover's move arrows and clock have no labels
  - "START FROM · My location / Start of route" is unexplained
  - the route vanishes on arrival
  - Saved routes takes a drag plus a scroll
- **Casey (one-handed):**
  - search, 3D and locate sit at the top of the screen
  - the thumbstick is fixed on the right edge
  - Skip wait and the 4× chip crowd each other
  - the speed popover opens mid-screen
- **Sam (TalkBack):**
  - the stop popover is outside the accessibility tree
  - adding a stop without gestures works only through search
  - dark map pills and the thumbstick base are near-black on a near-black basemap (likely non-text contrast failure)
  - the start-stop marker's white number on green is 4.34:1
- **Riya (location-game player, from PRODUCT.md's primary users):**
  - 4× from the last drive silently carried over (a walk became car speed in her game)
  - "Start of route" from a hold teleports
  - the notification, her only surface while in the game, shows neither speed nor mode, and its Stop means different things by state
  - the trio mixes simulated speed with wall-clock time left

## Minor observations
- **Search:**
  - distant results read "1459.5 mi"
  - the trailing icon stays a magnifier instead of becoming a clear ×
  - the 3D and locate pills jump with the results card
  - "coffee" returned only a distant town
- **Speed chips** wrap raggedly (3 + 2) beside an inline "SPEED" label.
- **Wait dialog:** the custom-minutes field is narrower than its rows.
- **Saved routes:**
  - the rename field text differs from the card's title plus city line
  - Rename has no icon beside "Delete route"
  - auto-names read "X Street to X Street"
  - one card said 16 s where the map said 1 min
  - one thumbnail was blank on first load
- **Settings:**
  - three tiles in a two-column grid
  - "Walk off-road stretches" is under Playback while its parent "Off-road stops" is under Routing
  - "Stay at destination" uses a stop-square icon
- **Setup:** "dontkillmyapp.com" isn't a link.
- **Notification** title repeats "Mockarr".
- **Formatting:** `SimpleDateFormat("MMM d, HH:mm")` forces a 24-hour clock, plurals are hand-rolled, and there are three spinner variants.
- **Tokens:** there is no list-row-height token.
- **Type drift:** `titleMedium` is used where DESIGN.md says "Title 14sp/500", and stat labels are sentence case where DESIGN.md says uppercase.
- **Structure:**
  - `MapScreen()` is 866 lines
  - `MapViewModel` is at 25 of 26 functions (detekt limit)
  - `isSystemInDarkTheme()` is called outside the theme twice (latent)
- **Overlaps:** the follow pill covered stop 3 once, and stop 1 crowds the attribution "i".

## Questions to consider
1. What if a drive never consumed its route, so arrival offered "Drive again" and saving became the exception?
2. What if the band said what *other apps see* ("Seen at Charleston Rd · 12 mph") instead of what Mockarr is doing? That would make "believable" visible and answer "is my real location leaking?" at a glance.
3. Does route building need to be a separate mode at all, when tapping the map already adds stops from idle?
