package com.cliplist.scan
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StorageVolumeReadTest {
    @Test fun `readFile returns written bytes, null when absent`() {
        val root = fakeDir("Rock")
        val volume = FakeVolume(root)
        assertNull(volume.readFile(root, "x.json"))
        volume.writeFile(root, "x.json", "hi".toByteArray(), "application/json")
        assertEquals("hi", volume.readFile(root, "x.json")!!.toString(Charsets.UTF_8))
    }
    @Test fun `nodes carry size and lastModified`() {
        val f = fakeFile("a.mp3", size = 123L, lastModified = 456L)
        assertEquals(123L, f.size); assertEquals(456L, f.lastModified)
        assertEquals(0L, fakeDir("D").size)  // dirs default 0
    }
}
