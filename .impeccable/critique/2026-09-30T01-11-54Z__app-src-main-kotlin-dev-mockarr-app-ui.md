---
target: everything (app UI)
total_score: 27
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
timestamp: 2026-09-30T01-11-54Z
slug: app-src-main-kotlin-dev-mockarr-app-ui
---
# Mockarr: impeccable critique (everything), 2026-09-29

**Method:** dual-agent.
- **A:** a design-review sub-agent on the mockarr_test emulator. 79 captures across light, dark, font scale 1.3 and landscape.
- **B:** the bundled detector plus a static read of the Compose sources. `detect.mjs` exited 0, but it only reads web sources, so it scanned 0 of the 67 Kotlin files.

## Design Health Score: 27/40 (Acceptable)

| # | Heuristic | Score | Key issue |
|---|---|---|---|
| 1 | Visibility of System Status | 3 | The band is excellent. A mock-app revoke mid-hold is only noticed on resume, and Start's spinner has no words. |
| 2 | Match System / Real World | 3 | Natural verbs. "Wobble", "Updates per second" and "Held spot vs My location" are abstract. |
| 3 | User Control and Freedom | 3 | Undo/redo and delete-with-Undo are good. Back at the map root silently loses an unsaved route. |
| 4 | Consistency and Standards | 3 | The button system is tight. The flag and pin glyphs are reused, and the sheet toggles duplicate Settings with different wording. |
| 5 | Error Prevention | 2 | Clear route sits first, beside Save. In "Discard changes?" the filled button is the destructive one. Search offers a result 10,552 mi away. |
| 6 | Recognition Rather Than Recall | 2 | The builder and popover icons have no labels. Long-press is taught once. Saved routes is at the bottom of a scrolling list. |
| 7 | Flexibility and Efficiency | 3 | Search or tap, list twins, notification controls, saved routes. Stops can't be reordered. |
| 8 | Aesthetic and Minimalist Design | 3 | Quiet and well made. The expanded builder shows about 14 actions, and the biggest control at cold start is a disabled Start. |
| 9 | Error Recovery | 3 | Clear, fixable messages; Setup expands the missing step. |
| 10 | Help and Documentation | 2 | Setup is the only help. The drive-in, hold vs drive, and wobble are never explained in place. |
| **Total** | | **27/40** | **Acceptable** |

## Specificity verdict
**The structure is borrowed (Strava's Record screen); the language is Mockarr's own.** The central tension, a fake location that other apps see, has no visual form while driving or holding.

Deterministic scan: 0 files scanned (web-only detector). The static read found:
- no literal colours, no text-only buttons, all targets at least 48dp, every icon-only control labelled
- glyph conflicts: the flag means End drive and Stay at destination; route actions use a pin reserved for stops and holds
- Setup shows up to 3 filled pills; "—" placeholders; wait-chip text in dp; a "!" text icon on Setup

## What's working
1. The state band as the single source of truth.
2. Forgiving editing: undo/redo, discard changes, delete-with-Undo, list twins for gestures.
3. Robust across dark mode, font 1.3 and landscape.

## Priority issues
- **[P1] An unsaved route is lost on Back at the map root.** Keep a draft across the activity closing. (/impeccable harden)
- **[P1] The fake location is never visible as such**, including after End drive. Add one clause to the band while mocking. (/impeccable clarify)
- **[P2] Glyph conflicts and risky builder order.** Clear route sits beside Save, and the flag and pin are reused. Reorder the tools, fix the glyphs, add tooltips. (/impeccable polish)
- **[P2] Hard-to-find navigation and duplicated toggles in the sheet.** Move Saved routes and Settings up and drop the duplicate toggles. (/impeccable distill)
- **[P2] The destructive button is filled in "Discard changes?", and Setup shows several filled pills.** Needs a rule decision. (/impeccable polish)

## Persona red flags
- **Jordan:** disabled Start with no reason; START FROM without a mental model; unlabeled popover icons; End drive keeps the fake location.
- **Casey:** top-third controls; mid-screen builder tools; the thumbstick on the right only.
- **Sam:** markers and popover missing from the accessibility tree; the wait shown only by a 5dp badge; disabled Start without a reason; low-contrast speed chip in dark; the search-field name unconfirmed.
- **Riley:** a mock revoke mid-hold is undetected until resume; a route loaded while holding isn't framed; after Undo, Save doesn't reflect the saved copy; search relevance.

## Minor observations
- The arrival band wraps to 3 lines and splits "2.7 / mi" (needs non-breaking spaces).
- Done is enabled with 0 stops.
- The Speed label is 4px off baseline at 1.3.
- The grey Start circle shows during holds.
- The Drive label collides with the dot.
- The Units tile is oversized.
- The Setup intro repeats About.
- Hard-coded 1/2dp borders and auto-size sp bounds.
- TalkBack strings are concatenated in Kotlin.

## Questions to consider
1. Where does the interface admit that it is the fake one?
2. Should the hold, not Start, be the main state?
3. Should End drive give the real location back by default?
4. Do the per-drive toggles belong in the sheet at all?
