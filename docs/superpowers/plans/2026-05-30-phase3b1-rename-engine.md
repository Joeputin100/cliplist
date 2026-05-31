# Phase 3b-1 — Rename & Undo Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pure-Kotlin logic that computes and applies the file/folder renames implied by the two "cleaning" toggles, with collision safety and a reversible undo — the engine the Preview/Progress screens (3b-2, 3b-3) will drive.

**Architecture:** Add a `renameNode` capability to the existing `StorageVolume` abstraction (implemented by both the JVM `FakeVolume` test double and the Android `SafTreeVolume`). `RenameRules.desiredName` decides the new name for one node from `RenameOptions`. `RenamePlanner` walks the whole tree and produces a `RenamePlan` (deepest-first ops + excluded collisions). `RenameExecutor` applies a plan and can undo it. All logic lives in `:core-scan` and is JVM-unit-tested with `FakeVolume`; the Android SAF implementation is compile-validated in CI.

**Tech Stack:** Kotlin 2.3.20 · JUnit Jupiter 5.14.4 · `:core-scan` (JVM) + `:core-format` (FilenameSanitizer, already a dependency) + `:data-storage` (SAF). Java 21.

**Non-destructive invariant (from spec §3a):** this engine **renames only — it never deletes**. Colliding renames (two names resolving to the same target in one directory) are *excluded* and reported, never applied, so a rename can never overwrite another file.

---

## File map

```
core-scan/src/main/kotlin/com/cliplist/scan/
  StorageVolume.kt        ← MODIFY: add RenameOutcome + renameNode() to the interface
  RenameRules.kt          ← CREATE: RenameOptions + desiredName() (per-node decision)
  RenameModels.kt         ← CREATE: RenameOp, RenameCollision, RenamePlan
  RenamePlanner.kt        ← CREATE: walk tree → RenamePlan (ops deepest-first + collisions)
  RenameExecutor.kt       ← CREATE: AppliedRename/RenameExecution/RenameFailure/UndoResult + execute()/undo()
core-scan/src/test/kotlin/com/cliplist/scan/
  FakeVolume.kt           ← MODIFY: FakeNode.name var; implement renameNode(); track renames
  StorageVolumeRenameTest.kt ← CREATE (Task 1)
  RenameRulesTest.kt      ← CREATE (Task 2)
  RenamePlannerTest.kt    ← CREATE (Task 3)
  RenameExecutorTest.kt   ← CREATE (Tasks 4 & 5)
data-storage/src/main/kotlin/com/cliplist/storage/
  SafTreeVolume.kt        ← MODIFY: implement renameNode() via DocumentsContract.renameDocument()
```

**Scope note (recursive):** the planner walks the entire subtree under the volume root. Name-cleaning applies to the whole selected folder, independent of the playlist "Search subfolders" toggle (cleaning a name is safe and you want consistent names across the card). Every rename is shown in Preview before it is applied (3b-2), so this is transparent.

---

### Task 1: Add `renameNode` to `StorageVolume` (interface + both implementations)

Adding an abstract method to the interface forces **both** implementers to provide it, so all three files change together to keep the build compiling.

**Files:**
- Modify: `core-scan/src/main/kotlin/com/cliplist/scan/StorageVolume.kt`
- Modify: `core-scan/src/test/kotlin/com/cliplist/scan/FakeVolume.kt`
- Modify: `data-storage/src/main/kotlin/com/cliplist/storage/SafTreeVolume.kt`
- Test: `core-scan/src/test/kotlin/com/cliplist/scan/StorageVolumeRenameTest.kt`

- [ ] **Step 1: Write the failing test**

Create `core-scan/src/test/kotlin/com/cliplist/scan/StorageVolumeRenameTest.kt`:

