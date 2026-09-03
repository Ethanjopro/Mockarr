# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

Android first. An iOS port is possible later (ADR 0001) — iOS has no mock-location API, so
that would be a different product shape; the insurance today is keeping `core:*`
platform-neutral. Design language stays Material 3 on Android.

## Users
Primary: enthusiasts who want their phone to report a different location for a while so
that *other* apps (games, travel and dating apps, map apps, privacy) follow along. They are
not developers; they will read a four-item setup checklist once and then expect a
consumer-grade map app. Ease and believability matter most.

Secondary (same UI, deeper settings): app developers and QA verifying their own app's
geofences, navigation, or location UI on a test device — precision and repeatability.

## Product Purpose
Drive a fake route through the real world. Pick points on a map, get a realistic road
route, press Play; the device's reported location drives that route with human-like
acceleration, corner slow-downs, wait times at stops and GPS-style noise, visible in any app
that reads location. Success: a new user goes cold-start → driving in under a minute from
in-app instructions alone, and the drive looks like a real drive in whatever app they open.

## Positioning
Two claims, weighted equally and told in this order:
1. **Realistic, road-true playback** — OSRM routes, kinematic braking before turns, graceful
   stops (never a teleport), Gaussian GPS jitter, per-stop wait times, live 0.25×–4× speed.
2. **Open stack, no keys, no accounts** — MapLibre, OpenStreetMap, OpenFreeMap tiles,
   OSRM, Photon, Open-Meteo; self-hostable routing and tiles. **Under review (ADR 0001):**
   the open-source stance may narrow and light monetisation (ads or similar) is possible;
   until decided, do not put "open source / no tracking" claims in new user-facing copy.
   What survives any outcome: no anti-detection, and nothing phones home or shows ads
   without being disclosed in-app. **Tooling policy (Ethan, 2026-08-28):** the priority is
   cheap/free over open-source — a free but non-open API or service is fine when it
   noticeably improves the app.
Realism is the feature; honesty is the trust story.

## Operating Context
- Uses Android's official "Select mock location app" developer facility; registers test
  providers via `LocationManager`. Nothing patched, hooked, or rooted.
- Typical session: search or tap to place stops → route → Play → switch to another app and
  leave Mockarr running in the background (foreground service with notification controls).
- Pin mode (long-press to hold a location) and a thumbstick for nudging while held.
- Saved routes replay offline. Public OSRM demo server is rate-limited and driving-only;
  self-hosted OSRM unlocks walking/cycling.
- Verification happens on the emulator via `scripts/emu.sh`; reference devices are Pixel
  and Samsung across API 26 / 31 / 34+.

## Capabilities and Constraints
- Information architecture (2026-08-28): the Map is the root — no navigation bar; Saved
  routes, Settings and Setup are pushed from the Map sheet's drag-up list (Strava's Record
  screen). Search direction: `docs/research/search-rnd.md`.
- Device floor API 26; dynamic color only on 31+, so a static brand scheme is required.
- Kotlin + Jetpack Compose + Material 3 + MapLibre; Hilt; Room; DataStore. Kotlin comes from
  AGP. Pure-Kotlin simulation core stays Android-free.
- **Never** add anti-detection features (hiding mock status from other apps). Declared in the
  README's fair-use note; this is a product commitment, not just a rule.
- Zero-leak mock ownership: hold/stop transitions must never let the real location leak.
- Terminology: *stop* (a waypoint), *hold* / *holding* (pinned location), *wait* (pause at a
  stop), *Play / Pause / Stop* for playback, *Following* (camera tracks the position).
- Decided (ADR 0002, provisional): Google Play is the primary channel via a personal developer
  account; free core with a one-time Pro unlock later, no ads. Backend provider choice is open
  until the production rollout (ADR 0003). Undecided: licence, iOS scope. Repo private until the
  licence is chosen. Release paperwork lives in `docs/release/`.

## Visual Baseline
Strava iOS (Mobbin, Jul 2026) is the UX/UI reference, translated into Material 3 — sheet
model, label-over-value stats, status strips, list cards, icon-grid settings. Palette:
**indigo**, never Strava orange. Curated refs: `docs/design/refs/refs.md`; brief:
`docs/design/brief.md`; tokens: `app/src/main/kotlin/dev/mockarr/app/ui/theme/`.

## Brand Commitments
- Name "Mockarr" is in use; whether the name and the current green-pin launcher icon are
  settled brand assets is **undecided** — do not treat them as fixed or replace them without
  asking.
- Voice (established in-app, keep): plain, specific, unapologetic; explains consequences
  ("Mocking isn't set up yet — routes will draw, but playback won't move your location.").

## Evidence on Hand
- `docs/design/audit-2026-08.md` + `audit-2026-08/` — 40 light/dark screenshots of every
  state, design critique (25/40) and code audit (12/20).
- `docs/design/refs/refs.md` — Mobbin reference collection (being filled by Ethan).
- `fastlane/metadata/android/en-US/images/phoneScreenshots/` — current store screenshots.
- No testimonials, user research, download numbers, or press. Do not fabricate any.

## Product Principles
1. **Believable over impressive.** Every visual and motion choice should make the drive read
   as real to the user watching it in another app.
2. **One-minute onboarding.** Anything between install and Play is friction; the checklist
   is the only tutorial.
3. **The map is the product.** Chrome earns its place by serving the drive; when it doesn't,
   it recedes.
4. **Honest tool.** No anti-detection, no dark patterns, no hidden telemetry; whatever
   the app does with data or ads is visible in the product, not just the README.
5. **Consumer-grade, developer-deep.** Defaults work for enthusiasts; settings depth serves
   testers without leaking into the primary flow.

## Accessibility & Inclusion
No product-specific standard established. Known gap from the 2026-08 audit: map-only
gestures (add stop, hold, marker menu, thumbstick) have no TalkBack / switch-access path;
all copy is inline and unlocalised. Treat both as work to do, not accepted limitations.
