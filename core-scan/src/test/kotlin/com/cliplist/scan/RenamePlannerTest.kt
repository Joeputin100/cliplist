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
