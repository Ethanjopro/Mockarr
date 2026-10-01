# Google Play launch runbook

Checkbox-style, in order. Written 2026-09-03 against Play Console as it was then — rules change
(target API each August, Billing Library every two years, testing requirements), so when a step
disagrees with what the Console shows, the Console wins and this file gets a fix. Honest advice
and trade-offs live in `docs/private/play-store-recommendations.md` (git-ignored); this file is the *what to click*.

Path: **personal developer account** (ADR 0002). The organization/LLC path is Appendix A.

**State (2026-09-29, session 53).** Publisher identity: **Three Streets Studios**. Ethan's new
Google account `ThreeStreets@gmail.com` owns the Play Console, the YouTube channel and the tester
group, and is the public contact. The repo side (§1) is done: the upload key, a signed 1.0.1 bundle,
store images at Play's specs, an FGS video, a 2:48 feature video (YouTube and the listing's Video
field), and the in-app privacy link. Ethan's click-by-click steps,
with copy buttons for every field, are in a private checklist page Claude publishes. The upload files
are in `~/Desktop/Mockarr launch/` (not committed).

## 0. Before touching the Console
- [ ] You are 18+, and you have a Google account you are comfortable owning this app forever (it
      cannot be moved between accounts without a transfer request; use a dedicated one if in doubt).
- [x] Dedicated contact mailbox: `ThreeStreets@gmail.com`, which is also the Play account owner. It
      is shown publicly on the listing and receives policy emails with deadlines.
- [x] Trademark sanity check: Play, web and GitHub are clear for "Mockarr" and "Three Streets
      Studios" (2026-09-29). USPTO's search is browser-only, so Ethan runs it himself (optional).
- [x] Developer name on the listing: **Three Streets Studios** (the legal name is verified privately).

## 1. Repo readiness (done this round unless unchecked)
- [x] R8 release build with `app/proguard-rules.pro`; backup rules; `versionCode` derived from
      `versionName` (`AndroidConfig.versionCode`).
- [x] Store copy without open-source/no-tracking/no-ads claims and without third-party app names
      (`fastlane/metadata/android/en-US/`).
- [x] `docs/release/privacy-policy.md`, `data-safety.md`, `fgs-declaration.md`.
- [x] Backend seams: `RouteProvider`, `Geocoder`, `ElevationProvider` interfaces.
- [x] **Geoapify key** (ADR 0003), in `secrets.properties` since 2026-09-03; the GitHub secret waits for §10: sign up at geoapify.com (free plan, no card), create a project,
      copy the API key into `secrets.properties` at the repo root (git-ignored):
      ```
      GEOAPIFY_KEY=…
      ```
      Add the same value as the GitHub Actions secret `GEOAPIFY_KEY` so CI builds carry it. Set a
      usage alert in the Geoapify dashboard at ~80 % of the daily credits and create a second key to
      keep in reserve. Without the key every build silently uses the public OSRM/Photon servers (build-time only — there is no in-app switch).
- [x] **Upload keystore** (done 2026-09-29: `~/Keys/mockarr-upload.jks`, CN=Three Streets Studios, a
      random password in `keystore.properties`, and a backup `.jks` in iCloud Drive › Mockarr keys).
      How it was made (once, on your machine, never in the repo):
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
- [x] `scripts/gradle :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
      `scripts/gradle build` must stay green *without* `keystore.properties` too (CI builds keyless).
      Checked for 1.0.0 and 1.0.1: `jarsigner -verify`, `zipalign -c -P 16`, every `.so` LOAD segment aligned to
      16 KB, targetSdk 37, no `AD_ID` permission in the merged manifest.
- [x] Verify the release build on the emulator (not just debug) — done 2026-09-03 with
      `scripts/gradle :app:assembleRelease` + `scripts/emu.sh installapk` (debug-signed on the fly):
      search → route → Play → notification Pause/Resume/Stop → Settings → Setup all pass under R8.
      Repeat on a real phone from the internal-test link (Play-signed build) before closed testing.
      `scripts/emu.sh installapk` re-signs any release APK (unsigned or upload-signed) with the debug
      key, so it installs over the debug build and the AVD keeps its data.
- [x] Six phone screenshots from the redesigned UI (ready route, driving, search, hold, saved routes,
      notification) at **1080×1920**, taken with `scripts/emu.sh resize 1080x1920` and a demo-mode
      status bar. Play rejects a long side more than 2× the short one, so the AVD's native 1080×2400
      doesn't qualify. They are 24-bit PNGs with no alpha. The feature graphic (1024×500, Night
      Indigo + the driving screen) is in `images/featureGraphic.png`.
- [x] 512×512 icon at `images/icon.png`, rendered from the adaptive icon's layers (the icon itself is
      still an undecided brand asset, PRODUCT.md).
- [x] First upload is `versionName` 1.0.1 (versionCode 10001), `changelogs/10001.txt`, tag `v1.0.1`. 1.0.0
      was tagged but never uploaded; 1.0.1 adds the stop-name fix.

## 2. Privacy policy hosting
Play needs a public URL, and the User Data policy also wants the policy reachable **inside the app**:
Settings › About › "Privacy policy" (`R.string.privacy_policy_url`). The main repo is public but
source-available (ADR 0004); the policy lives in its own place so it can change without a code release:
- [ ] The studio site, public repo `Ethanjopro/threestreets` with GitHub Pages (plain HTML, no Jekyll):
      `/` is the Three Streets Studios page, and `/mockarr/privacy/` is generated from
      `privacy-policy.md` by `scripts/privacy-site.py` → **https://ethanjopro.github.io/threestreets/mockarr/privacy/**. A custom
      domain can be added later without changing the repo (update the Play fields and the app string).
- [x] Placeholders filled (date, contact, providers, Play services mock mode). Keep the repo copy and
      the page identical; the app's About credits must list the same providers.

## 3. Developer account ($25 once)
- [ ] play.google.com/console → Create account → **Personal**. Legal name as on your ID, address,
      phone (verified by SMS), the contact email from §0.
- [ ] Identity verification: government ID upload; usually hours, can be days. The account is
      read-only until it passes.
- [ ] Developer page: display name **Three Streets Studios**, the contact email, (optional) website =
      `https://ethanjopro.github.io/threestreets/`.
- [ ] Verify an Android device: the Play Console app on the Pixel, signed in with the Three Streets
      account (a personal-account requirement).
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
| App access | "All or some functionality is restricted" → Add instructions (name "Mock location setup", no credentials): Settings → About phone → tap Build number 7× → Settings → System → Developer options → Select mock location app → Mockarr | reviewers cannot see playback otherwise; the unrestricted option has no instructions box |
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
| Advertising ID | Does not use | merged manifest has no `AD_ID` (rechecked with play-services-location, 2026-09-29) |

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
- [ ] **Closed testing** (required for production access on new personal accounts): the Alpha track,
      testers = the Google Group `mockarr-testers@googlegroups.com` (anyone can join), promote the
      internal release, send for review, then share the group link + opt-in URL.
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

## Appendix A — Organization account via an LLC (deferred)
1. Form the LLC with your state's filing office → EIN (IRS, free) → business bank account.
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
> wrong. No account, no ads. Opt-in link: <…>. Thank you!
