# ClipList Phase 2 — :core-scan + :data-storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the folder-scanning engine (`:core-scan`, pure JVM) and the Android SAF storage backend (`:data-storage`, Android library), wired together through a `StorageVolume` interface that keeps the scan logic 100% testable without a device.

**Architecture:** `StorageVolume` is a pure Kotlin interface (no Android imports) that lives in `:core-scan`. `PlaylistPlanner` walks a volume to produce a `ScanPlan`; `PlaylistWriter` executes that plan using `M3uSerializer`. Tests use `FakeVolume` (in-memory). `SafTreeVolume` in `:data-storage` implements the same interface over Android's SAF/DocumentsContract API. The local VPS has no Android SDK — Tasks 1–4 are verified locally; Tasks 5–6 are verified in CI only.

**Tech Stack:** Kotlin 2.3.20 · Gradle 9.5.1 · AGP 9.2.0 · JUnit Jupiter 5.14.4 · Android API 36 (compileSdk) / API 21 (minSdk) · `DocumentsContract` bulk-query for SAF performance.

**Dependency graph:**
```
:core-format  (pure JVM — M3uSerializer, FilenameSanitizer)
:core-scan    (pure JVM — StorageVolume interface, PlaylistPlanner, PlaylistWriter)
  └─ depends on :core-format
:data-storage (Android library — SafTreeVolume)
  └─ depends on :core-scan
```

---

## File map

```
gradle/libs.versions.toml               ← add agp version + android-library + kotlin-android plugins
settings.gradle.kts                     ← add :core-scan and :data-storage includes
build.gradle.kts                        ← add android-library + kotlin-android apply false

core-scan/
  build.gradle.kts                      ← kotlin("jvm"), depends on :core-format, JUnit 5
  src/main/kotlin/com/cliplist/scan/
    StorageVolume.kt                    ← VolumeNode interface, StorageVolume interface, VolumeWriteResult
    AudioExtensions.kt                  ← DEFAULT extension set + isAudio() helper
    ScanOptions.kt                      ← ScanOptions data class (recursive, alphabetize, extensions)
    PlaylistPlanner.kt                  ← FolderPlan, PlanWarning, ScanPlan data types + PlaylistPlanner
    PlaylistWriter.kt                   ← WriteReport + PlaylistWriter (executes ScanPlan via volume)
  src/test/kotlin/com/cliplist/scan/
    FakeVolume.kt                       ← FakeVolume, FakeNode, fakeDir(), fakeFile() test helpers
    PlaylistPlannerTest.kt              ← TDD: 12 tests covering all scan scenarios
    PlaylistWriterTest.kt               ← TDD: 5 tests verifying byte-exact writes via FakeVolume

data-storage/
  build.gradle.kts                      ← android library, compileSdk 36, depends on :core-scan
  src/main/kotlin/com/cliplist/storage/
    SafTreeVolume.kt                    ← SAF implementation of StorageVolume

.github/workflows/build.yml             ← split into two jobs: JVM tests + Android assemble
```

---

### Task 1: Update Gradle config for Android + new modules

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add AGP + kotlin-android to `gradle/libs.versions.toml`**

```toml
[versions]
kotlin = "2.3.20"
agp = "9.2.0"
junit5 = "5.14.4"

[libraries]
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit5" }

[plugins]
kotlin-jvm     = { id = "org.jetbrains.kotlin.jvm",     version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
android-library = { id = "com.android.library",         version.ref = "agp" }
```

- [ ] **Step 2: Add `:core-scan` and `:data-storage` to `settings.gradle.kts`**

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
```

- [ ] **Step 3: Add new plugin aliases to root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)      apply false
    alias(libs.plugins.kotlin.android)  apply false
    alias(libs.plugins.android.library) apply false
}
```

- [ ] **Step 4: Verify existing tests still pass after config change**

