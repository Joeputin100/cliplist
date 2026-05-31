# Phase 3c-2a — Settings: Theme + Audio Extensions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Settings screen that lets the user override the **theme** (System / Light / Dark) and choose which **audio extensions** are scanned — both persisted with DataStore and applied app-wide.

**Architecture:** A `SettingsRepository` over a single Preferences `DataStore` exposes `themeMode` + `audioExtensions` as `Flow`s. `SettingsViewModel` (Activity-scoped, created in `MainActivity`, passed down) surfaces them as `StateFlow`s. `MainActivity` maps `ThemeMode` → `darkTheme` for the existing `ClipListTheme`. `ScanViewModel` reads the chosen extensions when scanning. Per-app **language** switching is a separate phase (3c-2b) — not here.

**Tech Stack:** Compose Material3 · DataStore Preferences 1.2.1 · Lifecycle 2.10.0 · `:core-scan` (`AudioExtensions`). minSdk 24. All `:app` (CI-validated; no local SDK).

---

## File map

```
gradle/libs.versions.toml      ← add datastore version + library
app/build.gradle.kts           ← add datastore-preferences dep
app/src/main/kotlin/com/cliplist/app/
  settings/SettingsRepository.kt ← CREATE: ThemeMode + DataStore-backed repo
  settings/SettingsViewModel.kt  ← CREATE: Activity-scoped StateFlows + setters
  ui/settings/SettingsScreen.kt  ← REPLACE: theme radios + extension switches
  MainActivity.kt                ← MODIFY: map ThemeMode -> darkTheme; pass SettingsViewModel down
  nav/AppNavGraph.kt             ← MODIFY: accept settingsViewModel; pass to SettingsScreen
  ui/home/HomeScreen.kt          ← MODIFY: add a "Settings" button -> Settings
  workflow/ScanViewModel.kt      ← MODIFY: scan() uses persisted audio extensions
```

---

### Task 1: DataStore dep + `SettingsRepository`

**Files:** Modify `gradle/libs.versions.toml`, `app/build.gradle.kts`; Create `app/src/main/kotlin/com/cliplist/app/settings/SettingsRepository.kt`

- [ ] **Step 1: Version catalog** — under `[versions]` add `datastore = "1.2.1"`; under `[libraries]` add:

```toml
datastore-preferences       = { module = "androidx.datastore:datastore-preferences",                  version.ref = "datastore" }
```

- [ ] **Step 2: `app/build.gradle.kts`** — add to `dependencies { }` (after `lifecycle.runtime.compose`):

```kotlin
    implementation(libs.datastore.preferences)
```

- [ ] **Step 3: Create `SettingsRepository.kt`**

```kotlin
package com.cliplist.app.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cliplist.scan.AudioExtensions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { System, Light, Dark }

// Process-singleton DataStore for the whole app.
private val Context.settingsDataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val extsKey = stringSetPreferencesKey("audio_extensions")

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { p ->
        runCatching { ThemeMode.valueOf(p[themeKey] ?: ThemeMode.System.name) }
            .getOrDefault(ThemeMode.System)
    }

    val audioExtensions: Flow<Set<String>> = context.settingsDataStore.data.map { p ->
        p[extsKey] ?: AudioExtensions.DEFAULT
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setAudioExtensions(exts: Set<String>) {
        context.settingsDataStore.edit { it[extsKey] = exts }
    }
}
```

