# ClipList — Design Spec

- **Date:** 2026-05-29
- **Status:** Approved (design); awaiting written-spec review
- **Author:** Joeputin100 (with Claude)
- **Topic:** Modern, clean-room replacement for the 10-year-old *My Music Playlist Creator* (`com.matt.mym3ucreator`, v2.1.1, 2016), purpose-built for SanDisk Clip Sport + FAT32 SD-card workflows.

---

## 1. Summary

Build a modern Android app — **ClipList** — that scans music folders and writes SanDisk-Clip-Sport-correct `.m3u` playlists, **one per folder**, producing output that is **byte-for-byte identical** to the original app's known-good format. We extract that exact format by decompiling the original APK **in GitHub Actions** (never locally — to avoid out-of-memory on the VPS), then build a brand-new Kotlin + Jetpack Compose + Material 3 Expressive app to match it, with an improved workflow and feature set.

**Plain-language version:** The old app made playlist files that work on a SanDisk Clip Sport music player. It is ancient and unmaintained. We are rebuilding it from scratch with a modern look and better features, while guaranteeing the playlist files it produces are exactly as the player expects — down to the last byte.

## 2. Background

- **The original app** (`com.matt.mym3ucreator`): a compact (~2.7 MB bytecode) bilingual (French/English) tool that scans music and exports `.m3u` playlists, using the old Android Support Library (AppCompat) and `WRITE_EXTERNAL_STORAGE`. Its export routines (`ServiceExport`, `ecrirePlayListFromFiles`, `ecrirePlayListFromPaths`, `AsyncExport`) define the byte format we must replicate.
- **The target device — SanDisk Clip Sport** — is notoriously strict about playlists:
  - **Line endings must be CRLF (`\r\n`).** Unix `\n` makes playlists appear **completely empty** on the device. This is the single most common failure mode and the top correctness requirement.
  - Safest layout: the `.m3u` lives **in the same folder as its tracks**, referencing tracks by **bare filename**.
  - Device limits: **~50 playlists max**, **~1000 tracks per playlist max**.
- **Why rebuild rather than port:** the priority is byte-identical output and a modern, maintainable codebase; resurrecting 2016 decompiled code (dead Support Library, foreign method names) is slower and legally riskier than a clean reimplementation.

## 3. Key decisions (from brainstorming)

| Decision | Choice |
|---|---|
| Build strategy | **Clean-room rewrite**; decompile only as a private reference to extract the exact format |
| Storage access | **Both**: all-files access (`MANAGE_EXTERNAL_STORAGE`) for internal storage **and** SAF tree picker for removable SD cards |
| Filename cleaning | **Rename the real files** to ASCII/FAT32-safe names, with **mandatory preview** + **undo log** |
| Audio formats scanned | **All Clip Sport formats**: mp3, wma, m4a/aac, ogg/oga, flac, wav, plus Audible (aa/aax) — editable in Settings |
| Repo & privacy | **One private GitHub repo** holds the APK + decompile workflow + app. Decompiled source is **never committed** (CI artifact only) |
| Languages | **10**: en, es, fr, de, pt-BR, it, ru, ja, ko, zh-CN |
| Default language / theme | **Follow system**, both overridable in Settings |
| Platform target | **minSdk 21, targetSdk 36** (Android 16); edge-to-edge + predictive back; Material 3 Expressive |
| Discoverability | App Functions (`androidx.appfunctions`) + App Actions/BII + ASO listing metadata |

## 4. Non-goals (YAGNI)

- No cloud sync, accounts, or network features.
- No music playback or tag editing (beyond filename cleaning).
- No Google Play release pipeline in v1 (sideload-first; all-files access makes Play distribution impractical anyway).
- No support for non-Clip-Sport quirks of other players in v1 (architecture leaves room via "export profiles," but only the Clip Sport profile ships).

## 5. Constraints