```kotlin
package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StorageVolumeRenameTest {

    @Test fun `renameNode changes the node name and records the rename`() {
        val file = fakeFile("Café.mp3")
        val volume = FakeVolume(fakeDir("Music", file))

        val result = volume.renameNode(file, "Cafe.mp3")

        assertTrue(result is RenameOutcome.Renamed)
        assertEquals("Cafe.mp3", file.name)
        assertEquals(listOf("Café.mp3" to "Cafe.mp3"), volume.renames)
    }

    @Test fun `renameNode reports failure for flagged names and leaves the name unchanged`() {
        val file = fakeFile("bad.mp3")
        val volume = FakeVolume(fakeDir("Music", file)).also { it.renameFailNames.add("bad.mp3") }

        val result = volume.renameNode(file, "good.mp3")

        assertTrue(result is RenameOutcome.Failure)
        assertEquals("bad.mp3", file.name)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --tests '*StorageVolumeRenameTest*' --no-daemon 2>&1 | tail -15`
Expected: compile failure — `renameNode`, `RenameOutcome`, `renames`, `renameFailNames` are unresolved.

- [ ] **Step 3: Add `RenameOutcome` + `renameNode` to the interface**

Replace `core-scan/src/main/kotlin/com/cliplist/scan/StorageVolume.kt` with:

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

/** Outcome of a rename. On success it carries the updated node — a node's identity/URI may
 *  change when renamed (SAF returns a new document URI), so callers must use the returned node. */
sealed class RenameOutcome {
    data class Renamed(val node: VolumeNode) : RenameOutcome()
    data class Failure(val message: String) : RenameOutcome()
}

interface StorageVolume {
    val rootNode: VolumeNode
    fun children(node: VolumeNode): List<VolumeNode>
    fun writeFile(directory: VolumeNode, name: String, content: ByteArray): VolumeWriteResult
    fun deleteFile(directory: VolumeNode, fileName: String): Boolean
    fun renameNode(node: VolumeNode, newName: String): RenameOutcome
}
```

- [ ] **Step 4: Implement in `FakeVolume` (make `FakeNode.name` mutable + track renames)**

Replace `core-scan/src/test/kotlin/com/cliplist/scan/FakeVolume.kt` with:

```kotlin
package com.cliplist.scan

class FakeVolume(root: FakeNode) : StorageVolume {
    override val rootNode: VolumeNode = root
    val writtenFiles = mutableMapOf<String, ByteArray>()   // "folderName/fileName" -> bytes
    val deletedFiles = mutableListOf<String>()             // "folderName/fileName"
    val failFiles = mutableSetOf<String>()                 // keys that return Failure on writeFile
    val renames = mutableListOf<Pair<String, String>>()    // (oldName, newName) in call order
    val renameFailNames = mutableSetOf<String>()           // current name -> force a rename Failure

    override fun children(node: VolumeNode): List<VolumeNode> =
        (node as FakeNode).children.toList()

    fun findFile(directory: VolumeNode, fileName: String): VolumeNode? =
        (directory as FakeNode).children.find { it.name.equals(fileName, ignoreCase = true) }

    override fun writeFile(directory: VolumeNode, name: String, content: ByteArray): VolumeWriteResult {
        val key = "${directory.name}/$name"
        if (key in failFiles) return VolumeWriteResult.Failure("simulated write failure for $key")
        writtenFiles[key] = content
        return VolumeWriteResult.Success
    }

    override fun deleteFile(directory: VolumeNode, fileName: String): Boolean {
        deletedFiles.add("${directory.name}/$fileName")
        return true
    }

    override fun renameNode(node: VolumeNode, newName: String): RenameOutcome {
        node as FakeNode
        if (node.name in renameFailNames)
            return RenameOutcome.Failure("simulated rename failure for ${node.name}")
        renames.add(node.name to newName)
        node.name = newName
        return RenameOutcome.Renamed(node)
    }
}

data class FakeNode(
    override var name: String,
    override val isDirectory: Boolean,
    val children: MutableList<FakeNode> = mutableListOf()
) : VolumeNode

fun fakeDir(name: String, vararg children: FakeNode): FakeNode =
    FakeNode(name, isDirectory = true, children = children.toMutableList())

