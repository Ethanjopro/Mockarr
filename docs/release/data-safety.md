# Play Console → App content → Data safety: the answers

Fill the form from this file so it matches `privacy-policy.md` exactly. Re-check both whenever a
backend provider changes (ADR 0002 §3) or a new permission appears in the merged manifest
(`app/build/intermediates/merged_manifest/release/AndroidManifest.xml`).

## Overview questions
| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Stop coordinates and search text leave the device to third-party services. "Collected" in Play's definition includes data transmitted off device even when only processed ephemerally — it must still be declared. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | Every provider is called over HTTPS (`core/routing` clients). |
| Do you provide a way for users to request that their data is deleted? | **No — data is not stored** | Nothing is stored server-side; Play accepts this where the only "collection" is ephemeral processing. Explain in the free-text box: "Mockarr runs no servers and stores nothing off the device." |

## Data types
### Location → Approximate location and Precise location
| Field | Answer |
|---|---|
| Collected? | Yes |
| Shared? | Yes — with third-party service providers (routing, place search, elevation, map tiles) |
| Processed ephemerally? | Yes |
| Required or optional? | Required for core functionality (routing cannot work without stop coordinates) |
| Purposes | App functionality |

Note for the reviewer box: "Coordinates the user places on the map are sent to a routing service
to compute a road route. The device's real GPS position is only used if the user taps 'Start from
my location', and then only as a stop coordinate."

### Personal info / Messages / Photos / Files / Contacts / App activity / Device IDs
All **not collected**. (Search text is sent to the place-search service; Play's "Other user-
generated content" would over-declare here — the searched text is a location query, covered by the
Location entry. If a reviewer disagrees, add "App activity → Other user-generated content: search
queries, ephemeral, app functionality".)

### App info and performance → Crash logs / Diagnostics
**Not collected** by the app. Android vitals in Play Console is Google's own collection under
Google's policy and is not declared here.

## Security practices (shown on the listing)
- Data is encrypted in transit: **Yes**
- You can request that data be deleted: **No** (nothing stored)
- Committed to the Play Families policy: **No** (18+ target audience)
- Independent security review: **No**

## Things that would change these answers
- Adding any analytics or crash SDK → new "Diagnostics" collection, and ADR 0001 requires an in-app
  disclosure first.
- Adding Play Billing for Pro → Google handles purchase data under its own policy; declare
  "Purchase history" only if the app itself stores it (it should not — query entitlement live).
- A keyed provider that logs requests server-side → still "ephemeral" from the app's side, but read
  the provider's retention terms and reflect them on the privacy page.
