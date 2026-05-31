# Phase 3b-2 — Home + SAF Picker → Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the tested engines (PlaylistPlanner + RenamePlanner from Phases 2/3b-1) to real UI — a Home screen that picks a folder via SAF, holds the four toggles, runs the scan off the main thread, and a Preview screen that shows the resulting plan (playlists, renames, warnings) before anything is written.

**Architecture:** A pure `PreviewModelBuilder` (`:core-scan`, JVM-tested) turns `ScanPlan + RenamePlan` into a display `PreviewModel`. An activity-scoped `ScanViewModel` (`:app`, `AndroidViewModel`) holds the selected folder + options + scan state, runs the planners on `Dispatchers.IO`, and exposes `StateFlow`s. `AppNavGraph` creates that one ViewModel and shares it between `HomeScreen` (SAF picker + toggles + Scan) and `PreviewScreen` (renders the model). No writing happens yet — "Generate" navigates to the Progress stub (built in 3b-3).

**Tech Stack:** Kotlin 2.3.20 · Compose BOM 2026.05.01 · Material3 · Navigation Compose 2.9.8 · Lifecycle ViewModel/Runtime Compose 2.10.0 · Activity Compose 1.13.0 · coroutines (transitive) · `:core-scan` + `:data-storage`. minSdk 24.

**Local-test note:** Only Task 1 is unit-testable here (no Android SDK on this VPS). Tasks 2–5 are validated by `:app:assembleDebug` in CI (Task 6). Expect possibly one CI compile-fix loop — that's normal for Android work without a local SDK.

---

## File map

```
core-scan/src/main/kotlin/com/cliplist/scan/
  PreviewModel.kt            ← CREATE: PlaylistRow, RenameRow, PlaylistAction, PreviewModel
  PreviewModelBuilder.kt     ← CREATE: build(ScanPlan, RenamePlan) -> PreviewModel
core-scan/src/test/kotlin/com/cliplist/scan/
  PreviewModelBuilderTest.kt ← CREATE (Task 1)

gradle/libs.versions.toml    ← MODIFY: add lifecycle-runtime-compose library
app/build.gradle.kts         ← MODIFY: depend on :data-storage + lifecycle-runtime-compose

app/src/main/kotlin/com/cliplist/app/
  workflow/ScanViewModel.kt  ← CREATE: WorkflowOptions, SelectedFolder, ScanUiState, ScanViewModel
  nav/AppNavGraph.kt         ← MODIFY: create shared ScanViewModel, pass to Home + Preview
  ui/home/HomeScreen.kt      ← REPLACE: SAF picker, 4 toggles, Scan (was placeholder)
  ui/preview/PreviewScreen.kt← REPLACE: render PreviewModel (was placeholder)
```

---

### Task 1: `PreviewModel` + `PreviewModelBuilder` (pure logic, TDD)

**Files:**
- Create: `core-scan/src/main/kotlin/com/cliplist/scan/PreviewModel.kt`
- Create: `core-scan/src/main/kotlin/com/cliplist/scan/PreviewModelBuilder.kt`
- Test: `core-scan/src/test/kotlin/com/cliplist/scan/PreviewModelBuilderTest.kt`

- [ ] **Step 1: Write the failing test**

Create `core-scan/src/test/kotlin/com/cliplist/scan/PreviewModelBuilderTest.kt`:

