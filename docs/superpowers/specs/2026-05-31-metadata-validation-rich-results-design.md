# Track Metadata, Validation & Rich Results — Design Spec

- **Date:** 2026-05-31
- **Status:** Approved (design)
- **Author:** Joeputin100 (with Claude)
- **Topic:** Read real track durations, detect unreadable files, cache per-folder metadata for fast re-runs, and render an engaging animated success page that shows the exact results.

---

## 1. Summary

The success (Results) page is currently a plain receipt. We make it engaging **and** truthful: it shows **exact** numbers (real summed listening time, not an estimate) and surfaces files that couldn't be read. Producing those numbers requires a **metadata pass** over the audio — reading each track's duration and checking it's readable. To keep that affordable on repeat runs, each music folder gets a **portable, visible `mpc-metadata.json`** that caches what we learned; re-runs only re-open files that actually changed.

**Plain-language version:** When you make playlists, the app now also checks how long each song is (so it can tell you the real total time) and notices any broken files (it leaves those out of the playlist and tells you, but never deletes them). It remembers what it found in a small file in each folder, so the next time it's almost instant.

## 2. Key decisions (from brainstorming)

| Decision | Choice |
|---|---|
| Stats | **Exact only** — sum real durations; no estimates |
| Duration source | `MediaMetadataRetriever` per file (Android), behind a testable interface |
| Unreadable files | **Auto-exclude** from the `.m3u`; **never delete**; **alert** the user (Preview + Results) |
| Cache | **One `mpc-metadata.json` per music folder**, **visible** (some SanDisk firmware crashes on *any* hidden file), **portable** (travels with the folder) |
| Change detection | Free from the SAF listing: file **name + size + last-modified**; only new/changed files are re-opened |
| Success page | "Living summary": gradient hero + animated check, **count-up exact stats**, playlists as **staggered brand-gradient tiles**, removable-only eject, unreadable alert |

## 3. The metadata cache file

