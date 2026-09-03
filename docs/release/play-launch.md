# Google Play launch runbook

Checkbox-style, in order. Written 2026-09-03 against Play Console as it was then — rules change
(target API each August, Billing Library every two years, testing requirements), so when a step
disagrees with what the Console shows, the Console wins and this file gets a fix. Honest advice
and trade-offs live in `play-store-recommendations.md`; this file is the *what to click*.

Path: **personal developer account** (ADR 0002). The organization/LLC path is Appendix A.

## 0. Before touching the Console
- [ ] You are 18+, and you have a Google account you are comfortable owning this app forever (it
      cannot be moved between accounts without a transfer request; use a dedicated one if in doubt).
- [ ] Create a dedicated contact mailbox (e.g. a `mockarr@` address or a Gmail alias). It is shown
      publicly on the listing and receives policy emails with deadlines.
- [ ] Trademark sanity check (10 min): search "Mockarr" on Play and on USPTO TESS. A live conflict
      means renaming *now*, before the name is on a listing and an application ID.
- [ ] Decide the developer name shown on the listing (your legal name is verified; the display name
      can be "Mockarr").

## 1. Repo readiness (done this round unless unchecked)
- [x] R8 release build with `app/proguard-rules.pro`; backup rules; `versionCode` derived from
      `versionName` (`AndroidConfig.versionCode`).
- [x] Store copy without open-source/no-tracking/no-ads claims and without third-party app names
      (`fastlane/metadata/android/en-US/`).
- [x] `docs/release/privacy-policy.md`, `data-safety.md`, `fgs-declaration.md`.
- [x] Backend seams: `RouteProvider`, `Geocoder`, `ElevationProvider` interfaces.
- [ ] **Upload keystore** (once, on your machine, never in the repo):
  ```sh
  keytool -genkeypair -v -keystore ~/Keys/mockarr-upload.jks -alias mockarr-upload \
    -keyalg RSA -keysize 4096 -validity 9125
  ```
  Then create `keystore.properties` at the repo root (git-ignored):
  ```
  storeFile=/Users/<you>/Keys/mockarr-upload.jks
  storePassword=…
  keyAlias=mockarr-upload
  keyPassword=…
  ```
  Back the `.jks` + passwords up in a password manager and one offline copy. Losing the upload key
  is recoverable (Play lets you register a new one with a support request) but slow.
- [ ] `scripts/gradle :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
      `scripts/gradle build` must stay green *without* `keystore.properties` too.
- [x] Verify the release build on the emulator (not just debug) — done 2026-09-03 with
      `scripts/gradle :app:assembleRelease` + `scripts/emu.sh installapk` (debug-signed on the fly):
      search → route → Play → notification Pause/Resume/Stop → Settings → Setup all pass under R8.
      Repeat on a real phone from the internal-test link (Play-signed build) before closed testing.
- [x] Screenshots from the current UI (route builder, driving, setup checklist, settings) in
      `fastlane/metadata/android/en-US/images/phoneScreenshots/` and a placeholder feature graphic
      at `images/featureGraphic.png` (indigo + the driving screenshot; redo when the brand is settled).
- [ ] 512×512 PNG icon at `images/icon.png`: Android Studio → right-click `res` → New → Image
      Asset → Launcher Icons → reuse `ic_launcher_foreground` — it writes `ic_launcher-playstore.png`
      at the app root; move it there. (The icon itself is an undecided brand asset — PRODUCT.md.)
- [ ] Bump `versionName` in `app/build.gradle.kts`, add
      `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, tag `v<versionName>`.

## 2. Privacy policy hosting
Play needs a public URL. The main repo is private, so:
- [ ] Create a public repo `mockarr-site` (or a branch of a public docs repo) with GitHub Pages
      on; put `privacy-policy.md` there as `privacy.md` (Pages renders Markdown) → URL like
      `https://<user>.github.io/mockarr-site/privacy`.
- [ ] Fill in the placeholders (date, contact, current providers). Keep the repo copy and the page
      identical; the app's About credits must list the same providers.

## 3. Developer account ($25 once)
- [ ] play.google.com/console → Create account → **Personal**. Legal name as on your ID, address,
      phone (verified by SMS), the contact email from §0.
- [ ] Identity verification: government ID upload; usually hours, can be days. The account is
      read-only until it passes.
- [ ] Developer page: display name "Mockarr", the contact email, (optional) website = the Pages site.
- [ ] Note the account's creation date: personal accounts created after 13 Nov 2023 must complete
      the closed-test requirement (§6) before production access is granted.

## 4. Create the app
- [ ] All apps → Create app: name **Mockarr**, default language en-US, **App** (not game),
      **Free** (irreversible — fine, Pro is an in-app unlock later), accept the declarations.
- [ ] Set up your app (the dashboard checklist) — every item below is one of its rows.

