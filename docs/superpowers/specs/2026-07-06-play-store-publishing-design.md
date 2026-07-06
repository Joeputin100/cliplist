# Play Store Publishing: Automated Closed-Testing Releases + Listing-as-Code

**Date:** 2026-07-06 · **Status:** approved by user (this doc records the approved design)

## Goal

Publish "My Playlist Creator 2026" (`com.cliplist.app`) to the user's existing Play
developer account with maximum automation: after a one-time manual Console setup (guided
by a pre-filled cheat-sheet), every tagged release uploads a signed AAB to the
**closed-testing track** automatically, and the store listing (10 languages, graphics,
screenshots) lives in the repo and is pushed by CI.

Decisions made with the user:
- **Automation level:** full auto-upload + listing-as-code (more automated than pdfposter,
  which builds the AAB in CI but uploads manually).
- **First track: closed testing** — starts Google's mandatory personal-account clock
  (~12+ testers, 14 days) toward production.
- **Privacy policy URL: GitHub Pages** serving the repo's `docs/` folder →
  `https://joeputin100.github.io/cliplist/privacy-policy` (placeholder email already fixed,
  contact = joeputin100@gmail.com).
- **One-time manual Console work happens once, from a cheat-sheet** modeled on pdfposter's
  `play-listing/console-cheat-sheet.md` — every form answer pre-written.

## Platform constraints (facts the design bends around)

- The Play Developer API **cannot create an app** — the user creates it once in the Console.
- A **completed** release can't go live until the Console forms (content rating, data
  safety, app access, target audience) are done → the FIRST automated upload lands as a
  **draft**; later tags publish completed releases.
- Play requires **AAB** (not APK) — a `bundleRelease` step joins the existing release job,
  signed by the same upload keystore already in CI secrets.
- Closed testing in API terms is the **alpha track** (Console shows it as "Closed testing").

## Components

### 1. GitHub Pages privacy URL (automated)
Enable Pages via `gh api` (branch `main`, path `/docs`). Verify
`https://joeputin100.github.io/cliplist/privacy-policy` returns the policy. This URL goes
into the cheat-sheet and the listing.

### 2. Publisher service account (mirrors the proven `PLAY_DEVELOPER_API_KEY` pattern)
- Create `play-publisher@static-webbing-461904-c4.iam.gserviceaccount.com` via gcloud
  (no project roles needed — Play permissions are granted in the Play Console, not IAM).
- JSON key → GitHub secret `PLAY_SERVICE_ACCOUNT_JSON`. Local key file deleted after upload.
- The user invites this email in Play Console → Users & permissions with app-level
  permissions: **Release to testing tracks** + **Manage testing tracks** + **Manage store
  presence** (cheat-sheet step). (If the developer account's API access isn't linked to a
  GCP project yet, the cheat-sheet covers the one-click link.)

### 3. Upload pipeline (Gradle Play Publisher, with a scripted fallback)
- Add the **Gradle Play Publisher** plugin (`com.github.triplet.play`, latest release) to
  `:app`: `serviceAccountCredentials` from the CI-written key file, `track = "alpha"`,
  `defaultToAppBundles = true`, `releaseStatus` = draft until the Console is complete
  (flip to completed afterward — tracked as a follow-up commit).
- **Compatibility risk + fallback:** GPP must support AGP 9.2/Gradle 9.5. If the latest GPP
  fails against this build, fall back to a small Python `androidpublisher` v3 script in CI
  (`edits.insert → bundles.upload → tracks.update → edits.commit`) using the same secret —
  the exact API family pdfposter's md3e backend already exercises. The plan implements GPP
  first and keeps the script as the documented plan-B.
- Release job (`build.yml`, tags + dispatch): build `bundleRelease` (keystore + injected
  google-services.json as today), then `publishBundle`. Release notes from
  `app/src/main/play/release-notes/en-US/default.txt` (updated per release; other locales
  optional later).
- Version name: derive from the git tag (`v1.2.0` → `1.2.0`) via env override
  (`VERSION_NAME`), keeping `versionCode` = CI run number as today.

### 4. Listing-as-code (`app/src/main/play/`)
GPP's standard structure, checked into the repo:
- `listings/<locale>/title.txt` (≤30 chars), `short-description.txt` (≤80),
  `full-description.txt` (≤4000) for all 10 app locales, mapped to Play locale codes:
  en-US, es-ES, fr-FR, de-DE, pt-BR, it-IT, ru-RU, ja-JP, ko-KR, zh-CN. Text adapted from
  the existing translations + README copy (factual claims only, consistent with the
  truthful-privacy wording: crash reports exist, no ads/accounts/tracking).
- `listings/en-US/graphics/icon/1.png` (512×512, from the launcher art),
  `graphics/feature-graphic/1.png` (1024×500, cut from the hero banner),
  `graphics/phone-screenshots/{1,2,3}.png` (the three existing screenshots).
- `contact-email.txt` etc. are Console-side (cheat-sheet), not files.
- CI publishes listing changes with the same tagged run (`publishListing` runs with
  `publishBundle`).

### 5. Console cheat-sheet (`play-listing/console-cheat-sheet.md`)
Pre-filled answers for every one-time step, in Console order: create app (name, default
language en-US, App, Free); link API access to `static-webbing-461904-c4` if unlinked;
invite the service account with the permissions above; **content rating** (utility, no
user-generated content → expect "Everyone"; contact joeputin100@gmail.com); **target
audience** 13+ (avoids children's-policy obligations); **data safety** (collects Crash
logs + Diagnostics via Crashlytics, app-functionality purpose, encrypted in transit, not
shared, not user-deletable-in-app, opt-out not offered); **app access** (full access, no
credentials needed); **ads** = No; privacy policy URL (Pages link); create the closed
track, add tester emails (12+ for the production clock), get the opt-in link.

### 6. Verification
- SA credential probe before first use: `edits.insert` + `edits.validate` against the
  created app via a one-off script (read-only proof the invite + secret work).
- First tagged release: AAB appears on the closed track as **draft**, listing text/images
  visible in the Console in all 10 locales, Pages URL returns 200.
- Existing pipeline (FTL, App Distribution, Releases) unchanged and green on the same tag.

## Out of scope
Production promotion (after the 14-day closed test — separate small task), in-app
purchases, pre-registration, Play Games, staged rollouts, per-locale release notes,
tablet/Chromebook screenshots.