- **Name:** `mpc-metadata.json` (fixed, per folder — portable; independent of the folder's name).
- **Visible** (no leading dot). It is not an audio extension, so the Clip Sport ignores it.
- **Contents:**
  ```json
  { "schema": 1,
    "tracks": [
      { "name": "01 - Song.mp3", "size": 5242880, "lastModified": 1717000000000,
        "durationMs": 213000, "readable": true } ] }
  ```
- **Lifecycle:** read at the start of a folder's scan; rewritten after the metadata pass with the current, final track set (post-rename names). A file is "unchanged" iff a cache entry has the same `name`, `size`, and `lastModified`.

## 4. Scan becomes a metadata pass

For each folder with audio:
1. **List** children (SAF bulk query already returns name, size, last-modified — no file opens).
2. **Load** `mpc-metadata.json` if present (`StorageVolume.readFile`).
3. **Diff** current inventory vs cache → `reusable` (unchanged → reuse cached `durationMs`/`readable`) and `toProbe` (new/changed).
4. **Probe** each `toProbe` file via `AudioProbe` (`MediaMetadataRetriever`): success → `durationMs`, `readable=true`; failure/null → `readable=false`.
5. **Merge** → updated track list; **write** `mpc-metadata.json` back.
6. **Produce** a `FolderAnalysis`: `readableFiles` (ordered, for the playlist), `totalDurationMs`, `unreadable` (names). Unreadable files are **excluded** from `readableFiles`.

The existing planner/writer build the `.m3u` from `readableFiles`. Totals + unreadable names flow to Preview and Results.

**Performance:** first scan opens every file (slower — show a progress phase "Reading track info…"); subsequent scans only open changed files, so they're near-instant. Change detection never opens a file.

## 5. Architecture (layers)

**Layer 1 — Engine (`:core-scan`, pure JVM, unit-tested):**
- `StorageVolume.readFile(node): ByteArray?` — new interface method (+ `FakeVolume`, `SafTreeVolume`).
- `TrackMeta(name, size, lastModified, durationMs, readable)`; `FolderMetaCache(schema, tracks)`.
- `MetadataCacheCodec` — encode/decode `FolderMetaCache` ↔ JSON bytes (kotlinx.serialization).
- `MetadataDiff.plan(currentInventory, cache) → { reusable: Map<name,TrackMeta>, toProbe: List<FileStat> }` where `FileStat(name, size, lastModified)`.
- `FolderAnalysis(readableFiles: List<String>, totalDurationMs: Long, unreadable: List<String>)` and a builder that merges reusable + freshly-probed results into the analysis + the new cache.
- `AudioProbe` interface: `probe(node: VolumeNode) → ProbeResult(durationMs: Long, readable: Boolean)` — `FakeAudioProbe` in tests, `SafAudioProbe` on Android.
- `FolderMetadataAnalyzer(volume, probe)` orchestrates one folder: load cache → `MetadataDiff` → `probe` each changed file → merge → write cache → `FolderAnalysis`. The whole orchestration is unit-tested with `FakeVolume` + `FakeAudioProbe` (no Android needed).

**Layer 2 — Android (`:data-storage` / `:app`, CI-validated):**
- `SafTreeVolume.documentUri(node): Uri` — new public method so the probe can open a file (the engine never touches `Uri`; only the SAF impl does).
- `SafAudioProbe(volume: SafTreeVolume, context)` implements `AudioProbe` via `MediaMetadataRetriever.setDataSource(context, volume.documentUri(node))` + `extractMetadata(METADATA_KEY_DURATION)`; any throw or null duration → `readable=false`.
- `ScanViewModel` orchestrates the metadata pass per folder (read cache → diff → probe new/changed → merge → write cache), shows a "Reading track info…" progress phase, and carries totals + unreadable names into `PreviewModel`/`ResultModel`.

**Layer 3 — UI (`:app`, CI-validated):**
- `PreviewModel`/`ResultModel` gain `totalDurationMs` and `unreadable: List<String>`.
- **Preview** shows exact `N songs · Hh Mm` and a "⚠ N files couldn't be read — left out" warning.
- **Results — the animated "Living summary":** gradient hero with a `scaleIn` check; count-up exact stat chips (playlists · songs · listening time) via `animateIntAsState`; playlists as a 2-column grid of brand-gradient tiles (name + track count + EQ glyph) that `AnimatedVisibility`-stagger in; removable-only eject; Done / Make another; an unreadable-files alert chip when present.

## 6. Data flow

`scan()` → per folder: list (name/size/mtime) → load cache → `MetadataDiff` → probe changed (`AudioProbe`) → merge → write cache → `FolderAnalysis`. Planner builds `.m3u` from `readableFiles`. `PreviewModelBuilder`/`ResultModelBuilder` add `totalDurationMs` + `unreadable`. Generate writes playlists (readable only) + optional `folder.jpg`; the cache was already written during scan.

## 7. Error handling

- A folder with **no cache** → probe everything (first run).
- A **corrupt/unparseable** `mpc-metadata.json` → ignore it, treat as no cache (re-probe), overwrite with a fresh one.
- `readFile`/`writeFile` failures on the cache → degrade gracefully (skip caching that folder; still produce the playlist). Caching is an optimization, never a correctness requirement.
- `AudioProbe` failure on a file → `readable=false` (excluded + alerted), not a crash.

## 8. Testing

- **Engine (JVM, `FakeVolume` + `FakeAudioProbe`):** JSON round-trip; diff (unchanged reuse vs changed/new probe; removed files dropped); merge/analysis (unreadable excluded from `readableFiles`, durations summed, unreadable collected); corrupt-cache handling; readable-only playlist content.
- **Android (CI compile + assemble):** `SafAudioProbe`, `ScanViewModel` integration, `StorageVolume.readFile` on `SafTreeVolume`, the animated Results UI.
- Existing byte-exact `.m3u` golden tests still hold (playlists now list readable files only).

## 9. Non-goals (YAGNI)

- No other tags (artist/album/art) — duration + readability only.
- No audio playback or transcoding.
- No central/disk-wide index — per-folder caches only.
- `MediaMetadataRetriever` success is the readability oracle; we do not attempt deeper Clip-Sport-codec validation (honest caveat: it's a strong-but-imperfect proxy that reliably catches corrupt/zero-byte/unsupported files).

## 10. Implementation phases (to follow via writing-plans)

1. **Engine:** `readFile` + cache model/codec + diff + analysis + readable-only planning (TDD, `:core-scan`).
2. **Android:** `SafAudioProbe` (`MediaMetadataRetriever`) + `ScanViewModel` metadata pass + "Reading track info…" progress + Preview alert + totals/unreadable in the models.
3. **UI:** the animated "Living summary" Results page (gradient hero, count-up, staggered tiles, alert).
