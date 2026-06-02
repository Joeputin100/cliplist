package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MetadataDiffTest {
    private fun cacheOf(vararg t: TrackMeta) = FolderMetaCache(tracks = t.toList())

    @Test fun `unchanged files are reused, changed and new are probed`() {
        val cache = cacheOf(
            TrackMeta("same.mp3", 10, 20, 5000, true),
            TrackMeta("changed.mp3", 10, 20, 7000, true),
        )
        val inv = listOf(
            FileStat("same.mp3", 10, 20),    // identical -> reuse
            FileStat("changed.mp3", 10, 99), // mtime changed -> probe
            FileStat("new.mp3", 1, 2),       // new -> probe
        )
        val r = MetadataDiff.compute(inv, cache)
        assertEquals(setOf("same.mp3"), r.reusable.keys)
        assertEquals(listOf("changed.mp3", "new.mp3"), r.toProbe.map { it.name })
    }

    @Test fun `no cache means probe everything`() {
        val r = MetadataDiff.compute(listOf(FileStat("a.mp3", 1, 2)), null)
        assertTrue(r.reusable.isEmpty())
        assertEquals(1, r.toProbe.size)
    }
}
