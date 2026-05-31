package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlaylistWriterProgressTest {

    @Test fun `onProgress is called once per folder with running done and total`() {
        val root = fakeDir("Music",
            fakeDir("Rock", fakeFile("a.mp3")),
            fakeDir("Jazz", fakeFile("b.mp3")))
        val volume = FakeVolume(root)
        val rock = root.children[0]; val jazz = root.children[1]
        val plan = ScanPlan(
            folders = listOf(
                FolderPlan(rock, listOf("a.mp3"), null, "Rock.m3u"),
                FolderPlan(jazz, listOf("b.mp3"), null, "Jazz.m3u")
            ),
            warnings = emptyList()
        )
        val seen = mutableListOf<Pair<Int, Int>>()

        val report = PlaylistWriter(volume).execute(plan) { done, total -> seen.add(done to total) }

        assertEquals(2, report.written)
        assertEquals(listOf(1 to 2, 2 to 2), seen)
    }

    @Test fun `default execute still works with no callback`() {
        val root = fakeDir("Rock", fakeFile("a.mp3"))
        val volume = FakeVolume(root)
        val plan = ScanPlan(listOf(FolderPlan(root, listOf("a.mp3"), null, "Rock.m3u")), emptyList())
        assertEquals(1, PlaylistWriter(volume).execute(plan).written)
    }
}