```kotlin
package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PreviewModelBuilderTest {

    private fun scanOf(vararg folders: FolderPlan, warnings: List<PlanWarning> = emptyList()) =
        ScanPlan(folders.toList(), warnings)

    @Test fun `maps folders to playlist rows with NEW and REPLACE`() {
        val rock = fakeDir("Rock", fakeFile("a.mp3"), fakeFile("b.mp3"))
        val jazz = fakeDir("Jazz")
        val scan = scanOf(
            FolderPlan(rock, listOf("a.mp3", "b.mp3"), existingPlaylistName = null, playlistName = "Rock.m3u"),
            FolderPlan(jazz, listOf("c.mp3"), existingPlaylistName = "Jazz.m3u", playlistName = "Jazz.m3u")
        )
        val model = PreviewModelBuilder.build(scan, RenamePlan(emptyList(), emptyList()))

        assertEquals(2, model.playlists.size)
        assertEquals(PlaylistAction.NEW, model.playlists[0].action)
        assertEquals(2, model.playlists[0].trackCount)
        assertEquals(PlaylistAction.REPLACE, model.playlists[1].action)
        assertEquals(3, model.totalTracks)
    }

    @Test fun `maps rename ops to rename rows`() {
        val f = fakeFile("Café.mp3")
        val rename = RenamePlan(
            ops = listOf(RenameOp(f, "Music/Rock", "Café.mp3", "Cafe.mp3", depth = 2)),
            collisions = emptyList()
        )
        val model = PreviewModelBuilder.build(scanOf(), rename)

        assertEquals(1, model.renames.size)
        assertEquals("Café.mp3", model.renames[0].oldName)
        assertEquals("Cafe.mp3", model.renames[0].newName)
        assertFalse(model.renames[0].isDirectory)
    }

    @Test fun `limit warnings become human-readable strings and clear withinLimits`() {
        val scan = scanOf(warnings = listOf(PlanWarning.TooManyPlaylists(60)))
        val model = PreviewModelBuilder.build(scan, RenamePlan(emptyList(), emptyList()))

        assertFalse(model.withinLimits)
        assertTrue(model.warnings.any { it.contains("60") })
    }

    @Test fun `collisions appear as warnings but do not clear withinLimits`() {
        val rename = RenamePlan(
            ops = emptyList(),
            collisions = listOf(RenameCollision("Music", "Cafe.mp3", listOf("Café.mp3", "Cafè.mp3")))
        )
        val model = PreviewModelBuilder.build(scanOf(), rename)

        assertTrue(model.withinLimits)                  // collisions are not a "limit" issue
        assertTrue(model.warnings.any { it.contains("Cafe.mp3") })
    }

    @Test fun `empty plan is within limits with nothing to do`() {
        val model = PreviewModelBuilder.build(scanOf(), RenamePlan(emptyList(), emptyList()))
        assertTrue(model.playlists.isEmpty())
        assertTrue(model.renames.isEmpty())
        assertTrue(model.warnings.isEmpty())
        assertTrue(model.withinLimits)
        assertEquals(0, model.totalTracks)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --tests '*PreviewModelBuilderTest*' --no-daemon 2>&1 | tail -12`
Expected: compile failure — `PreviewModelBuilder`, `PreviewModel`, `PlaylistAction`, `PlaylistRow`, `RenameRow` unresolved.

- [ ] **Step 3: Create `PreviewModel.kt`**

```kotlin
package com.cliplist.scan

/** Whether a folder's playlist is created fresh or replaces an existing one. */
enum class PlaylistAction { NEW, REPLACE }

data class PlaylistRow(
    val folderName: String,
    val trackCount: Int,
    val playlistName: String,
    val action: PlaylistAction
)

data class RenameRow(
    val parentPath: String,
    val oldName: String,
    val newName: String,
    val isDirectory: Boolean
)

/** Everything the Preview screen renders — derived purely from a ScanPlan + RenamePlan. */
data class PreviewModel(
    val playlists: List<PlaylistRow>,
    val totalTracks: Int,
    val renames: List<RenameRow>,
    val warnings: List<String>,
    val withinLimits: Boolean
)
```

- [ ] **Step 4: Create `PreviewModelBuilder.kt`**