```bash
cd /home/projects/mpc
./gradlew :core-format:test --no-daemon 2>&1 | tail -4
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
cd /home/projects/mpc
git add gradle/libs.versions.toml settings.gradle.kts build.gradle.kts
git commit -m "build: add AGP 9.2.0 + kotlin-android plugin; declare :core-scan :data-storage modules"
git push origin main
```

---

### Task 2: Scaffold `:core-scan` module + define `StorageVolume` interface

**Files:**
- Create: `core-scan/build.gradle.kts`
- Create: `core-scan/src/main/kotlin/com/cliplist/scan/StorageVolume.kt`
- Create: `core-scan/src/main/kotlin/com/cliplist/scan/AudioExtensions.kt`
- Create: `core-scan/src/main/kotlin/com/cliplist/scan/ScanOptions.kt`
- Create: `core-scan/src/test/kotlin/com/cliplist/scan/FakeVolume.kt`

- [ ] **Step 1: Create `core-scan/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.cliplist"
version = "1.0.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core-format"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Create `StorageVolume.kt`**

Create `core-scan/src/main/kotlin/com/cliplist/scan/StorageVolume.kt`:

```kotlin
package com.cliplist.scan

interface VolumeNode {
    val name: String
    val isDirectory: Boolean
}

sealed class VolumeWriteResult {
    object Success : VolumeWriteResult()
    data class Failure(val message: String) : VolumeWriteResult()
}

interface StorageVolume {
    val rootNode: VolumeNode
    fun children(node: VolumeNode): List<VolumeNode>
    fun findFile(directory: VolumeNode, fileName: String): VolumeNode?
    fun writeFile(directory: VolumeNode, name: String, content: ByteArray): VolumeWriteResult
    fun deleteFile(directory: VolumeNode, fileName: String): Boolean
}
```

- [ ] **Step 3: Create `AudioExtensions.kt`**

Create `core-scan/src/main/kotlin/com/cliplist/scan/AudioExtensions.kt`:

```kotlin
package com.cliplist.scan

object AudioExtensions {
    val DEFAULT: Set<String> = setOf(
        "mp3", "ogg", "oga", "wav", "m4a", "aac", "alac", "flac",
        "wma", "ac3", "opus", "aa", "aax"
    )

    fun isAudio(filename: String, extensions: Set<String> = DEFAULT): Boolean {
        val dot = filename.lastIndexOf('.')
        if (dot < 0) return false
        return filename.substring(dot + 1).lowercase() in extensions
    }
}
```

- [ ] **Step 4: Create `ScanOptions.kt`**

Create `core-scan/src/main/kotlin/com/cliplist/scan/ScanOptions.kt`:

```kotlin
package com.cliplist.scan

data class ScanOptions(
    val recursive: Boolean,
    val alphabetize: Boolean,
    val audioExtensions: Set<String> = AudioExtensions.DEFAULT
)
```

- [ ] **Step 5: Create `FakeVolume.kt` (test helper)**

Create `core-scan/src/test/kotlin/com/cliplist/scan/FakeVolume.kt`:

```kotlin
package com.cliplist.scan

class FakeVolume(root: FakeNode) : StorageVolume {
    override val rootNode: VolumeNode = root
    val writtenFiles = mutableMapOf<String, ByteArray>()   // "folderName/fileName" -> bytes
    val deletedFiles = mutableListOf<String>()             // "folderName/fileName"

    override fun children(node: VolumeNode): List<VolumeNode> =
        (node as FakeNode).children.toList()

    override fun findFile(directory: VolumeNode, fileName: String): VolumeNode? =
        (directory as FakeNode).children.find { it.name.equals(fileName, ignoreCase = true) }

    override fun writeFile(directory: VolumeNode, name: String, content: ByteArray): VolumeWriteResult {
        writtenFiles["${directory.name}/$name"] = content
        return VolumeWriteResult.Success
    }

    override fun deleteFile(directory: VolumeNode, fileName: String): Boolean {
        deletedFiles.add("${directory.name}/$fileName")
        return true
    }
}

