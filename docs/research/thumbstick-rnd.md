# Thumbstick R&D — why the hold nudge felt uncontrollable

Date: 2026-08-28 · Session 17 · Ethan: "It's too difficult to control the mocked location
accurately" while keeping the stick simple.

## Measurements (before)
Constants in `ui/map/ThumbstickOverlay.kt` at session 16:

| what | value | effect |
|------|-------|--------|
| tick | 200 ms (5 Hz) | at full deflection the dot **hops 44 px per tick** — visibly stuttery, and the smallest "tap" of the stick already moves 44 px |
| curve | linear | 30 % travel = 66 px/s; there is no fine range at all |
| travel | 28 dp radius | 28 dp of thumb travel to express 0–100 % — about 1 % per 0.3 dp |
| min speed | 0.3 m/s | the dot never fully stops while the thumb rests on the knob |
| screen speed | 220 px/s | full push crosses the screen in ~5 s |
| max speed | 1000 m/s | at z10 a full push is a 1 km/s teleport |

Metres per tick at full push (lat 37°): z15 ≈ 168 m · z17 ≈ 42 m · z19 ≈ 10 m. At z17 a
single tick is longer than a house frontage — "accurate" was impossible by construction.

## Options weighed
- **Drag the pin directly** — precise, but conflicts with map panning and needs a long-press
  handoff; also loses the "walk the dot" feel Ethan likes.
- **Tap-to-step arrows / d-pad** — precise at the cost of speed and a second control.
- **Two-finger precision (second finger halves speed)** — hidden gesture; nobody finds it.
- **Speed slider next to the stick** — one more control on an Operate surface.
- **Keep one stick, fix the physics** — chosen: smoother ticks, a response curve with a
  dead zone, more travel, visible push strength.

## Changes (after)
| what | value | why |
|------|-------|-----|
| tick | 50 ms (20 Hz) | 11 px steps at full push instead of 44; the service path is a flow + one fix push per tick, name/altitude resolution is already settle-debounced |
| curve | 8 % dead zone, then squared | half stick ≈ 22 % speed: the inner half of the travel is a fine range, the rim is still full speed; a resting thumb never drifts |
| travel | 38 dp radius (base 120, knob 44) | 36 % more resolution per unit of thumb movement |
| min speed | 0.1 m/s (only outside the dead zone) | can creep a metre at a time |
| screen speed | 180 px/s | slightly calmer full push |
| max speed | 300 m/s | never a teleport; world-zoom travel still fast |
| feedback | knob tints indigo → hold-amber with push | the user sees how hard they are pushing |
| base | white pill with the floating shadow | same family as the map pills |

Metres per tick after (lat 37°, full push): z15 ≈ 34 m · z17 ≈ 8.6 m · z19 ≈ 2.1 m; at half
stick: z17 ≈ 1.9 m · z19 ≈ 0.5 m.

## Session 2026-08-30: the zoom the stick read was stale
Two plumbing bugs, not physics: the persisted camera was seeded into a variable nothing
read, so a cold-start nudge ran at a fixed z15 until the first pan; and zoom was only
sampled on camera *idle*, so it lagged behind pinches and follow/keep-in-view moves.
`MapViewModel.camera` is now fed from MapLibre's move listener on every frame (plus the
persisted seed); the idle listener feeds only the debounced DataStore save.

## Not done (candidates if it still feels coarse)
- Hold-to-lock a bearing (straight-line runs).
- A zoom-independent absolute mode ("2 km/h walking pace").
- Haptic tick at the dead-zone edge.
