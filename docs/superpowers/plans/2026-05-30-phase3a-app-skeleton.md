# ClipList Phase 3a — :app Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a launchable Android app (`:app` module) with Material 3 Expressive theme, edge-to-edge layout, predictive back, and a navigation skeleton showing 5 placeholder screens — verifiable by CI assembling the APK.

**Architecture:** Single `MainActivity` calls `enableEdgeToEdge()` then hosts a `ClipListTheme`-wrapped `AppNavGraph`. Navigation uses Compose Navigation with a `Screen` sealed class for 5 routes. Each screen is a placeholder composable. Theme uses Material 3 dynamic colour on API 31+ and a brand fallback on older devices. No ViewModels or scan logic yet — those arrive in Phase 3b.

**Tech Stack:** Kotlin 2.3.20 · AGP 9.2.0 · Compose BOM 2026.05.01 · Material3 1.11.x · Navigation Compose 2.9.8 · Activity Compose 1.13.0 · Lifecycle ViewModel Compose 2.10.0 · Java 21 · minSdk 21 · targetSdk 36.

**Version provenance (verified 2026-05-30 against Google Maven `group-index.xml`):** `activity-compose` latest stable = 1.13.0 (1.9.0 in the original draft was stale); `navigation-compose` latest stable = 2.9.8 (2.10.0 still alpha); `lifecycle-viewmodel-compose` latest stable = 2.10.0 (2.11.0 still beta); `compose-bom` latest = 2026.05.01.

**Note:** `kotlin-android` plugin is intentionally absent — AGP 9.0+ has built-in Kotlin support; applying it alongside `com.android.application` is a hard error (same as `:data-storage`, confirmed in Phase 2).

**The local VPS has no Android SDK.** Tasks 1–5 write code locally; Task 6 validates compilation in CI.

---

## File map

```
gradle/libs.versions.toml               ← add compose-bom, activity, navigation, lifecycle versions + android-application plugin
settings.gradle.kts                     ← add :app
build.gradle.kts                        ← add android-application apply false

app/
  build.gradle.kts                      ← android application, Compose enabled, all deps
  src/main/
    AndroidManifest.xml                 ← application, launcher activity
    res/values/themes.xml               ← base Android theme (NoActionBar, transparent bars)
    kotlin/com/cliplist/app/
      ClipListApp.kt                    ← Application subclass (placeholder for future DI)
      MainActivity.kt                   ← enableEdgeToEdge(), setContent{ ClipListTheme{ AppNavGraph() } }
      theme/
        Color.kt                        ← brand fallback palette (LightColorScheme, DarkColorScheme)
        Type.kt                         ← ClipListTypography (Material3 defaults)
        ClipListTheme.kt                ← @Composable theme wrapper (dynamic color + fallback)
      nav/
        Screen.kt                       ← sealed class Screen with 5 route objects
        AppNavGraph.kt                  ← NavHost binding all 5 routes
      ui/home/HomeScreen.kt             ← placeholder: centred "Home" text
      ui/preview/PreviewScreen.kt       ← placeholder
      ui/progress/ProgressScreen.kt     ← placeholder
      ui/results/ResultsScreen.kt       ← placeholder
      ui/settings/SettingsScreen.kt     ← placeholder

.github/workflows/build.yml             ← add :app:assembleDebug to android-build job
```

---

### Task 1: Update Gradle config for `:app`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`

- [ ] **Step 1: Replace `gradle/libs.versions.toml` with the full content below**

```toml
[versions]
kotlin       = "2.3.20"
agp          = "9.2.0"
junit5       = "5.14.4"
compose-bom  = "2026.05.01"
activity     = "1.13.0"
navigation   = "2.9.8"
lifecycle    = "2.10.0"

[libraries]
junit-jupiter               = { module = "org.junit.jupiter:junit-jupiter",                           version.ref = "junit5" }
compose-bom                 = { module = "androidx.compose:compose-bom",                              version.ref = "compose-bom" }
compose-ui                  = { module = "androidx.compose.ui:ui" }
compose-ui-tooling          = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview  = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3           = { module = "androidx.compose.material3:material3" }
activity-compose            = { module = "androidx.activity:activity-compose",                        version.ref = "activity" }
navigation-compose          = { module = "androidx.navigation:navigation-compose",                    version.ref = "navigation" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose",            version.ref = "lifecycle" }

[plugins]
kotlin-jvm          = { id = "org.jetbrains.kotlin.jvm",  version.ref = "kotlin" }
android-library     = { id = "com.android.library",       version.ref = "agp" }
android-application = { id = "com.android.application",  version.ref = "agp" }
# kotlin-android intentionally absent — AGP 9.0+ has built-in Kotlin support.
```

