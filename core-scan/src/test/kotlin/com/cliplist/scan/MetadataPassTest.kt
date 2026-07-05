package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MetadataPassTest {
    private val exts = setOf("mp3")
    private fun planFor(vol: FakeVolume) =
        PlaylistPlanner().plan(vol, ScanOptions(recursive = true, alphabetize = true, audioExtensions = exts))

    @Test fun `excludes unreadable, sums durations, collects unreadable`() {
        val a = fakeFile("a.mp3", 1, 1)
        val bad = fakeFile("bad.mp3", 2, 2)
        val vol = FakeVolume(fakeDir("Rock", a, bad))
        val probe = FakeAudioProbe(mapOf("a.mp3" to ProbeResult(180000, true), "bad.mp3" to ProbeResult(0, false)))

        val r = MetadataPass.run(vol, probe, planFor(vol), exts)

        assertEquals(1, r.plan.folders.size)
        assertEquals(listOf("a.mp3"), r.plan.folders[0].audioFiles)   // bad.mp3 dropped
        assertEquals(180000, r.totalDurationMs)
        assertEquals(listOf("bad.mp3"), r.unreadable)
    }

    @Test fun `reports per-folder progress`() {
        val vol = FakeVolume(
            fakeDir("Music",
                fakeDir("Rock", fakeFile("a.mp3", 1, 1)),
                fakeDir("Jazz", fakeFile("b.mp3", 2, 2)))
        )
        val probe = FakeAudioProbe(mapOf("a.mp3" to ProbeResult(1000, true), "b.mp3" to ProbeResult(1000, true)))

        val ticks = mutableListOf<Pair<Int, Int>>()
        MetadataPass.run(vol, probe, planFor(vol), exts) { done, total -> ticks += done to total }

        assertEquals(listOf(1 to 2, 2 to 2), ticks)
    }

    @Test fun `a folder with no readable audio is dropped`() {
        val bad = fakeFile("bad.mp3", 2, 2)
        val vol = FakeVolume(fakeDir("Rock", bad))
        val probe = FakeAudioProbe(mapOf("bad.mp3" to ProbeResult(0, false)))

        val r = MetadataPass.run(vol, probe, planFor(vol), exts)

        assertTrue(r.plan.folders.isEmpty())
        assertEquals(0, r.totalDurationMs)
        assertEquals(listOf("bad.mp3"), r.unreadable)
    }
}
