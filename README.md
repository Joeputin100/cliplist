# ClipList

A modern Android playlist creator for the **SanDisk Clip Sport** (and FAT32 SD-card workflows) — a clean-room replacement for the 10-year-old *My Music Playlist Creator*.

It scans your music folders and writes Clip-Sport-correct `.m3u` playlists (one per folder) with **byte-identical** output to the original known-good app, plus a modern Material 3 Expressive UI, ASCII/FAT32 filename cleaning, recursive scanning, and 10-language localization.

- **Status:** design approved; implementation pending.
- **Design spec:** [`docs/superpowers/specs/2026-05-29-cliplist-design.md`](docs/superpowers/specs/2026-05-29-cliplist-design.md)
- **Stack:** Kotlin · Jetpack Compose · Material 3 Expressive · minSdk 21 · targetSdk 36.
- **Build:** decompilation of the reference APK and all builds run in **GitHub Actions** (CI), not locally.

> This is a **private** repository. The reference APK and any decompiled output are kept for personal interoperability/study and are **not** redistributed.
