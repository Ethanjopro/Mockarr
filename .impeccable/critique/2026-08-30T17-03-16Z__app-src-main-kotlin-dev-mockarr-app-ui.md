---
target: everything (app UI)
total_score: 28
max_score: 40
na_heuristics: 
p0_count: 1
p1_count: 3
timestamp: 2026-08-30T17-03-16Z
slug: app-src-main-kotlin-dev-mockarr-app-ui
---
# Mockarr — impeccable critique (everything), 2026-08-30

Method: dual-agent (A: emulator design review, 47 screenshots light/dark/font-scale 1.3 · B: detector + static scan). Detector: 0 findings = nothing scannable (Compose tree; web file types only). Screenshots: scratchpad/critique-a/.

## Design Health Score — 28/40 (Good)
| # | Heuristic | Score | Key issue |
|---|---|---|---|
| 1 | Visibility of System Status | 3 | Save and Stop give no feedback; two strips stacked after arrival |
| 2 | Match System / Real World | 3 | "Map tab" copy; "USFRT" leaks; route named after a shop |
| 3 | User Control and Freedom | 3 | Expanding the sheet hides the only Stop during a hold |
| 4 | Consistency and Standards | 2 | Builder strip pill vs card band; 6 vs 5 min; chip/strip clocks differ |
| 5 | Error Prevention | 3 | Stop unconfirmed; Rush-hour traffic on by default |
| 6 | Recognition Rather Than Recall | 2 | Resting map gives no cue; tap does nothing until Add route |
| 7 | Flexibility and Efficiency | 3 | No non-gesture path to map actions |
| 8 | Aesthetic and Minimalist Design | 3 | Five builder pills always on; lavender results card |
| 9 | Error Recovery | 3 | Routing failure shows raw exception text |
| 10 | Help and Documentation | 3 | First-run hint explains the sheet, not route-making |

## Design specificity
Authored from "Route ready" onward (driving card, designed dark theme); category-interchangeable before it (mute empty map, generic builder chrome). Static scan: colour discipline holds (only MapBuilder.kt:222 Color.Black + marker shadow); 8 dp literals duplicate Tokens; 3 targets <48dp (MapSheet.kt:117, MapChrome.kt:174, MapScreen.kt:986); 1 Role, 1 customActions, 0 liveRegion; ~20 inline English strings; no status-bar style in enableEdgeToEdge; permission denial silent in all three launchers.

## Priority issues
- [P0] Map-only actions (add stop, hold, popover, drag, thumbstick) have no accessible path; popovers outside a11y tree. Fix: sheet rows "Add stop at centre"/"Hold at centre", stop list with Wait/Move/Delete, customActions on thumbstick, role on clickables. → audit, harden
- [P1] Two strips on one card after arrival / holding with route (MapStatCard.kt:59-85 `secondary`). Fix: drop secondary or fold into one line. → distill
- [P1] Empty map has no instruction; tapping does nothing until Add route. Fix: idle copy/illustration in peek, or map tap enters builder. → onboard
- [P1] No Arrived moment; Stop gives no feedback. Fix: "Arrived at …" strip line; snackbar "Stopped — your real location is live again". → delight, clarify
- [P2] Speed-chip row clips the stat card. Fix: chip row inside card or lift card with sheet. → layout
- [P2] Builder "Ready to drive" strip is a detached pill; card only assembles after Done. → layout
- [P2] Search dropdown reopens with recents + stale query on relaunch (MapScreen.kt:663, MapSearchComponents.kt:94). → polish
- [P3] Light-theme status-bar icons light-on-light (MainActivity.kt:15). → polish

## Persona red flags
- Jordan: Setup opens on first run even when All set (MockarrApp.kt:58-63); no cue how to start; Rush-hour traffic silent default.
- Alex: 6 vs 5 min; chip/strip 1 s drift; raw routing error; popovers unhittable under position marker/thumbstick; Stop unconfirmed.
- Sam: no TalkBack path to any map action; 1.3× hides All settings below sheet cap and clips OPTIONS under action row.

## Minor observations
Five builder pills full-opacity at zero stops; duplicate search rows, sixth row clipped, results card too heavy; hold pin lacks ring; wait badge invisible on via disc; thumbnail drops stop 2; Settings/sheet hard-clip under top chrome; dark Setup banner too loud, dark amber strip olive; expanded sheet repeats action row; MapScreen.kt 1077 lines.

## Questions
1. Why doesn't the card exist until the route is built? 2. Is Holding-after-arrival a state or a missing "Arrived"? 3. Which two of five builder tools would you cut? 4. Should the sheet stop list be the primary editor? 5. What does the user see when their real location goes live again?
