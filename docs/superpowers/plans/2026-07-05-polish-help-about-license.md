# Polish & Open-Sourcing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Help / ⓘ-info / About surfaces in-app, then GPLv3-license the repo, scrub the original APK from history, and turn the GitHub page into a public showcase crediting the original developer.

**Architecture:** Phase A follows the app's existing patterns exactly: new `Screen` routes + composables mirroring `PrivacyScreen`, drawer rows mirroring the Privacy row, strings only via the `localization/strings.json` → `generate_strings.py` pipeline. Phase B is repo surgery: `git filter-repo` to drop the proprietary APK from history, GPLv3 `LICENSE`, showcase `README.md`, `gh repo edit` metadata, then the visibility flip.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Navigation-Compose, GitHub Actions CI, git-filter-repo, gh CLI.

## Global Constraints

- **No hardcoded user-facing strings.** Every new string goes into `localization/strings.json` under ALL 10 languages (`en es fr de pt-BR it ru ja ko zh-CN`), then run `python3 localization/generate_strings.py`. English text below is verbatim; translate the other 9 yourself, matching the tone of neighboring entries in each language block (impersonal German, existing punctuation style).
- **No local Android builds** (no SDK). Compile verification = `git push origin main` → the push-triggered GitHub Actions run (`gh run list --workflow=build.yml`) must be green. Push after every task.
- **Local JVM tests are flaky** (sandbox OOM). Core modules aren't touched here; rely on CI.
- Repo: `https://github.com/Joeputin100/cliplist` (owner `Joeputin100`). User-facing app name: **My Playlist Creator 2026**.
- Original-app credit, exact facts: *My Playlist Creator* (store title "My Music Playlist Creator", package `com.matt.mym3ucreator`) by **Matt Duss**, v2.1.1, delisted from Google Play.
- License: **GPLv3**.
- Commit messages end with the standard `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` + `Claude-Session: https://claude.ai/code/session_01WFAxiLoJggHoZdmznvxDjC` trailers used by this repo.

---

### Task 1: Localized strings for Help, About, and info dialogs

**Files:**
- Modify: `localization/strings.json` (all 10 language objects)
- Generated: `app/src/main/res/values*/strings.xml` (via script — do not hand-edit)

**Interfaces:**
- Produces string keys used by Tasks 2–6: `help_title`, `help_show_wizard`, `help_q_make`, `help_a_make`, `help_q_notshow`, `help_a_notshow`, `help_q_clean`, `help_a_clean`, `help_q_hidden`, `help_a_hidden`, `help_q_where`, `help_a_where`, `help_q_eject`, `help_a_eject`, `help_q_formats`, `help_a_formats`, `about_title`, `about_version_fmt`, `about_story`, `about_github`, `about_report`, `about_license`, `about_license_sub`, `info_subfolders`, `info_alphabetize`, `info_cover_art`, `info_dialog_ok`.