data class FakeNode(
    override val name: String,
    override val isDirectory: Boolean,
    val children: MutableList<FakeNode> = mutableListOf()
) : VolumeNode

fun fakeDir(name: String, vararg children: FakeNode): FakeNode =
    FakeNode(name, isDirectory = true, children = children.toMutableList())

fun fakeFile(name: String): FakeNode =
    FakeNode(name, isDirectory = false)
```

- [ ] **Step 6: Verify `:core-scan` builds with empty test sources**

```bash
cd /home/projects/mpc
./gradlew :core-scan:build --no-daemon 2>&1 | tail -4
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
cd /home/projects/mpc
git add core-scan/
git commit -m "feat: scaffold :core-scan with StorageVolume interface, AudioExtensions, ScanOptions, FakeVolume"
git push origin main
```

---

### Task 3: PlaylistPlanner — TDD

**Files:**
- Create: `core-scan/src/main/kotlin/com/cliplist/scan/PlaylistPlanner.kt`
- Create: `core-scan/src/test/kotlin/com/cliplist/scan/PlaylistPlannerTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `core-scan/src/test/kotlin/com/cliplist/scan/PlaylistPlannerTest.kt`:

```kotlin
package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlaylistPlannerTest {
    private val planner = PlaylistPlanner()
    private val opts = ScanOptions(recursive = false, alphabetize = false)

    @Test fun `folder with audio files produces a plan`() {
        val volume = FakeVolume(fakeDir("Music",
            fakeFile("01 - Alpha.mp3"), fakeFile("02 - Beta.mp3")
        ))
        val plan = planner.plan(volume, opts)
        assertEquals(1, plan.folders.size)
        assertEquals("Music", plan.folders[0].folder.name)
        assertEquals(listOf("01 - Alpha.mp3", "02 - Beta.mp3"), plan.folders[0].audioFiles)
        assertEquals("Music.m3u", plan.folders[0].playlistName)
    }

    @Test fun `folder with no audio files - empty plan`() {
        val volume = FakeVolume(fakeDir("Music", fakeFile("cover.jpg"), fakeFile("notes.txt")))
        assertTrue(planner.plan(volume, opts).folders.isEmpty())
    }

    @Test fun `non-recursive - only root folder scanned`() {
        val volume = FakeVolume(fakeDir("Music",
            fakeDir("Rock", fakeFile("song.mp3")),
            fakeFile("top.mp3")
        ))
        val plan = planner.plan(volume, opts)
        assertEquals(1, plan.folders.size)
        assertEquals("Music", plan.folders[0].folder.name)
        assertEquals(listOf("top.mp3"), plan.folders[0].audioFiles)
    }

    @Test fun `recursive - all subfolders with audio included`() {
        val volume = FakeVolume(fakeDir("Music",
            fakeDir("Rock", fakeFile("a.mp3")),
            fakeDir("Jazz", fakeFile("b.flac")),
            fakeFile("top.mp3")
        ))
        val plan = planner.plan(volume, ScanOptions(recursive = true, alphabetize = false))
        val names = plan.folders.map { it.folder.name }.toSet()
        assertEquals(setOf("Music", "Rock", "Jazz"), names)
    }

    @Test fun `alphabetize true - files sorted in plan`() {
        val volume = FakeVolume(fakeDir("Rock",
            fakeFile("03 - Gamma.mp3"), fakeFile("01 - Alpha.mp3"), fakeFile("02 - Beta.mp3")
        ))
        val plan = planner.plan(volume, ScanOptions(recursive = false, alphabetize = true))
        assertEquals(
            listOf("01 - Alpha.mp3", "02 - Beta.mp3", "03 - Gamma.mp3"),
            plan.folders[0].audioFiles
        )
    }

    @Test fun `alphabetize false - preserves listing order`() {
        val volume = FakeVolume(fakeDir("Rock",
            fakeFile("03 - Gamma.mp3"), fakeFile("01 - Alpha.mp3")
        ))
        val plan = planner.plan(volume, opts)
        assertEquals(listOf("03 - Gamma.mp3", "01 - Alpha.mp3"), plan.folders[0].audioFiles)
    }

    @Test fun `existing playlist detected by folder name match`() {
        val volume = FakeVolume(fakeDir("Rock", fakeFile("song.mp3"), fakeFile("Rock.m3u")))
        assertEquals("Rock.m3u", planner.plan(volume, opts).folders[0].existingPlaylistName)
    }

    @Test fun `existing playlist detection is case-insensitive`() {
        val volume = FakeVolume(fakeDir("Rock", fakeFile("song.mp3"), fakeFile("rock.M3U")))
        assertEquals("rock.M3U", planner.plan(volume, opts).folders[0].existingPlaylistName)
    }

    @Test fun `no existing playlist - existingPlaylistName is null`() {
        val volume = FakeVolume(fakeDir("Rock", fakeFile("song.mp3")))
        assertNull(planner.plan(volume, opts).folders[0].existingPlaylistName)
    }

    @Test fun `all Clip Sport audio formats recognised`() {
        val volume = FakeVolume(fakeDir("Music",
            fakeFile("a.mp3"), fakeFile("b.ogg"), fakeFile("c.oga"),
            fakeFile("d.wav"), fakeFile("e.m4a"), fakeFile("f.aac"),
            fakeFile("g.alac"), fakeFile("h.flac"), fakeFile("i.wma"),
            fakeFile("j.ac3"), fakeFile("k.opus"), fakeFile("l.aa"),
            fakeFile("m.aax"), fakeFile("cover.jpg")
        ))
        val plan = planner.plan(volume, opts)
        assertEquals(13, plan.folders[0].audioFiles.size)
        assertFalse("cover.jpg" in plan.folders[0].audioFiles)
    }

    @Test fun `warning when folder exceeds 1000 tracks`() {
        val files = (1..1001).map { fakeFile("track$it.mp3") }.toTypedArray()
        val volume = FakeVolume(fakeDir("Huge", *files))
        val plan = planner.plan(volume, opts)
        val w = plan.warnings.filterIsInstance<PlanWarning.TooManyTracksInFolder>().single()
        assertEquals("Huge", w.folderName)
        assertEquals(1001, w.count)
    }

    @Test fun `warning when more than 50 playlists total`() {
        val subfolders = (1..51).map { fakeDir("F$it", fakeFile("t.mp3")) }.toTypedArray()
        val volume = FakeVolume(fakeDir("Root", *subfolders))
        val plan = planner.plan(volume, ScanOptions(recursive = true, alphabetize = false))
        val w = plan.warnings.filterIsInstance<PlanWarning.TooManyPlaylists>().single()
        assertEquals(51, w.count)
    }
}
```

