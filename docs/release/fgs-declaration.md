# Play Console → App content → Foreground service: the declaration

Mockarr targets API 34+ and runs one foreground service, `MockSessionService`, with
`foregroundServiceType="location"` (`app/src/main/AndroidManifest.xml`). Play requires a
description and a demo video per type.

## Type: Location
**Description (paste as is):**

> Mockarr plays back a user-chosen route as the device's mock location using Android's
> official mock-location developer feature. The location-type foreground service runs only
> while the user has pressed Play (or holds a pinned location); it publishes simulated fixes
> to the platform test providers about once a second so that other apps see a continuous,
> realistic drive even while Mockarr is in the background or the screen is off. The ongoing
> notification shows progress and offers Pause / Resume / Stop; the service stops itself when
> playback ends or the user stops it. The service is never started without a user action, and
> it does not read or upload the device's real location.

**Why this type is required:** Android 14+ mandates a type for every foreground service and
`location` is the only type whose permitted use ("continued location access while the app is not
in the foreground") matches publishing location fixes. Alternatives considered: `dataSync` (not
location work), `specialUse` (Play asks for a justification and it is worse-fitting).

## Video (unlisted YouTube link, 20–40 s, phone portrait)
Record on the emulator once the release build is installed:

```sh
scripts/emu.sh boot && scripts/emu.sh launch && scripts/emu.sh settle
scripts/emu.sh record 35 /tmp/mockarr-fgs.mp4    # then drive the steps below while it records
```
Steps to show, in order (narration not needed; captions optional):
1. App open on the map with a two-stop route ready ("Ready to drive").
2. Tap **Play** → the stat card appears; the notification shade shows the ongoing notification.
3. Press Home → open the notification shade → tap **Pause**, then **Resume**.
4. Open another app that shows location (the emulator's Maps, or Mockarr's own map after
   returning) to show the position moving.
5. Return to the shade → **Stop** → the notification disappears.

Upload as unlisted, paste the link in the declaration. Keep the file in `docs/release/` locally
only if it is small; do not commit videos.

## Also declared on the same page
- **Full-screen intent**: not used.
- **Location permissions** (separate declaration if Play asks): Mockarr requests foreground
  location only (`ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`, no
  `ACCESS_BACKGROUND_LOCATION`); the foreground service is what keeps playback alive in the
  background.
