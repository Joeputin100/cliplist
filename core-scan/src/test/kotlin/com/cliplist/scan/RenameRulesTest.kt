package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RenameRulesTest {
    private val all       = RenameOptions(cleanNames = true,  renameHidden = true)
    private val none      = RenameOptions(cleanNames = false, renameHidden = false)
    private val cleanOnly = RenameOptions(cleanNames = true,  renameHidden = false)
    private val hiddenOnly= RenameOptions(cleanNames = false, renameHidden = true)

    @Test fun `clean visible file when cleanNames on`() {
        assertEquals("Cafe.mp3", RenameRules.desiredName(fakeFile("Café.mp3"), cleanOnly))
    }
    @Test fun `already-clean file is a no-op`() {
        assertNull(RenameRules.desiredName(fakeFile("Song.mp3"), all))
    }
    @Test fun `visible file untouched when cleanNames off`() {
        assertNull(RenameRules.desiredName(fakeFile("Café.mp3"), hiddenOnly))
    }
    @Test fun `hidden file un-hidden and sanitized when renameHidden on`() {
        assertEquals("My Track.mp3", RenameRules.desiredName(fakeFile(".My Track.mp3"), hiddenOnly))
    }
    @Test fun `hidden file untouched when renameHidden off even if cleanNames on`() {
        assertNull(RenameRules.desiredName(fakeFile(".DS_Store"), cleanOnly))
    }
    @Test fun `folder name sanitized as a whole stem`() {
        assertEquals("Rock Roll", RenameRules.desiredName(fakeDir("Rock: Roll"), cleanOnly))
    }
    @Test fun `no options means no rename`() {
        assertNull(RenameRules.desiredName(fakeFile("Café.mp3"), none))
    }
    @Test fun `app's own metadata cache is never renamed`() {
        assertNull(RenameRules.desiredName(fakeFile("mpc-metadata.json"), all))
    }
    @Test fun `metadata cache is protected regardless of case (FAT32 is case-insensitive)`() {
        assertNull(RenameRules.desiredName(fakeFile("MPC-Metadata.JSON"), all))
    }
}