- [ ] **Step 4: Verify catalog parses (JVM build configures):** `cd /home/projects/mpc && ./gradlew :core-scan:test --no-daemon 2>&1 | tail -3` → `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
cd /home/projects/mpc
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/kotlin/com/cliplist/app/settings/SettingsRepository.kt
git commit -m "feat(settings): DataStore-backed SettingsRepository (theme + audio extensions)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: `SettingsViewModel`

**Files:** Create `app/src/main/kotlin/com/cliplist/app/settings/SettingsViewModel.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.cliplist.app.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cliplist.scan.AudioExtensions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)

    val themeMode: StateFlow<ThemeMode> =
        repo.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.System)

    val audioExtensions: StateFlow<Set<String>> =
        repo.audioExtensions.stateIn(viewModelScope, SharingStarted.Eagerly, AudioExtensions.DEFAULT)

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }

    fun toggleExtension(ext: String, enabled: Boolean) = viewModelScope.launch {
        val current = audioExtensions.value
        repo.setAudioExtensions(if (enabled) current + ext else current - ext)
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/settings/SettingsViewModel.kt
git commit -m "feat(settings): SettingsViewModel exposing theme + extensions as StateFlows

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Settings screen

**Files:** Replace `app/src/main/kotlin/com/cliplist/app/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Replace the file**

```kotlin
package com.cliplist.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cliplist.app.R
import com.cliplist.app.settings.SettingsViewModel
import com.cliplist.app.settings.ThemeMode
import com.cliplist.scan.AudioExtensions

@Composable
fun SettingsScreen(navController: NavController, vm: SettingsViewModel) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val exts by vm.audioExtensions.collectAsStateWithLifecycle()

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(20.dp))

            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            ThemeOption(stringResource(R.string.theme_system), themeMode == ThemeMode.System) {
                vm.setThemeMode(ThemeMode.System)
            }
            ThemeOption(stringResource(R.string.theme_light), themeMode == ThemeMode.Light) {
                vm.setThemeMode(ThemeMode.Light)
            }
            ThemeOption(stringResource(R.string.theme_dark), themeMode == ThemeMode.Dark) {
                vm.setThemeMode(ThemeMode.Dark)
            }

            Spacer(Modifier.height(24.dp))
            Text("Audio formats", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            AudioExtensions.DEFAULT.sorted().forEach { ext ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(".$ext", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = ext in exts, onCheckedChange = { vm.toggleExtension(ext, it) })
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.height(0.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp))
    }
}
```

Note: "Audio formats" header is left literal for now (a small, low-traffic Settings header); add a string key later if desired.

- [ ] **Step 2: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/ui/settings/SettingsScreen.kt
git commit -m "feat(settings): theme picker + audio-extension toggles screen

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Apply theme in `MainActivity` + thread VM through nav

**Files:** Modify `MainActivity.kt`, `nav/AppNavGraph.kt`

- [ ] **Step 1: Replace `MainActivity.kt`**

```kotlin
package com.cliplist.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliplist.app.nav.AppNavGraph
import com.cliplist.app.settings.SettingsViewModel
import com.cliplist.app.settings.ThemeMode
import com.cliplist.app.theme.ClipListTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            ClipListTheme(darkTheme = darkTheme) {
                AppNavGraph(
                    modifier = Modifier.fillMaxSize(),
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}
```

- [ ] **Step 2: Update `AppNavGraph.kt`** — accept the SettingsViewModel and pass it to `SettingsScreen`. Add the import `com.cliplist.app.settings.SettingsViewModel`, change the signature, and the Settings composable:

Change the function signature:
```kotlin
@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    settingsViewModel: SettingsViewModel
) {
```
Change the Settings destination line:
```kotlin
        composable(Screen.Settings.route) { SettingsScreen(navController, settingsViewModel) }
```

- [ ] **Step 3: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/MainActivity.kt \
        app/src/main/kotlin/com/cliplist/app/nav/AppNavGraph.kt
git commit -m "feat(settings): apply persisted theme override app-wide; thread SettingsViewModel

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Settings entry from Home + scan uses chosen extensions

**Files:** Modify `ui/home/HomeScreen.kt`, `workflow/ScanViewModel.kt`

- [ ] **Step 1: Add a Settings entry in `HomeScreen.kt`.** Wrap the title + tagline in a header `Row` with a trailing `TextButton`. Replace the two title lines:

```kotlin
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
```

with:

```kotlin
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.Top
            ) {
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.TextButton(
                    onClick = { navController.navigate(com.cliplist.app.nav.Screen.Settings.route) }
                ) { Text(stringResource(R.string.settings)) }
            }