- [ ] **Step 2: Run tests — expect FAILURE (PlaylistPlanner not yet defined)**

```bash
cd /home/projects/mpc
./gradlew :core-scan:compileTestKotlin --no-daemon 2>&1 | grep "Unresolved" | head -5
```

Expected: `Unresolved reference 'PlaylistPlanner'` and `Unresolved reference 'PlanWarning'`.

- [ ] **Step 3: Implement `PlaylistPlanner.kt`**

Create `core-scan/src/main/kotlin/com/cliplist/scan/PlaylistPlanner.kt`:

```kotlin
package com.cliplist.scan

data class FolderPlan(
    val folder: VolumeNode,
    val audioFiles: List<String>,
    val existingPlaylistName: String?,
    val playlistName: String
)

sealed class PlanWarning {
    data class TooManyTracksInFolder(val folderName: String, val count: Int) : PlanWarning()
    data class TooManyPlaylists(val count: Int) : PlanWarning()
}

data class ScanPlan(
    val folders: List<FolderPlan>,
    val warnings: List<PlanWarning>
)

class PlaylistPlanner {
    fun plan(volume: StorageVolume, options: ScanOptions): ScanPlan {
        val queue = ArrayDeque<VolumeNode>()
        queue.addLast(volume.rootNode)
        val folderPlans = mutableListOf<FolderPlan>()

        while (queue.isNotEmpty()) {
            val folder = queue.removeFirst()
            val children = volume.children(folder)

            val subfolders = children.filter { it.isDirectory }
            val audioFiles = children.filter {
                !it.isDirectory && AudioExtensions.isAudio(it.name, options.audioExtensions)
            }
            val existingPlaylist = children.find {
                !it.isDirectory && it.name.equals("${folder.name}.m3u", ignoreCase = true)
            }

            if (options.recursive) subfolders.forEach { queue.addLast(it) }

            if (audioFiles.isNotEmpty()) {
                val fileNames = audioFiles.map { it.name }
                    .let { if (options.alphabetize) it.sorted() else it }
                folderPlans.add(FolderPlan(
                    folder = folder,
                    audioFiles = fileNames,
                    existingPlaylistName = existingPlaylist?.name,
                    playlistName = "${folder.name}.m3u"
                ))
            }
        }

        val warnings = mutableListOf<PlanWarning>()
        folderPlans.forEach { fp ->
            if (fp.audioFiles.size > 1000)
                warnings.add(PlanWarning.TooManyTracksInFolder(fp.folder.name, fp.audioFiles.size))
        }
        if (folderPlans.size > 50) warnings.add(PlanWarning.TooManyPlaylists(folderPlans.size))

        return ScanPlan(folders = folderPlans, warnings = warnings)
    }
}
```

