PLAY STORE, MONEY, AND THE LLC QUESTION — HONEST NOTES FOR ETHAN
================================================================
Written 2026-09-03. Plain text on purpose: read it once end to end, then use
play-launch.md as the checklist. Facts about Play/Texas/IRS were checked on
that date; anything with a date or a dollar amount can drift — verify in the
Console or on the .gov page before acting on it. I am not a lawyer or a CPA.


1. THE SHORT VERSION
--------------------
- Go with a personal developer account. You are right that the LLC is more
  work than the app justifies today. Nothing you do now closes the LLC door.
- Ship free, no ads, and keep the honest-tool story intact. Add a one-time
  "Pro" unlock later if people actually use the thing.
- The real risk to the launch is not Google's review. It is the backends:
  the public OSRM and Photon servers you default to are demo instances that
  throttle heavy use. Decide what to do about that before the production
  rollout — not before closed testing, which is tiny traffic.
- The code is now shaped so swapping any backend is one class and one DI
  binding, and the store paperwork names providers by role, so a swap is
  never a policy re-review. That was your main worry; it is handled.
- Budget: $25 once. Time: about 3–4 weeks, most of it the mandatory 14-day
  closed test, which you run in parallel with the polish work.


2. WHY A PERSONAL ACCOUNT IS FINE
---------------------------------
What it costs you:
- Your legal name shows on the listing next to the display name. Your home
  address and phone do NOT show unless you become an EU "trader" (which
  happens when you sell Pro to EU users). That is a Pro-launch problem.
- The 12-testers-for-14-days requirement before production access. Annoying
  but it doubles as real device coverage you have no other way to get.
- If you later want an organization account, you create a second account
  and transfer the app. It is a form, it is free, and Play App Signing means
  the signing key stays valid. Ratings, reviews and installs move with it.

What people get wrong:
- "Personal accounts can't earn money." False. Add a payments profile, fill
  the tax form (W-9 as a US individual), receive payouts, report on
  Schedule C. Google is the merchant of record for Play purchases, so sales
  tax and VAT are Google's job, not yours.
- "I need an LLC for the $25 account." No. The D-U-N-S number is only for
  organization accounts.


3. THE LLC: WHEN, NOT WHETHER
-----------------------------
Form one when any of these becomes true:
  a) Pro is about to ship and you would rather the EU trader disclosure show
     a business address than your home. (A registered-agent or virtual
     mailbox address on the LLC solves that; a personal account cannot.)
  b) The app nets more than a few thousand dollars a year. At that point
     ~$300 once plus a CPA hour is cheap, and separate books stop being a
     chore and start being useful.
  c) Someone wants a contract: a QA team buying seats, a sponsor, a company
     that wants a self-hosted build. Entities want to contract with entities.
  d) You want to publish a second app without a second 14-day test
     (organization accounts are exempt).

Liability, honestly: modest but not zero. The realistic bad day is someone
breaking a service's terms with a spoofed location and blaming the tool, or
a QA user's app misbehaving. Day-one mitigation is the fair-use note that is
now in the listing and in About; an LLC is the second layer, later. The
thing that keeps an LLC's shield meaningful is boring: separate bank
account, app money in, app costs out, no mixing.

Texas, when the day comes (all of this is a 20-minute online form; skip the
"formation services"):
  1. Name check on SOSDirect. "Mockarr LLC" or a holding-style name like
     "<Surname> Software LLC" so future apps fit. The Play display name can
     differ from the legal name.
  2. Registered agent: yourself at a Texas street address (public record)
     or a commercial agent, roughly $50–150/yr, if you want your home
     address off the record.
  3. Certificate of Formation, Form 205, $300 (+2.7% by card). Approval in a
     few business days.
  4. EIN from irs.gov, free, instant. Never pay for this.
  5. Single-member operating agreement (template; not filed).
  6. Business bank account with the EIN + certificate.
  7. Every year by 15 May: the Texas Franchise Tax Public Information
     Report. Under the no-tax-due threshold (~$2.65M revenue) you owe $0,
     but the PIR itself is mandatory or the LLC forfeits. Federal: a single-
     member LLC is "disregarded" — the app's profit goes on your 1040
     Schedule C plus self-employment tax. Texas has no personal income tax.
     BOI/FinCEN reporting: domestic LLCs are exempt as of the August 2026
     final rule; nothing to file (re-check if that rule changes).
  8. Only think about an S-corp election when net profit passes roughly
     $60–80k/yr. Before that it costs more than it saves.
