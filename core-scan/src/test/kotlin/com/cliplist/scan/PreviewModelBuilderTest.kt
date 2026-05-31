package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PreviewModelBuilderTest {

    private fun scanOf(vararg folders: FolderPlan, warnings: List<PlanWarning> = emptyList()) =
        ScanPlan(folders.toList(), warnings)

    @Test fun `maps folders to playlist rows with NEW and REPLACE`() {
        val rock = fakeDir("Rock", fakeFile("a.mp3"), fakeFile("b.mp3"))
        val jazz = fakeDir("Jazz")
        val scan = scanOf(
            FolderPlan(rock, listOf("a.mp3", "b.mp3"), existingPlaylistName = null, playlistName = "Rock.m3u"),
            FolderPlan(jazz, listOf("c.mp3"), existingPlaylistName = "Jazz.m3u", playlistName = "Jazz.m3u")
        )
        val model = PreviewModelBuilder.build(scan, RenamePlan(emptyList(), emptyList()))

        assertEquals(2, model.playlists.size)
        assertEquals(PlaylistAction.NEW, model.playlists[0].action)
        assertEquals(2, model.playlists[0].trackCount)
        assertEquals(PlaylistAction.REPLACE, model.playlists[1].action)
        assertEquals(3, model.totalTracks)
    }

    @Test fun `maps rename ops to rename rows`() {
        val f = fakeFile("Café.mp3")
        val rename = RenamePlan(
            ops = listOf(RenameOp(f, "Music/Rock", "Café.mp3", "Cafe.mp3", depth = 2)),
            collisions = emptyList()
        )
        val model = PreviewModelBuilder.build(scanOf(), rename)

        assertEquals(1, model.renames.size)
        assertEquals("Café.mp3", model.renames[0].oldName)
        assertEquals("Cafe.mp3", model.renames[0].newName)
        assertFalse(model.renames[0].isDirectory)
    }

    @Test fun `limit warnings become human-readable strings and clear withinLimits`() {
        val scan = scanOf(warnings = listOf(PlanWarning.TooManyPlaylists(60)))
        val model = PreviewModelBuilder.build(scan, RenamePlan(emptyList(), emptyList()))

        assertFalse(model.withinLimits)
        assertTrue(model.warnings.any { it.contains("60") })
    }

    @Test fun `collisions appear as warnings but do not clear withinLimits`() {
        val rename = RenamePlan(
            ops = emptyList(),
            collisions = listOf(RenameCollision("Music", "Cafe.mp3", listOf("Café.mp3", "Cafè.mp3")))
        )
        val model = PreviewModelBuilder.build(scanOf(), rename)

        assertTrue(model.withinLimits)                  // collisions are not a "limit" issue
        assertTrue(model.warnings.any { it.contains("Cafe.mp3") })
    }

    @Test fun `empty plan is within limits with nothing to do`() {
        val model = PreviewModelBuilder.build(scanOf(), RenamePlan(emptyList(), emptyList()))
        assertTrue(model.playlists.isEmpty())
        assertTrue(model.renames.isEmpty())
        assertTrue(model.warnings.isEmpty())
        assertTrue(model.withinLimits)
        assertEquals(0, model.totalTracks)
    }
}