```kotlin
package com.cliplist.scan

/** Turns the engine outputs into a flat, display-ready [PreviewModel]. Pure; no I/O. */
object PreviewModelBuilder {
    fun build(scan: ScanPlan, rename: RenamePlan): PreviewModel {
        val playlists = scan.folders.map { fp ->
            PlaylistRow(
                folderName = fp.folder.name,
                trackCount = fp.audioFiles.size,
                playlistName = fp.playlistName,
                action = if (fp.existingPlaylistName != null) PlaylistAction.REPLACE else PlaylistAction.NEW
            )
        }
        val renames = rename.ops.map { op ->
            RenameRow(op.parentPath, op.oldName, op.newName, op.isDirectory)
        }
        val warnings = buildList {
            scan.warnings.forEach { w ->
                when (w) {
                    is PlanWarning.TooManyTracksInFolder ->
                        add("\"${w.folderName}\" has ${w.count} tracks (Clip Sport max is 1000).")
                    is PlanWarning.TooManyPlaylists ->
                        add("${w.count} playlists — Clip Sport handles about 50.")
                }
            }
            rename.collisions.forEach { c ->
                val where = c.parentPath.ifEmpty { "the root folder" }
                add("Skipped in $where: ${c.sources.joinToString(", ")} → \"${c.targetName}\" would collide.")
            }
        }
        return PreviewModel(
            playlists = playlists,
            totalTracks = scan.folders.sumOf { it.audioFiles.size },
            renames = renames,
            warnings = warnings,
            withinLimits = scan.warnings.isEmpty()
        )
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --tests '*PreviewModelBuilderTest*' --no-daemon 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`, 5 tests passing.

- [ ] **Step 6: Commit**

```bash
cd /home/projects/mpc
git add core-scan/src/main/kotlin/com/cliplist/scan/PreviewModel.kt \
        core-scan/src/main/kotlin/com/cliplist/scan/PreviewModelBuilder.kt \
        core-scan/src/test/kotlin/com/cliplist/scan/PreviewModelBuilderTest.kt
git commit -m "feat(preview): PreviewModelBuilder — ScanPlan + RenamePlan -> display model

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: `:app` dependencies (`:data-storage` + lifecycle-runtime-compose)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the library to the version catalog**

In `gradle/libs.versions.toml`, under `[libraries]`, after the `lifecycle-viewmodel-compose` line, add:

```toml
lifecycle-runtime-compose   = { module = "androidx.lifecycle:lifecycle-runtime-compose",              version.ref = "lifecycle" }
```

- [ ] **Step 2: Add both dependencies to `app/build.gradle.kts`**

In the `dependencies { }` block, replace:

```kotlin
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(project(":core-scan"))
```

with:

```kotlin
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(project(":core-scan"))
    implementation(project(":data-storage"))