- **Byte-exact output** is the hard acceptance bar (see §8).
- **CRLF always**, regardless of host OS conventions.
- **FAT32 reality:** illegal filename characters `\ / : * ? " < > |`; reserved names (`CON`, `PRN`, `AUX`, `NUL`, `COM1–9`, `LPT1–9`); case-insensitive collisions; long-name/path limits.
- **minSdk 21** means some 2025-era features (App Functions, dynamic color, predictive-back animation) only activate on newer OS versions and must degrade cleanly.
- **Privacy/IP:** the proprietary APK and any decompiled output stay in the private repo / CI artifacts; only original work + a written format description + synthetic fixtures could ever go public.

## 6. Architecture (isolated, independently testable modules)

```
:core-format   pure Kotlin/JVM — m3u serializer (byte-exact), filename sanitizer, sort logic.
               No Android deps → golden-master byte tests run as plain JUnit, no emulator.
:core-scan     folder walking + playlist planning over a StorageVolume abstraction.
:data-storage  two StorageVolume backends: FileSystemVolume (all-files) and SafTreeVolume (SAF).
:app           Compose UI, ViewModels, DI, localization, theming, App Functions entry points.
```

**Why this split:** keeping serialization pure and Android-free makes the most critical, hardest-to-get-right code (the bytes) testable in milliseconds without a device, and lets the scan engine ignore whether it is talking to the File API or SAF.

## 7. Phase 0 — Decompile in CI → the reference (runs first)

A GitHub Actions workflow (`decompile.yml`) runs **jadx** (+ **apktool** for resources/manifest) on the APK. CI runners have ample RAM (~16 GB) versus a 2.7 MB dex, so OOM is a non-issue.

Outputs:
- Decompiled source as a downloadable **CI artifact** (not committed).
- A hand-authored **`reference/FORMAT.md`** that pins every byte-level parameter, read directly from the export routines.
- A **golden-master fixture set** (`core-format/src/test/resources/golden/`): for synthetic input folders (invented dummy filenames — zero proprietary content), the exact expected `.m3u` bytes.

## 8. Export format profile (frozen by Phase 0)

The serializer emits a single "Clip Sport (classic)" profile whose parameters are **determined by reading the decompiled original**, then frozen as fixtures. Parameters to pin:

| Parameter | Determined from original |
|---|---|
| Character encoding (UTF-8 / Latin-1 / Windows-1252) | export stream writer charset |
| Header lines (`#EXTM3U` / `#EXTINF` vs none) | presence in writer |
| Path style (bare filename vs relative subpath) | path written per entry |
| Path separator | separator used |
| Line ending | expected `\r\n` (CRLF) — verify |
| Trailing newline (present/absent) | end-of-file write |
| Per-folder playlist filename (`<FolderName>.m3u` vs other) | naming logic |
| Sort order (locale Collator vs simple compare; case handling) | sort call |

**Acceptance criterion:** for every fixture input, `:core-format` output is **byte-identical** to the captured golden master. This is the automated stand-in for "works on the Clip Sport."

## 9. Scan & generate engine (`:core-scan` + `:core-format`)

Inputs: a root location (via `StorageVolume`), `recursive` flag, the audio-extension set, and options (`alphabetize`, `cleanFilenames`).

Behavior:
1. Walk folders (recursively if enabled).
2. For each folder containing ≥1 supported audio file, build a playlist of **that folder's** audio files (per-folder content).
3. Optionally alphabetize (order matching the original).
4. If cleaning is on, apply the rename plan first (§10), then write entries matching the renamed files.
5. Write `<FolderName>.m3u` **inside the folder**, replacing any existing file, using the §8 byte profile.
6. Surface device-limit warnings: **> 1000 tracks in a folder** or **> 50 playlists total**.

## 10. Filename cleaning (rename + preview + undo)

When enabled, the app makes the actual audio files ASCII/FAT32-safe so both the files and the playlist entries are device-friendly.

**Sanitization rules:**
- Transliterate to ASCII: `é→e`, smart quotes `“ ” ‘ ’ → " '`, em/en-dash `→ -`, strip emoji and other non-ASCII.
- Remove/replace FAT32-illegal `\ / : * ? " < > |` and control chars.
- Avoid reserved device names (`CON`, `PRN`, `AUX`, `NUL`, `COM1–9`, `LPT1–9`).
- Collapse repeated whitespace, trim, enforce length limits, preserve the file extension.
- Resolve case-insensitive collisions deterministically (`name`, `name_1`, `name_2`, …) with no data loss.