- [ ] **Step 4: Run tests — expect all 12 to pass**

```bash
cd /home/projects/mpc
./gradlew :core-scan:test --no-daemon 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`. If any test fails, read the JUnit XML:
```bash
find core-scan/build/test-results -name "*.xml" | xargs grep -l "failure" 2>/dev/null | xargs grep -A5 "failure" | head -30
```

- [ ] **Step 5: Commit**

```bash
cd /home/projects/mpc
git add core-scan/src/
git commit -m "feat: add PlaylistPlanner with 12 TDD tests (recursive scan, warnings, extension filter)"
git push origin main
```

---

### Task 4: PlaylistWriter — TDD

**Files:**
- Create: `core-scan/src/main/kotlin/com/cliplist/scan/PlaylistWriter.kt`
- Create: `core-scan/src/test/kotlin/com/cliplist/scan/PlaylistWriterTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `core-scan/src/test/kotlin/com/cliplist/scan/PlaylistWriterTest.kt`:

```kotlin
package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlaylistWriterTest {

    @Test fun `writer produces byte-exact M3U content`() {
        val root = fakeDir("Rock", fakeFile("01 - Alpha.mp3"), fakeFile("02 - Beta.mp3"))
        val volume = FakeVolume(root)
        val plan = ScanPlan(
            folders = listOf(FolderPlan(root, listOf("01 - Alpha.mp3", "02 - Beta.mp3"), null, "Rock.m3u")),
            warnings = emptyList()
        )

        val report = PlaylistWriter(volume).execute(plan)

        assertEquals(1, report.written)
        assertEquals(0, report.failed)
        val expected = "01 - Alpha.mp3\r\n02 - Beta.mp3\r\n".toByteArray(Charsets.UTF_8)
        assertArrayEquals(expected, volume.writtenFiles["Rock/Rock.m3u"])
    }

    @Test fun `existing playlist deleted before writing`() {
        val root = fakeDir("Rock", fakeFile("song.mp3"), fakeFile("Rock.m3u"))
        val volume = FakeVolume(root)
        val plan = ScanPlan(
            folders = listOf(FolderPlan(root, listOf("song.mp3"), "Rock.m3u", "Rock.m3u")),
            warnings = emptyList()
        )

        PlaylistWriter(volume).execute(plan)

        assertTrue("Rock/Rock.m3u" in volume.deletedFiles)
        assertNotNull(volume.writtenFiles["Rock/Rock.m3u"])
    }

    @Test fun `empty plan - nothing written`() {
        val volume = FakeVolume(fakeDir("Empty"))
        val report = PlaylistWriter(volume).execute(ScanPlan(emptyList(), emptyList()))
        assertEquals(0, report.written)
        assertEquals(0, report.failed)
        assertTrue(volume.writtenFiles.isEmpty())
    }

    @Test fun `multiple folders - each gets its own playlist`() {
        val root = fakeDir("Music",
            fakeDir("Rock", fakeFile("a.mp3")),
            fakeDir("Jazz", fakeFile("b.mp3"))
        )
        val volume = FakeVolume(root)
        val rock = root.children[0]
        val jazz = root.children[1]
        val plan = ScanPlan(
            folders = listOf(
                FolderPlan(rock, listOf("a.mp3"), null, "Rock.m3u"),
                FolderPlan(jazz, listOf("b.mp3"), null, "Jazz.m3u")
            ),
            warnings = emptyList()
        )

        val report = PlaylistWriter(volume).execute(plan)

        assertEquals(2, report.written)
        assertNotNull(volume.writtenFiles["Rock/Rock.m3u"])
        assertNotNull(volume.writtenFiles["Jazz/Jazz.m3u"])
    }

    @Test fun `files pre-sorted by planner - writer preserves order`() {
        val root = fakeDir("Rock", fakeFile("Zappa.mp3"), fakeFile("Alice.mp3"))
        val volume = FakeVolume(root)
        // Planner pre-sorts; writer must not re-sort
        val plan = ScanPlan(
            folders = listOf(FolderPlan(root, listOf("Alice.mp3", "Zappa.mp3"), null, "Rock.m3u")),
            warnings = emptyList()
        )

        PlaylistWriter(volume).execute(plan)

        val content = volume.writtenFiles["Rock/Rock.m3u"]!!.toString(Charsets.UTF_8)
        assertTrue(content.startsWith("Alice.mp3\r\n"), "Expected Alice first, got: $content")
        assertTrue(content.endsWith("Zappa.mp3\r\n"), "Expected Zappa last, got: $content")
    }
}
```

- [ ] **Step 2: Run — expect FAILURE (`PlaylistWriter` not yet defined)**

```bash
cd /home/projects/mpc
./gradlew :core-scan:compileTestKotlin --no-daemon 2>&1 | grep "Unresolved" | head -3
```

Expected: `Unresolved reference 'PlaylistWriter'`.

- [ ] **Step 3: Implement `PlaylistWriter.kt`**

Create `core-scan/src/main/kotlin/com/cliplist/scan/PlaylistWriter.kt`:

```kotlin
package com.cliplist.scan