- [ ] **Step 1: Add the English strings** to the `"en"` object of `localization/strings.json` (keep keys alphabetical within the file's existing style; the generator sorts anyway):

```json
"about_github": "View on GitHub",
"about_license": "Free software — GNU GPL v3",
"about_license_sub": "You may use, share and improve this app; improvements must stay free.",
"about_report": "Report a problem",
"about_story": "A modern, open-source revival of My Playlist Creator by Matt Duss — the little app Clip Sport owners relied on for a decade until it was abandoned and disappeared from the Play Store. This app writes the same byte-exact playlist format, rebuilt from scratch for today's Android. Not affiliated with or endorsed by the original author or SanDisk.",
"about_title": "About",
"about_version_fmt": "Version %1$s",
"help_a_clean": "The Clip Sport can choke on symbols and accented letters in file names. This option renames files and folders on the card to plain letters and numbers (\"Rock & Roll!.mp3\" becomes \"Rock and Roll.mp3\"). The preview lists every rename before anything is changed.",
"help_a_eject": "After generating, tap \"Eject SD card\" on the results page. It opens the card's own settings — tap Unmount there, then remove the card.",
"help_a_formats": "The formats selected under Settings → Audio formats are included in playlists. The Clip Sport itself plays MP3, WMA, AAC, WAV and FLAC.",
"help_a_hidden": "Files whose names start with a dot are invisible to the player. This option removes the dot so the player can see them.",
"help_a_make": "1. Tap the folder card and choose your music folder or SD card.\n2. Tap Scan folder and check the preview.\n3. Tap Generate playlists.\nOne .m3u playlist is written into each folder that contains music.",
"help_a_notshow": "Eject the card safely and reinsert it into the player. The Clip Sport rebuilds its music database when it starts — give it a moment. Each playlist is named after its folder.",
"help_a_where": "Inside each music folder, as a .m3u file named after the folder. Nothing leaves the card — the app has no internet access.",
"help_q_clean": "What does \"Clean file names\" do?",
"help_q_eject": "How do I eject safely?",
"help_q_formats": "Which audio formats are supported?",
"help_q_hidden": "What does \"Rename hidden files\" do?",
"help_q_make": "How do I make playlists?",
"help_q_notshow": "Why don't my playlists show on the Clip Sport?",
"help_q_where": "Where are the playlists saved?",
"help_show_wizard": "Show welcome guide again",
"help_title": "Help",
"info_alphabetize": "Sorts the tracks in each playlist alphabetically. Turn this off to keep the card's own file order.",
"info_cover_art": "Saves a small album-art image (folder.jpg) into each music folder that doesn't already have one, so the player can show artwork.",
"info_dialog_ok": "OK",
"info_subfolders": "Also scans folders inside the folder you picked. Each subfolder that contains music gets its own playlist.",
```

- [ ] **Step 2: Translate all 27 keys into the other 9 language objects** (`es`, `fr`, `de`, `pt-BR`, `it`, `ru`, `ja`, `ko`, `zh-CN`) in the same file. Keep product terms consistent with existing entries (e.g. ja uses SDカード, de says "Wiedergabelisten"). `%1$s` placeholders must survive verbatim.

- [ ] **Step 3: Regenerate resources and verify**

Run: `python3 localization/generate_strings.py`
Expected: `wrote values (101 strings)` … one line per locale, all with the SAME count (101 = 74 existing + 27 new).

Run: `python3 -c "import json; d=json.load(open('localization/strings.json')); ks=[set(v) for v in d.values()]; assert all(k==ks[0] for k in ks), 'key mismatch'; print('keys consistent')"`
Expected: `keys consistent`

- [ ] **Step 4: Commit**

```bash
git add localization/strings.json app/src/main/res
git commit -m "feat(i18n): strings for Help, About and option info dialogs (10 languages)"
git push origin main
```

---

### Task 2: `InfoDot` component

**Files:**
- Create: `app/src/main/kotlin/com/cliplist/app/ui/components/InfoDot.kt`

**Interfaces:**
- Produces: `@Composable fun InfoDot(titleRes: Int, bodyRes: Int)` — a small ⓘ IconButton; tapping opens an AlertDialog with the given title/body string resources and an OK button. Used by Tasks 3–4.

- [ ] **Step 1: Create the component**

```kotlin
package com.cliplist.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.cliplist.app.R

/** A small ⓘ button that explains one option in plain language. */
@Composable
fun InfoDot(titleRes: Int, bodyRes: Int) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = stringResource(titleRes),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(bodyRes)) },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.info_dialog_ok)) }
            },
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/cliplist/app/ui/components/InfoDot.kt
git commit -m "feat(ui): InfoDot — tappable plain-language explanation for an option"
git push origin main
```

---

### Task 3: Info dots on the Home toggles

**Files:**
- Modify: `app/src/main/kotlin/com/cliplist/app/ui/home/HomeScreen.kt` (the `ToggleRow` composable at the bottom of the file, and its two call sites)

**Interfaces:**
- Consumes: `InfoDot(titleRes, bodyRes)` from Task 2; strings `info_subfolders`, `info_alphabetize` from Task 1.

- [ ] **Step 1: Extend `ToggleRow` and both call sites.** Replace the existing `ToggleRow` composable with:

```kotlin
@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    infoTitleRes: Int? = null,
    infoBodyRes: Int? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.8f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
            if (infoTitleRes != null && infoBodyRes != null) InfoDot(infoTitleRes, infoBodyRes)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
```

and change the two call sites (currently around line 173) to:

```kotlin
ToggleRow(stringResource(R.string.opt_subfolders), options.searchSubfolders, vm::setSearchSubfolders,
    R.string.opt_subfolders, R.string.info_subfolders)
ToggleRow(stringResource(R.string.opt_alphabetize), options.alphabetize, vm::setAlphabetize,
    R.string.opt_alphabetize, R.string.info_alphabetize)
```

Add import: `import com.cliplist.app.ui.components.InfoDot`

- [ ] **Step 2: Commit + push; confirm the push CI run goes green** (`gh run list --workflow=build.yml --limit 1`).

```bash
git add app/src/main/kotlin/com/cliplist/app/ui/home/HomeScreen.kt
git commit -m "feat(home): info dots on the two scan toggles"
git push origin main
```

---

### Task 4: Settings drawer — info dots + Help/About navigation rows

**Files:**
- Modify: `app/src/main/kotlin/com/cliplist/app/ui/settings/SettingsDrawer.kt`
- Modify: `app/src/main/kotlin/com/cliplist/app/ui/home/HomeScreen.kt:104-116` (drawer wiring)

**Interfaces:**
- Consumes: `InfoDot` (Task 2); strings `help_a_clean`, `help_a_hidden`, `info_cover_art`, `help_a_formats`, `help_title`, `about_title` (Task 1).
- Produces: `SettingsDrawer(vm, onPrivacy, onHelp, onAbout)` — two new required callbacks. Task 5/6 routes are navigated from here.

- [ ] **Step 1: Extend `SwitchRow` with optional info** (same pattern as Task 3) and thread the new callbacks:

Signature: `fun SettingsDrawer(vm: SettingsViewModel, onPrivacy: () -> Unit, onHelp: () -> Unit, onAbout: () -> Unit)`.

`SwitchRow` gains `infoTitleRes: Int? = null, infoBodyRes: Int? = null`; inside its title `Column`, wrap the title `Text` in a `Row(verticalAlignment = Alignment.CenterVertically)` that appends `if (infoTitleRes != null && infoBodyRes != null) InfoDot(infoTitleRes, infoBodyRes)`.

Call sites:
- Clean file names → `R.string.opt_clean_names, R.string.help_a_clean`
- Rename hidden → `R.string.opt_rename_hidden, R.string.help_a_hidden`
- Cover art → `R.string.opt_cover_art, R.string.info_cover_art`
- On the Audio-formats `Row`, append `InfoDot(R.string.audio_formats, R.string.help_a_formats)` after the count `Text`.

- [ ] **Step 2: Replace the ABOUT section's single Privacy row with three rows** (Help · About · Privacy), each the same clickable-Row pattern as the existing Privacy row:

```kotlin
// ABOUT
SectionLabel(stringResource(R.string.settings_about))
Spacer(Modifier.height(4.dp))
DrawerLink(stringResource(R.string.help_title), onHelp)
DrawerLink(stringResource(R.string.about_title), onAbout)
DrawerLink(stringResource(R.string.privacy_policy), onPrivacy)
```

with the shared row extracted as:

```kotlin
@Composable
private fun DrawerLink(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}
```

- [ ] **Step 3: Wire the callbacks in `HomeScreen`'s `ModalDrawerSheet`** (mirror the existing `onPrivacy` lambda — close drawer, then navigate):

```kotlin
SettingsDrawer(
    vm = settingsVm,
    onPrivacy = {
        scope.launch { drawerState.close() }
        navController.navigate(Screen.Privacy.route)
    },
    onHelp = {
        scope.launch { drawerState.close() }
        navController.navigate(Screen.Help.route)
    },
    onAbout = {
        scope.launch { drawerState.close() }
        navController.navigate(Screen.About.route)
    }
)
```

(`Screen.Help`/`Screen.About` are added in Tasks 5–6; commit this task TOGETHER with Task 5's route additions if pushing between them would break compile — order the commits so every push compiles: do Task 5 Step 1 (routes) first if needed.)

- [ ] **Step 4: Commit + push (after Task 5 Step 1 exists so routes resolve); confirm CI green.**

```bash
git add app/src/main/kotlin/com/cliplist/app/ui/settings/SettingsDrawer.kt app/src/main/kotlin/com/cliplist/app/ui/home/HomeScreen.kt
git commit -m "feat(settings): option info dots; Help and About entries in the drawer"
git push origin main
```

---

### Task 5: Help screen

**Files:**
- Modify: `app/src/main/kotlin/com/cliplist/app/nav/Screen.kt` (add `object Help : Screen("help")` and `object About : Screen("about")` — both now so Task 4/6 compile)
- Create: `app/src/main/kotlin/com/cliplist/app/ui/help/HelpScreen.kt`
- Modify: `app/src/main/kotlin/com/cliplist/app/nav/AppNavGraph.kt` (add `composable(Screen.Help.route) { HelpScreen(navController, settingsViewModel) }`)

**Interfaces:**
- Consumes: `WizardDialog(onDismiss, onDontShowAgain)` (existing, package `com.cliplist.app.ui.home`); `SettingsViewModel.setHideWizard(Boolean)`; Task 1 strings.
- Produces: `HelpScreen(navController: NavController, settingsVm: SettingsViewModel)`; routes `Screen.Help`, `Screen.About`.

- [ ] **Step 1: Add both routes to `Screen.kt`** (exact lines):

```kotlin
object Help    : Screen("help")
object About   : Screen("about")
```

- [ ] **Step 2: Create `HelpScreen.kt`**

```kotlin
package com.cliplist.app.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cliplist.app.R
import com.cliplist.app.settings.SettingsViewModel
import com.cliplist.app.ui.home.WizardDialog

private val FAQ: List<Pair<Int, Int>> = listOf(
    R.string.help_q_make to R.string.help_a_make,
    R.string.help_q_notshow to R.string.help_a_notshow,
    R.string.help_q_clean to R.string.help_a_clean,
    R.string.help_q_hidden to R.string.help_a_hidden,
    R.string.help_q_where to R.string.help_a_where,
    R.string.help_q_eject to R.string.help_a_eject,
    R.string.help_q_formats to R.string.help_a_formats,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(navController: NavController, settingsVm: SettingsViewModel) {
    var showWizard by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.help_title))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            OutlinedButton(onClick = { showWizard = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.help_show_wizard))
            }
            Spacer(Modifier.height(16.dp))
            FAQ.forEach { (q, a) ->
                FaqCard(q, a)
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    if (showWizard) {
        WizardDialog(
            onDismiss = { showWizard = false },
            onDontShowAgain = {
                settingsVm.setHideWizard(true)
                showWizard = false
            }
        )
    }
}

@Composable
private fun FaqCard(questionRes: Int, answerRes: Int) {
    var expanded by remember { mutableStateOf(false) }
    Card(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(questionRes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f)
                )
            }
            AnimatedVisibility(expanded) {
                Text(
                    stringResource(answerRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
```

Note: `WizardDialog` and `SettingsViewModel.setHideWizard` already exist — check `app/src/main/kotlin/com/cliplist/app/settings/SettingsViewModel.kt` for the exact method name (`setHideWizard` is used by `HomeScreen.kt:201`).

- [ ] **Step 3: Register the route in `AppNavGraph.kt`**

```kotlin
composable(Screen.Help.route)     { HelpScreen(navController, settingsViewModel) }
```
with import `com.cliplist.app.ui.help.HelpScreen`.

- [ ] **Step 4: Commit + push; confirm CI green.**

```bash
git add app/src/main/kotlin/com/cliplist/app/nav app/src/main/kotlin/com/cliplist/app/ui/help
git commit -m "feat(help): FAQ screen with expandable answers + welcome-guide replay"
git push origin main
```

---

### Task 6: About screen

**Files:**
- Create: `app/src/main/kotlin/com/cliplist/app/ui/about/AboutScreen.kt`
- Modify: `app/src/main/kotlin/com/cliplist/app/nav/AppNavGraph.kt` (add route)

**Interfaces:**
- Consumes: `AppLogo` (existing, `com.cliplist.app.ui.components`); route `Screen.About` (Task 5); Task 1 strings.
- Produces: `AboutScreen(navController: NavController)`.

- [ ] **Step 1: Create `AboutScreen.kt`**

```kotlin
package com.cliplist.app.ui.about

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cliplist.app.R
import com.cliplist.app.nav.Screen
import com.cliplist.app.ui.components.AppLogo

private const val REPO_URL = "https://github.com/Joeputin100/cliplist"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val LICENSE_URL = "$REPO_URL/blob/main/LICENSE"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.about_title))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppLogo(modifier = Modifier.size(72.dp).clip(CircleShape))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (version.isNotEmpty()) {
                Text(
                    stringResource(R.string.about_version_fmt, version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.about_story), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { open(REPO_URL) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_github))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { open(ISSUES_URL) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_report))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { navController.navigate(Screen.Privacy.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.privacy_policy))
            }
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { open(LICENSE_URL) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.about_license), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.about_license_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Register the route in `AppNavGraph.kt`**

```kotlin
composable(Screen.About.route)    { AboutScreen(navController) }
```
with import `com.cliplist.app.ui.about.AboutScreen`.

- [ ] **Step 3: Commit + push; confirm CI green.**

```bash
git add app/src/main/kotlin/com/cliplist/app/ui/about app/src/main/kotlin/com/cliplist/app/nav/AppNavGraph.kt
git commit -m "feat(about): About screen — origin story, credit to Matt Duss, GitHub links, GPLv3 notice"
git push origin main
```

---

### Task 7: Phase A gate — full CI dispatch + on-device check

- [ ] **Step 1:** `gh workflow run build.yml --ref main`, then watch the run (`gh run list --workflow=build.yml --limit 1`, then `gh run view <id> --json jobs`). All five jobs must succeed (JVM tests, Android build, FTL flow test, FTL R8 smoke, App Distribution).
- [ ] **Step 2:** Ask the user to open the new App Tester build and spot-check: drawer shows Help/About/Privacy; ⓘ dots explain options; Help FAQ expands; wizard replays; About shows version, story, working links. **Checkpoint — wait for user confirmation before Phase B.**

---

### Task 8: Remove the APK from the working tree; retire the decompile workflow

**Files:**
- Delete: `reference/mym3ucreator-2.1.1.apk`, `.github/workflows/decompile.yml`
- Modify: `reference/README.md`

- [ ] **Step 1:** `git rm reference/mym3ucreator-2.1.1.apk .github/workflows/decompile.yml`

- [ ] **Step 2: Replace `reference/README.md` contents with:**

```markdown
# Reference format material

- **`FORMAT.md`** — the frozen, byte-exact `.m3u` format the serializer must produce
  (CRLF line endings, ordering, header rules). Derived by studying the output of the
  original *My Playlist Creator* (`com.matt.mym3ucreator`) by Matt Duss, for
  interoperability with the SanDisk Clip Sport.
- **`format-fixtures/`** — golden input/output pairs replayed by `:core-format` tests.

The original app itself is not distributed here.
```

- [ ] **Step 3: Commit (do NOT push yet — history rewrite comes next):**

```bash
git add -A
git commit -m "chore: drop the reference APK and its decompile workflow ahead of open-sourcing"
```

---

### Task 9: Scrub the APK from git history and force-push

- [ ] **Step 1: Install git-filter-repo:** `pip3 install --user git-filter-repo` (or `pipx install git-filter-repo`). Verify: `git filter-repo --version`.

- [ ] **Step 2: Fresh-clone safety check** — run the rewrite on a clean state: `git status` must be clean, and note the origin URL (`git remote get-url origin`; filter-repo deletes the remote).

- [ ] **Step 3: Rewrite history:**

```bash
git filter-repo --invert-paths --path reference/mym3ucreator-2.1.1.apk --force
```

- [ ] **Step 4: Verify no proprietary blobs remain in ANY commit:**

```bash
git rev-list --objects --all | grep -iE "\.(apk|dex)$" ; echo "exit=$? (1 = clean)"
```
Expected: no output, `exit=1`.

- [ ] **Step 5: Restore remote and force-push all refs:**

```bash
git remote add origin https://github.com/Joeputin100/cliplist.git
git push --force origin main
git push --force origin --tags
```

- [ ] **Step 6:** Confirm the push-triggered CI run is green, and `gh api repos/Joeputin100/cliplist/contents/reference --jq '.[].name'` no longer lists the APK.

---

### Task 10: GPLv3 LICENSE

- [ ] **Step 1:** `curl -fsSL https://www.gnu.org/licenses/gpl-3.0.txt -o LICENSE` — verify with `head -3 LICENSE` → `GNU GENERAL PUBLIC LICENSE / Version 3, 29 June 2007`.
- [ ] **Step 2: Commit + push:**

```bash
git add LICENSE
git commit -m "chore: license the project under GPLv3"
git push origin main
```

---

### Task 11: Screenshots + showcase README + repo metadata

**Design brief (user directive — do not water down):** the GitHub page must look like it was
built by a famous design studio for an award competition judged by Banksy. Concretely:
- **Custom hero banner** committed under `docs/readme-assets/`: dark and light variants served
  via `<picture>` + `prefers-color-scheme`. Aesthetic: stencil / spray-paint street-art energy
  meets crisp product design — bold condensed display type, the teal→lime brand gradient,
  a stenciled Clip Sport / SD-card motif, tagline with attitude (e.g. "ABANDONWARE DIES.
  THE MUSIC DOESN'T."). Two viable production paths, in order of preference:
  1. Vertex AI image generation (see memory: google-genai + ADC, model `gemini-3-pro-image`)
     for painterly spray texture, post-processed to exact size (1280×400).
  2. Hand-authored SVG using feTurbulence/displacement filters for spray grit — convert
     display text to paths (GitHub's image proxy has no webfonts).
- **The whole README is art-directed**: strong vertical rhythm, centered hero block,
  screenshot row presented cleanly (consistent width, alt text), section glyphs used
  sparingly, one accent color discipline (brand teal/lime), no default-README smell.
  Badges styled consistently (shields.io `for-the-badge` or flat-square, one style only).
- Keep every claim factual (byte-identical format, GPLv3, 10 languages, no internet
  permission) — swagger in tone, precision in facts. Credit to Matt Duss stays prominent
  and respectful.

**Files:**
- Create: `docs/screenshots/home.png`, `docs/screenshots/preview.png`, `docs/screenshots/results.png` (names by content)
- Create: `docs/readme-assets/hero-dark.png` (or `.svg`), `docs/readme-assets/hero-light.png` (or `.svg`)
- Rewrite: `README.md` (the markdown skeleton below is the CONTENT baseline; elevate its
  presentation per the design brief — the brief wins wherever they conflict)

- [ ] **Step 1: Screenshots.** Ask the user for 3–4 phone screenshots (Home, Preview or Progress, Results — light or dark, their choice). (FTL emulator shots are a fallback but look sterile; the user's Samsung shots are better.) Save under `docs/screenshots/`. **Checkpoint — needs user input.**

- [ ] **Step 2: Replace `README.md` with the showcase** (adjust the screenshots row to the files actually provided):

```markdown
# My Playlist Creator 2026

**Byte-perfect `.m3u` playlists for the SanDisk Clip Sport — one tap, one playlist per folder.**

[![Build](https://github.com/Joeputin100/cliplist/actions/workflows/build.yml/badge.svg)](https://github.com/Joeputin100/cliplist/actions/workflows/build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)

| Home | Preview | Results |
|---|---|---|
| ![Home](docs/screenshots/home.png) | ![Preview](docs/screenshots/preview.png) | ![Results](docs/screenshots/results.png) |

## Why this exists

For a decade, Clip Sport owners depended on *My Playlist Creator* by **Matt Duss** to
generate the `.m3u` playlists the player needs. The app was abandoned and eventually
vanished from the Play Store — leaving the format working but the tool gone.

**My Playlist Creator 2026** (codename *ClipList*) is a from-scratch, open-source revival:
the same byte-exact playlist format (verified against golden fixtures on every build),
wrapped in a modern Material 3 app. It is not affiliated with or endorsed by the original
author or SanDisk — it exists so this little corner of the music world keeps working,
and, being GPLv3, it can never be abandoned the way its predecessor was.

## Features

- 📁 **One playlist per folder** — scans your SD card and writes `<Folder>.m3u` beside the music
- 🔤 **Clean file names** — optional plain-ASCII renaming the Clip Sport can digest (`Rock & Roll!.mp3` → `Rock and Roll.mp3`), with full preview and nothing ever deleted
- 👀 **Preview before writing** — every playlist, rename and warning shown up front
- 🕒 **Real durations** — reads each track's metadata, skips unreadable files
- 🖼️ **Cover art** — optional `folder.jpg` for players that show artwork
- ⏏️ **Safe eject** — jumps straight to the card's Unmount page
- 🌍 **10 languages** — English, Español, Français, Deutsch, Português (BR), Italiano, Русский, 日本語, 한국어, 简体中文
- 🔒 **No internet permission** — your music never leaves the card

## How it works

The `.m3u` byte format (CRLF endings, ordering, naming) is frozen in
[`reference/FORMAT.md`](reference/FORMAT.md) and enforced by golden-fixture tests in
`:core-format` on every CI run — output is byte-identical to the original app's.

## Get it

Grab the latest APK from [Releases](https://github.com/Joeputin100/cliplist/releases)
(Play Store release planned). Builds are produced entirely by
[GitHub Actions](.github/workflows/build.yml), tested on Firebase Test Lab devices.

## License

[GPLv3](LICENSE) — free software: use it, learn from it, improve it; improvements stay free.
```

Remove the old README's "private repository" paragraph entirely (this rewrite already does).

- [ ] **Step 3: Repo metadata:**

```bash
gh repo edit Joeputin100/cliplist \
  --description "Byte-perfect .m3u playlist creator for the SanDisk Clip Sport — open-source revival of Matt Duss's abandoned My Playlist Creator" \
  --add-topic android --add-topic kotlin --add-topic jetpack-compose \
  --add-topic m3u --add-topic playlist --add-topic sandisk --add-topic clip-sport --add-topic music
```

- [ ] **Step 4: Commit + push:**

```bash
git add README.md docs/screenshots
git commit -m "docs: showcase README with screenshots, origin story and GPLv3 badge"
git push origin main
```

---

### Task 12: User review → flip public → verify

- [ ] **Step 1:** Ask the user to review the README on GitHub (send the repo URL). **Checkpoint — wait for approval.**
- [ ] **Step 2:** Flip visibility:

```bash
gh repo edit Joeputin100/cliplist --visibility public --accept-visibility-change-consequences
```

- [ ] **Step 3: Verify public state:** `gh repo view Joeputin100/cliplist --json visibility` → `PUBLIC`; open the repo logged-out (WebFetch `https://github.com/Joeputin100/cliplist`) — README renders, badges resolve, LICENSE detected as GPL-3.0; About-screen URLs now work for anyone.
- [ ] **Step 4:** Update memory (`release-deferred-items` / project notes): repo is public, GPLv3.

## Self-Review Notes

- Spec coverage: A1→Task 5, A2→Tasks 2–4, A3→Task 6, B1→Tasks 8–9, B2→Task 10, B3→Task 11, B4→Task 12; localization→Task 1; CI/FTL gate→Task 7. Screenshot fallback decision resolved in Task 11 Step 1 (user shots preferred).
- Version display uses `PackageManager` (`versionName = "1.0.0"` in `app/build.gradle.kts:20`) — avoids enabling the BuildConfig feature.
- Every push compiles: Task 4 explicitly depends on Task 5 Step 1's route objects; execute Task 5 Step 1 before pushing Task 4 if run strictly in order (or land Tasks 4–5 in one push).
```
