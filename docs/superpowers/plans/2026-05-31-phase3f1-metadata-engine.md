# Phase 3f-1 — Metadata Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use `- [ ]` checkboxes.

**Goal:** The unit-testable `:core-scan` foundation for track metadata: a `readFile` capability, file size/mtime on nodes, a per-folder cache model + JSON codec, an incremental diff (what changed since last run), and a `FolderMetadataAnalyzer` that turns a folder + an injected `AudioProbe` into a `FolderAnalysis` (readable files, total duration, unreadable list) while maintaining `mpc-metadata.json`.

**Architecture:** Pure logic in `:core-scan`, fully tested with `FakeVolume` + `FakeAudioProbe`. The Android `MediaMetadataRetriever` probe and scan wiring are **Phase 3f-2**; the animated Results UI is **3f-3**. Adding `readFile` + node size/mtime to the `StorageVolume`/`VolumeNode` interfaces forces matching edits to `SafTreeVolume`/`SafNode` (compile-validated in CI).

**Tech Stack:** Kotlin 2.3.20 · kotlinx-serialization-json 1.11.0 · JUnit 5 · `:core-scan` (+ `:data-storage` interface impls). Spec: `docs/superpowers/specs/2026-05-31-metadata-validation-rich-results-design.md`.

---

### Task 1: `readFile` + node `size`/`lastModified` (interface + impls)

Adding to the interfaces forces all implementers to update, so the three files change together.

**Files:** Modify `core-scan/.../StorageVolume.kt`, `core-scan/src/test/.../FakeVolume.kt`, `data-storage/.../SafTreeVolume.kt`; Test `core-scan/src/test/.../StorageVolumeReadTest.kt`.

- [ ] **Step 1: failing test** — `StorageVolumeReadTest.kt`:

```kotlin
package com.cliplist.scan
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StorageVolumeReadTest {
    @Test fun `readFile returns written bytes, null when absent`() {
        val root = fakeDir("Rock")
        val volume = FakeVolume(root)
        assertNull(volume.readFile(root, "x.json"))
        volume.writeFile(root, "x.json", "hi".toByteArray(), "application/json")
        assertEquals("hi", volume.readFile(root, "x.json")!!.toString(Charsets.UTF_8))
    }
    @Test fun `nodes carry size and lastModified`() {
        val f = fakeFile("a.mp3", size = 123L, lastModified = 456L)
        assertEquals(123L, f.size); assertEquals(456L, f.lastModified)
        assertEquals(0L, fakeDir("D").size)  // dirs default 0
    }
}
```

- [ ] **Step 2:** run `./gradlew :core-scan:test --tests '*StorageVolumeReadTest*' --no-daemon 2>&1 | tail -12` → FAIL.

- [ ] **Step 3:** `StorageVolume.kt` — add `size`/`lastModified` to `VolumeNode` and `readFile` to the interface:

```kotlin
interface VolumeNode {
    val name: String
    val isDirectory: Boolean
    val size: Long
    val lastModified: Long
}
```
and inside `interface StorageVolume`, add after `writeFile(...)`:
```kotlin
    fun readFile(directory: VolumeNode, fileName: String): ByteArray?
```

- [ ] **Step 4:** `FakeVolume.kt` — extend `FakeNode`, `fakeFile`, and implement `readFile`:

```kotlin
data class FakeNode(
    override var name: String,
    override val isDirectory: Boolean,
    val children: MutableList<FakeNode> = mutableListOf(),
    override val size: Long = 0,
    override val lastModified: Long = 0,
) : VolumeNode
```
```kotlin
fun fakeFile(name: String, size: Long = 0, lastModified: Long = 0): FakeNode =
    FakeNode(name, isDirectory = false, size = size, lastModified = lastModified)
```
Add to the `FakeVolume` class (after `writeFile`):
```kotlin
    override fun readFile(directory: VolumeNode, fileName: String): ByteArray? =
        writtenFiles["${directory.name}/$fileName"]
```

- [ ] **Step 5:** `SafTreeVolume.kt` — add `size`/`lastModified` to `SafNode`, fetch them in `children`, and implement `readFile`.

In the private `SafNode`:
```kotlin
    private inner class SafNode(
        val documentId: String,
        override val name: String,
        override val isDirectory: Boolean,
        override val size: Long = 0,
        override val lastModified: Long = 0,
    ) : VolumeNode
```
In `children(...)`, extend the projection + construction:
```kotlin
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
```
```kotlin
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                val mtime = if (cursor.isNull(4)) 0L else cursor.getLong(4)
                result.add(SafNode(id, name, isDir, size, mtime))
```
(The `rootNode` `SafNode(rootId, name, isDirectory = true)` keeps default 0/0.)
Add the `readFile` method (after `writeFile`):
```kotlin
    override fun readFile(directory: VolumeNode, fileName: String): ByteArray? {
        directory as SafNode
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directory.documentId)
        var targetId: String? = null
        context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1)?.equals(fileName, ignoreCase = true) == true) { targetId = c.getString(0); break }
            }
        }
        val id = targetId ?: return null
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) { Log.e("SafTreeVolume", "read $fileName: ${e.message}"); null }
    }
```