import com.cliplist.format.M3uSerializer
import com.cliplist.format.SerializerOptions

data class WriteReport(val written: Int, val failed: Int, val errors: List<String>)

class PlaylistWriter(private val volume: StorageVolume) {
    fun execute(plan: ScanPlan): WriteReport {
        var written = 0
        var failed = 0
        val errors = mutableListOf<String>()

        for (fp in plan.folders) {
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
        }

        return WriteReport(written = written, failed = failed, errors = errors)
    }
}
```

- [ ] **Step 4: Run all `:core-scan` tests — expect 17 total, all pass**

```bash
cd /home/projects/mpc
./gradlew :core-scan:test --no-daemon 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`. Confirm counts:
```bash
grep 'tests=' core-scan/build/test-results/test/TEST-*.xml | grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*"'
```

Expected: two lines, `failures="0"` on each, totalling 17 tests.

- [ ] **Step 5: Commit**

```bash
cd /home/projects/mpc
git add core-scan/src/main/kotlin/com/cliplist/scan/PlaylistWriter.kt \
        core-scan/src/test/kotlin/com/cliplist/scan/PlaylistWriterTest.kt
git commit -m "feat: add PlaylistWriter — executes ScanPlan, writes byte-exact playlists via StorageVolume"
git push origin main
```

---

### Task 5: Scaffold `:data-storage` Android library + `SafTreeVolume`

**Note:** The local VPS has no Android SDK (`sdkmanager` missing). This task writes the code locally, then CI validates it compiles. Verify locally with `./gradlew :data-storage:tasks` (which only reads the build script, not the SDK). The real compilation check is in CI (Task 6).

**Files:**
- Create: `data-storage/build.gradle.kts`
- Create: `data-storage/src/main/kotlin/com/cliplist/storage/SafTreeVolume.kt`

- [ ] **Step 1: Create `data-storage/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cliplist.storage"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(project(":core-scan"))
}
```

- [ ] **Step 2: Create `SafTreeVolume.kt`**

Create `data-storage/src/main/kotlin/com/cliplist/storage/SafTreeVolume.kt`:

```kotlin
package com.cliplist.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.cliplist.scan.StorageVolume
import com.cliplist.scan.VolumeNode
import com.cliplist.scan.VolumeWriteResult
import java.io.IOException

