# Phase 3e — UX Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use `- [ ]` checkboxes.

**Goal:** Implement the approved redesign (`docs/mockups/redesign.png`): a hamburger-opened Settings **drawer**, theme/language **dropdowns**, an audio-formats **dialog**, a first-run **wizard**, the EQ **logo** on Home, a **richer Results** screen, **removable-only eject**, and moving Clean-names / Rename-hidden / Cover-art into the (persisted, **default ON**) drawer.

**Design direction (Claude Design, adapted to Material 3):** refined, quiet Material 3 in the teal→lime dark/light brand; the memorable element is the EQ-bars mark; dropdowns + one-page drawer for density; generous spacing, no clutter.

**Architecture:** The 3 destructive options move from `WorkflowOptions` (transient) to `SettingsRepository` (persisted, default true); `ScanViewModel` reads them from settings at scan/generate time (like `audioExtensions` already does). Home keeps only Search-subfolders + Alphabetize. Settings becomes a `ModalNavigationDrawer` inside Home (no separate Settings route); Privacy is a new route.

**Tech Stack:** Compose Material3 (`ModalNavigationDrawer`, `ExposedDropdownMenuBox`, `AlertDialog`, `Dialog`), `material-icons-core` (BOM-managed, for `Icons.Default.Menu`/`ArrowDropDown`), DataStore, AppCompatDelegate (language). `:core-scan`. minSdk 24. **No local SDK → CI (final task) is the gate for all UI.**

---

### Task 1: Logic — removable-media helper + Results folder list (TDD, `:core-scan`)

**Files:** Create `core-scan/.../StorageHeuristics.kt`; modify `core-scan/.../ResultModel.kt`; tests `core-scan/src/test/.../StorageHeuristicsTest.kt`, extend `ResultModelBuilderTest.kt`.

- [ ] **Step 1: failing tests** — `StorageHeuristicsTest.kt`:

```kotlin
package com.cliplist.scan
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class StorageHeuristicsTest {
    @Test fun `primary internal storage is not removable`() {
        assertFalse(StorageHeuristics.isRemovableTreeDocumentId("primary:Music"))
        assertFalse(StorageHeuristics.isRemovableTreeDocumentId("home:Documents"))
    }
    @Test fun `a volume uuid (SD card) is removable`() {
        assertTrue(StorageHeuristics.isRemovableTreeDocumentId("1A2B-3C4D:Music"))
        assertTrue(StorageHeuristics.isRemovableTreeDocumentId("0123-4567:"))
    }
}
```
And append to `ResultModelBuilderTest.kt` a test that `build` now carries per-folder rows + destination:

```kotlin
    @Test fun `build includes written folders and destination`() {
        val rock = fakeDir("Rock", fakeFile("a.mp3"), fakeFile("b.mp3"))
        val scan = ScanPlan(listOf(FolderPlan(rock, listOf("a.mp3","b.mp3"), null, "Rock.m3u")), emptyList())
        val r = ResultModelBuilder.build(WriteReport(1,0,emptyList()), null, scan, "SD card / Music")
        assertEquals("SD card / Music", r.destination)
        assertEquals(1, r.folders.size)
        assertEquals("Rock", r.folders[0].folderName)
        assertEquals(2, r.folders[0].trackCount)
    }
```

- [ ] **Step 2:** run `./gradlew :core-scan:test --tests '*StorageHeuristicsTest*' --tests '*ResultModelBuilderTest*' --no-daemon 2>&1 | tail -12` → FAIL (unresolved).

- [ ] **Step 3: implement.** Create `StorageHeuristics.kt`:

```kotlin
package com.cliplist.scan

/** Pure heuristics over SAF identifiers. */
object StorageHeuristics {
    /**
     * A SAF tree document id looks like "<volume>:<path>", e.g. "primary:Music" (internal
     * shared storage) or "1A2B-3C4D:Music" (a removable SD card / USB volume). Internal
     * volumes use the reserved ids "primary"/"home"; anything else is removable.
     */
    fun isRemovableTreeDocumentId(treeDocumentId: String): Boolean {
        val volume = treeDocumentId.substringBefore(':')
        return !volume.equals("primary", ignoreCase = true) &&
               !volume.equals("home", ignoreCase = true)
    }
}
```

Modify `ResultModel.kt` — add `ResultFolderRow`, two fields, and update the builder signature:

```kotlin
data class ResultFolderRow(val folderName: String, val trackCount: Int)

data class ResultModel(
    val playlistsWritten: Int,
    val playlistsFailed: Int,
    val renamesApplied: Int,
    val renamesFailed: Int,
    val errors: List<String>,
    val folders: List<ResultFolderRow>,
    val destination: String,
) {
    val totalFailed: Int get() = playlistsFailed + renamesFailed
    val totalTracks: Int get() = folders.sumOf { it.trackCount }
    val allSucceeded: Boolean get() = totalFailed == 0
}

object ResultModelBuilder {
    fun build(write: WriteReport, rename: RenameExecution?, scan: ScanPlan, destination: String): ResultModel =
        ResultModel(
            playlistsWritten = write.written,
            playlistsFailed = write.failed,
            renamesApplied = rename?.applied?.size ?: 0,
            renamesFailed = rename?.failed?.size ?: 0,
            errors = write.errors + (rename?.failed?.map { "${it.op.oldName}: ${it.message}" } ?: emptyList()),
            folders = scan.folders.map { ResultFolderRow(it.folder.name, it.audioFiles.size) },
            destination = destination,
        )
}
```

- [ ] **Step 4:** run the two test classes → PASS. Then full suite `:core-format:test :core-scan:test` → BUILD SUCCESSFUL (existing ResultModelBuilder calls now need the new args — they're only in `ScanViewModel`, updated in Task 3; tests updated here). Expected ~99 tests.

- [ ] **Step 5: commit** `feat(redesign): removable-media heuristic + Results folder list/destination`.

---

### Task 2: Settings — persist the 3 options + wizard flag

**Files:** modify `app/.../settings/SettingsRepository.kt`, `SettingsViewModel.kt`.

- [ ] **Step 1:** In `SettingsRepository.kt` add keys + flows + setters (defaults: the three options **true**, hideWizard **false**):

```kotlin
import androidx.datastore.preferences.core.booleanPreferencesKey
// ... in the class:
private val cleanKey = booleanPreferencesKey("clean_names")
private val renameKey = booleanPreferencesKey("rename_hidden")
private val coverKey = booleanPreferencesKey("write_cover_art")
private val wizardKey = booleanPreferencesKey("hide_wizard")

val cleanNames: Flow<Boolean> = context.settingsDataStore.data.map { it[cleanKey] ?: true }
val renameHidden: Flow<Boolean> = context.settingsDataStore.data.map { it[renameKey] ?: true }
val writeCoverArt: Flow<Boolean> = context.settingsDataStore.data.map { it[coverKey] ?: true }
val hideWizard: Flow<Boolean> = context.settingsDataStore.data.map { it[wizardKey] ?: false }

suspend fun setCleanNames(v: Boolean) { context.settingsDataStore.edit { it[cleanKey] = v } }
suspend fun setRenameHidden(v: Boolean) { context.settingsDataStore.edit { it[renameKey] = v } }
suspend fun setWriteCoverArt(v: Boolean) { context.settingsDataStore.edit { it[coverKey] = v } }
suspend fun setHideWizard(v: Boolean) { context.settingsDataStore.edit { it[wizardKey] = v } }
```

- [ ] **Step 2:** In `SettingsViewModel.kt` expose each as a `StateFlow` (`stateIn(viewModelScope, SharingStarted.Eagerly, true/true/true/false)`) and add setters that `viewModelScope.launch { repo.setX(v) }`. Keep existing `themeMode`/`audioExtensions`.

- [ ] **Step 3: commit** `feat(redesign): persist clean/rename/cover options (default on) + wizard flag`.

---

### Task 3: `ScanViewModel` — slim Home options, read settings, removable folder, richer result

**Files:** modify `app/.../workflow/ScanViewModel.kt`.

- [ ] **Step 1:** Reduce `WorkflowOptions` to `data class WorkflowOptions(val searchSubfolders: Boolean = true, val alphabetize: Boolean = true)`; remove `setCleanNames/setRenameHidden/setWriteCoverArt` and `writeCoverArt` from it.
- [ ] **Step 2:** Add `val isRemovable: Boolean` to `SelectedFolder`.
- [ ] **Step 3:** In `scan()`, build `RenameOptions` from `SettingsRepository` (read in the IO block):
```kotlin
val settings = SettingsRepository(getApplication())
val renamePlan = RenamePlanner().plan(volume, RenameOptions(
    cleanNames = settings.cleanNames.first(),
    renameHidden = settings.renameHidden.first()))
```
(`audioExtensions` already comes from settings.)
- [ ] **Step 4:** In `generate()`, read `writeCoverArt` from `SettingsRepository(getApplication()).writeCoverArt.first()` (instead of `_options`), and pass the destination + scan plan to `ResultModelBuilder.build(report, renameExec, planToWrite, _folder.value?.displayName ?: "")`.
- [ ] **Step 5: commit** `feat(redesign): ScanViewModel reads persisted options; removable flag; richer result`.

---

### Task 4: Home + Settings drawer + wizard

**Files:** add `material-icons-core` dep (catalog + `app/build.gradle.kts`); replace `app/.../ui/home/HomeScreen.kt`; create `app/.../ui/settings/SettingsDrawer.kt`; create `app/src/main/assets/privacy_policy.txt` (copy of `docs/privacy-policy.md`).

Design/behavior spec (implementer writes idiomatic Compose for BOM 2026.05.01; cross-check `ModalNavigationDrawer`, `ExposedDropdownMenuBox`, `AlertDialog`, `Dialog`, `painterResource` against the BOM):

- **Dependency:** catalog `compose-material-icons-core = { module = "androidx.compose.material:material-icons-core" }` (no version — BOM-managed); `implementation(libs.compose.material.icons.core)` in app.
- **HomeScreen(navController, scanVm, settingsVm)** — wrap content in `ModalNavigationDrawer` whose `drawerContent` is `ModalDrawerSheet { SettingsDrawer(settingsVm, onPrivacy = { navController.navigate(Screen.Privacy.route) }) }`, with a `rememberDrawerState(DrawerValue.Closed)` + `rememberCoroutineScope()`.
  - Top bar: a `Menu` `IconButton` (top-left) that opens the drawer (`scope.launch { drawerState.open() }`).
  - Below: the **logo** — `Image(painterResource(R.mipmap.ic_launcher), contentDescription=null, Modifier.size(64.dp).clip(CircleShape))`, centered; then `app_name` (centered, headlineSmall — NOT headlineMedium, to avoid overflow) + `app_tagline` centered.
  - Folder card (unchanged behaviour: SAF picker; when picking, compute `isRemovable = StorageHeuristics.isRemovableTreeDocumentId(DocumentsContract.getTreeDocumentId(uri))` and store on `SelectedFolder`).
  - **Only two** `ToggleRow`s: Search subfolders, Alphabetize tracks (bound to `scanVm`).
  - Scan button (unchanged).
  - **Wizard:** collect `settingsVm.hideWizard`; keep a `remember { mutableStateOf(true) }` "show this session"; if `!hideWizard && showThisSession`, render `WizardDialog(onDismiss = { showThisSession = false }, onDontShowAgain = { settingsVm.setHideWizard(true); showThisSession = false })`.
- **SettingsDrawer(settingsVm, onPrivacy)** — a scrollable `Column` inside the sheet:
  - Header: small logo + "Settings" (stringResource `settings`).
  - **APPEARANCE** label, then two `ExposedDropdownMenuBox` rows:
    - Theme: options System/Light/Dark (labels `theme_system/theme_light/theme_dark`) → `settingsVm.setThemeMode(...)`.
    - Language: options "System default" (`language_system`) + the 10 autonyms (English, Español, Français, Deutsch, Português (Brasil), Italiano, Русский, 日本語, 한국어, 简体中文) → `AppCompatDelegate.setApplicationLocales(...)` (empty list for system).
  - **PLAYLIST OPTIONS** label, then three switches bound to `settingsVm.cleanNames/renameHidden/writeCoverArt` (labels `opt_clean_names`/`opt_rename_hidden`/`opt_cover_art`); then an "Audio formats" row (count badge) opening `AudioFormatsDialog`.
  - **ABOUT** label, then a "Privacy Policy" row → `onPrivacy()`.
- **AudioFormatsDialog** — `AlertDialog` with a scrollable column of `AudioExtensions.DEFAULT.sorted()` rows, each a label + `Checkbox` reflecting `settingsVm.audioExtensions`, toggling via `settingsVm.toggleExtension(ext, checked)`; a single "Done" confirm button. (Default state already = all selected.)
- **WizardDialog(onDismiss, onDontShowAgain)** — a `Dialog` card: logo, "Welcome", "Make Clip Sport playlists in 3 steps", three numbered steps ("Choose your music folder or SD card", "Pick options, then Scan to preview", "Generate — one .m3u per folder"), a `Checkbox` "Show this next time" (default checked), and a "Get started" button. On Get started: if the checkbox is unchecked call `onDontShowAgain()` else `onDismiss()`.

- [ ] **Commit** `feat(redesign): Home logo + hamburger + Settings drawer (dropdowns, audio dialog) + wizard`.

---

### Task 5: Richer Results + Privacy screen + nav wiring

**Files:** replace `app/.../ui/results/ResultsScreen.kt`; create `app/.../ui/privacy/PrivacyScreen.kt`; add `Screen.Privacy`; modify `app/.../nav/Screen.kt` + `AppNavGraph.kt`; delete `app/.../ui/settings/SettingsScreen.kt` (replaced by the drawer).

- **Screen.kt:** add `object Privacy : Screen("privacy")`. (Keep `Settings` object only if still referenced; otherwise remove — Home no longer navigates to it.)
- **AppNavGraph(modifier, navController, settingsViewModel):** pass `settingsViewModel` to `HomeScreen(navController, scanViewModel, settingsViewModel)`; remove the `Settings` destination; add `composable(Screen.Privacy.route) { PrivacyScreen(navController) }`.
- **ResultsScreen(navController, scanVm)** — read `scanVm.generateState` (Done→model) and `scanVm.folder` (for `isRemovable`). Render: success check, "All playlists created" (`results_success`) + `"in ${model.destination}"`, three stat cards (`model.playlists`→ playlistsWritten, `model.totalTracks`, `model.renamesApplied`), a "FOLDERS WRITTEN" `LazyColumn`/list of `model.folders` (name + "N tracks"), then **only if `scanVm.folder.value?.isRemovable == true`**: the "Eject SD card" button (`eject`) + `eject_note`; then "Done" (`done`) + "Make another" (`make_another`) which call `scanVm.resetWorkflow()` + navigate Home.
- **PrivacyScreen(navController)** — read `LocalContext.current.assets.open("privacy_policy.txt").bufferedReader().use { it.readText() }` (remember it) and show it in a vertically-scrollable `Text` with a back affordance (top bar or system back). Title from `settings`-area or a literal "Privacy Policy".

- [ ] **Commit** `feat(redesign): richer Results (folders + conditional eject) + in-app Privacy screen + nav`.

---

### Task 6: CI

- [ ] `./gradlew :core-format:test :core-scan:test --no-daemon 2>&1 | tail -4` → BUILD SUCCESSFUL (~99 tests).
- [ ] Push; watch CI; both jobs green (`:app:assembleDebug` + `:app:assembleRelease`).
- **Triage if `android-build` fails:** unresolved `Icons.Default.Menu`/`ArrowDropDown` → confirm `material-icons-core` dep; `ExposedDropdownMenuBox`/`ModalNavigationDrawer`/`menuAnchor()` API drift → match the signature the compiler names (these are stable in BOM 2026.05.01); `painterResource`/`R.mipmap.ic_launcher` → import `androidx.compose.ui.res.painterResource`; `ResultModelBuilder` call sites → only `ScanViewModel` (Task 3).

---

## Definition of Done

- [ ] Both CI jobs green (debug + release).
- [ ] Home: logo + hamburger; only Search-subfolders + Alphabetize toggles; no malformed button.
- [ ] Hamburger opens the Settings drawer: Theme + Language dropdowns, 3 persisted options (default ON), Audio-formats dialog, Privacy Policy link.
- [ ] First-run wizard with "show this next time" (default on); dismissible permanently.
- [ ] Results: destination + stat cards + per-folder list; eject shown **only** for removable media.
- [ ] In-app Privacy screen from the drawer.
- [ ] ~99 JVM tests pass (StorageHeuristics + ResultModelBuilder folder list).

## Self-review

Spec coverage: every numbered request from the user maps to a task (drawer→T4, dropdowns→T4, audio dialog→T4, move+default-on options→T2/T3/T4, removable eject→T1/T3/T5, richer results→T1/T5, wizard→T2/T4, privacy→T4/T5, logo→T4, malformed button→T4 hamburger). Logic is TDD-exact; UI is spec'd for the implementer + CI-gated (no local SDK). Types: `ResultModel`/`ResultFolderRow`/`StorageHeuristics` defined T1, used T3/T5; `SettingsRepository`/`SettingsViewModel` additions T2 used T3/T4; `WorkflowOptions` slimmed T3, used T4 (Home 2 toggles).