```

- [ ] **Step 3: Verify the catalog still parses (JVM tests unaffected)**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --no-daemon 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL` (this confirms the catalog edit didn't break Gradle configuration).

- [ ] **Step 4: Commit**

```bash
cd /home/projects/mpc
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build(app): depend on :data-storage (SafTreeVolume) + lifecycle-runtime-compose

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: `ScanViewModel` + workflow types

**Files:**
- Create: `app/src/main/kotlin/com/cliplist/app/workflow/ScanViewModel.kt`

- [ ] **Step 1: Create `ScanViewModel.kt`**

```kotlin
package com.cliplist.app.workflow

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cliplist.scan.AudioExtensions
import com.cliplist.scan.PlaylistPlanner
import com.cliplist.scan.PreviewModel
import com.cliplist.scan.PreviewModelBuilder
import com.cliplist.scan.RenameOptions
import com.cliplist.scan.RenamePlanner
import com.cliplist.scan.ScanOptions
import com.cliplist.storage.SafTreeVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The four Home toggles. Defaults match spec §3a. */
data class WorkflowOptions(
    val searchSubfolders: Boolean = true,
    val alphabetize: Boolean = true,
    val cleanNames: Boolean = false,
    val renameHidden: Boolean = false,
)

/** A folder the user granted via SAF. */
data class SelectedFolder(val uri: Uri, val displayName: String)

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Scanning : ScanUiState
    data class Ready(val model: PreviewModel) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

/**
 * Activity-scoped: shared by Home (sets folder/options, triggers scan) and Preview (reads result).
 * Scans run on Dispatchers.IO because the SAF volume does ContentResolver I/O.
 */
class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val _options = MutableStateFlow(WorkflowOptions())
    val options: StateFlow<WorkflowOptions> = _options.asStateFlow()

    private val _folder = MutableStateFlow<SelectedFolder?>(null)
    val folder: StateFlow<SelectedFolder?> = _folder.asStateFlow()

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    fun setFolder(folder: SelectedFolder) { _folder.value = folder }
    fun setSearchSubfolders(v: Boolean) = _options.update { it.copy(searchSubfolders = v) }
    fun setAlphabetize(v: Boolean) = _options.update { it.copy(alphabetize = v) }
    fun setCleanNames(v: Boolean) = _options.update { it.copy(cleanNames = v) }
    fun setRenameHidden(v: Boolean) = _options.update { it.copy(renameHidden = v) }

    /** Resets the scan result (e.g. when returning to Home to re-run). */
    fun clearResult() { _scanState.value = ScanUiState.Idle }

    fun scan() {
        val f = _folder.value ?: return
        val opts = _options.value
        _scanState.value = ScanUiState.Scanning
        viewModelScope.launch {
            try {
                val model = withContext(Dispatchers.IO) {
                    val volume = SafTreeVolume(getApplication(), f.uri)
                    val scanPlan = PlaylistPlanner().plan(
                        volume,
                        ScanOptions(
                            recursive = opts.searchSubfolders,
                            alphabetize = opts.alphabetize,
                            audioExtensions = AudioExtensions.DEFAULT
                        )
                    )
                    val renamePlan = RenamePlanner().plan(
                        volume,
                        RenameOptions(cleanNames = opts.cleanNames, renameHidden = opts.renameHidden)
                    )
                    PreviewModelBuilder.build(scanPlan, renamePlan)
                }
                _scanState.value = ScanUiState.Ready(model)
            } catch (e: Exception) {
                _scanState.value = ScanUiState.Error(e.message ?: "Scan failed")
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/workflow/ScanViewModel.kt
git commit -m "feat(app): ScanViewModel — holds folder/options, runs planners off-main-thread

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Shared ViewModel in nav + Home screen (SAF picker, toggles, Scan)

**Files:**
- Modify: `app/src/main/kotlin/com/cliplist/app/nav/AppNavGraph.kt`
- Replace: `app/src/main/kotlin/com/cliplist/app/ui/home/HomeScreen.kt`

- [ ] **Step 1: Update `AppNavGraph.kt` to create + share the ViewModel**

Replace the whole file with:

```kotlin
package com.cliplist.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cliplist.app.ui.home.HomeScreen
import com.cliplist.app.ui.preview.PreviewScreen
import com.cliplist.app.ui.progress.ProgressScreen
import com.cliplist.app.ui.results.ResultsScreen
import com.cliplist.app.ui.settings.SettingsScreen
import com.cliplist.app.workflow.ScanViewModel

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    // Activity-scoped: one instance shared by Home and Preview.
    val scanViewModel: ScanViewModel = viewModel()
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route)     { HomeScreen(navController, scanViewModel) }
        composable(Screen.Preview.route)  { PreviewScreen(navController, scanViewModel) }
        composable(Screen.Progress.route) { ProgressScreen(navController) }
        composable(Screen.Results.route)  { ResultsScreen(navController) }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
    }
}
```

- [ ] **Step 2: Replace `HomeScreen.kt`**

```kotlin
package com.cliplist.app.ui.home

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cliplist.app.nav.Screen
import com.cliplist.app.workflow.ScanUiState
import com.cliplist.app.workflow.ScanViewModel
import com.cliplist.app.workflow.SelectedFolder

@Composable
fun HomeScreen(navController: NavController, vm: ScanViewModel) {
    val context = LocalContext.current
    val options by vm.options.collectAsStateWithLifecycle()
    val folder by vm.folder.collectAsStateWithLifecycle()
    val scanState by vm.scanState.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val name = docId.substringAfterLast('/').substringAfterLast(':').ifEmpty { "Selected folder" }
            vm.setFolder(SelectedFolder(uri, name))
        }
    }

    // When a scan finishes, move to Preview exactly once.
    androidx.compose.runtime.LaunchedEffect(scanState) {
        if (scanState is ScanUiState.Ready) navController.navigate(Screen.Preview.route)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("My Playlist Creator", style = MaterialTheme.typography.headlineMedium)
            Text(
                "for SanDisk Clip Sport",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            Card(onClick = { picker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("MUSIC FOLDER", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        folder?.displayName ?: "Tap to choose a folder",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            ToggleRow("Search subfolders", null, options.searchSubfolders, vm::setSearchSubfolders)
            ToggleRow("Alphabetize tracks", null, options.alphabetize, vm::setAlphabetize)
            ToggleRow("Clean file names", "Plain ASCII for SanDisk Clip Sport",
                options.cleanNames, vm::setCleanNames)
            ToggleRow("Rename hidden files", "For SanDisk Clip Sport compatibility",
                options.renameHidden, vm::setRenameHidden)
            Spacer(Modifier.height(24.dp))

            val scanning = scanState is ScanUiState.Scanning
            Button(
                onClick = { vm.scan() },
                enabled = folder != null && !scanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (scanning) "Scanning…" else "Scan folder")
            }
            (scanState as? ScanUiState.Error)?.let {
                Spacer(Modifier.height(12.dp))
                Text(it.message, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.fillMaxWidth(0.8f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
```

- [ ] **Step 3: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/nav/AppNavGraph.kt \
        app/src/main/kotlin/com/cliplist/app/ui/home/HomeScreen.kt
git commit -m "feat(home): SAF folder picker + 4 toggles + Scan wired to ScanViewModel

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Preview screen (render the model)

**Files:**
- Replace: `app/src/main/kotlin/com/cliplist/app/ui/preview/PreviewScreen.kt`

- [ ] **Step 1: Replace `PreviewScreen.kt`**

```kotlin
package com.cliplist.app.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cliplist.app.nav.Screen
import com.cliplist.app.workflow.ScanUiState
import com.cliplist.app.workflow.ScanViewModel
import com.cliplist.scan.PlaylistAction
import com.cliplist.scan.PlaylistRow
import com.cliplist.scan.PreviewModel
import com.cliplist.scan.RenameRow

@Composable
fun PreviewScreen(navController: NavController, vm: ScanViewModel) {
    val scanState by vm.scanState.collectAsStateWithLifecycle()
    val model = (scanState as? ScanUiState.Ready)?.model

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        if (model == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Text("Nothing to preview yet.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("Preview", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${model.playlists.size} playlists · ${model.totalTracks} tracks" +
                        if (model.withinLimits) " · within Clip Sport limits" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }
            if (model.warnings.isNotEmpty()) {
                item { SectionHeader("Warnings") }
                items(model.warnings) { w ->
                    WarningCard(w)
                    Spacer(Modifier.height(8.dp))
                }
            }
            item { SectionHeader("Playlists") }
            items(model.playlists) { p ->
                PlaylistCard(p)
                Spacer(Modifier.height(8.dp))
            }
            if (model.renames.isNotEmpty()) {
                item { SectionHeader("Renames (${model.renames.size})") }
                items(model.renames) { r ->
                    RenameCardRow(r)
                    Spacer(Modifier.height(8.dp))
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { navController.navigate(Screen.Progress.route) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Generate playlists") }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun PlaylistCard(p: PlaylistRow) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(p.folderName, style = MaterialTheme.typography.titleMedium)
                Text("${p.trackCount} tracks · ${p.playlistName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (p.action == PlaylistAction.NEW) "NEW" else "REPLACE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (p.action == PlaylistAction.NEW)
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun RenameCardRow(r: RenameRow) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("${r.oldName}  →  ${r.newName}", style = MaterialTheme.typography.bodyMedium)
            Text(
                (if (r.isDirectory) "folder" else "file") +
                    (if (r.parentPath.isNotEmpty()) " in ${r.parentPath}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/ui/preview/PreviewScreen.kt
git commit -m "feat(preview): render PreviewModel — playlists, renames, warnings, generate

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: CI — `:app:assembleDebug` green

**Files:** none (verification only)

- [ ] **Step 1: Confirm JVM suite locally**

Run: `cd /home/projects/mpc && ./gradlew :core-format:test :core-scan:test --no-daemon 2>&1 | tail -4`
Expected: `BUILD SUCCESSFUL`, **90 tests** (85 + 5 new from Task 1).

- [ ] **Step 2: Push and watch CI**

```bash
cd /home/projects/mpc
git push origin main
sleep 8
RUN_ID=$(gh run list --workflow=build.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run watch "$RUN_ID" --exit-status --interval 25 2>&1 | tail -3
```
Expected: both `jvm-tests` (90 tests) and `Android modules build` (`:app:assembleDebug` compiles the ViewModel + both screens) green.

**If `android-build` fails:** read `gh run view "$RUN_ID" --log-failed 2>&1 | head -80`. Likely causes & fixes:
- `Unresolved reference: collectAsStateWithLifecycle` → confirm `lifecycle-runtime-compose` was added (Task 2) and synced.
- `Unresolved reference: SafTreeVolume` → confirm `implementation(project(":data-storage"))` (Task 2).
- `viewModel()` ambiguity / missing → import is `androidx.lifecycle.viewmodel.compose.viewModel`.
- `items` unresolved in LazyColumn → import `androidx.compose.foundation.lazy.items`.
- Any Compose API mismatch → fix the specific symbol the compiler names; the BOM (`2026.05.01`) governs Compose versions.

---

## Definition of Done (Phase 3b-2)

- [ ] `:core-format:test :core-scan:test` passes: **90 tests**, 0 failures.
- [ ] Both CI jobs green; `:app:assembleDebug` produces an APK with the new screens.
- [ ] Home: SAF `OpenDocumentTree` picker, persisted permission, 4 toggles bound to `ScanViewModel`, Scan disabled until a folder is chosen, "Scanning…" state.
- [ ] Scan runs on `Dispatchers.IO`; on success navigates to Preview; on failure shows an error.
- [ ] Preview renders playlists (NEW/REPLACE), rename rows, warnings, within-limits note, and a Generate button → Progress (stub).
- [ ] All screens vertically scrollable (`verticalScroll`/`LazyColumn`), text in theme `sp` styles, no fixed content heights — survives 200% font / 360 dp (spec §3a editorial requirement).
- [ ] No writes to disk anywhere in 3b-2 (preview only; generation is 3b-3).

---

## Self-review (completed by plan author)

**Spec coverage (3b-2 scope):** §3a Home four toggles → Task 4 `ToggleRow`s bound to `ScanViewModel`. §3a SAF picker → Task 4 `OpenDocumentTree` + persisted permission. §3a Preview (badges, counts, rename previews, limit check) → Tasks 1 + 5. §3a edge-to-edge/responsive → screens scroll + use sp styles + `Scaffold` insets (from 3a). Out of scope (later): actual generation/Progress/Results (3b-3), Settings/localization (3c), eject + folder.jpg (3b-3/3c).

**Placeholder scan:** none — every step has complete code and an exact command.

**Type consistency:** `PreviewModel`/`PlaylistRow`/`RenameRow`/`PlaylistAction` defined Task 1, consumed in Task 5. `ScanViewModel`/`ScanUiState`/`SelectedFolder`/`WorkflowOptions` defined Task 3, used in Tasks 4–5. `ScanOptions(recursive, alphabetize, audioExtensions)` and `RenameOptions(cleanNames, renameHidden)` match the real signatures (verified in `:core-scan`). `SafTreeVolume(context, uri)` matches `:data-storage`. `FolderPlan(folder, audioFiles, existingPlaylistName, playlistName)` and `PlanWarning` subtypes match `PlaylistPlanner.kt`. `RenameOp(node, parentPath, oldName, newName, depth)` + `.isDirectory` and `RenameCollision(parentPath, targetName, sources)` match Phase 3b-1.

**Android-without-SDK caveat:** Tasks 3–5 can't compile locally; Task 6 is the real gate and includes a failure-triage list. The pure model (Task 1) carries the testable logic.
