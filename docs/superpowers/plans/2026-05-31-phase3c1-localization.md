# Phase 3c-1 — Localization (screen refactor) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every hardcoded UI string in the screens with `stringResource(R.string.<key>)` so the app shows in the user's language. The 10-language string resources already exist (`app/src/main/res/values*/strings.xml`, generated from `localization/strings.json`).

**Architecture:** Compose reads `res/values-<locale>/strings.xml` automatically per device locale; we just point the composables at the keys. One small change: `GenerateUiState.Working` carries a `GenPhase` enum instead of an English string, so the Progress screen can localize the phase label (a ViewModel can't call `stringResource`).

**Tech Stack:** Compose Material3 (`androidx.compose.ui.res.stringResource`), `R = com.cliplist.app.R`. No new deps. minSdk 24.

**Already done (committed):** `localization/strings.json` + generator, all `values-*/strings.xml` (43 keys × 10 langs), `xml/locales_config.xml`, manifest `android:localeConfig` + `android:label="@string/app_name"`.

**Deferred (note, not in scope):** `PreviewModelBuilder` warning strings (built in pure `:core-scan`, no resources) and the rename-row "file/folder in path" descriptor stay English for now — minor, revisit if needed. Settings + per-app language switching = 3c-2.

**Local-test note:** All tasks are `:app` Compose — no local SDK, so CI (Task 5) is the gate. Add `import androidx.compose.ui.res.stringResource` and `import com.cliplist.app.R` to each screen you touch.

---

### Task 1: Home screen → `stringResource`

**Files:** Modify `app/src/main/kotlin/com/cliplist/app/ui/home/HomeScreen.kt`

- [ ] **Step 1:** Add imports `androidx.compose.ui.res.stringResource` and `com.cliplist.app.R`. Replace each literal with `stringResource(R.string.<key>)` per this table (the `ToggleRow` calls pass strings, so wrap each argument):

| Literal | Key |
|---|---|
| `"My Playlist Creator"` (title) | `app_name` |
| `"for SanDisk Clip Sport"` | `app_tagline` |
| `"MUSIC FOLDER"` | `music_folder` |
| `"Tap to choose a folder"` | `choose_folder` |
| `"Search subfolders"` | `opt_subfolders` |
| `"Alphabetize tracks"` | `opt_alphabetize` |
| `"Clean file names"` | `opt_clean_names` |
| `"Plain ASCII for SanDisk Clip Sport"` | `opt_clean_names_sub` |
| `"Rename hidden files"` | `opt_rename_hidden` |
| `"For SanDisk Clip Sport compatibility"` | `opt_rename_hidden_sub` |
| `"Scan folder"` | `scan_folder` |
| `"Scanning…"` | `scanning` |

Example for the title and a toggle:
```kotlin
Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant)
...
ToggleRow(stringResource(R.string.opt_clean_names), stringResource(R.string.opt_clean_names_sub),
    options.cleanNames, vm::setCleanNames)
```
Leave dynamic values (`folder?.displayName`, the error message) as-is. The `folder?.displayName ?: "Tap to choose a folder"` becomes `folder?.displayName ?: stringResource(R.string.choose_folder)`.

- [ ] **Step 2: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/ui/home/HomeScreen.kt
git commit -m "i18n(home): use stringResource for all Home strings

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Preview screen → `stringResource` (incl. format strings)

**Files:** Modify `app/src/main/kotlin/com/cliplist/app/ui/preview/PreviewScreen.kt`

- [ ] **Step 1:** Add the two imports. Apply this mapping:

| Literal | Key / call |
|---|---|
| `"Nothing to preview yet."` | `stringResource(R.string.no_preview)` |
| `"Preview"` | `stringResource(R.string.preview)` |
| summary text | `stringResource(R.string.preview_summary_fmt, model.playlists.size, model.totalTracks)` then `+ if (model.withinLimits) " · " + stringResource(R.string.within_limits) else ""` |
| `"Warnings"` (SectionHeader arg) | `stringResource(R.string.section_warnings)` |
| `"Playlists"` (SectionHeader arg) | `stringResource(R.string.section_playlists)` |
| `"Renames (${model.renames.size})"` | `stringResource(R.string.section_renames) + " (${model.renames.size})"` |
| `"Generate playlists"` | `stringResource(R.string.generate)` |
| PlaylistCard subtitle `"${p.trackCount} tracks · ${p.playlistName}"` | `stringResource(R.string.tracks_with_playlist_fmt, p.trackCount, p.playlistName)` |
| `"NEW"` | `stringResource(R.string.badge_new)` |
| `"REPLACE"` | `stringResource(R.string.badge_replace)` |

Note: `stringResource` is `@Composable`, so build the summary string inside the composable scope (the `item { }` lambda is composable — fine). Leave `WarningCard(w)` text and the `RenameCardRow` "file/folder in path" descriptor as-is (deferred).

- [ ] **Step 2: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/ui/preview/PreviewScreen.kt
git commit -m "i18n(preview): stringResource for Preview, incl. format strings

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: `GenPhase` enum + Progress screen localization

**Files:**
- Modify `app/src/main/kotlin/com/cliplist/app/workflow/ScanViewModel.kt`
- Modify `app/src/main/kotlin/com/cliplist/app/ui/progress/ProgressScreen.kt`

- [ ] **Step 1: In `ScanViewModel.kt`, replace the `GenerateUiState` sealed interface with:**

```kotlin
enum class GenPhase { Starting, Cleaning, Writing }

sealed interface GenerateUiState {
    data object Idle : GenerateUiState
    data class Working(val phase: GenPhase, val done: Int, val total: Int) : GenerateUiState
    data class Done(val result: ResultModel) : GenerateUiState
    data class Error(val message: String) : GenerateUiState
}
```

- [ ] **Step 2: In `ScanViewModel.generate()`, change the three `Working(...)` constructions to use the enum:**

- `GenerateUiState.Working("Starting…", 0, scanPlan.folders.size)` → `GenerateUiState.Working(GenPhase.Starting, 0, scanPlan.folders.size)`
- `GenerateUiState.Working("Cleaning names…", 0, renamePlan.ops.size)` → `GenerateUiState.Working(GenPhase.Cleaning, 0, renamePlan.ops.size)`
- `GenerateUiState.Working("Writing playlists…", done, total)` → `GenerateUiState.Working(GenPhase.Writing, done, total)`

- [ ] **Step 3: Replace `ProgressScreen.kt`'s `when (val s = state)` `Working` branch** so it maps the enum to a resource. Add imports `androidx.compose.ui.res.stringResource`, `com.cliplist.app.R`, `com.cliplist.app.workflow.GenPhase`. The `Working` branch becomes:

```kotlin
                is GenerateUiState.Working -> {
                    val phaseText = when (s.phase) {
                        GenPhase.Starting -> stringResource(R.string.preparing)
                        GenPhase.Cleaning -> stringResource(R.string.phase_cleaning)
                        GenPhase.Writing -> stringResource(R.string.phase_writing)
                    }
                    Text(phaseText, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(20.dp))
                    if (s.total > 0 && s.done > 0) {
                        LinearProgressIndicator(
                            progress = { s.done.toFloat() / s.total.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.progress_count_fmt, s.done, s.total),
                            style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.writing_crlf),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
```

Also change the `Error` branch's `"Something went wrong"` → `stringResource(R.string.something_wrong)`, and the `else` branch's `"Preparing…"` → `stringResource(R.string.preparing)`.

- [ ] **Step 4: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/workflow/ScanViewModel.kt \
        app/src/main/kotlin/com/cliplist/app/ui/progress/ProgressScreen.kt
git commit -m "i18n(progress): GenPhase enum + localized phase labels

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Results screen → `stringResource`

**Files:** Modify `app/src/main/kotlin/com/cliplist/app/ui/results/ResultsScreen.kt`

- [ ] **Step 1:** Add the two imports. Apply this mapping:

| Literal | Key / call |
|---|---|
| `"No results yet."` | `stringResource(R.string.no_preview)` |
| `"All playlists created"` / `"Finished with some issues"` | `if (result.allSucceeded) stringResource(R.string.results_success) else stringResource(R.string.results_issues)` |
| stats text `"${written} written · ${totalFailed} failed"` | `stringResource(R.string.results_stats_fmt, result.playlistsWritten, result.totalFailed)` |
| `" · ${renamesApplied} renamed"` suffix | `if (result.renamesApplied > 0) " · " + stringResource(R.string.results_renamed_fmt, result.renamesApplied) else ""` |
| `"Eject SD card"` | `stringResource(R.string.eject)` |
| `"Opens Android's storage screen — apps can't eject directly."` | `stringResource(R.string.eject_note)` |
| `"Done"` | `stringResource(R.string.done)` |
| `"Make another playlist"` | `stringResource(R.string.make_another)` |

Leave `result.errors` items as-is (dynamic/deferred).

- [ ] **Step 2: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/ui/results/ResultsScreen.kt
git commit -m "i18n(results): stringResource for Results + eject + make-another

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: CI — localized app assembles

**Files:** none (verification only)

- [ ] **Step 1: Confirm JVM suite locally (unchanged)**

Run: `cd /home/projects/mpc && ./gradlew :core-format:test :core-scan:test --no-daemon 2>&1 | tail -4`
Expected: `BUILD SUCCESSFUL`, 94 tests (localization doesn't touch `:core-*`).

- [ ] **Step 2: Push and watch CI**

```bash
cd /home/projects/mpc
git push origin main
sleep 8
RUN_ID=$(gh run list --workflow=build.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run watch "$RUN_ID" --exit-status --interval 25 2>&1 | tail -3
```
Expected: both jobs green; `:app:assembleDebug` resolves every `R.string.<key>` against `values/strings.xml` and compiles the `GenPhase` change.

**If `android-build` fails:** `gh run view "$RUN_ID" --log-failed 2>&1 | head -80`. Likely:
- `Unresolved reference: R` → add `import com.cliplist.app.R` to the screen.
- `Unresolved reference: <key>` → the key is missing from `values/strings.xml`; check spelling against `localization/strings.json` keys.
- `stringResource` unresolved → add `import androidx.compose.ui.res.stringResource`.
- `GenPhase` unresolved in ProgressScreen → add `import com.cliplist.app.workflow.GenPhase`.

---

## Definition of Done (Phase 3c-1)

- [ ] Both CI jobs green; APK assembles with localized strings.
- [ ] Every static UI string in Home/Preview/Progress/Results uses `stringResource`.
- [ ] Format strings (`%1$d` …) use `stringResource(id, args…)`.
- [ ] `GenerateUiState.Working` carries `GenPhase`; Progress maps it to a localized label.
- [ ] 10 `values-*/strings.xml` exist (already committed); device locale selects the language; missing keys fall back to English default.

---

## Self-review (completed by plan author)

**Spec coverage (3c-1):** §12 localization (10 languages, single table → resources) → committed `strings.json` + generator + `values-*`; screens now consume them. Per-app language *switching* (§12 selectable in Settings) = 3c-2 (groundwork `locales_config` already laid).

**Placeholder scan:** Mapping tables give exact literal→key for every string; the two structural code changes (GenPhase, Progress branch) are shown in full. No "TBD".

**Type consistency:** `GenPhase { Starting, Cleaning, Writing }` defined in `ScanViewModel.kt`, consumed in `ProgressScreen.kt` (import noted). All `R.string.<key>` keys exist in `localization/strings.json` (verified: app_name, app_tagline, music_folder, choose_folder, opt_*, scan_folder, scanning, preview, no_preview, preview_summary_fmt, within_limits, section_*, tracks_with_playlist_fmt, badge_*, generate, phase_*, progress_count_fmt, writing_crlf, preparing, something_wrong, results_success, results_issues, results_stats_fmt, results_renamed_fmt, eject, eject_note, done, make_another).