Year-one cost ~$300–450, then ~$0–150/yr.


4. MONEY: FREE CORE, PRO UNLOCK, NO ADS
---------------------------------------
Why not ads: they need an SDK that phones home, which collides with the
"nothing hidden" promise, forces a Data safety rewrite, and pays almost
nothing for a tool people open briefly. Why not a paid app: Play makes
"free" irreversible and a paid listing kills the 14-day test recruitment.

Pro as a one-time non-consumable purchase (Google Play Billing Library 8+,
required for anything published after 31 Aug 2026):
- Free tier keeps the entire core loop — search, route, Play, pin mode,
  background playback, saved routes. If the free tier feels crippled, the
  reviews will say so and the honest-tool story dies.
- Pro is the power-user layer: unlimited saved routes (free: a handful),
  GPX/KML import and export, walking/cycling profiles on a hosted engine,
  scheduled start, style/realism presets, wait-time templates.
- Price it once, low: $4–8. Subscriptions only make sense if Pro depends on
  a server you pay for monthly (hosted routing for walk/bike would be one).
- Fee: Google's tier for small developers has been 15% on the first $1M; a
  June 2026 change starts moving the US/EEA/UK toward "10% + billing fee".
  Read the current terms in the Console the day you enrol; do not plan on a
  number from this file.
- Paperwork when Pro ships: payments profile, W-9, EU trader declaration
  (see 3a), a Data safety re-check (Google handles purchase data; the app
  should not store it — query entitlement live).
- Code: keep all billing in the app module behind one Entitlements seam
  (isPro as a StateFlow; true in debug builds). core:* never knows Pro
  exists.

Do not build any of this until the free app has a month of production data.
You will learn which features people ask for, which is the only good way to
choose what goes behind Pro.


5. THE BACKEND DECISION — DECIDED 2026-09-03 (ADR 0003): GEOAPIFY DEFAULT,
   PUBLIC SERVERS AS FALLBACK, AWS TERRARIUM TILES FOR ELEVATION. GOOGLE
   REJECTED (Google-map-only terms, 30-day cache limit, most expensive).
   Full numbers: docs/research/backend-cost-analysis.md. Original notes:
------------------------------------------------------------------
Today: routing = router.project-osrm.org, search = photon.komoot.io,
elevation = api.open-meteo.com, tiles = tiles.openfreemap.org. OpenFreeMap
and Open-Meteo are fine: free, no key, meant for public use. The other two
say, in their own words, that extensive usage is throttled and they do not
guarantee availability. Closed testing will never hit that. A real launch
might, and "routing failed" is the first thing a new user would see.

