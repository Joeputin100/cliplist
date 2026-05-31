# Phase 3b-3 — Execute → Progress → Results Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the app act — apply the planned renames (reversibly), write the byte-exact `.m3u` playlists with live progress, and show a Results screen with an eject hand-off. This completes the end-to-end workflow.

**Architecture:** `PlaylistWriter` gains an optional progress callback (default no-op). A pure `ResultModelBuilder` (`:core-scan`, JVM-tested) folds a `WriteReport` + optional `RenameExecution` into a `ResultModel`. `ScanViewModel.generate()` orchestrates on `Dispatchers.IO`: **apply renames → re-scan (so playlists reference the cleaned names) → write playlists with progress → build result.** Progress and Results screens observe a new `generateState`. Results offers an eject deep-link to the system storage screen.

**Tech Stack:** Kotlin 2.3.20 · Compose BOM 2026.05.01 · Material3 · Navigation Compose 2.9.8 · Lifecycle 2.10.0 · `:core-scan` + `:data-storage`. minSdk 24.

**Order-of-operations (correctness):** when renames happen, the original `ScanPlan` lists pre-rename filenames. So `generate()` re-runs `PlaylistPlanner` *after* renaming, and writes that fresh plan — the `.m3u` then lists the final, cleaned names.

**Out of scope (deferred):** `folder.jpg` generation (needs a binary/mime write path; "an extra") and per-rename progress granularity (rename phase shows an indeterminate bar). Settings/localization = 3c; discoverability/signing = 3d.

**Local-test note:** Tasks 1–2 are JVM-tested locally. Tasks 3–5 (`:app`) compile only in CI (Task 6).

---

## File map

```
core-scan/src/main/kotlin/com/cliplist/scan/
  PlaylistWriter.kt          ← MODIFY: add onProgress callback (default {})
  ResultModel.kt             ← CREATE: ResultModel + ResultModelBuilder
core-scan/src/test/kotlin/com/cliplist/scan/
  PlaylistWriterProgressTest.kt ← CREATE (Task 1)
  ResultModelBuilderTest.kt     ← CREATE (Task 2)

app/src/main/kotlin/com/cliplist/app/
  workflow/ScanViewModel.kt  ← MODIFY: retain plans; add GenerateUiState + generate()
  nav/AppNavGraph.kt         ← MODIFY: pass ScanViewModel to Progress + Results
  ui/preview/PreviewScreen.kt← MODIFY: Generate button calls vm.generate() then navigates
  ui/progress/ProgressScreen.kt ← REPLACE: observe generateState, show progress, → Results
  ui/results/ResultsScreen.kt   ← REPLACE: show ResultModel + eject deep-link + make-another
```

---

### Task 1: `PlaylistWriter` progress callback

**Files:**
- Modify: `core-scan/src/main/kotlin/com/cliplist/scan/PlaylistWriter.kt`
- Test: `core-scan/src/test/kotlin/com/cliplist/scan/PlaylistWriterProgressTest.kt`

- [ ] **Step 1: Write the failing test**

Create `core-scan/src/test/kotlin/com/cliplist/scan/PlaylistWriterProgressTest.kt`:

```kotlin
package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlaylistWriterProgressTest {

    @Test fun `onProgress is called once per folder with running done and total`() {
        val root = fakeDir("Music",
            fakeDir("Rock", fakeFile("a.mp3")),
            fakeDir("Jazz", fakeFile("b.mp3")))
        val volume = FakeVolume(root)
        val rock = root.children[0]; val jazz = root.children[1]
        val plan = ScanPlan(
            folders = listOf(
                FolderPlan(rock, listOf("a.mp3"), null, "Rock.m3u"),
                FolderPlan(jazz, listOf("b.mp3"), null, "Jazz.m3u")
            ),
            warnings = emptyList()
        )
        val seen = mutableListOf<Pair<Int, Int>>()

        val report = PlaylistWriter(volume).execute(plan) { done, total -> seen.add(done to total) }

        assertEquals(2, report.written)
        assertEquals(listOf(1 to 2, 2 to 2), seen)
    }

    @Test fun `default execute still works with no callback`() {
        val root = fakeDir("Rock", fakeFile("a.mp3"))
        val volume = FakeVolume(root)
        val plan = ScanPlan(listOf(FolderPlan(root, listOf("a.mp3"), null, "Rock.m3u")), emptyList())
        assertEquals(1, PlaylistWriter(volume).execute(plan).written)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --tests '*PlaylistWriterProgressTest*' --no-daemon 2>&1 | tail -12`
