package com.cliplist.scan
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MetadataCacheCodecTest {
    @Test fun `round-trips a cache`() {
        val c = FolderMetaCache(tracks = listOf(
            TrackMeta("a.mp3", 100, 200, 213000, true),
            TrackMeta("b.mp3", 50, 60, 0, false)))
        val back = MetadataCacheCodec.decode(MetadataCacheCodec.encode(c))
        assertEquals(c, back)
    }
    @Test fun `decode of null or garbage is null`() {
        assertNull(MetadataCacheCodec.decode(null))
        assertNull(MetadataCacheCodec.decode("not json".toByteArray()))
    }
}