```

(`Column` is already imported in HomeScreen.)

- [ ] **Step 2: `ScanViewModel.kt` — use the persisted extensions in `scan()`.** Add imports `com.cliplist.app.settings.SettingsRepository` and `kotlinx.coroutines.flow.first`. Inside `scan()`'s `withContext(Dispatchers.IO) { … }`, replace:

```kotlin
                        audioExtensions = AudioExtensions.DEFAULT
```
with:
```kotlin
                        audioExtensions = SettingsRepository(getApplication()).audioExtensions.first()
```

(The `ScanOptions` is still built the same way otherwise; `AudioExtensions` import may now be unused — remove it if the compiler warns.)

- [ ] **Step 3: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/ui/home/HomeScreen.kt \
        app/src/main/kotlin/com/cliplist/app/workflow/ScanViewModel.kt
git commit -m "feat(settings): reach Settings from Home; scan honors chosen audio extensions

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: CI

- [ ] **Step 1:** `cd /home/projects/mpc && ./gradlew :core-format:test :core-scan:test --no-daemon 2>&1 | tail -4` → 94 tests, BUILD SUCCESSFUL (unchanged).

- [ ] **Step 2: Push and watch**

```bash
cd /home/projects/mpc
git push origin main
sleep 8
RUN_ID=$(gh run list --workflow=build.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run watch "$RUN_ID" --exit-status --interval 25 2>&1 | tail -3
```
Expected: both jobs green; `:app:assembleDebug` compiles DataStore + Settings.

**If `android-build` fails:** `gh run view "$RUN_ID" --log-failed 2>&1 | head -80`. Likely:
- `preferencesDataStore` / `edit` / `stringPreferencesKey` unresolved → confirm `libs.datastore.preferences` added (Task 1) and synced.
- `AppNavGraph` call-site mismatch → MainActivity passes `settingsViewModel = …` (Task 4); the default-arg ordering means `settingsViewModel` is a required trailing param — fine since MainActivity names it.
- `selectable` unresolved → import `androidx.compose.foundation.selection.selectable`.
- `stateIn`/`SharingStarted` unresolved → imports from `kotlinx.coroutines.flow.*`.

---

## Definition of Done (Phase 3c-2a)

- [ ] Both CI jobs green; APK assembles with the Settings screen.
- [ ] Theme override (System/Light/Dark) persists across launches and applies app-wide via `ClipListTheme`.
- [ ] Audio-extension toggles persist; `scan()` uses the chosen set.
- [ ] Settings reachable from Home; one shared Activity-scoped `SettingsViewModel`.
- [ ] DataStore is a single process-instance (top-level `settingsDataStore`).

---

## Self-review (completed by plan author)

**Spec coverage (3c-2a):** §3a/§12 theme override (follow system + light/dark, selectable) → Tasks 1–4. Audio extensions editable in Settings (§3) → Tasks 1–3, 5. Language switching → 3c-2b (separate, AppCompat).

**Placeholder scan:** complete code per file; "Audio formats" header literal is explicitly flagged, not a hidden gap.

**Type consistency:** `ThemeMode` (Settings) used in `SettingsRepository`, `SettingsViewModel`, `MainActivity`, `SettingsScreen`. `SettingsViewModel` created in `MainActivity`, passed via `AppNavGraph(settingsViewModel=…)` to `SettingsScreen` — single Activity-scoped instance. `AudioExtensions.DEFAULT: Set<String>` matches. `ScanOptions(recursive, alphabetize, audioExtensions)` unchanged shape; only the `audioExtensions` source changed. `Screen.Settings.route` exists.

**Runtime caveat (CI compiles but cannot run):** DataStore reads/writes + theme recomposition are standard patterns but unexercised without a device; the failure-triage list covers the compile risks.