fun fakeFile(name: String): FakeNode =
    FakeNode(name, isDirectory = false)
```

- [ ] **Step 5: Implement in `SafTreeVolume` (so `:data-storage` still compiles)**

In `data-storage/src/main/kotlin/com/cliplist/storage/SafTreeVolume.kt`, add the import near the other `com.cliplist.scan` imports:

```kotlin
import com.cliplist.scan.RenameOutcome
```

Then add this method inside the class, after `deleteFile(...)` (before the closing brace):

```kotlin
    override fun renameNode(node: VolumeNode, newName: String): RenameOutcome {
        node as SafNode
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, node.documentId)
        return try {
            val newUri = DocumentsContract.renameDocument(context.contentResolver, docUri, newName)
                ?: return RenameOutcome.Failure("renameDocument returned null for ${node.name}")
            // renameDocument may return a NEW document URI; derive the new id from it.
            RenameOutcome.Renamed(SafNode(DocumentsContract.getDocumentId(newUri), newName, node.isDirectory))
        } catch (e: Exception) {
            Log.e("SafTreeVolume", "rename ${node.name} -> $newName: ${e.message}")
            RenameOutcome.Failure(e.message ?: "rename failed for ${node.name}")
        }
    }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --tests '*StorageVolumeRenameTest*' --no-daemon 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`, 2 tests passing.

- [ ] **Step 7: Commit**

```bash
cd /home/projects/mpc
git add core-scan/src/main/kotlin/com/cliplist/scan/StorageVolume.kt \
        core-scan/src/test/kotlin/com/cliplist/scan/FakeVolume.kt \
        core-scan/src/test/kotlin/com/cliplist/scan/StorageVolumeRenameTest.kt \
        data-storage/src/main/kotlin/com/cliplist/storage/SafTreeVolume.kt
git commit -m "feat(rename): add renameNode to StorageVolume (FakeVolume + SafTreeVolume)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: `RenameOptions` + `RenameRules.desiredName` (per-node decision)

The trickiest single decision — *should this one node be renamed, and to what?* — isolated and exhaustively tested.

**Files:**
- Create: `core-scan/src/main/kotlin/com/cliplist/scan/RenameRules.kt`
- Test: `core-scan/src/test/kotlin/com/cliplist/scan/RenameRulesTest.kt`

- [ ] **Step 1: Write the failing test**

Create `core-scan/src/test/kotlin/com/cliplist/scan/RenameRulesTest.kt`:

```kotlin
package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RenameRulesTest {
    private val all       = RenameOptions(cleanNames = true,  renameHidden = true)
    private val none      = RenameOptions(cleanNames = false, renameHidden = false)
    private val cleanOnly = RenameOptions(cleanNames = true,  renameHidden = false)
    private val hiddenOnly= RenameOptions(cleanNames = false, renameHidden = true)

    @Test fun `clean visible file when cleanNames on`() {
        assertEquals("Cafe.mp3", RenameRules.desiredName(fakeFile("Café.mp3"), cleanOnly))
    }
    @Test fun `already-clean file is a no-op`() {
        assertNull(RenameRules.desiredName(fakeFile("Song.mp3"), all))
    }
    @Test fun `visible file untouched when cleanNames off`() {
        assertNull(RenameRules.desiredName(fakeFile("Café.mp3"), hiddenOnly))
    }
    @Test fun `hidden file un-hidden and sanitized when renameHidden on`() {
        assertEquals("My Track.mp3", RenameRules.desiredName(fakeFile(".My Track.mp3"), hiddenOnly))
    }
    @Test fun `hidden file untouched when renameHidden off even if cleanNames on`() {
        assertNull(RenameRules.desiredName(fakeFile(".DS_Store"), cleanOnly))
    }
    @Test fun `folder name sanitized as a whole stem`() {
        assertEquals("Rock Roll", RenameRules.desiredName(fakeDir("Rock: Roll"), cleanOnly))
    }
    @Test fun `no options means no rename`() {
        assertNull(RenameRules.desiredName(fakeFile("Café.mp3"), none))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --tests '*RenameRulesTest*' --no-daemon 2>&1 | tail -12`
