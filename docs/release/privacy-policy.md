# Mockarr — Privacy Policy

*Effective: 29 September 2026 · Contact: threestreetstudios@gmail.com*

This is the source text for the policy page Google Play links to, published at
https://ethanjopro.github.io/threestreets/mockarr/privacy/ from the `Ethanjopro/threestreets`
repo (see `play-launch.md` → Privacy policy hosting). Keep this file and the page identical, and
update both whenever a provider changes.

---

Mockarr is an Android app by Three Streets Studios that plays back simulated locations through
Android's official mock location developer feature. It has no accounts, no sign-in, no
advertising, and no analytics or crash-reporting library. This page explains the little data that
does leave your device, and why.

## What Mockarr does not do
- It does not collect or store any personal information on our servers — we do not run any servers.
- It does not read your contacts, messages, files, or the location reported to other apps.
- It contains no advertising, analytics or crash-reporting libraries, and it does not sell or
  share data for advertising.
- It does not hide its mock status from other apps.

## Data that leaves your device, and where it goes
Mockarr needs a few online services to draw the map and plan routes. Each request carries only
what that request needs, is sent over HTTPS, and is used by us only in memory to answer that
request — Mockarr keeps no copy of it off the device.

| What is sent | Why | Sent to |
|---|---|---|
| The coordinates of the stops you place, and the route between them | To fetch a road route and its elevation profile | The **routing service** and the **elevation service** |
| The coordinates of a stop you tap on the map | To show the place's name instead of "Stop 2" | The **place-search service** |
| The text you type into search, plus the map's current centre and zoom | To find places near what you are looking at | The **place-search service** |
| The map area you are viewing (tile coordinates) | To download the map tiles for that area | The **map-tile service** |
| Your device's real location — only when you tap "Go to my location" or start a drive from "My location", and only after you grant the location permission | To centre the map on you, or to start the drive where you are | Read from Android's location service on the device; it becomes a stop coordinate or map position and is sent like any other |

The current providers are listed at the end of this page. They are independent services with
their own privacy policies; Mockarr sends each of them the minimum above and identifies itself
with a user-agent string containing the app name and version.

## Google Play services on your device
While a drive or a held location is running, Mockarr hands the simulated positions to Google Play
services on your device, using its official mock mode, so that apps which get their location from
Play services see the same simulated drive. Only the simulated positions are passed on, and only
on the device; Mockarr sends nothing else to Google. When the session ends, mock mode is switched
off.

## Data stored on your device
Saved routes, the route you are building, settings and the map-tile cache are stored only on your
device, in the app's private storage. Saved routes and settings are included in Android's standard
app backup when you have backup enabled for the device; the tile cache is not. Uninstalling the
app deletes everything.

## Permissions
- **Location** (fine/coarse): required by Android to run a location-type foreground service, and
  used for "Go to my location" and "My location" starts. Mockarr never sends your real location
  anywhere on its own.
- **Notifications**: the playback controls in the ongoing notification.
- **Mock location** (Developer Options): what lets Mockarr set the location other apps see.

## Children
Mockarr is not directed at children and is rated for adults; it requires Android Developer
Options to be enabled.

## Changes
When a provider changes or the app starts doing something new with data, this page changes first
and the app's "About" screen credits change with it.

## Contact
Questions about this policy: threestreetstudios@gmail.com

## Current providers
- Routing and place search: Geoapify (https://www.geoapify.com/) — with the public OSRM demo server
  (https://project-osrm.org/) and Photon by komoot (https://photon.komoot.io/) as fallbacks
- Elevation: Terrain Tiles on AWS Open Data (Mapzen data, https://registry.opendata.aws/terrain-tiles/)
- Map tiles and style: OpenFreeMap (https://openfreemap.org/); map data © OpenStreetMap contributors
- Simulated-location delivery to other apps: Google Play services, on the device (see above)