**Process (safety-first):**
1. Compute a full rename plan (old → new) across affected files.
2. Show a **mandatory preview** of every change; nothing touches disk until the user confirms.
3. Execute renames (`File.renameTo` for File backend; `DocumentsContract.renameDocument` for SAF).
4. Write an **undo log** (JSON) so "Undo last run" fully reverts.
5. Generate playlists **after** renames so entries always match.

## 11. Storage model (the "both / it varies" answer)

A `StorageVolume` interface with two backends, chosen by the user's entry point:

- **"Scan a folder on this phone"** → `FileSystemVolume` over `MANAGE_EXTERNAL_STORAGE`: fast recursion and writes via `java.io.File`. Best for internal staging.
- **"Scan the SD card / external drive"** → `SafTreeVolume`: user picks the volume via the system folder picker once; the tree URI is persisted (`takePersistableUriPermission`); traversal/writes via `DocumentFile`/`ContentResolver`. This is the reliable path for **removable** cards, which Android blocks from raw File writes even with all-files access.

The scan/generate engine consumes the abstraction and is agnostic to which backend is active.

## 12. Localization

- **Single source of truth:** `localization/strings.json`, keyed by string ID with a value map per locale:
  ```json
  { "btn_generate": { "en": "Generate playlists", "fr": "Générer les listes", "es": "Generar listas" } }
  ```
- A **Gradle codegen task** generates native `res/values-<lang>/strings.xml` from it at build time, so the app keeps full Android i18n (plurals, accessibility, system switching) while translators edit one table. (CSV export available if preferred for translators.)
- **10 locales:** en (base), es, fr, de, pt-BR, it, ru, ja, ko, zh-CN.
- **Per-app language:** default follows the system; user override via `AppCompatDelegate.setApplicationLocales()` (works to API 21). RTL not required for this set, but layouts use start/end (not left/right) so adding an RTL language later is trivial.

## 13. Theming

- Mode: **System / Light / Dark**, default System (`DayNight`), persisted, selectable in Settings.
- **Material 3 Expressive** color system; **dynamic color** (wallpaper-based) on Android 12+ (API 31+), falling back to a fixed brand scheme below.

## 14. Android 16 platform support

- **Edge-to-edge:** `enableEdgeToEdge()` + inset-aware Compose scaffolding so content never hides behind status/nav bars. (Enforced by default at targetSdk 35+; back-ports to old OS via AndroidX.)
- **Predictive back:** default-on at our target (the legacy manifest flag is ignored at SDK 35+); we wire a Compose `PredictiveBackHandler` for in-app predictive transitions across the multi-step generate flow. Degrades to standard back below Android 13.

## 15. Discoverability

We implement everything within our control and are honest about what we cannot guarantee:

- **App Functions** (`androidx.appfunctions`, **alpha**): expose e.g. `@AppFunction createClipSportPlaylists(...)` so Gemini can discover/invoke it. Guarded so older OSes ignore it gracefully.
- **App Actions / Built-in Intents** via `shortcuts.xml` capabilities for assistant surfacing.
- **ASO (store listing) metadata:** title/description/keywords such as *"SanDisk Clip Sport playlist maker"* and a *"modern replacement for My Music Playlist Creator"* descriptor, so searches for the old program surface this app.
- **Caveat:** whether Gemini/Play actually *recommend* the app depends on their algorithms and OS version; we make it **eligible and discoverable**, which is the ceiling for any app.

## 16. UI screens (Material 3 Expressive, Compose)

1. **Home** — choose location (two entry points), recursive toggle, options (alphabetize, clean filenames), Generate CTA, last-run summary.
2. **Preview** — folders found with music + counts, device-limit warnings, and (if cleaning) the rename diff.
3. **Progress** — expressive loading indicator, per-folder progress.
4. **Results** — written / renamed / errors summary, Undo, export log/share.
5. **Settings** — extension list, export profile, language, theme, about.