Expected: FAIL — `execute` does not accept a trailing lambda.

- [ ] **Step 3: Add the callback to `PlaylistWriter.execute`**

Replace `core-scan/src/main/kotlin/com/cliplist/scan/PlaylistWriter.kt` with:

```kotlin
package com.cliplist.scan

import com.cliplist.format.M3uSerializer
import com.cliplist.format.SerializerOptions

data class WriteReport(val written: Int, val failed: Int, val errors: List<String>)

class PlaylistWriter(private val volume: StorageVolume) {
    /**
     * Writes one playlist per folder. [onProgress] is invoked after each folder with
     * (foldersDone, totalFolders) so the UI can show live progress. Default is a no-op.
     */
    fun execute(plan: ScanPlan, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): WriteReport {
        var written = 0
        var failed = 0
        val errors = mutableListOf<String>()
        val total = plan.folders.size

        plan.folders.forEachIndexed { index, fp ->
            fp.existingPlaylistName?.let { volume.deleteFile(fp.folder, it) }
            // Files already sorted by planner; pass alphabetize=false to preserve order.
            val bytes = M3uSerializer.serialize(fp.audioFiles, SerializerOptions(alphabetize = false))
            when (val result = volume.writeFile(fp.folder, fp.playlistName, bytes)) {
                is VolumeWriteResult.Success -> written++
                is VolumeWriteResult.Failure -> {
                    failed++
                    errors.add("${fp.folder.name}/${fp.playlistName}: ${result.message}")
                }
            }
            onProgress(index + 1, total)
        }

        return WriteReport(written = written, failed = failed, errors = errors)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --tests '*PlaylistWriterProgressTest*' --no-daemon 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`, 2 tests passing.

- [ ] **Step 5: Commit**

