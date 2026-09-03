# Mockarr — Privacy Policy

*Effective: [fill in the date the policy page goes live] · Contact: [mockarr@… — a dedicated
mailbox, not a personal one]*

This is the source text for the policy page Google Play links to. Host it on a public page (see
`play-launch.md` → Privacy policy hosting); keep this file and the page identical, and update
both whenever a provider changes.

---

Mockarr is an Android app that plays back simulated locations through Android's official mock
location developer feature. It has no accounts, no sign-in, no advertising, and no analytics or
crash-reporting library. This page explains the little data that does leave your device, and why.

## What Mockarr does not do
- It does not collect or store any personal information on our servers — we do not run any servers.
- It does not read your contacts, messages, files, or the location reported to other apps.
- It does not contain advertising or tracking code, and it does not sell or share data for
  advertising.
- It does not hide its mock status from other apps.

## Data that leaves your device, and where it goes
Mockarr needs a few online services to draw the map and plan routes. Each request carries only
what that request needs, is sent over HTTPS, and is used by us only in memory to answer that
request — Mockarr keeps no copy of it off the device.

| What is sent | Why | Sent to |
|---|---|---|
| The coordinates of the stops you place, and the route between them | To fetch a road route and its elevation profile | The **routing service** and the **elevation service** |
| The text you type into search, plus the map's current centre and zoom | To find places near what you are looking at | The **place-search service** |
| The map area you are viewing (tile coordinates) | To download the map tiles for that area | The **map-tile service** |
| Your device's real location (only if you use "Start from my location" and grant the location permission) | To place the first stop where you are | Used on the device; it becomes a stop coordinate and is sent to the routing service like any other stop |

The current providers are listed at the end of this page. They are independent services with
their own privacy policies; Mockarr sends each of them the minimum above and identifies itself
with a user-agent string containing the app name and version. Mockarr's own settings let you
replace the routing service and the map style with servers you run yourself.

## Data stored on your device
Saved routes, settings and the map-tile cache are stored only on your device, in the app's private
storage. Saved routes and settings are included in Android's standard app backup when you have
backup enabled for the device; the tile cache is not. Uninstalling the app deletes everything.

## Permissions
- **Location** (fine/coarse): required by Android to run a location-type foreground service, and
  used to offer "Start from my location". Mockarr never sends your real location anywhere on its
  own.
- **Notifications**: the playback controls in the ongoing notification.
- **Mock location** (Developer Options): what lets Mockarr set the location other apps see.

## Children
Mockarr is not directed at children and is rated for adults; it requires Android Developer
Options to be enabled.

## Changes
When a provider changes or the app starts doing something new with data, this page changes first
and the app's "About" screen credits change with it.

## Current providers
- Routing: [name + link — today: Project OSRM public demo server]
- Place search: [name + link — today: Photon by komoot]
- Elevation: [name + link — today: Open-Meteo]
- Map tiles and style: [name + link — today: OpenFreeMap; map data © OpenStreetMap contributors]