## 5. App content declarations (Policy → App content)
| Row | Answer | Source |
|---|---|---|
| Privacy policy | the Pages URL | §2 |
| App access | "All functionality is available without special access" **plus** instructions: "Testing requires Android Developer Options → Select mock location app → Mockarr. Steps: Settings → About phone → tap Build number 7× → Settings → System → Developer options → Select mock location app → Mockarr." | reviewers cannot see playback otherwise |
| Ads | No, the app has no ads | ADR 0002 |
| Content rating | IARC questionnaire → Utility/Productivity; no violence, no user interaction, no sharing of location *between users* | — |
| Target audience | 18 and over only (keeps the app out of the Families policy; Developer Options is not a children's feature) | — |
| News app | No | — |
| COVID-19 contact tracing | No | — |
| Data safety | copy from `data-safety.md` | — |
| Government app | No | — |
| Financial features | None | — |
| Health | None | — |
| Foreground service types | **Location** → description + video from `fgs-declaration.md` | manifest |
| Advertising ID | Does not use | merged manifest has no `AD_ID` |

## 6. Store listing (Grow → Store presence → Main store listing)
- [ ] App name "Mockarr"; short description ≤ 80 chars and full description ≤ 4000 chars from the
      fastlane files (paste; keep them the source of truth).
- [ ] Icon 512×512, feature graphic 1024×500, phone screenshots (2–8; 16:9 or 9:16, 320–3840 px).
      7-inch/10-inch tablet screenshots are optional; skip unless the UI is verified on tablets.
- [ ] Category **Tools**; tags: mock location, GPS testing, developer tools. Contact email and
      website.
- [ ] Do **not** mention specific third-party apps, games or dating apps anywhere in the listing —
      mock-location apps are accepted on Play, listings that advertise cheating are not.

## 7. Testing tracks
- [ ] Release → Testing → **Internal testing**: create an email list (you + a couple of devices),
      upload the AAB, Play App Signing: accept "let Google manage and protect your app signing key"
      (the uploaded bundle is signed with your upload key; Google re-signs with the app key).
- [ ] Fix anything the upload flags: target API, 16 KB page support, missing declarations, and
      the pre-launch report (robo crawler; it will only ever see the setup checklist because it
      cannot select a mock-location app — check it for crashes and accessibility warnings only).
- [ ] Install from the internal-test link on a real phone: mock selection, notification actions,
      background playback, screen-off — the Play-signed build must behave exactly like the local one.
- [ ] **Closed testing** (required for production access on new personal accounts): create a track
      (Alpha), add an email list or a Google Group, upload the same bundle, opt-in URL.
  - Need **≥ 12 testers opted in continuously for 14 consecutive days**; a tester who opts out
    resets their own clock. Recruit 15–18 to be safe: friends/family with Android phones,
    r/androidapps "beta testers wanted" threads, r/androiddev, Mastodon/OSM communities (the
    OpenStreetMap stack is a genuine draw), a Discord you are in. Send them the one-paragraph brief
    in Appendix B. Never pay a "12 testers" service — Google looks for that pattern.
  - Ask explicitly for at least one Samsung and one Pixel; watch Android vitals for crashes/ANRs.
- [ ] After 14 days: Dashboard → **Apply for production access**. The form asks what you tested,
      how testers were recruited, what feedback you acted on — answer concretely (it is reviewed by
      a person; a few days).

## 8. Production
- [ ] Release → Production → create release from the *same* bundle (or a fix release); countries:
      all; **staged rollout 20 %**.
- [ ] Review takes hours to ~7 days for a first release (location permission + foreground service
      declarations make this a manual review; that is normal).
- [ ] 20 % → 50 % → 100 % over about a week, halting on any vitals regression.
- [ ] Reply to every review in the first month — it is the only feedback channel (no telemetry).

## 9. Every later release
1. Bump `versionName`; write `changelogs/<versionCode>.txt`; update the privacy page if a provider
   changed; update About credits.
2. `scripts/gradle build` green → `:app:bundleRelease` → emulator check of the release APK.
3. Tag `v<versionName>`, push, CI green.
4. Upload to internal → production (staged). Fastlane `supply` can push metadata/screenshots once a
   service account exists (§10).
5. Calendar: target-API deadline every 31 August; Billing Library deadline every two years once
   Pro exists; policy emails always have a deadline in the first paragraph.

## 10. CI upload (wire only after the first manual upload succeeds)
Play's API cannot create the app or the first release. After that:
- Google Cloud project → service account → JSON key → Play Console → Users and permissions →
  invite the service account with "Release to testing tracks" only.
- GitHub secrets: `PLAY_SERVICE_ACCOUNT_JSON`, `MOCKARR_UPLOAD_STORE_FILE` (base64 of the jks,
  decoded in the job), `MOCKARR_UPLOAD_STORE_PASSWORD`, `MOCKARR_UPLOAD_KEY_ALIAS`,
  `MOCKARR_UPLOAD_KEY_PASSWORD` — the convention plugin already reads these env vars.
- Job sketch (`.github/workflows/release.yml`, on tag `v*`): checkout → JDK 21 →
  `./gradlew :app:bundleRelease` → `r0adkll/upload-google-play@v1` with
  `track: internal`, `packageName: dev.mockarr.app`, `releaseFiles: app/build/outputs/bundle/release/app-release.aab`,
  `mappingFile: app/build/outputs/mapping/release/mapping.txt`,
  `whatsNewDirectory: fastlane/metadata/android/en-US/changelogs`.

## Appendix A — Organization account via a Texas LLC (deferred; see recommendations §LLC)
1. Form the LLC (SOSDirect Form 205, $300) → EIN (IRS, free) → business bank account.
2. D-U-N-S number (dnb.com, free, up to 30 days; refuse the paid upsell).
3. Play Console → Create account → **Organization**: legal name, D-U-N-S, address, verified
   phone/email, authorised representative ID, formation documents.
4. Exempt from the closed-test requirement. The existing app can be moved with Play's free app
   transfer form (Play App Signing keeps the signing key; installs, ratings, reviews carry over).

## Appendix B — Tester brief (paste into the invite)
> Mockarr is an Android app that plays a fake drive through your phone's location using Android's
> official "mock location" developer setting — for testing location apps. I need ~15 people to
> install it from a private Play link and **stay opted in for 14 days** (Google's rule for new
> developers). Use it as much or as little as you like; the in-app checklist shows the two
> settings to flip. Please tell me your phone model, and message me if anything crashes or looks
> wrong. No account, no ads, nothing tracked. Opt-in link: <…>. Thank you!