Options, cheapest first:

  Keep the demo servers
    Cost: $0.  Terms: throttled at the operator's discretion, no uptime
    promise.  Fit: fine for testing; a gamble for production. If you do
    this, at least make the failure friendly ("routing is busy, try again
    or set your own server") and keep the BYO-server setting prominent.

  Geoapify (routing + geocoding in one)
    Cost: $0 up to 3,000 credits/day; commercial use allowed on the free
    plan; attribution required ("Powered by Geoapify").  One request ≈ one
    credit.  With one route request per edit (you already debounce 500 ms)
    that budget covers on the order of hundreds of daily active users.
    Fit: simplest single swap — one RouteProvider + one Geocoder class.

  OpenRouteService (HeiGIT, academic)
    Cost: $0 up to ~2,500 requests/day across all endpoints.  Fit: solid,
    smaller quota, geocoding is Pelias-based. Good fallback candidate.

  Stadia Maps (Valhalla routing + geocoding + tiles)
    Cost: 200k credits/month free BUT the free tier is non-commercial;
    commercial starts at $20/month.  A Pro unlock makes the app commercial.
    Fit: best quality of the bunch; costs money the day Pro ships.

  Self-host OSRM + Photon
    Cost: $10–40/month VPS plus your evenings.  Region-limited unless you
    buy a big box (planet-scale OSRM car wants ~64 GB RAM to build).
    Fit: not for v1.

What I would do: launch closed testing on the demo servers unchanged; watch
whether routing errors show up at all; before the production rollout, add a
Geoapify (or ORS) RouteProvider + Geocoder as the *default* and keep the
public OSRM/Photon URLs as the BYO option behind Settings. Key handling:
secrets.properties (git-ignored) -> BuildConfig; restrict the key by
Android package name in the provider dashboard where offered. Record the
choice, key handling, quota and terms in ADR 0003 — the tooling policy in
ADR 0001 explicitly allows a free non-open API when it improves the app.

Post-launch flexibility, concretely: RouteProvider, Geocoder and
ElevationProvider are interfaces in core:routing; the app never names a
concrete client outside app/di/RoutingModule.kt. Privacy policy and Data
safety describe providers by role and list the current names only on the
hosted policy page. So a swap = new class + one binding + one credits string
+ an app update + editing the policy page. No re-declaration, no re-review.


6. WHAT GETS MOCK-LOCATION APPS REJECTED (AND HOW YOU AVOID IT)
---------------------------------------------------------------
Mock-location apps are allowed on Play; dozens are live. The ones that get
pulled advertise cheating ("for Pokémon Go", "trick dating apps"), hide
mock status, or request permissions they cannot justify. Your protection:
- The listing and the About screen describe a testing tool and carry the
  fair-use note. Never name a third-party app as a target anywhere public.
  (PRODUCT.md mentions games/dating apps as *user motivations* — that is
  fine internally; it must never reach copy.)
- No anti-detection, ever. This is already a hard rule; it is also what
  keeps the listing safe.
- Only the permissions you use. No background-location permission — the
  foreground service is what keeps playback alive, and the Console
  declaration explains exactly that.
- Reviewers cannot see playback without Developer Options. The "App access"
  instructions in play-launch.md exist so they do not reject for "app does
  nothing".


7. THINGS THAT WILL BITE IF IGNORED
-----------------------------------
- Policy emails go to the account owner's inbox and have deadlines; miss one
  and the app is removed until you comply. Use the dedicated mailbox and
  check it.
- Target API moves every August (36+ for new apps from 31 Aug 2026; you are
  on 37). 16 KB page support is required; MapLibre 13.x has it. Both are
  checked at upload — read the warnings.
- Keystore loss: with Play App Signing the *app* key is safe with Google;
  losing the *upload* key means a support request and a wait. Back it up
  twice.
- The pre-launch report will look useless (the robo crawler only sees the
  checklist). That is expected; read it for crashes only.
- Reviews are your only telemetry. Reply to every one for the first month.
- Screenshots in the repo are from the old UI. Reshoot before upload.
- The README still says "no API keys or accounts needed today" — true — but
  it also lists the stack as if it were a promise. Once ADR 0003 picks a
  keyed provider, update README/PRODUCT.md the same day.


8. WHAT I AM NOT SURE ABOUT (VERIFY BEFORE RELYING ON IT)
---------------------------------------------------------
- Whether Play asks a *personal* account for any additional public contact
  details on the listing beyond name + email in your region: check the
  developer-page preview before publishing.
- Whether Google's 2026 fee change has reached the small-developer tier by
  the time Pro ships: read the Console enrolment page.
- Whether Geoapify's free-tier terms still allow commercial use on the day
  you pick it (they did on 2026-09-03).
- Texas franchise-tax threshold and the BOI exemption are as of 2026; both
  are legislature-dependent.


9. IF I WERE YOU, THIS WEEK
---------------------------
1. Generate the upload keystore, back it up, build the bundle, run the
   release APK on the emulator.  (An afternoon.)
2. Open the personal account, get ID-verified, create the app, paste the
   declarations from docs/release/.  (An evening; verification may take a
   day.)
3. Put the privacy page on GitHub Pages.  (30 minutes.)
4. Internal test on your own phone; fix what the upload flags.
5. Recruit 15 testers and start the 14-day clock. Use those two weeks for
   the screenshot reshoot, the "routing is busy" state, and the backend ADR.
6. Apply for production access; staged rollout; reply to reviews.