- [ ] **Step 2: Add `:app` to `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "cliplist"
include(":core-format")
include(":core-scan")
include(":data-storage")
include(":app")
```

- [ ] **Step 3: Add `android-application` to root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)          apply false
    alias(libs.plugins.android.library)     apply false
    alias(libs.plugins.android.application) apply false
    // kotlin-android intentionally absent: AGP 9.0+ has built-in Kotlin support.
}
```

- [ ] **Step 4: Verify existing tests still pass**

```bash
cd /home/projects/mpc
./gradlew :core-format:test :core-scan:test --no-daemon 2>&1 | tail -4
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
cd /home/projects/mpc
git add gradle/libs.versions.toml settings.gradle.kts build.gradle.kts
git commit -m "build: add android-application plugin + Compose/navigation versions; declare :app module"
git push origin main
```

---

### Task 2: Scaffold `:app` module (build script + manifest + Application class)

**Files:**
- Create/Replace: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/kotlin/com/cliplist/app/ClipListApp.kt`

- [ ] **Step 1: Create `app/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // kotlin-android intentionally absent: AGP 9.0+ has built-in Kotlin support.
}

android {
    namespace = "com.cliplist.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cliplist.app"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(project(":core-scan"))
    debugImplementation(libs.compose.ui.tooling)
}
```

- [ ] **Step 2: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".ClipListApp"
        android:allowBackup="true"
        android:label="ClipList"
        android:theme="@style/Theme.ClipList"
        android:supportsRtl="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

- [ ] **Step 3: Create `app/src/main/res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!--
        Minimal base theme for a Compose app.
        All visual theming is handled by ClipListTheme in Compose.
        NoActionBar prevents a duplicate title bar; transparent bars let
        enableEdgeToEdge() control the window chrome.
    -->
    <style name="Theme.ClipList" parent="android:Theme.Material.Light.NoTitleBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

- [ ] **Step 4: Create `app/src/main/kotlin/com/cliplist/app/ClipListApp.kt`**

```kotlin
package com.cliplist.app

import android.app.Application

class ClipListApp : Application()
```

- [ ] **Step 5: Verify `:app:tasks` runs cleanly (lists tasks without compiling)**

```bash
cd /home/projects/mpc
./gradlew :app:tasks --no-daemon 2>&1 | grep -E "BUILD|SDK location|error" | head -5
```

Expected: `BUILD SUCCESSFUL` (or an SDK-not-found message — both are fine locally; compile check is in CI).

- [ ] **Step 6: Commit**

```bash
cd /home/projects/mpc
git add app/
git commit -m "feat: scaffold :app module — build script, manifest, base theme, Application class"
git push origin main
```

---

### Task 3: Material 3 Expressive theme

**Files:**
- Create: `app/src/main/kotlin/com/cliplist/app/theme/Color.kt`
- Create: `app/src/main/kotlin/com/cliplist/app/theme/Type.kt`
- Create: `app/src/main/kotlin/com/cliplist/app/theme/ClipListTheme.kt`

- [ ] **Step 1: Create `Color.kt`**

```kotlin
package com.cliplist.app.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette — used on API < 31 (no dynamic color).
// Inspired by the SanDisk Clip Sport's orange-and-blue palette.
private val BrandBlue   = Color(0xFF0053A4)
private val BrandOrange = Color(0xFFF5800F)

val LightColorScheme = lightColorScheme(
    primary   = BrandBlue,
    secondary = BrandOrange,
)

val DarkColorScheme = darkColorScheme(
    primary   = Color(0xFF9ECAFF),
    secondary = Color(0xFFFFB86C),
)
```

- [ ] **Step 2: Create `Type.kt`**

```kotlin
package com.cliplist.app.theme

import androidx.compose.material3.Typography

// Use Material 3 defaults; customise per-screen as needed in later phases.
val ClipListTypography = Typography()
```

- [ ] **Step 3: Create `ClipListTheme.kt`**

```kotlin
package com.cliplist.app.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun ClipListTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,       // Material 3 Expressive dynamic colour (API 31+)
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = ClipListTypography,
        content     = content
    )
}
```

- [ ] **Step 4: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/theme/
git commit -m "feat: add Material 3 theme with dynamic color (API 31+) and brand fallback"
git push origin main
```

---

### Task 4: MainActivity — edge-to-edge + predictive back + Compose entry point

**Files:**
- Create: `app/src/main/kotlin/com/cliplist/app/MainActivity.kt`

- [ ] **Step 1: Create `MainActivity.kt`**