```bash
cd /home/projects/mpc
git add core-scan/src/main/kotlin/com/cliplist/scan/PlaylistWriter.kt \
        core-scan/src/test/kotlin/com/cliplist/scan/PlaylistWriterProgressTest.kt
git commit -m "feat(write): PlaylistWriter progress callback (default no-op)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: `ResultModel` + `ResultModelBuilder`

**Files:**
- Create: `core-scan/src/main/kotlin/com/cliplist/scan/ResultModel.kt`
- Test: `core-scan/src/test/kotlin/com/cliplist/scan/ResultModelBuilderTest.kt`

- [ ] **Step 1: Write the failing test**

Create `core-scan/src/test/kotlin/com/cliplist/scan/ResultModelBuilderTest.kt`:

```kotlin
package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ResultModelBuilderTest {

    @Test fun `combines write report and rename execution`() {
        val write = WriteReport(written = 12, failed = 1, errors = listOf("Rock/Rock.m3u: boom"))
        val node = fakeFile("Cafe.mp3")
        val rename = RenameExecution(
            applied = listOf(AppliedRename(node, "Music", "Café.mp3", "Cafe.mp3")),
            failed = listOf(RenameFailure(
                RenameOp(node, "Music", "Bad.mp3", "Good.mp3", depth = 1), "denied"))
        )

        val r = ResultModelBuilder.build(write, rename)

        assertEquals(12, r.playlistsWritten)
        assertEquals(1, r.playlistsFailed)
        assertEquals(1, r.renamesApplied)
        assertEquals(1, r.renamesFailed)
        assertEquals(2, r.totalFailed)
        assertFalse(r.allSucceeded)
        assertEquals(2, r.errors.size)
    }

    @Test fun `null rename means zero renames and success when nothing failed`() {
        val r = ResultModelBuilder.build(WriteReport(5, 0, emptyList()), null)
        assertEquals(5, r.playlistsWritten)
        assertEquals(0, r.renamesApplied)
        assertEquals(0, r.totalFailed)
        assertTrue(r.allSucceeded)
        assertTrue(r.errors.isEmpty())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --tests '*ResultModelBuilderTest*' --no-daemon 2>&1 | tail -12`
Expected: compile failure — `ResultModel`, `ResultModelBuilder` unresolved.

- [ ] **Step 3: Create `ResultModel.kt`**

```kotlin
package com.cliplist.scan

/** Final outcome of a generation run, shown on the Results screen. */
data class ResultModel(
    val playlistsWritten: Int,
    val playlistsFailed: Int,
    val renamesApplied: Int,
    val renamesFailed: Int,
    val errors: List<String>,
) {
    val totalFailed: Int get() = playlistsFailed + renamesFailed
    val allSucceeded: Boolean get() = totalFailed == 0
}

object ResultModelBuilder {
    fun build(write: WriteReport, rename: RenameExecution?): ResultModel =
        ResultModel(
            playlistsWritten = write.written,
            playlistsFailed = write.failed,
            renamesApplied = rename?.applied?.size ?: 0,
            renamesFailed = rename?.failed?.size ?: 0,
            errors = write.errors + (rename?.failed?.map { "${it.op.oldName}: ${it.message}" } ?: emptyList()),
        )
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --tests '*ResultModelBuilderTest*' --no-daemon 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`, 2 tests passing.

- [ ] **Step 5: Commit**

```bash
cd /home/projects/mpc
git add core-scan/src/main/kotlin/com/cliplist/scan/ResultModel.kt \
        core-scan/src/test/kotlin/com/cliplist/scan/ResultModelBuilderTest.kt
git commit -m "feat(result): ResultModel + builder (write report + rename execution)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: `ScanViewModel` — retain plans + `generate()`

**Files:**
- Modify: `app/src/main/kotlin/com/cliplist/app/workflow/ScanViewModel.kt`

- [ ] **Step 1: Replace `ScanViewModel.kt` with the version below**

This keeps everything from 3b-2 and adds: retained `lastScanPlan`/`lastRenamePlan`/`lastScanOptions`, `GenerateUiState`, `_generateState`, and `generate()`.

```kotlin
package com.cliplist.app.workflow

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cliplist.scan.AudioExtensions
import com.cliplist.scan.PlaylistPlanner
import com.cliplist.scan.PlaylistWriter
import com.cliplist.scan.PreviewModel
import com.cliplist.scan.PreviewModelBuilder
import com.cliplist.scan.RenameExecution
import com.cliplist.scan.RenameExecutor
import com.cliplist.scan.RenameOptions
import com.cliplist.scan.RenamePlan
import com.cliplist.scan.RenamePlanner
import com.cliplist.scan.ResultModel
import com.cliplist.scan.ResultModelBuilder
import com.cliplist.scan.ScanOptions
import com.cliplist.scan.ScanPlan
import com.cliplist.storage.SafTreeVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WorkflowOptions(
    val searchSubfolders: Boolean = true,
    val alphabetize: Boolean = true,
    val cleanNames: Boolean = false,
    val renameHidden: Boolean = false,
)

data class SelectedFolder(val uri: Uri, val displayName: String)

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Scanning : ScanUiState
    data class Ready(val model: PreviewModel) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

sealed interface GenerateUiState {
    data object Idle : GenerateUiState
    data class Working(val phase: String, val done: Int, val total: Int) : GenerateUiState
    data class Done(val result: ResultModel) : GenerateUiState
    data class Error(val message: String) : GenerateUiState
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val _options = MutableStateFlow(WorkflowOptions())
    val options: StateFlow<WorkflowOptions> = _options.asStateFlow()

    private val _folder = MutableStateFlow<SelectedFolder?>(null)
    val folder: StateFlow<SelectedFolder?> = _folder.asStateFlow()

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private val _generateState = MutableStateFlow<GenerateUiState>(GenerateUiState.Idle)
    val generateState: StateFlow<GenerateUiState> = _generateState.asStateFlow()

    // Raw plans retained from the last scan, needed to actually execute.
    private var lastScanPlan: ScanPlan? = null
    private var lastRenamePlan: RenamePlan? = null
    private var lastScanOptions: ScanOptions? = null

    fun setFolder(folder: SelectedFolder) { _folder.value = folder }
    fun setSearchSubfolders(v: Boolean) = _options.update { it.copy(searchSubfolders = v) }
    fun setAlphabetize(v: Boolean) = _options.update { it.copy(alphabetize = v) }
    fun setCleanNames(v: Boolean) = _options.update { it.copy(cleanNames = v) }
    fun setRenameHidden(v: Boolean) = _options.update { it.copy(renameHidden = v) }

    fun clearResult() { _scanState.value = ScanUiState.Idle }

    fun scan() {
        val f = _folder.value ?: return
        val opts = _options.value
        _scanState.value = ScanUiState.Scanning
        viewModelScope.launch {
            try {
                val model = withContext(Dispatchers.IO) {
                    val volume = SafTreeVolume(getApplication(), f.uri)
                    val scanOptions = ScanOptions(
                        recursive = opts.searchSubfolders,
                        alphabetize = opts.alphabetize,
                        audioExtensions = AudioExtensions.DEFAULT
                    )
                    val scanPlan = PlaylistPlanner().plan(volume, scanOptions)
                    val renamePlan = RenamePlanner().plan(
                        volume,
                        RenameOptions(cleanNames = opts.cleanNames, renameHidden = opts.renameHidden)
                    )
                    lastScanPlan = scanPlan
                    lastRenamePlan = renamePlan
                    lastScanOptions = scanOptions
                    PreviewModelBuilder.build(scanPlan, renamePlan)
                }
                _scanState.value = ScanUiState.Ready(model)
            } catch (e: Exception) {
                _scanState.value = ScanUiState.Error(e.message ?: "Scan failed")
            }
        }
    }

    /**
     * Applies renames (if any), re-scans so playlists reference the cleaned names, writes the
     * playlists with progress, and publishes a ResultModel. No-op if no scan has been run.
     */
    fun generate() {
        val f = _folder.value ?: return
        val scanPlan = lastScanPlan ?: return
        val renamePlan = lastRenamePlan ?: return
        val scanOptions = lastScanOptions ?: return
        _generateState.value = GenerateUiState.Working("Starting…", 0, scanPlan.folders.size)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val volume = SafTreeVolume(getApplication(), f.uri)
                    var renameExec: RenameExecution? = null
                    if (renamePlan.ops.isNotEmpty()) {
                        _generateState.value =
                            GenerateUiState.Working("Cleaning names…", 0, renamePlan.ops.size)
                        renameExec = RenameExecutor(volume).execute(renamePlan)
                    }
                    // After renames, filenames changed — re-scan so the .m3u lists the new names.
                    val planToWrite = if (renamePlan.ops.isNotEmpty())
                        PlaylistPlanner().plan(volume, scanOptions) else scanPlan
                    val report = PlaylistWriter(volume).execute(planToWrite) { done, total ->
                        _generateState.value =
                            GenerateUiState.Working("Writing playlists…", done, total)
                    }
                    ResultModelBuilder.build(report, renameExec)
                }
                _generateState.value = GenerateUiState.Done(result)
            } catch (e: Exception) {
                _generateState.value = GenerateUiState.Error(e.message ?: "Generation failed")
            }
        }
    }

    /** Resets everything for "make another playlist". */
    fun resetWorkflow() {
        _scanState.value = ScanUiState.Idle
        _generateState.value = GenerateUiState.Idle
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/workflow/ScanViewModel.kt
git commit -m "feat(app): ScanViewModel.generate() — rename, re-scan, write with progress

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Preview Generate wiring + Progress screen

**Files:**
- Modify: `app/src/main/kotlin/com/cliplist/app/ui/preview/PreviewScreen.kt`
- Replace: `app/src/main/kotlin/com/cliplist/app/ui/progress/ProgressScreen.kt`

- [ ] **Step 1: In `PreviewScreen.kt`, make Generate start the work then navigate**

Find this block (the Generate button item):

```kotlin
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { navController.navigate(Screen.Progress.route) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Generate playlists") }
            }
```

Replace it with:

```kotlin
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        vm.generate()
                        navController.navigate(Screen.Progress.route)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Generate playlists") }
            }
```

- [ ] **Step 2: Replace `ProgressScreen.kt`**

```kotlin
package com.cliplist.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cliplist.app.nav.Screen
import com.cliplist.app.workflow.GenerateUiState
import com.cliplist.app.workflow.ScanViewModel

@Composable
fun ProgressScreen(navController: NavController, vm: ScanViewModel) {
    val state by vm.generateState.collectAsStateWithLifecycle()

    // When done, go to Results and remove Progress from the back stack.
    androidx.compose.runtime.LaunchedEffect(state) {
        if (state is GenerateUiState.Done) {
            navController.navigate(Screen.Results.route) {
                popUpTo(Screen.Progress.route) { inclusive = true }
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val s = state) {
                is GenerateUiState.Working -> {
                    Text(s.phase, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(20.dp))
                    if (s.total > 0 && s.done > 0) {
                        LinearProgressIndicator(
                            progress = { s.done.toFloat() / s.total.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("${s.done} of ${s.total}",
                            style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Writing .m3u files in CRLF format",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
                is GenerateUiState.Error -> {
                    Text("Something went wrong", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Text(s.message, color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center)
                }
                else -> {
                    Text("Preparing…", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/ui/preview/PreviewScreen.kt \
        app/src/main/kotlin/com/cliplist/app/ui/progress/ProgressScreen.kt
git commit -m "feat(progress): live generation progress; Generate triggers work + navigates

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Results screen + eject deep-link + nav wiring

**Files:**
- Modify: `app/src/main/kotlin/com/cliplist/app/nav/AppNavGraph.kt`
- Replace: `app/src/main/kotlin/com/cliplist/app/ui/results/ResultsScreen.kt`

- [ ] **Step 1: Update `AppNavGraph.kt` to pass the ViewModel to Progress + Results**

Replace the two relevant `composable(...)` lines:

```kotlin
        composable(Screen.Progress.route) { ProgressScreen(navController) }
        composable(Screen.Results.route)  { ResultsScreen(navController) }
```

with:

```kotlin
        composable(Screen.Progress.route) { ProgressScreen(navController, scanViewModel) }
        composable(Screen.Results.route)  { ResultsScreen(navController, scanViewModel) }
```

- [ ] **Step 2: Replace `ResultsScreen.kt`**

```kotlin
package com.cliplist.app.ui.results

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cliplist.app.nav.Screen
import com.cliplist.app.workflow.GenerateUiState
import com.cliplist.app.workflow.ScanViewModel

@Composable
fun ResultsScreen(navController: NavController, vm: ScanViewModel) {
    val context = LocalContext.current
    val state by vm.generateState.collectAsStateWithLifecycle()
    val result = (state as? GenerateUiState.Done)?.result

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (result == null) {
                Text("No results yet.", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Text(
                if (result.allSucceeded) "All playlists created" else "Finished with some issues",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "${result.playlistsWritten} written · ${result.totalFailed} failed" +
                    if (result.renamesApplied > 0) " · ${result.renamesApplied} renamed" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (result.errors.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                result.errors.take(5).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(28.dp))
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Eject SD card") }
            Text(
                "Opens Android's storage screen — apps can't eject directly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    vm.resetWorkflow()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Done") }
            TextButton(
                onClick = {
                    vm.resetWorkflow()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            ) { Text("Make another playlist") }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/nav/AppNavGraph.kt \
        app/src/main/kotlin/com/cliplist/app/ui/results/ResultsScreen.kt
git commit -m "feat(results): show outcome + eject deep-link + make-another; wire VM into nav

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: CI — full flow assembles

**Files:** none (verification only)

- [ ] **Step 1: Confirm JVM suite locally**

Run: `cd /home/projects/mpc && ./gradlew :core-format:test :core-scan:test --no-daemon 2>&1 | tail -4`
Expected: `BUILD SUCCESSFUL`, **94 tests** (90 + 4 new: 2 from Task 1, 2 from Task 2).

- [ ] **Step 2: Push and watch CI**

```bash
cd /home/projects/mpc
git push origin main
sleep 8
RUN_ID=$(gh run list --workflow=build.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run watch "$RUN_ID" --exit-status --interval 25 2>&1 | tail -3
```
Expected: both jobs green; `:app:assembleDebug` compiles the new ViewModel logic + Progress/Results screens.

**If `android-build` fails:** `gh run view "$RUN_ID" --log-failed 2>&1 | head -80`. Likely:
- `LinearProgressIndicator(progress = { ... })` — the lambda-progress overload is current in Material3 (BOM 2026.05.01); if it flags the old `Float` overload, the compiler will name it — match the signature it wants.
- `Settings.ACTION_INTERNAL_STORAGE_SETTINGS` unresolved → it's in `android.provider.Settings` (import present).
- Unresolved `generate`/`resetWorkflow`/`GenerateUiState` → confirm Task 3 replaced `ScanViewModel.kt` fully.

---

## Definition of Done (Phase 3b-3)

- [ ] `:core-format:test :core-scan:test` passes: **94 tests**, 0 failures.
- [ ] Both CI jobs green; APK assembles with Progress + Results.
- [ ] `generate()` order is rename → re-scan → write; playlists list post-rename names.
- [ ] Progress shows live per-folder progress while writing; Results shows written/failed/renamed + errors.
- [ ] Eject button deep-links to `Settings.ACTION_INTERNAL_STORAGE_SETTINGS`.
- [ ] "Done" / "Make another" reset the workflow and return Home.
- [ ] `PlaylistWriter`'s new callback defaults to no-op (existing callers/tests unaffected).

---

## Self-review (completed by plan author)

**Spec coverage (3b-3 scope):** §3a Progress (live, CRLF mention) → Task 4. §3a Results (written/failed) → Task 5. §3a eject deep-link (`Settings.ACTION_INTERNAL_STORAGE_SETTINGS`) → Task 5. Byte-exact write via existing `PlaylistWriter`/`M3uSerializer` → Tasks 1/3. Non-destructive (reversible renames, no deletes beyond replacing a playlist's own prior file) → uses 3b-1 `RenameExecutor` + the writer's existing replace-then-write. folder.jpg + per-rename progress → explicitly deferred.

**Placeholder scan:** none — complete code + exact commands throughout.

**Type consistency:** `WriteReport(written, failed, errors)` matches `PlaylistWriter.kt`. `RenameExecution(applied, failed)`, `AppliedRename`, `RenameFailure(op, message)`, `RenameOp(node, parentPath, oldName, newName, depth)` match 3b-1. `ScanOptions(recursive, alphabetize, audioExtensions)` matches. `ResultModel`/`ResultModelBuilder` defined Task 2, used in Task 3 + Task 5. `GenerateUiState` defined Task 3, used in Tasks 4–5. `Screen.{Home,Progress,Results}` routes exist. `PlaylistWriter.execute(plan, onProgress)` new signature used in Task 3.

**Android-without-SDK caveat:** Tasks 3–5 compile only in CI (Task 6 triage list provided). Tasks 1–2 carry the testable logic.
```
