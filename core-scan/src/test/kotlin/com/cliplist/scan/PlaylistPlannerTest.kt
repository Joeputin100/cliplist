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

    @Test fun `exactly 1000 tracks - no warning`() {
        val files = (1..1000).map { fakeFile("track$it.mp3") }.toTypedArray()
        val volume = FakeVolume(fakeDir("AtLimit", *files))
        val plan = planner.plan(volume, opts)
        assertTrue(plan.warnings.filterIsInstance<PlanWarning.TooManyTracksInFolder>().isEmpty())
    }

    @Test fun `exactly 50 playlists - no warning`() {
        val subfolders = (1..50).map { fakeDir("F$it", fakeFile("t.mp3")) }.toTypedArray()
        val volume = FakeVolume(fakeDir("Root", *subfolders))
        val plan = planner.plan(volume, ScanOptions(recursive = true, alphabetize = false))
        assertTrue(plan.warnings.filterIsInstance<PlanWarning.TooManyPlaylists>().isEmpty())
    }
}