Expected: compile failure — `RenameOptions` and `RenameRules` unresolved.

- [ ] **Step 3: Implement `RenameRules.kt`**

Create `core-scan/src/main/kotlin/com/cliplist/scan/RenameRules.kt`:

```kotlin
package com.cliplist.scan

import com.cliplist.format.FilenameSanitizer

/** The two cleaning toggles from the Home screen. */
data class RenameOptions(
    val cleanNames: Boolean,    // rewrite VISIBLE file & folder names to ASCII Clip-Sport-safe
    val renameHidden: Boolean   // un-hide dot-prefixed names (strip leading dot + sanitize)
)

object RenameRules {
    /**
     * The name [node] should be renamed to, or null to leave it untouched.
     *
     * Hidden (dot-prefixed) names are only touched when [RenameOptions.renameHidden];
     * visible names only when [RenameOptions.cleanNames]. FilenameSanitizer strips the leading
     * dot, so a hidden name becomes visible ("literal rename"). Returns null on a no-op
     * (the sanitized name equals the current name).
     */
    fun desiredName(node: VolumeNode, options: RenameOptions): String? {
        val hidden = node.name.startsWith(".")
        val active = if (hidden) options.renameHidden else options.cleanNames
        if (!active) return null
        val candidate = FilenameSanitizer.sanitize(node.name, preserveExtension = !node.isDirectory)
        return if (candidate != node.name) candidate else null
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --tests '*RenameRulesTest*' --no-daemon 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`, 7 tests passing.

- [ ] **Step 5: Commit**

```bash
cd /home/projects/mpc
git add core-scan/src/main/kotlin/com/cliplist/scan/RenameRules.kt \
        core-scan/src/test/kotlin/com/cliplist/scan/RenameRulesTest.kt
git commit -m "feat(rename): RenameOptions + RenameRules.desiredName per-node decision

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: `RenameModels` + `RenamePlanner` (tree walk → ordered ops + collisions)

**Files:**
- Create: `core-scan/src/main/kotlin/com/cliplist/scan/RenameModels.kt`
- Create: `core-scan/src/main/kotlin/com/cliplist/scan/RenamePlanner.kt`
- Test: `core-scan/src/test/kotlin/com/cliplist/scan/RenamePlannerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `core-scan/src/test/kotlin/com/cliplist/scan/RenamePlannerTest.kt`:

```kotlin
package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RenamePlannerTest {
    private val clean = RenameOptions(cleanNames = true, renameHidden = false)
    private val all   = RenameOptions(cleanNames = true, renameHidden = true)
    private val off   = RenameOptions(cleanNames = false, renameHidden = false)

    @Test fun `no options - empty plan`() {
        val plan = RenamePlanner().plan(
            FakeVolume(fakeDir("Music", fakeFile("Café.mp3"), fakeFile(".DS_Store"))), off)
        assertTrue(plan.ops.isEmpty())
        assertTrue(plan.collisions.isEmpty())
    }

    @Test fun `cleans a dirty file, leaves a clean one`() {
        val plan = RenamePlanner().plan(
            FakeVolume(fakeDir("Music", fakeFile("Café.mp3"), fakeFile("OK.mp3"))), clean)
        val op = plan.ops.single()
        assertEquals("Café.mp3", op.oldName)
        assertEquals("Cafe.mp3", op.newName)
        assertFalse(op.isDirectory)
    }

    @Test fun `hidden file only renamed with renameHidden`() {
        assertTrue(RenamePlanner().plan(
            FakeVolume(fakeDir("Music", fakeFile(".My Track.mp3"))), clean).ops.isEmpty())
        val plan = RenamePlanner().plan(
            FakeVolume(fakeDir("Music", fakeFile(".My Track.mp3"))), all)
        assertEquals("My Track.mp3", plan.ops.single().newName)
    }

    @Test fun `nested folders - ops ordered deepest first`() {
        val root = fakeDir("Music", fakeDir("Jazz:Sub", fakeFile("Tëst.mp3")))
        val plan = RenamePlanner().plan(FakeVolume(root), clean)
        assertEquals(listOf("Tëst.mp3", "Jazz:Sub"), plan.ops.map { it.oldName })
        assertTrue(plan.ops.first().depth > plan.ops.last().depth)
    }

    @Test fun `two names colliding on one target are both excluded and reported`() {
        val plan = RenamePlanner().plan(
            FakeVolume(fakeDir("Music", fakeFile("Café.mp3"), fakeFile("Cafè.mp3"))), clean)
        assertTrue(plan.ops.isEmpty())
        val c = plan.collisions.single()
        assertEquals("Cafe.mp3", c.targetName)
        assertEquals(setOf("Café.mp3", "Cafè.mp3"), c.sources.toSet())
    }

    @Test fun `rename that would clobber an existing clean sibling is excluded`() {
        val plan = RenamePlanner().plan(
            FakeVolume(fakeDir("Music", fakeFile("Café.mp3"), fakeFile("Cafe.mp3"))), clean)
        assertTrue(plan.ops.isEmpty(), "must not overwrite existing Cafe.mp3")
        assertEquals("Cafe.mp3", plan.collisions.single().targetName)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --tests '*RenamePlannerTest*' --no-daemon 2>&1 | tail -12`