/**
 * SAF-backed StorageVolume. Uses bulk DocumentsContract queries for performance
 * (avoids the slow DocumentFile.listFiles() which does one IPC per child).
 *
 * The treeUri must have been granted via takePersistableUriPermission before use.
 */
class SafTreeVolume(
    private val context: Context,
    private val treeUri: Uri
) : StorageVolume {

    private inner class SafNode(
        val documentId: String,
        override val name: String,
        override val isDirectory: Boolean
    ) : VolumeNode

    override val rootNode: VolumeNode by lazy {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val name = context.contentResolver.query(
            docUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else rootId } ?: rootId
        SafNode(rootId, name, isDirectory = true)
    }

    override fun children(node: VolumeNode): List<VolumeNode> {
        node as SafNode
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, node.documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val result = mutableListOf<VolumeNode>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id   = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2) ?: ""
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                result.add(SafNode(id, name, isDir))
            }
        }
        return result
    }

    override fun findFile(directory: VolumeNode, fileName: String): VolumeNode? =
        children(directory).find { it.name.equals(fileName, ignoreCase = true) }

    override fun writeFile(directory: VolumeNode, name: String, content: ByteArray): VolumeWriteResult {
        directory as SafNode
        val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, directory.documentId)
        return try {
            val newUri = DocumentsContract.createDocument(
                context.contentResolver, dirUri, "audio/x-mpegurl", name
            ) ?: return VolumeWriteResult.Failure("createDocument returned null for $name in ${directory.name}")
            context.contentResolver.openOutputStream(newUri, "wt")?.use { it.write(content) }
                ?: return VolumeWriteResult.Failure("openOutputStream returned null for $name")
            VolumeWriteResult.Success
        } catch (e: IOException) {
            VolumeWriteResult.Failure(e.message ?: "IOException writing $name")
        }
    }

    override fun deleteFile(directory: VolumeNode, fileName: String): Boolean {
        val node = findFile(directory, fileName) as? SafNode ?: return false
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, node.documentId)
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: Exception) {
            Log.e("SafTreeVolume", "delete $fileName: ${e.message}")
            false
        }
    }
}
```

- [ ] **Step 3: Verify the build script is readable (does not require Android SDK)**

```bash
cd /home/projects/mpc
./gradlew :data-storage:tasks --no-daemon 2>&1 | grep -E "BUILD|error" | head -5
```

Expected: `BUILD SUCCESSFUL` (just listing tasks — does not compile Kotlin yet). If you see `SDK location not found`, the local VPS has no Android SDK, which is expected; CI will compile it.

- [ ] **Step 4: Confirm `:core-scan` tests still pass with the new module added**

```bash
cd /home/projects/mpc
./gradlew :core-scan:test --no-daemon 2>&1 | tail -4
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
cd /home/projects/mpc
git add data-storage/
git commit -m "feat: add :data-storage Android library with SafTreeVolume (SAF + bulk DocumentsContract query)"
git push origin main
```

---

### Task 6: Update `build.yml` CI — add Android SDK 36 + `:data-storage:assemble`

**Files:**
- Modify: `.github/workflows/build.yml`

- [ ] **Step 1: Rewrite `build.yml` with two jobs**

```yaml
name: Build and test

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

