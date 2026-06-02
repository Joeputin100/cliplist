package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FolderMetadataAnalyzerTest {
    private fun setup(vararg files: FakeNode): Pair<FakeVolume, FakeNode> {
        val rock = fakeDir("Rock", *files)
        return FakeVolume(rock) to rock
    }

    @Test fun `first run probes all, writes cache, sums readable, lists unreadable`() {
        val a = fakeFile("a.mp3", 1, 1)
        val bad = fakeFile("bad.mp3", 2, 2)
        val (vol, rock) = setup(a, bad)
        val r = FolderMetadataAnalyzer(
            vol,
            FakeAudioProbe(mapOf("a.mp3" to ProbeResult(180000, true), "bad.mp3" to ProbeResult(0, false))),
        ).analyze(rock, listOf(a, bad))
        assertEquals(listOf("a.mp3"), r.readableFiles)
        assertEquals(180000, r.totalDurationMs)
        assertEquals(listOf("bad.mp3"), r.unreadable)
        assertNotNull(vol.writtenFiles["Rock/mpc-metadata.json"])
    }

    @Test fun `second run reuses cache, does not re-probe`() {
        val a = fakeFile("a.mp3", 1, 1)
        val b = fakeFile("b.mp3", 1, 1)
        val (vol, rock) = setup(a, b)
        val p1 = FakeAudioProbe(mapOf("a.mp3" to ProbeResult(180000, true), "b.mp3" to ProbeResult(120000, true)))
        FolderMetadataAnalyzer(vol, p1).analyze(rock, listOf(a, b))
        val p2 = FakeAudioProbe(emptyMap())
        val r = FolderMetadataAnalyzer(vol, p2).analyze(rock, listOf(a, b))
        assertTrue(p2.probed.isEmpty(), "unchanged files must not be re-probed")
        assertEquals(300000, r.totalDurationMs)
    }

    @Test fun `changed file is re-probed`() {
        val a = fakeFile("a.mp3", 1, 1)
        val (vol, rock) = setup(a)
        FolderMetadataAnalyzer(vol, FakeAudioProbe(mapOf("a.mp3" to ProbeResult(180000, true))))
            .analyze(rock, listOf(a))
        val a2 = fakeFile("a.mp3", 1, 999)   // mtime changed
        rock.children[0] = a2
        val p = FakeAudioProbe(mapOf("a.mp3" to ProbeResult(200000, true)))
        val r = FolderMetadataAnalyzer(vol, p).analyze(rock, listOf(a2))
        assertEquals(listOf("a.mp3"), p.probed)
        assertEquals(200000, r.totalDurationMs)
    }
}