Expected: compile failure — `RenamePlanner`, `RenameOp`, `RenameCollision`, `RenamePlan` unresolved.

- [ ] **Step 3: Implement `RenameModels.kt`**

Create `core-scan/src/main/kotlin/com/cliplist/scan/RenameModels.kt`:

```kotlin
package com.cliplist.scan

/** One planned rename of a file or folder. */
data class RenameOp(
    val node: VolumeNode,
    val parentPath: String,   // "/"-joined path of the containing directory ("" = root)
    val oldName: String,
    val newName: String,
    val depth: Int            // directory distance from root; ops apply deepest-first
) {
    val isDirectory: Boolean get() = node.isDirectory
}

/** Two or more names in one directory would resolve to the same target; excluded for safety. */
data class RenameCollision(
    val parentPath: String,
    val targetName: String,
    val sources: List<String>   // current names that wanted this target
)

/** Safe, deepest-first rename ops plus the collisions that were deliberately NOT planned. */
data class RenamePlan(
    val ops: List<RenameOp>,
    val collisions: List<RenameCollision>
)
```

- [ ] **Step 4: Implement `RenamePlanner.kt`**

Create `core-scan/src/main/kotlin/com/cliplist/scan/RenamePlanner.kt`:

```kotlin
package com.cliplist.scan

/**
 * Computes the file/folder renames implied by [RenameOptions] over the whole tree under the
 * volume root. Walks recursively (cleaning applies to the entire selected folder). Detects
 * collisions — names that would clash inside one directory — and EXCLUDES them so a rename
 * never overwrites another file. Ops are ordered deepest-first so a directory is only renamed
 * after its contents.
 */
class RenamePlanner {
    private data class Walked(val dir: VolumeNode, val path: String, val depth: Int)

    fun plan(volume: StorageVolume, options: RenameOptions): RenamePlan {
        val ops = mutableListOf<RenameOp>()
        val collisions = mutableListOf<RenameCollision>()

        val queue = ArrayDeque<Walked>()
        queue.addLast(Walked(volume.rootNode, path = "", depth = 0))

        while (queue.isNotEmpty()) {
            val (dir, dirPath, depth) = queue.removeFirst()
            val children = volume.children(dir)

            // Always recurse into subdirectories — cleaning is recursive over the selected tree.
            children.filter { it.isDirectory }.forEach { sub ->
                val childPath = if (dirPath.isEmpty()) sub.name else "$dirPath/${sub.name}"
                queue.addLast(Walked(sub, childPath, depth + 1))
            }

            // Desired new name per child (null = leave alone).
            val desired: Map<VolumeNode, String?> =
                children.associateWith { RenameRules.desiredName(it, options) }
            // What each child's name WOULD be after planning, lowercased (FAT32 is case-insensitive).
            val finalCounts: Map<String, Int> =
                children.groupingBy { (desired[it] ?: it.name).lowercase() }.eachCount()

            val excluded = linkedMapOf<String, MutableList<String>>() // target -> source oldNames
            for (child in children) {
                val newName = desired[child] ?: continue
                if (finalCounts.getValue(newName.lowercase()) > 1) {
                    excluded.getOrPut(newName) { mutableListOf() }.add(child.name)
                } else {
                    ops.add(RenameOp(child, dirPath, child.name, newName, depth + 1))
                }
            }
            excluded.forEach { (target, sources) ->
                collisions.add(RenameCollision(dirPath, target, sources))
            }
        }

        ops.sortByDescending { it.depth }
        return RenamePlan(ops, collisions)
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --tests '*RenamePlannerTest*' --no-daemon 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`, 6 tests passing.

