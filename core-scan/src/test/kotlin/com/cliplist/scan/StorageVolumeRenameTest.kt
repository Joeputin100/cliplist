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