env:
  FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true

permissions:
  contents: read

jobs:
  jvm-tests:
    name: "JVM module tests"
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Run :core-format and :core-scan tests
        run: ./gradlew :core-format:test :core-scan:test --no-daemon

      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: jvm-test-reports
          path: |
            core-format/build/reports/tests/
            core-scan/build/reports/tests/
          retention-days: 7

  android-build:
    name: ":data-storage Android assemble"
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - name: Install Android SDK 36
        run: |
          sdkmanager --install "platforms;android-36" "build-tools;36.0.0" 2>&1 | tail -5

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Assemble :data-storage
        run: ./gradlew :data-storage:assemble --no-daemon
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
git commit -m "ci: split build into jvm-tests + android-build jobs; install SDK 36 for :data-storage"
git push origin main
```

- [ ] **Step 4: Watch both CI jobs**

```bash
cd /home/projects/mpc
sleep 10
RUN_ID=$(gh run list --workflow=build.yml --limit 1 --json databaseId -q '.[0].databaseId')
echo "Watching run $RUN_ID"
gh run watch "$RUN_ID" --exit-status --interval 15
```

Expected: both `jvm-tests` and `android-build` jobs show ✓. Total run time ~3-4 minutes.

If `android-build` fails on `platforms;android-36` not found, try:
```bash
sdkmanager --list | grep "platforms;android-3"
```
in a CI debugging step to see available SDK versions, then adjust the platform version accordingly.

---

## Definition of Done (Phase 2)

- [ ] `./gradlew :core-format:test :core-scan:test` passes locally: 47 + 17 = 64 tests, 0 failures.
- [ ] Both CI jobs (`jvm-tests` and `android-build`) are green on `main`.
- [ ] `SafTreeVolume` implements `StorageVolume` fully and compiles against SDK 36.
- [ ] `PlaylistPlanner` produces correct `ScanPlan` for recursive/non-recursive scans with warnings.
- [ ] `PlaylistWriter` writes byte-exact CRLF M3U content via `StorageVolume.writeFile()`.
- [ ] `decompiled/` is **not** tracked by git.

---

## Self-review (completed by plan author)

**Spec coverage:**
- §6 `:core-scan` module: ✓ Tasks 2–4.
- §6 `:data-storage` module: ✓ Task 5.
- §9 Scan & generate engine (recursive, audio filter, alphabetize, replace existing, device-limit warnings): ✓ Tasks 3–4.
- §11 SAF storage model (bulk DocumentsContract query, `StorageVolume` abstraction): ✓ Task 5.
- §18 TDD: ✓ Every file written after a failing test.
- §17 CI: ✓ Task 6.
- Filename cleaning (§10): intentionally out of scope — Phase 4.
- Compose UI (§16): out of scope — Phase 3.

**Placeholder scan:** No TBDs. Every step has exact code. The `sdkmanager` SDK-36 availability caveat in Task 6 includes a concrete fallback diagnostic command.

**Type consistency:**
- `VolumeNode`, `StorageVolume`, `VolumeWriteResult` defined in Task 2 → used in Tasks 3, 4, 5. ✓
- `FakeVolume`, `fakeDir()`, `fakeFile()`, `FakeNode` defined in Task 2 → used in Tasks 3, 4. ✓
- `FolderPlan`, `ScanPlan`, `PlanWarning` defined in Task 3 → used in Task 4. ✓
- `PlaylistWriter(volume).execute(plan): WriteReport` consistent across Task 4 test and implementation. ✓
- `SafTreeVolume(context, treeUri)` in Task 5 implements `StorageVolume` from Task 2. ✓