```kotlin
package com.cliplist.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.cliplist.app.nav.AppNavGraph
import com.cliplist.app.theme.ClipListTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // enableEdgeToEdge() must be called before super.onCreate() on API < 29;
        // calling it here (after super) is safe on API 29+ and is the Activity Compose convention.
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClipListTheme {
                // AppNavGraph fills the window; each screen owns its own Scaffold + inset padding.
                // Compose Navigation 2.9+ handles predictive back automatically via NavBackStack.
                AppNavGraph(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/MainActivity.kt
git commit -m "feat: add MainActivity with edge-to-edge and Compose Navigation entry point"
git push origin main
```

---

### Task 5: Navigation + 5 placeholder screens

**Files:**
- Create: `app/src/main/kotlin/com/cliplist/app/nav/Screen.kt`
- Create: `app/src/main/kotlin/com/cliplist/app/nav/AppNavGraph.kt`
- Create: `app/src/main/kotlin/com/cliplist/app/ui/home/HomeScreen.kt`
- Create: `app/src/main/kotlin/com/cliplist/app/ui/preview/PreviewScreen.kt`
- Create: `app/src/main/kotlin/com/cliplist/app/ui/progress/ProgressScreen.kt`
- Create: `app/src/main/kotlin/com/cliplist/app/ui/results/ResultsScreen.kt`
- Create: `app/src/main/kotlin/com/cliplist/app/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Create `Screen.kt`**

```kotlin
package com.cliplist.app.nav

sealed class Screen(val route: String) {
    object Home     : Screen("home")
    object Preview  : Screen("preview")
    object Progress : Screen("progress")
    object Results  : Screen("results")
    object Settings : Screen("settings")
}
```

- [ ] **Step 2: Create `AppNavGraph.kt`**

```kotlin
package com.cliplist.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cliplist.app.ui.home.HomeScreen
import com.cliplist.app.ui.preview.PreviewScreen
import com.cliplist.app.ui.progress.ProgressScreen
import com.cliplist.app.ui.results.ResultsScreen
import com.cliplist.app.ui.settings.SettingsScreen

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController  = navController,
        startDestination = Screen.Home.route,
        modifier       = modifier
    ) {
        composable(Screen.Home.route)     { HomeScreen(navController) }
        composable(Screen.Preview.route)  { PreviewScreen(navController) }
        composable(Screen.Progress.route) { ProgressScreen(navController) }
        composable(Screen.Results.route)  { ResultsScreen(navController) }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
    }
}
```

- [ ] **Step 3: Create the 5 placeholder screen composables**

Create `app/src/main/kotlin/com/cliplist/app/ui/home/HomeScreen.kt`:

```kotlin
package com.cliplist.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.cliplist.app.nav.Screen

@Composable
fun HomeScreen(navController: NavController) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Home — Phase 3b", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
```

Create `app/src/main/kotlin/com/cliplist/app/ui/preview/PreviewScreen.kt`:

```kotlin
package com.cliplist.app.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

