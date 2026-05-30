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

    @Test fun `write failure increments failed count and records error`() {
        val root = fakeDir("Rock", fakeFile("song.mp3"))
        val volume = FakeVolume(root).also { it.failFiles.add("Rock/Rock.m3u") }
        val plan = ScanPlan(
            folders = listOf(FolderPlan(root, listOf("song.mp3"), null, "Rock.m3u")),
            warnings = emptyList()
        )

        val report = PlaylistWriter(volume).execute(plan)

        assertEquals(0, report.written)
        assertEquals(1, report.failed)
        assertTrue(report.errors.any { "Rock/Rock.m3u" in it })
    }
}
