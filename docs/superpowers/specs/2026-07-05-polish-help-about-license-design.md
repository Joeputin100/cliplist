# Polish & Open-Sourcing: Help, Tooltips, About, GPLv3, GitHub Showcase

**Date:** 2026-07-05 · **Status:** approved by user (this doc records the approved design)

## Goal

Two phases. Phase A adds the missing "polish" surfaces inside the app (Help, option
explanations, About). Phase B turns the private repo into a public, GPLv3-licensed showcase
that credits the original app, after scrubbing content we may not redistribute.

Decisions already made with the user:

- **License: GPLv3** — copyleft chosen deliberately: the project exists because the original
  app became closed abandonware; GPLv3 prevents that fate for this one.
- **Help = FAQ + wizard replay**, in-app, offline.
- **Tooltips = ⓘ info buttons** that open a short explanation dialog (not long-press tooltips,
  not always-on subtitles).
- **Credit the original**: *My Playlist Creator* (store title "My Music Playlist Creator",
  `com.matt.mym3ucreator`) **by Matt Duss**, v2.1.1, last updated 2023-07-17, since delisted.
  Credit appears on the About screen and in the README.
- **Going public**: Claude flips the repo to public as the final step, after the user has
  reviewed the showcase README and the history scrub is verified.

## Phase A — in-app polish

All new user-facing text is added to `localization/strings.json` (all 10 languages) and
regenerated via `localization/generate_strings.py`. No hardcoded UI strings.

### A1. Help screen

- New route `Screen.Help` ("help"), registered in `AppNavGraph`; opened from the Settings
  drawer's ABOUT section.
- Layout: Scaffold (edge-to-edge safe) → LazyColumn of expandable Q&A cards
  (question row + expand arrow; tap toggles the answer text).
- Top item: **"Show welcome guide again"** button → shows the existing `WizardDialog`
  directly on the Help screen (local state; no hoisting or navigation involved).
- FAQ content (one string pair per item, `help_q_*` / `help_a_*`):
  1. How do I make playlists? (choose folder → scan → preview → generate)
  2. Why don't my playlists show on the Clip Sport? (one `.m3u` per folder; eject safely;
     the player rebuilds its database after the card is reinserted)
  3. What does "Clean file names" do? (player mishandles symbols/accents; renames files on
     the card to plain ASCII; preview shows every rename before anything happens)
  4. What does "Rename hidden files" do? (un-hides dot-files so the player can see them)
  5. Where are playlists saved? (inside each music folder, named after the folder)
  6. How do I eject safely? (Results-page Eject button → system Unmount)
  7. Which audio formats are supported? (list follows the Audio formats setting)

### A2. ⓘ info buttons

- Reusable `InfoDot(titleRes, bodyRes)` composable: small ⓘ `IconButton` → `AlertDialog`
  with title, 1–3 sentence body, single OK button.
- Placement: Home toggles (Search subfolders, Alphabetize) and Settings-drawer options
  (Clean file names, Rename hidden files, Cover art, Audio formats).
- Bodies reuse/condense the matching Help answers so wording stays consistent.
- `ToggleRow` (Home) and the drawer option rows gain an optional info slot.

### A3. About screen

- New route `Screen.About` ("about"); opened from the Settings drawer's ABOUT section.
- Content, top to bottom:
  - App logo (`AppLogo`), display name, version (`BuildConfig.VERSION_NAME`), tagline.
  - Story paragraph: a modern, open-source revival of *My Playlist Creator* by **Matt Duss** —
    the playlist tool Clip Sport owners relied on for a decade until it was abandoned and
    delisted. Same byte-exact playlist format, rebuilt from scratch. (Wording localized;
    no claim of affiliation or endorsement.)
  - Buttons: **View on GitHub** (repo URL), **Report a problem** (repo issues URL),
    **Privacy Policy** (existing screen).
  - License line: "Free software, licensed under GPLv3" → opens the LICENSE on GitHub.
- Settings drawer ABOUT section order: Help · About · Privacy Policy.

## Phase B — open-sourcing + GitHub showcase

### B1. History scrub (blocker for public)

- Remove `reference/mym3ucreator-2.1.1.apk` from the working tree **and all git history**
  (git-filter-repo or equivalent), then force-push. Verify with a fresh clone + object scan
  (`git rev-list --objects --all` shows no `.apk`/`.dex`/foreign `.jar` blobs).
- Retire `.github/workflows/decompile.yml` (its input no longer exists in the repo).
  `decompiled/` stays local-only (already untracked). `reference/FORMAT.md` and
  `reference/format-fixtures/` remain — original work, part of the showcase.
- Update `reference/README.md` wording (no committed APK anymore).

### B2. License

- `LICENSE` at repo root: full GPLv3 text.
- README license badge + section. About screen notice (A3). No per-file headers.

### B3. Showcase README + repo metadata

- README: icon + user-facing name ("My Playlist Creator 2026", codename ClipList), one-line
  pitch, screenshots row, badges (CI, license, minSdk/targetSdk), features, the origin story
  with credit to Matt Duss, install instructions (GitHub Releases; Play "planned"),
  "how it works" linking `reference/FORMAT.md`, 10-language list, building-in-CI note,
  "not affiliated with SanDisk or the original author" disclaimer. Remove the
  "private repository" paragraph.
- Screenshots: prefer real device screenshots; try FTL run artifacts first, otherwise ask the
  user for 3–4 (Home, Preview, Results). Stored under `docs/screenshots/`.
- Repo metadata via `gh`: description, topics (android, kotlin, jetpack-compose, m3u,
  playlist, sandisk, clip-sport, music). Social-preview image is web-UI-only — optional,
  user can upload later.

### B4. Go public

- Order: scrub verified → LICENSE + README merged → user reviews showcase → `gh repo edit
  --visibility public` → confirm About/README links resolve.

## Testing & verification

- JVM: no core-module logic changes expected; existing suites must stay green.
- CI (GitHub Actions) compiles Phase A; FTL flow test must still pass (nav graph changes).
- Manual: user verifies Help/About/ⓘ dialogs and localized strings spot-checks via App Tester
  build; user reviews README before the visibility flip.

## Out of scope

- Play Store publishing (see release-deferred-items), per-file GPL headers, CONTRIBUTING/issue
  templates, in-app license text viewer, App Functions spike.