@Composable
fun PreviewScreen(navController: NavController) {
    Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Preview — Phase 3b", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
```

Create `app/src/main/kotlin/com/cliplist/app/ui/progress/ProgressScreen.kt`:

```kotlin
package com.cliplist.app.ui.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

@Composable
fun ProgressScreen(navController: NavController) {
    Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Progress — Phase 3b", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
```

Create `app/src/main/kotlin/com/cliplist/app/ui/results/ResultsScreen.kt`:

```kotlin
package com.cliplist.app.ui.results

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

@Composable
fun ResultsScreen(navController: NavController) {
    Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Results — Phase 3b", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
```

Create `app/src/main/kotlin/com/cliplist/app/ui/settings/SettingsScreen.kt`:

```kotlin
package com.cliplist.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

@Composable
fun SettingsScreen(navController: NavController) {
    Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Settings — Phase 3c", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
```

- [ ] **Step 4: Confirm `:core-scan` tests still pass locally**

```bash
cd /home/projects/mpc
./gradlew :core-format:test :core-scan:test --no-daemon 2>&1 | tail -4
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
cd /home/projects/mpc
git add app/src/main/kotlin/com/cliplist/app/nav/ \
        app/src/main/kotlin/com/cliplist/app/ui/
git commit -m "feat: add navigation graph + 5 placeholder screens (Home/Preview/Progress/Results/Settings)"
git push origin main
```

---

### Task 6: Update CI `build.yml` — add `:app:assembleDebug`

**Files:**
- Modify: `.github/workflows/build.yml`

- [ ] **Step 1: Update the `android-build` job's assemble step**

In `.github/workflows/build.yml`, replace the single `Assemble :data-storage` step with a combined assemble:

Find the step:
```yaml
      - name: Assemble :data-storage
        run: ./gradlew :data-storage:assemble --no-daemon
```

Replace it with:
```yaml
      - name: Assemble Android modules
        run: ./gradlew :data-storage:assemble :app:assembleDebug --no-daemon
```

The full updated `android-build` job section should look like:

```yaml
  android-build:
    name: "Android modules build"
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      # Android SDK 36 is pre-installed on ubuntu-latest runners.
      # No sdkmanager install step needed.

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Assemble Android modules
        run: ./gradlew :data-storage:assemble :app:assembleDebug --no-daemon
```

- [ ] **Step 2: Validate YAML**

```bash
cd /home/projects/mpc
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/build.yml')); print('YAML OK')"
```

Expected: `YAML OK`.

- [ ] **Step 3: Commit and push**

```bash
cd /home/projects/mpc
git add .github/workflows/build.yml
git commit -m "ci: add :app:assembleDebug to android-build job"
git push origin main
```

- [ ] **Step 4: Watch both CI jobs**

```bash
cd /home/projects/mpc
sleep 10
RUN_ID=$(gh run list --workflow=build.yml --limit 1 --json databaseId -q '.[0].databaseId')
echo "Watching run $RUN_ID"
gh run watch "$RUN_ID" --exit-status --interval 15 2>&1
```

Expected: both `jvm-tests` (67 tests passing) and `Android modules build` jobs show ✓.

**If `android-build` fails:** Read the log with `gh run view "$RUN_ID" --log-failed 2>&1 | head -100`. Common causes:
- Compose BOM `2026.05.01` not resolvable → fall back to `2026.05.00` or `2026.04.01` (update `libs.versions.toml`, recommit)
- Kotlin compilation error in a screen file → fix the specific error reported
- Navigation library version issue → check for a compatible version combination

---

## Definition of Done (Phase 3a)

- [ ] `./gradlew :core-format:test :core-scan:test` still passes: 67 tests, 0 failures.
- [ ] Both CI jobs green: `jvm-tests` (tests) + `Android modules build` (`:data-storage:assemble :app:assembleDebug`).
- [ ] `app/build/outputs/apk/debug/app-debug.apk` produced in CI.
- [ ] `ClipListTheme` uses Material3 dynamic color on API 31+, brand palette on API < 31.
- [ ] `MainActivity` calls `enableEdgeToEdge()` and hosts the nav graph.
- [ ] All 5 screens reachable via `Screen.*.route`.
- [ ] No `kotlin-android` plugin applied anywhere — AGP 9 built-in Kotlin throughout.

---

## Self-review (completed by plan author)

**Spec coverage (Phase 3a scope only):**
- §6 `:app` module: ✓ Tasks 1–2.
- §13 Theming (Material 3, dynamic color, light/dark): ✓ Task 3.
- §14 Edge-to-edge + predictive back: ✓ Task 4 (`enableEdgeToEdge()` + Navigation Compose 2.9+ handles predictive back automatically).
- §16 UI screens (5 routes): ✓ Task 5.
- §17 CI: ✓ Task 6.
- Localization (§12): out of scope — Phase 3c.
- Home screen logic, SAF picker, ViewModels (§9, §11, §16): out of scope — Phase 3b.
- Settings screen logic (§16): out of scope — Phase 3c.
- App Functions / discoverability (§15): out of scope — Phase 3d.
- Signing / release pipeline (§17): out of scope — Phase 3d.

**Placeholder scan:** No TBDs. Every step has exact code. The Compose BOM version fallback (`2026.04.01`) is provided if `2026.05.00` isn't available yet.

**Type consistency:**
- `Screen.Home.route`, `Screen.Preview.route`, etc. defined in `Screen.kt` → used in `AppNavGraph.kt`. ✓
- `AppNavGraph(modifier, navController)` defined in Task 5 Step 2 → called in `MainActivity.kt` Task 4 Step 1. ✓
- `ClipListTheme` defined in Task 3 Step 3 → used in `MainActivity.kt`. ✓
- `LightColorScheme`, `DarkColorScheme` defined in `Color.kt` → used in `ClipListTheme.kt`. ✓
- `ClipListTypography` defined in `Type.kt` → used in `ClipListTheme.kt`. ✓

**Note for AGP 9 executor:** If the build fails with `"The Kotlin Gradle plugin was found on the compile classpath"` or similar, it means something is still applying `kotlin-android`. Verify `app/build.gradle.kts` does NOT list it, and `build.gradle.kts` root does NOT apply it. The `android-application` plugin alone is correct.
