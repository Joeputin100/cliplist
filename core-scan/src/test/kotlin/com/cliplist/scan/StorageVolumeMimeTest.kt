package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StorageVolumeMimeTest {

    @Test fun `writeFile records the given mime type`() {
        val root = fakeDir("Music")
        val volume = FakeVolume(root)
        volume.writeFile(root, "art.jpg", byteArrayOf(2), "image/jpeg")
        assertEquals("image/jpeg", volume.writtenMimes["Music/art.jpg"])
    }

    @Test fun `PlaylistWriter writes playlists with the m3u mime type`() {
        val root = fakeDir("Rock", fakeFile("a.mp3"))
        val volume = FakeVolume(root)
        val plan = ScanPlan(listOf(FolderPlan(root, listOf("a.mp3"), null, "Rock.m3u")), emptyList())
        PlaylistWriter(volume).execute(plan)
        assertEquals("audio/x-mpegurl", volume.writtenMimes["Rock/Rock.m3u"])
    }
}
