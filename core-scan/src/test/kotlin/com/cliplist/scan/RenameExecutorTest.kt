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
        val sub = root.children.first { it.isDirectory }
        assertEquals("Tëst.mp3", sub.children.single().name)
    }
}
