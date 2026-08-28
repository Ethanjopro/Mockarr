# Brief 3 — Record-tab parity: no navigation bar, floating stat card, Pause → Resume/Finish

Status: **confirmed by Ethan 2026-08-28** (plan approved). Supersedes the layout parts of
`brief.md` §3 ("one sheet, one strip", "Flat Over Map", label-over-value) and brief 2's
Record layout. References: `strava/hud-030` (idle), `hud-033` (recording), `hud-037`
(paused: Resume + Finish), `hud-048` (sheet expanded), `map-393` (dropped pin).

## 1. Why
Ethan's call: the Map screen should match Strava's Record screen *exactly*, not be
"translated into Material". Three things blocked that: the bottom navigation bar (Strava
has none — everything is in the drag-up sheet), and three DESIGN.md / `.impeccable`
rules written in brief 1 that forbade the Strava feel (one merged sheet, no shadows,
label-over-value quiet numerals). Two small bugs rode along: the idle trio showed `—`
placeholders, and the hold strip flashed raw coordinates before the place name arrived.

## 2. Decisions
- **Card over sheet.** A floating stat card (strip + trio, 16dp, `surface-container-lowest`,
  4dp shadow) rides `map-edge` above a separate sheet (28dp, drag handle, 8dp shadow).
- **Sheet peek = action row** (Mode · Start 64dp · Add/Edit route); expanded = options list
  ending in *Saved routes ›* and *All settings ›*. **No navigation bar**; Routes, Settings
  and Setup are pushed screens with a back arrow.
- **Pause splits.** One full-width Pause pill (56dp, bold) → Resume (primary) + Finish
  (inverse surface). **Finish = Stop**: the route stays loaded, the Record layout returns.
  Strip while paused: amber "Paused".
- **Value over label**, bold 28sp tabular numerals; trio hidden when nothing is loaded.
  Route loaded: Distance · Duration · Stops. Driving: Time left · Distance left · Speed +
  progress bar; the "1×" speed chip sits in the strip's trailing slot.
- **Hold banner waits.** The strip never shows coordinates: `stripFor` returns null while
  the name resolves and the previous line stays; a 5 s timeout / failure falls back to
  "Holding at dropped pin". The notification follows the same rule.
- **Flair, indigo only.** Shadows on card + sheet, bold pills, 64dp Start. Still no orange,
  no second accent, no iOS chrome.

## 3. States
Idle (strip-only card "Plan a drive") · not set up (error strip + Fix) · route loaded
(Ready strip + trio) · holding (amber strip, name only) · builder (strip-only card, trio in
the sheet) · driving (indigo strip, trio + progress, Pause) · paused (Resume + Finish) ·
waiting at stop · stopping · sheet expanded (card behind the sheet) · Routes / Settings /
Setup pushed with back arrow · light + dark · landscape.

## 4. Verification
Emulator screenshots for every state above in both themes; `emu.sh record` around Play,
Pause and a long-press hold (no frame may contain a coordinate pair); `tab` in `emu.sh`
now means "open that screen" (Map = back to root; Routes/Settings via the sheet rows).
