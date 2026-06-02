package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileStorageVolumeTest {
    @Test fun `write, read, list, rename, delete in a real dir`(@TempDir tmp: File) {
        val vol = FileStorageVolume(tmp)
        val root = vol.rootNode
        assertTrue(root.isDirectory)

        assertEquals(
            VolumeWriteResult.Success,
            vol.writeFile(root, "a.m3u", "hi".toByteArray(), "audio/x-mpegurl"),
        )
        assertEquals("hi", vol.readFile(root, "a.m3u")!!.toString(Charsets.UTF_8))

        val kids = vol.children(root)
        assertEquals(listOf("a.m3u"), kids.map { it.name })
        assertTrue(kids[0].size > 0L && !kids[0].isDirectory)

        val outcome = vol.renameNode(kids[0], "b.m3u")
        assertTrue(outcome is RenameOutcome.Renamed)
        assertEquals(listOf("b.m3u"), vol.children(root).map { it.name })

        assertTrue(vol.deleteFile(root, "b.m3u"))
        assertTrue(vol.children(root).isEmpty())
    }

    @Test fun `readFile of absent is null`(@TempDir tmp: File) {
        val vol = FileStorageVolume(tmp)
        assertNull(vol.readFile(vol.rootNode, "nope.mp3"))
    }
}
