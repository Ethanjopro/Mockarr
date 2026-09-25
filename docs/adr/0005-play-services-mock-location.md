# ADR 0005 — Mock Google Play services' fused location, not only the platform providers

Date: 2026-09-24 · Status: **accepted** (Ethan, on a real Pixel 8a: "the real location shows
through once the app is not focused… Fix this immediately").

## Context
Mockarr mocks location through Android's official facility: it registers test providers
(`gps`, `network`, and `fused` on API 31+) with `LocationManager` and pushes fixes to them
once a second from its foreground service. On the emulator every check passed: `dumpsys
location` showed the mocked providers, and Google Maps on the AVD followed a hold for 60 s.

On Ethan's Pixel 8a, Google Maps showed the mocked spot for about three seconds after
Mockarr left the screen and then jumped to the phone's real location, whether holding or
driving. Google Maps, like most apps (games, dating and travel apps), reads Google Play
services' `FusedLocationProviderClient`. That is a separate engine: it blends its own Wi-Fi
and cell positioning with GNSS, and the platform test providers do not control it. The
`fused` test provider replaces only AOSP's `LocationManager.FUSED_PROVIDER`. The emulator
hid this because it has no real Wi-Fi or cell positioning for Play services to fuse.

Play services offers the one supported way to control it:
`FusedLocationProviderClient.setMockMode(true)`. While on, the fused engine reports only the
locations set with `setMockLocation`. It requires the calling app to be the selected mock
location app, which the Setup checklist already requires. It is the same developer facility,
not an anti-detection measure: the fixes still carry the platform's mock flag.

## Decision
1. **`core:mocklocation` depends on `com.google.android.gms:play-services-location`.** It is
   free, not open source; the ADR 0001 addendum allows that when it noticeably improves the
   app, and here it is what makes the core feature work on real phones.
2. **A mock session drives both layers.** Start turns Play services' mock mode on after the
   test providers register. Every push goes to each test provider and to the fused engine.
   Stop turns mock mode off as part of the one release path, so the zero-leak rule
   (`MockSessionService.release()` / `onDestroy` are the only callers) is unchanged.
3. **No Play services, no failure.** On a device without Play services, or if a call fails,
   the session runs on the platform test providers alone, as before, and apps that read
   `LocationManager` still follow.

## Consequences
- An F-Droid build would need to drop this dependency; that channel was already dropped
  (ADR 0002).
- Verification must include a Play-services consumer (Google Maps) with Mockarr in the
  background, on a real device. The emulator cannot reproduce the leak (emulator-verify
  skill).
- If Play services kills or restarts, mock mode lapses until the next push re-arms it
  (`setMockMode` is re-sent on the next session start). Tracked in PROGRESS if seen.
