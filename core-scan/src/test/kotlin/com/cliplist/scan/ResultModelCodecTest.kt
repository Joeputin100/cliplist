package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ResultModelCodecTest {
    @Test fun `round-trips a result`() {
        val m = ResultModel(
            playlistsWritten = 12, playlistsFailed = 1,
            renamesApplied = 3, renamesFailed = 0,
            errors = listOf("oops"),
            folders = listOf(ResultFolderRow("Rock", 24), ResultFolderRow("Jazz", 18)),
            destination = "SD card",
        )
        val back = ResultModelCodec.decode(ResultModelCodec.encode(m))
        assertEquals(m, back)
        assertEquals(42, back!!.totalTracks)   // computed property still works
        assertFalse(back.allSucceeded)
    }

    @Test fun `decode of null or garbage is null`() {
        assertNull(ResultModelCodec.decode(null))
        assertNull(ResultModelCodec.decode("not json"))
    }
}
