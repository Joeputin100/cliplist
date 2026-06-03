package com.cliplist.app.functions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure logic behind the App Function. These run on the JVM via
 * `:app:testDebugUnitTest` — they exercise [summarizePlaylist] directly, with no Android or
 * App Functions runtime involved.
 */
class PlaylistAppFunctionsTest {

    @Test
    fun `names the playlist after the folder and counts the tracks`() {
        val summary = summarizePlaylist("Road Trip", 24)
        assertTrue(summary, summary.contains("\"Road Trip.m3u\""))
        assertTrue(summary, summary.contains("24 songs"))
        assertTrue(summary, summary.contains("Clip Sport"))
    }

    @Test
    fun `uses the singular form for a single track`() {
        val summary = summarizePlaylist("Solo", 1)
        assertTrue(summary, summary.contains("1 song,"))
        assertFalse(summary, summary.contains("1 songs"))
    }

    @Test
    fun `explains there is nothing to do for an empty folder`() {
        val summary = summarizePlaylist("Empty", 0)
        assertTrue(summary, summary.contains("doesn't have any songs"))
    }

    @Test
    fun `trims surrounding whitespace from the folder name`() {
        val summary = summarizePlaylist("  Jazz  ", 3)
        assertTrue(summary, summary.contains("\"Jazz.m3u\""))
    }
}