Mockups will be shown to the user as **in-chat screenshots** (headless VPS) before building.

## 17. CI/CD & repo

- **Repo:** one **private** GitHub repo (`gh` account: Joeputin100). Holds the reference APK (under `reference/`), the workflows, and the app. Private = not redistribution.
- **`decompile.yml`** — Phase 0 reference extraction (manual `workflow_dispatch`); uploads decompiled source as an artifact; does not commit it.
- **`build.yml`** — Gradle assemble + `:core-format` golden-master unit tests + lint; **release-signs** the APK with a keystore from repo secrets (so updates install over each other); uploads the APK artifact and attaches it to a GitHub Release on tag. Gradle caching enabled.
- A future public source repo (app only, no APK/decompiled source) is possible if open-sourcing is desired later.

## 18. Testing strategy (TDD)

- **Golden-master byte tests** for the serializer (from §8 fixtures) — the core correctness gate.
- **Sanitizer tests** — Unicode→ASCII transliteration, illegal/reserved names, collisions, length limits.
- **Scan-engine tests** against a fake `StorageVolume` (no device needed).
- **Localization completeness check** — every string ID present for all 10 locales (build-time validation in the codegen task).
- Optional minimal instrumented tests for SAF and the rename/undo flow.
- Process: write tests from `FORMAT.md` **before** implementing the serializer.

## 19. Acceptance criteria

1. For all golden fixtures, generated `.m3u` bytes are identical to the captured originals (encoding, header, paths, CRLF, trailing newline).
2. Playlists generated on a real Clip Sport from a sample library are non-empty and play in order.
3. App scans and writes via **both** File and SAF backends.
4. Filename cleaning previews before touching disk and is fully reversible via undo.
5. UI runs edge-to-edge with working predictive back; theme + language follow system and are overridable.
6. App installs and runs on Android 5.0 (API 21) through Android 16, with newer features degrading gracefully.
7. CI builds, tests, signs, and publishes the APK; decompiled source is never committed.

## 20. Risks & mitigations

- **Wrong byte format → empty playlists.** Mitigation: golden masters from the actual original; CRLF asserted explicitly.
- **Removable-SD write restrictions.** Mitigation: SAF backend for cards.
- **Destructive renames.** Mitigation: mandatory preview + undo log; generate after rename.
- **App Functions is alpha / API churn.** Mitigation: isolate behind a thin module, guard by OS version, treat as best-effort.
- **minSdk 21 vs modern libraries.** Mitigation: graceful degradation; keep newest features optional.

## 21. Implementation phases (detailed plan to follow via writing-plans)

0. Create private repo; add reference APK; `decompile.yml`; extract `FORMAT.md` + golden fixtures.
1. `:core-format` — byte-exact serializer + sanitizer + sort (TDD against fixtures).
2. `:core-scan` + `:data-storage` — engine + File and SAF backends.
3. `:app` — Compose MD3E UI, ViewModels, DI; localization codegen; theming; edge-to-edge; predictive back.
4. Filename-cleaning preview/undo flow.
5. Discoverability (App Functions / BII / ASO); `build.yml` sign + release; on-device verification checklist.

## 22. Glossary (plain language)

- **APK** — the installable Android app file (a ZIP of compiled code + resources).
- **Decompile** — turn compiled app code back into human-readable code to study it.
- **`.m3u`** — a plain-text playlist file listing which songs to play, in order.
- **CRLF** — the Windows-style invisible "end of line" marker the Clip Sport requires.
- **FAT32** — the older SD-card format the Clip Sport uses; picky about filename characters.
- **SAF (Storage Access Framework)** — Android's system folder-picker; the sanctioned way to write to removable SD cards.
- **Golden master** — a saved "known-correct" output we compare against to prove nothing changed.
- **Material 3 Expressive** — Google's newest visual design system (shapes, motion, color).
- **App Functions** — a new Android feature letting assistants like Gemini find and run actions inside apps.
