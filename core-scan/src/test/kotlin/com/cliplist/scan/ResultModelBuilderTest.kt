package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ResultModelBuilderTest {

    @Test fun `combines write report and rename execution`() {
        val write = WriteReport(written = 12, failed = 1, errors = listOf("Rock/Rock.m3u: boom"))
        val node = fakeFile("Cafe.mp3")
        val rename = RenameExecution(
            applied = listOf(AppliedRename(node, "Music", "Café.mp3", "Cafe.mp3")),
            failed = listOf(RenameFailure(
                RenameOp(node, "Music", "Bad.mp3", "Good.mp3", depth = 1), "denied"))
        )

        val r = ResultModelBuilder.build(write, rename, ScanPlan(emptyList(), emptyList()), "")

        assertEquals(12, r.playlistsWritten)
        assertEquals(1, r.playlistsFailed)
        assertEquals(1, r.renamesApplied)
        assertEquals(1, r.renamesFailed)
        assertEquals(2, r.totalFailed)
        assertFalse(r.allSucceeded)
        assertEquals(2, r.errors.size)
    }

    @Test fun `null rename means zero renames and success when nothing failed`() {
        val r = ResultModelBuilder.build(WriteReport(5, 0, emptyList()), null, ScanPlan(emptyList(), emptyList()), "")
        assertEquals(5, r.playlistsWritten)
        assertEquals(0, r.renamesApplied)
        assertEquals(0, r.totalFailed)
        assertTrue(r.allSucceeded)
        assertTrue(r.errors.isEmpty())
    }

    @Test fun `build includes written folders and destination`() {
        val rock = fakeDir("Rock", fakeFile("a.mp3"), fakeFile("b.mp3"))
        val scan = ScanPlan(listOf(FolderPlan(rock, listOf("a.mp3","b.mp3"), null, "Rock.m3u")), emptyList())
        val r = ResultModelBuilder.build(WriteReport(1,0,emptyList()), null, scan, "SD card / Music")
        assertEquals("SD card / Music", r.destination)
        assertEquals(1, r.folders.size)
        assertEquals("Rock", r.folders[0].folderName)
        assertEquals(2, r.folders[0].trackCount)
    }
}