- [ ] **Step 6: Commit**

```bash
cd /home/projects/mpc
git add core-scan/src/main/kotlin/com/cliplist/scan/RenameModels.kt \
        core-scan/src/main/kotlin/com/cliplist/scan/RenamePlanner.kt \
        core-scan/src/test/kotlin/com/cliplist/scan/RenamePlannerTest.kt
git commit -m "feat(rename): RenamePlanner — recursive walk, collision exclusion, deepest-first ordering

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: `RenameExecutor.execute` (apply a plan, collect failures)

**Files:**
- Create: `core-scan/src/main/kotlin/com/cliplist/scan/RenameExecutor.kt`
- Test: `core-scan/src/test/kotlin/com/cliplist/scan/RenameExecutorTest.kt`

- [ ] **Step 1: Write the failing test**

Create `core-scan/src/test/kotlin/com/cliplist/scan/RenameExecutorTest.kt`:

```kotlin
package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RenameExecutorTest {
    private val clean = RenameOptions(cleanNames = true, renameHidden = false)

    @Test fun `execute applies every op and mutates the tree`() {
        val root = fakeDir("Music",
            fakeFile("Café.mp3"),
            fakeDir("Jazz:Sub", fakeFile("Tëst.mp3")))
        val volume = FakeVolume(root)
        val plan = RenamePlanner().plan(volume, clean)

        val exec = RenameExecutor(volume).execute(plan)

        assertEquals(3, exec.applied.size)           // Café.mp3, Tëst.mp3, Jazz:Sub
        assertTrue(exec.failed.isEmpty())
        assertEquals(setOf("Cafe.mp3", "JazzSub"), root.children.map { it.name }.toSet())
    }

    @Test fun `execute records failures and keeps going`() {
        val root = fakeDir("Music", fakeFile("Café.mp3"), fakeFile("Tëst.mp3"))
        val volume = FakeVolume(root).also { it.renameFailNames.add("Café.mp3") }
        val plan = RenamePlanner().plan(volume, clean)

        val exec = RenameExecutor(volume).execute(plan)

        assertEquals(1, exec.applied.size)
        assertEquals(1, exec.failed.size)
        assertEquals("Café.mp3", exec.failed.single().op.oldName)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --tests '*RenameExecutorTest*' --no-daemon 2>&1 | tail -12`
Expected: compile failure — `RenameExecutor`, `RenameExecution` unresolved.

- [ ] **Step 3: Implement `RenameExecutor.kt`**

Create `core-scan/src/main/kotlin/com/cliplist/scan/RenameExecutor.kt`:

```kotlin
package com.cliplist.scan

/** A rename that was actually applied; keeps the POST-rename node so undo can target it. */
data class AppliedRename(
    val node: VolumeNode,
    val parentPath: String,
    val oldName: String,
    val newName: String
)

data class RenameFailure(val op: RenameOp, val message: String)

data class RenameExecution(
    val applied: List<AppliedRename>,
    val failed: List<RenameFailure>
)

data class UndoResult(val reverted: Int, val failed: List<RenameFailure>)

class RenameExecutor(private val volume: StorageVolume) {
    /** Applies plan ops in order (already deepest-first). Never throws; collects failures. */
    fun execute(plan: RenamePlan): RenameExecution {
        val applied = mutableListOf<AppliedRename>()
        val failed = mutableListOf<RenameFailure>()
        for (op in plan.ops) {
            when (val r = volume.renameNode(op.node, op.newName)) {
                is RenameOutcome.Renamed ->
                    applied.add(AppliedRename(r.node, op.parentPath, op.oldName, op.newName))
                is RenameOutcome.Failure ->
                    failed.add(RenameFailure(op, r.message))
            }
        }
        return RenameExecution(applied, failed)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --tests '*RenameExecutorTest*' --no-daemon 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`, 2 tests passing.

- [ ] **Step 5: Commit**

```bash
cd /home/projects/mpc
git add core-scan/src/main/kotlin/com/cliplist/scan/RenameExecutor.kt \
        core-scan/src/test/kotlin/com/cliplist/scan/RenameExecutorTest.kt
git commit -m "feat(rename): RenameExecutor.execute applies a plan, collecting failures

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: `RenameExecutor.undo` (reverse an execution)

**Files:**
- Modify: `core-scan/src/main/kotlin/com/cliplist/scan/RenameExecutor.kt`
- Test: `core-scan/src/test/kotlin/com/cliplist/scan/RenameExecutorTest.kt`

- [ ] **Step 1: Add the failing test**

Append this test method inside `RenameExecutorTest` (before the closing brace):

```kotlin
    @Test fun `undo restores every original name`() {
        val root = fakeDir("Music",
            fakeFile("Café.mp3"),
            fakeDir("Jazz:Sub", fakeFile("Tëst.mp3")))
        val volume = FakeVolume(root)
        val exec = RenameExecutor(volume).execute(RenamePlanner().plan(volume, clean))

        val undo = RenameExecutor(volume).undo(exec)

        assertEquals(3, undo.reverted)
        assertTrue(undo.failed.isEmpty())
        assertEquals(setOf("Café.mp3", "Jazz:Sub"), root.children.map { it.name }.toSet())
        val sub = root.children.first { it.isDirectory } as FakeNode
        assertEquals("Tëst.mp3", sub.children.single().name)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --tests '*RenameExecutorTest*' --no-daemon 2>&1 | tail -12`
Expected: compile failure — `undo` is unresolved.

- [ ] **Step 3: Add `undo` to `RenameExecutor`**

In `core-scan/src/main/kotlin/com/cliplist/scan/RenameExecutor.kt`, add this method inside the `RenameExecutor` class, after `execute(...)`:

```kotlin
    /**
     * Reverts an execution by renaming each applied node back to its old name, newest-first
     * (the inverse order of how they were applied). Uses the post-rename node captured in
     * [AppliedRename], whose identity/URI is the current one.
     */
    fun undo(execution: RenameExecution): UndoResult {
        var reverted = 0
        val failed = mutableListOf<RenameFailure>()
        for (a in execution.applied.asReversed()) {
            when (volume.renameNode(a.node, a.oldName)) {
                is RenameOutcome.Renamed -> reverted++
                is RenameOutcome.Failure -> failed.add(
                    RenameFailure(
                        RenameOp(a.node, a.parentPath, a.newName, a.oldName, depth = 0),
                        "undo failed for ${a.newName}"
                    )
                )
            }
        }
        return UndoResult(reverted, failed)
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd /home/projects/mpc && ./gradlew :core-scan:test --tests '*RenameExecutorTest*' --no-daemon 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`, 3 tests passing.

- [ ] **Step 5: Commit**

```bash
cd /home/projects/mpc
git add core-scan/src/main/kotlin/com/cliplist/scan/RenameExecutor.kt \
        core-scan/src/test/kotlin/com/cliplist/scan/RenameExecutorTest.kt
git commit -m "feat(rename): RenameExecutor.undo reverts an execution (newest-first)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: Full suite + CI (confirms `:data-storage` compiles with the new method)

**Files:** none (verification only)

- [ ] **Step 1: Run the full JVM suite locally**

Run: `cd /home/projects/mpc && ./gradlew :core-format:test :core-scan:test --no-daemon 2>&1 | tail -6`
Expected: `BUILD SUCCESSFUL`. Test count rises by 18 (2 + 7 + 6 + 3 — Task 5 appends to Task 4's file) to **85 tests**, 0 failures.

- [ ] **Step 2: Push and watch CI**

```bash
cd /home/projects/mpc
git push origin main
sleep 8
RUN_ID=$(gh run list --workflow=build.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run watch "$RUN_ID" --exit-status --interval 25 2>&1 | tail -3
```
Expected: both `jvm-tests` and `Android modules build` green. The Android job proves `SafTreeVolume.renameNode` compiles against the new interface.

**If `android-build` fails:** read `gh run view "$RUN_ID" --log-failed`. Most likely a `SafTreeVolume` import or `SafNode` constructor mismatch — confirm `SafNode(documentId, name, isDirectory)` argument order and that `DocumentsContract.getDocumentId` / `renameDocument` are imported (both live in the already-imported `android.provider.DocumentsContract`).

---

## Definition of Done (Phase 3b-1)

- [ ] `./gradlew :core-format:test :core-scan:test` passes: **85 tests**, 0 failures.
- [ ] Both CI jobs green (the Android job validates `SafTreeVolume.renameNode`).
- [ ] `StorageVolume` exposes `renameNode(node, newName): RenameOutcome`, implemented by `FakeVolume` and `SafTreeVolume`.
- [ ] `RenamePlanner` produces deepest-first ops, recurses the whole tree, and excludes+reports collisions (never overwrites).
- [ ] `RenameExecutor` applies a plan and can fully undo it; failures are collected, never thrown.
- [ ] **No deletions anywhere in this engine** (non-destructive invariant, spec §3a).

---

## Self-review (completed by plan author)

**Spec coverage (3b-1 scope):**
- §3a "Clean file names" + "Rename hidden files" toggles → `RenameOptions` + `RenameRules` (Task 2). ✓
- §3a non-destructive (rename, never delete; collisions excluded) → planner collision exclusion (Task 3) + DoD invariant. ✓
- §3a undo log → `RenameExecutor.undo` (Task 5). ✓
- Rename previews for Preview screen → `RenamePlan.ops`/`collisions` are the exact data 3b-2 renders. ✓
- UI, SAF picker, ViewModels, progress → out of scope (3b-2 / 3b-3).

**Placeholder scan:** none. Every step has exact code and an exact command with expected output.

**Type consistency:** `RenameOutcome.Renamed(node)` (Task 1) consumed by `RenameExecutor.execute`/`undo` (Tasks 4-5). `RenameOptions(cleanNames, renameHidden)` defined Task 2, used Tasks 3-5. `RenameOp(node, parentPath, oldName, newName, depth)` defined Task 3, constructed in planner (Task 3) and undo (Task 5) — argument order matches. `RenamePlan(ops, collisions)` defined Task 3, consumed Task 4. `desiredName` returns `String?`, callers null-check. `FakeNode.name` is `var` (Task 1) so the planner/executor mutation and undo round-trip work. `FilenameSanitizer.sanitize(name, preserveExtension)` matches the real signature in `:core-format`.

**Sanitizer behavior verified against `FilenameSanitizer` source:** `"Café.mp3"`→`"Cafe.mp3"`, `"Cafè.mp3"`→`"Cafe.mp3"` (collision), `".My Track.mp3"`→`"My Track.mp3"` (leading dot stripped in step 4), `"Rock: Roll"`→`"Rock Roll"` (`:` removed), `"Tëst.mp3"`→`"Test.mp3"`, `"Jazz:Sub"`→`"JazzSub"` (folder, no extension).