- [ ] **Step 6:** run the test → PASS; full suite `:core-format:test :core-scan:test` → BUILD SUCCESSFUL (existing tests unaffected; node defaults keep `fakeFile(name)` working).

- [ ] **Step 7: commit** `feat(meta): StorageVolume.readFile + node size/lastModified`.

---

### Task 2: kotlinx-serialization + cache model + codec

**Files:** Modify `gradle/libs.versions.toml`, `core-scan/build.gradle.kts`; Create `core-scan/.../MetadataCache.kt`; Test `core-scan/src/test/.../MetadataCacheCodecTest.kt`.

- [ ] **Step 1: deps.** Catalog `[versions]`: add `kotlinx-serialization = "1.11.0"`. `[libraries]`: `kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }`. `[plugins]`: `kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }`.
  In `core-scan/build.gradle.kts`: add `alias(libs.plugins.kotlin.serialization)` to `plugins {}` and `implementation(libs.kotlinx.serialization.json)` to `dependencies {}`.

- [ ] **Step 2: failing test** — `MetadataCacheCodecTest.kt`:

```kotlin
package com.cliplist.scan
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MetadataCacheCodecTest {
    @Test fun `round-trips a cache`() {
        val c = FolderMetaCache(tracks = listOf(
            TrackMeta("a.mp3", 100, 200, 213000, true),
            TrackMeta("b.mp3", 50, 60, 0, false)))
        val back = MetadataCacheCodec.decode(MetadataCacheCodec.encode(c))
        assertEquals(c, back)
    }
    @Test fun `decode of null or garbage is null`() {
        assertNull(MetadataCacheCodec.decode(null))
        assertNull(MetadataCacheCodec.decode("not json".toByteArray()))
    }
}
```

- [ ] **Step 3:** run → FAIL. **Step 4:** create `MetadataCache.kt`:

```kotlin
package com.cliplist.scan

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TrackMeta(
    val name: String, val size: Long, val lastModified: Long,
    val durationMs: Long, val readable: Boolean,
)

@Serializable
data class FolderMetaCache(val schema: Int = 1, val tracks: List<TrackMeta> = emptyList())

object MetadataCacheCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(cache: FolderMetaCache): ByteArray =
        json.encodeToString(cache).toByteArray(Charsets.UTF_8)
    fun decode(bytes: ByteArray?): FolderMetaCache? = bytes?.let {
        runCatching { json.decodeFromString<FolderMetaCache>(it.toString(Charsets.UTF_8)) }.getOrNull()
    }
}
```

- [ ] **Step 5:** run → PASS. **Step 6: commit** `feat(meta): cache model + JSON codec (kotlinx-serialization)`.

---

### Task 3: `MetadataDiff`

**Files:** Create `core-scan/.../MetadataDiff.kt`; Test `core-scan/src/test/.../MetadataDiffTest.kt`.

- [ ] **Step 1: failing test**:

```kotlin
package com.cliplist.scan
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MetadataDiffTest {
    private fun cacheOf(vararg t: TrackMeta) = FolderMetaCache(tracks = t.toList())
    @Test fun `unchanged files are reused, changed and new are probed`() {
        val cache = cacheOf(
            TrackMeta("same.mp3", 10, 20, 5000, true),
            TrackMeta("changed.mp3", 10, 20, 7000, true))
        val inv = listOf(
            FileStat("same.mp3", 10, 20),       // identical -> reuse
            FileStat("changed.mp3", 10, 99),    // mtime changed -> probe
            FileStat("new.mp3", 1, 2))          // new -> probe
        val r = MetadataDiff.compute(inv, cache)
        assertEquals(setOf("same.mp3"), r.reusable.keys)
        assertEquals(listOf("changed.mp3", "new.mp3"), r.toProbe.map { it.name })
    }
    @Test fun `no cache means probe everything`() {
        val r = MetadataDiff.compute(listOf(FileStat("a.mp3", 1, 2)), null)
        assertTrue(r.reusable.isEmpty()); assertEquals(1, r.toProbe.size)
    }
}
```

- [ ] **Step 2:** run → FAIL. **Step 3:** create `MetadataDiff.kt`:

```kotlin
package com.cliplist.scan

/** A file as seen in the current listing (no open required). */
data class FileStat(val name: String, val size: Long, val lastModified: Long)

data class MetadataDiffResult(
    val reusable: Map<String, TrackMeta>,
    val toProbe: List<FileStat>,
)

object MetadataDiff {
    /** A cached track is reusable iff name + size + lastModified all match. */
    fun compute(inventory: List<FileStat>, cache: FolderMetaCache?): MetadataDiffResult {
        val cached = cache?.tracks?.associateBy { it.name } ?: emptyMap()
        val reusable = LinkedHashMap<String, TrackMeta>()
        val toProbe = mutableListOf<FileStat>()
        for (fs in inventory) {
            val c = cached[fs.name]
            if (c != null && c.size == fs.size && c.lastModified == fs.lastModified) reusable[fs.name] = c
            else toProbe.add(fs)
        }
        return MetadataDiffResult(reusable, toProbe)
    }
}
```

- [ ] **Step 4:** run → PASS. **Step 5: commit** `feat(meta): MetadataDiff — reuse unchanged, probe changed/new`.

---

### Task 4: `AudioProbe` + `FolderMetadataAnalyzer`

**Files:** Create `core-scan/.../FolderMetadataAnalyzer.kt`; Create test helper `core-scan/src/test/.../FakeAudioProbe.kt`; Test `core-scan/src/test/.../FolderMetadataAnalyzerTest.kt`.

- [ ] **Step 1:** create `FakeAudioProbe.kt`:

```kotlin
package com.cliplist.scan

class FakeAudioProbe(private val results: Map<String, ProbeResult>) : AudioProbe {
    val probed = mutableListOf<String>()
    override fun probe(node: VolumeNode): ProbeResult {
        probed.add(node.name)
        return results[node.name] ?: ProbeResult(0, false)
    }
}
```

- [ ] **Step 2: failing test** — `FolderMetadataAnalyzerTest.kt`:

```kotlin
package com.cliplist.scan
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FolderMetadataAnalyzerTest {
    private fun setup(vararg files: FakeNode): Pair<FakeVolume, FakeNode> {
        val rock = fakeDir("Rock", *files); return FakeVolume(rock) to rock
    }
    private val probe = FakeAudioProbe(mapOf(
        "a.mp3" to ProbeResult(180000, true),
        "b.mp3" to ProbeResult(120000, true),
        "bad.mp3" to ProbeResult(0, false)))

    @Test fun `first run probes all, writes cache, sums readable, lists unreadable`() {
        val a = fakeFile("a.mp3", 1, 1); val bad = fakeFile("bad.mp3", 2, 2)
        val (vol, rock) = setup(a, bad)
        val r = FolderMetadataAnalyzer(vol, FakeAudioProbe(mapOf(
            "a.mp3" to ProbeResult(180000, true), "bad.mp3" to ProbeResult(0, false))))
            .analyze(rock, listOf(a, bad))
        assertEquals(listOf("a.mp3"), r.readableFiles)
        assertEquals(180000, r.totalDurationMs)
        assertEquals(listOf("bad.mp3"), r.unreadable)
        assertNotNull(vol.writtenFiles["Rock/mpc-metadata.json"])  // cache written
    }

    @Test fun `second run reuses cache, does not re-probe`() {
        val a = fakeFile("a.mp3", 1, 1); val b = fakeFile("b.mp3", 1, 1)
        val (vol, rock) = setup(a, b)
        val p1 = FakeAudioProbe(mapOf("a.mp3" to ProbeResult(180000, true), "b.mp3" to ProbeResult(120000, true)))
        FolderMetadataAnalyzer(vol, p1).analyze(rock, listOf(a, b))     // run 1 writes cache
        val p2 = FakeAudioProbe(emptyMap())
        val r = FolderMetadataAnalyzer(vol, p2).analyze(rock, listOf(a, b))  // run 2
        assertTrue(p2.probed.isEmpty(), "unchanged files must not be re-probed")
        assertEquals(300000, r.totalDurationMs)
    }

    @Test fun `changed file is re-probed`() {
        val a = fakeFile("a.mp3", 1, 1)
        val (vol, rock) = setup(a)
        FolderMetadataAnalyzer(vol, FakeAudioProbe(mapOf("a.mp3" to ProbeResult(180000, true)))).analyze(rock, listOf(a))
        val a2 = fakeFile("a.mp3", 1, 999)  // mtime changed
        rock.children[0] = a2
        val p = FakeAudioProbe(mapOf("a.mp3" to ProbeResult(200000, true)))
        val r = FolderMetadataAnalyzer(vol, p).analyze(rock, listOf(a2))
        assertEquals(listOf("a.mp3"), p.probed)
        assertEquals(200000, r.totalDurationMs)
    }
}
```

- [ ] **Step 3:** run → FAIL. **Step 4:** create `FolderMetadataAnalyzer.kt`:

```kotlin
package com.cliplist.scan

data class ProbeResult(val durationMs: Long, val readable: Boolean)

/** Reads one audio file's duration/readability. Real impl uses MediaMetadataRetriever (Phase 3f-2). */
interface AudioProbe {
    fun probe(node: VolumeNode): ProbeResult
}

data class FolderAnalysis(
    val readableFiles: List<String>,
    val totalDurationMs: Long,
    val unreadable: List<String>,
)

/**
 * Per-folder metadata: reuse the cached durations for unchanged files, probe the rest, and
 * keep mpc-metadata.json up to date. Unreadable files are excluded from [FolderAnalysis.readableFiles].
 */
class FolderMetadataAnalyzer(
    private val volume: StorageVolume,
    private val probe: AudioProbe,
) {
    companion object { const val CACHE_NAME = "mpc-metadata.json" }

    fun analyze(folder: VolumeNode, audioNodes: List<VolumeNode>): FolderAnalysis {
        val inventory = audioNodes.map { FileStat(it.name, it.size, it.lastModified) }
        val cache = MetadataCacheCodec.decode(volume.readFile(folder, CACHE_NAME))
        val diff = MetadataDiff.compute(inventory, cache)

        val nodeByName = audioNodes.associateBy { it.name }
        val fresh = diff.toProbe.associate { fs ->
            val r = probe.probe(nodeByName.getValue(fs.name))
            fs.name to TrackMeta(fs.name, fs.size, fs.lastModified, r.durationMs, r.readable)
        }
        // Merge in listing order.
        val merged = audioNodes.map { n -> diff.reusable[n.name] ?: fresh.getValue(n.name) }

        // Rewrite the cache only when something actually changed (keeps re-runs write-free).
        val changed = cache == null ||
            diff.toProbe.isNotEmpty() ||
            cache.tracks.map { it.name } != merged.map { it.name }
        if (changed) {
            volume.writeFile(folder, CACHE_NAME, MetadataCacheCodec.encode(FolderMetaCache(tracks = merged)),
                "application/json")
        }

        val readable = merged.filter { it.readable }
        return FolderAnalysis(
            readableFiles = readable.map { it.name },
            totalDurationMs = readable.sumOf { it.durationMs },
            unreadable = merged.filterNot { it.readable }.map { it.name },
        )
    }
}
```

- [ ] **Step 5:** run → PASS. **Step 6: commit** `feat(meta): FolderMetadataAnalyzer + AudioProbe (cache-aware, readable-only)`.

---

### Task 5: CI

- [ ] **Step 1:** `./gradlew :core-format:test :core-scan:test --no-daemon 2>&1 | tail -4` → BUILD SUCCESSFUL (~107 tests: 99 + 8 new).
- [ ] **Step 2:** push; watch CI; both jobs green (`:app:assembleDebug` + `:app:assembleRelease` — the new `SafTreeVolume.readFile`/projection compile-validate here).
- **Triage if Android fails:** `SafTreeVolume` must implement the new `readFile` + `SafNode` must provide `size`/`lastModified` (Task 1 Step 5); the `kotlin-serialization` plugin applies only to `:core-scan` (a JVM module), not `:app`.

---

## Definition of Done

- [ ] ~107 JVM tests pass; both CI jobs green.
- [ ] `StorageVolume.readFile` + node `size`/`lastModified` on interface, `FakeVolume`, `SafTreeVolume`.
- [ ] Cache model round-trips; corrupt/empty JSON decodes to null (engine re-probes).
- [ ] `FolderMetadataAnalyzer`: unchanged files reuse cache (no re-probe), changed/new are probed, unreadable excluded from `readableFiles`, durations summed, `mpc-metadata.json` written only on change.

## Self-review

Spec coverage: §3 cache file → Task 2/4 (`CACHE_NAME = "mpc-metadata.json"`, visible). §4 metadata pass (list→load→diff→probe→merge→write) → Task 4 analyzer. §5 Layer-1 components (`readFile`, `TrackMeta`/`FolderMetaCache`, `MetadataCacheCodec`, `MetadataDiff`, `FolderAnalysis`, `AudioProbe`, `FolderMetadataAnalyzer`) → Tasks 1–4. Change detection by name+size+mtime → `MetadataDiff`. Unreadable excluded → analyzer. Layer 2 (`SafAudioProbe`, scan integration) and Layer 3 (UI) are later phases. Types: `ProbeResult`/`AudioProbe` (T4) consumed by analyzer; `FileStat`/`MetadataDiffResult` (T3); `TrackMeta`/`FolderMetaCache` (T2) used in T3/T4; node `size`/`lastModified` (T1) used in `FolderMetadataAnalyzer`.
