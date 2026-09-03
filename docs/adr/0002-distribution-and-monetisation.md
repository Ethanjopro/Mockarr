# ADR 0002 — Distribution on Google Play, free core with a later Pro unlock

Date: 2026-09-03 · Status: **provisional** (the backend-provider decision it depends on is still
open; see "Deferred").

## Context
ADR 0001 left distribution, monetisation and licence undecided. Ethan now wants the app on Google
Play and, later, a way to earn something from it without ads or a paid-app price. Play's rules
shape the code: a signed App Bundle, R8, a privacy policy, a Data safety declaration, a foreground
service declaration, target API ≥ 36, 16 KB page support, and — for a personal developer account
created after Nov 2023 — a 14-day closed test with 12 opted-in testers before production.

The public routing/search backends Mockarr defaults to (`router.project-osrm.org`,
`photon.komoot.io`) are demo instances whose operators throttle heavy use. A Play launch brings
that traffic. Ethan's stated priority: **stay free to change the API after launch**.

## Decision
1. **Google Play is the primary distribution channel**, via a *personal* developer account (an LLC
   and organization account are deferred; triggers for revisiting are in
   `docs/release/play-store-recommendations.md`). GitHub Releases APKs remain a secondary channel
   until the licence is chosen. F-Droid is not pursued while the repo is private.
2. **Free core, one-time "Pro" in-app unlock later.** No ads by default, no paid-app price (Play
   makes "free" irreversible, which suits this model). Everything in the core loop stays free:
   search → route → Play, pin mode, background playback, saved routes. Pro candidates are the
   power features (unlimited saved routes, GPX import/export, walking/cycling on a hosted engine,
   scheduling, style/realism presets). Pro must never touch mock honesty — the two firm commitments
   from ADR 0001 hold: **no anti-detection, no hidden telemetry**. No Play Billing code lands until
   Pro is designed; when it does it lives in `app` only, behind one `Entitlements` seam.
3. **Backends are swappable by construction.** The app depends on `RouteProvider`, `Geocoder` and
   `ElevationProvider` (all in `core:routing`, pure Kotlin); concrete clients (`OsrmRouteProvider`,
   `PhotonGeocoder`, `OpenMeteoElevationClient`) are bound in `app/di/RoutingModule.kt`. Changing a
   provider is one new class, one binding, one credits string. Store-facing documents (privacy
   policy, Data safety) describe providers **by role**, naming the current ones only on the hosted
   policy page, so a swap is an app update plus a page edit, never a policy re-review.
4. **Release build**: R8 with `app/proguard-rules.pro`; upload key from a git-ignored
   `keystore.properties` or `MOCKARR_UPLOAD_*` env vars, unsigned when absent so clean clones
   build; Play App Signing holds the app key; `versionCode` is derived from `versionName`
   (`AndroidConfig.versionCode`, 0.2.0 → 200).
5. **Copy**: store and in-app copy drops "fully open stack / no tracking / no ads" (ADR 0001) and
   never names third-party apps as targets — the listing describes a testing tool with a fair-use
   note. "No accounts, no sign-in" stays because it is true.
6. **No crash-reporting SDK.** Play Console's Android vitals provides crashes/ANRs without any
   code that phones home; revisit only with an in-app disclosure per ADR 0001.

## Consequences
- New: `docs/release/` (launch runbook, recommendations, privacy policy, Data safety inventory,
  foreground-service declaration), `app/proguard-rules.pro`, backup rules, the two interfaces.
- `PRODUCT.md` "Undecided" list shrinks: distribution = Play (personal account), monetisation
  direction = free + Pro unlock. Licence and iOS scope stay open.
- Every release: bump `versionName`, add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`,
  tag, bundle, internal track, then production (runbook in `docs/release/play-launch.md`).

## Deferred
- **Backend provider choice** (keep demo servers / Geoapify / OpenRouteService / Stadia /
  self-host) — decide before the production rollout, not before closed testing; record in ADR 0003
  with keys, quotas and terms (ADR 0001 addendum).
- Pro feature set, price, and the `Entitlements` implementation — its own ADR when Pro is designed.
- Licence; LLC/organization account (see the recommendations doc for triggers).
