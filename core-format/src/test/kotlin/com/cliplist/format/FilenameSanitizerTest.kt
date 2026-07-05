package com.cliplist.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FilenameSanitizerTest {

    // ── Plain ASCII ──────────────────────────────────────────────────────
    @Test fun `plain ASCII filename is unchanged`() = check("hello.mp3", "hello.mp3")
    @Test fun `plain ASCII folder is unchanged`() = check("Rock", "Rock", ext = false)
    @Test fun `letters digits and single spaces pass through`() =
        check("Track 01 Remix.mp3", "Track 01 Remix.mp3")

    // ── Accented characters (decompose + strip combining marks) ──────────
    @Test fun `e-acute becomes e`() = check("Resume.mp3", "Résumé.mp3")
    @Test fun `i-diaeresis becomes i`() = check("Naive.mp3", "Naïve.mp3")
    @Test fun `accented folder name`() = check("Cafe", "Café", ext = false)
    @Test fun `German umlaut u becomes u`() = check("Uber.mp3", "Über.mp3")
    @Test fun `German eszett becomes ss`() = check("Strasse.mp3", "Straße.mp3")
    @Test fun `ae ligature becomes ae`() = check("AEon Flux.mp3", "Æon Flux.mp3")
    @Test fun `oe ligature becomes oe`() = check("coeur.mp3", "cœur.mp3")

    // ── Word substitutions ────────────────────────────────────────────────
    @Test fun `ampersand becomes and`() = check("Rock and Roll.mp3", "Rock & Roll.mp3")
    @Test fun `ampersand without spaces becomes and`() = check("A and B.mp3", "A&B.mp3")
    @Test fun `plus becomes plus`() = check("A plus B.mp3", "A+B.mp3")
    @Test fun `at-sign becomes at`() = check("Live at Wembley.mp3", "Live @ Wembley.mp3")

    // ── Apostrophes are dropped so contractions stay joined ──────────────
    @Test fun `straight apostrophe dropped`() = check("dont stop.mp3", "don't stop.mp3")
    @Test fun `right single quote dropped`() = check("its alright.mp3", "it’s alright.mp3")
    @Test fun `left single quote dropped`() = check("its alright.mp3", "it‘s alright.mp3")

    // ── All other symbols become spaces ───────────────────────────────────
    @Test fun `hyphen becomes space`() = check("Artist Title.mp3", "Artist - Title.mp3")
    @Test fun `em dash becomes space`() = check("AC DC.mp3", "AC—DC.mp3")
    @Test fun `en dash becomes space`() = check("A B.mp3", "A–B.mp3")
    @Test fun `underscore becomes space`() = check("a b.mp3", "a_b.mp3")
    @Test fun `brackets and parens removed`() = check("Live Remix.mp3", "(Live) [Remix].mp3")
    @Test fun `curly double quotes removed`() = check("hello.mp3", "“hello”.mp3")
    @Test fun `hash removed`() = check("Track 1.mp3", "Track #1.mp3")
    @Test fun `percent removed`() = check("100 pure.mp3", "100% pure.mp3")
    @Test fun `pipe becomes space`() = check("AC DC.mp3", "AC|DC.mp3")
    @Test fun `colon becomes space`() = check("Track 1.mp3", "Track: 1.mp3")
    @Test fun `question mark removed`() = check("Why.mp3", "Why?.mp3")
    @Test fun `asterisk removed`() = check("star.mp3", "*star.mp3")
    @Test fun `double-quote removed`() = check("hello.mp3", "\"hello\".mp3")
    @Test fun `angle brackets removed`() = check("hello.mp3", "<hello>.mp3")
    @Test fun `backslash becomes space`() = check("A B.mp3", "A\\B.mp3")
    @Test fun `forward-slash becomes space`() = check("A B.mp3", "A/B.mp3")
    @Test fun `dot inside stem becomes space`() = check("Mr Blue Sky.mp3", "Mr. Blue Sky.mp3")

    // ── Emoji (non-decomposable non-ASCII, deleted without a space) ──────
    @Test fun `emoji stripped from prefix`() = check("song.mp3", "🎵song.mp3")
    @Test fun `all-emoji stem becomes Untitled`() = check("Untitled.mp3", "🎵🎶.mp3")

    // ── Whitespace ────────────────────────────────────────────────────────
    @Test fun `multiple spaces collapsed to one`() = check("a b.mp3", "a   b.mp3")
    @Test fun `leading space trimmed`() = check("song.mp3", " song.mp3")
    @Test fun `trailing space trimmed`() = check("song.mp3", "song .mp3")

    // ── Reserved Windows/FAT32 device names (case-insensitive) ───────────
    @Test fun `CON reserved - gets digit suffix`() = check("CON1.mp3", "CON.mp3")
    @Test fun `con lowercase also reserved`() = check("con1.mp3", "con.mp3")
    @Test fun `NUL reserved`() = check("NUL1.mp3", "NUL.mp3")
    @Test fun `AUX reserved`() = check("AUX1.mp3", "AUX.mp3")
    @Test fun `PRN reserved`() = check("PRN1.mp3", "PRN.mp3")
    @Test fun `COM1 reserved`() = check("COM11.mp3", "COM1.mp3")
    @Test fun `LPT9 reserved`() = check("LPT91.mp3", "LPT9.mp3")
    @Test fun `CONSOLE is not reserved`() = check("CONSOLE.mp3", "CONSOLE.mp3")
    @Test fun `COM10 is not reserved`() = check("COM10.mp3", "COM10.mp3")

    // ── Extension / folder mode ───────────────────────────────────────────
    @Test fun `file extension is preserved unchanged`() = check("song.flac", "song.flac")
    @Test fun `preserveExtension false treats whole name as stem`() =
        check("Rock", "Rock", ext = false)

    // ── Length cap ───────────────────────────────────────────────────────
    @Test fun `stem over 200 chars is truncated`() {
        val long = "a".repeat(210)
        val result = FilenameSanitizer.sanitize("$long.mp3")
        assertEquals(200 + ".mp3".length, result.length)
        assert(result.endsWith(".mp3"))
    }

    // ── Edge cases ────────────────────────────────────────────────────────
    @Test fun `empty name returns Untitled`() = check("Untitled", "", ext = false)
    @Test fun `all-illegal chars in stem returns Untitled`() = check("Untitled.mp3", ":.mp3")
    @Test fun `leading dot stripped`() = check("hidden.mp3", ".hidden.mp3")
    @Test fun `combined mess is cleaned`() =
        check("01 Bjork and Moby Live at Roskilde.mp3", "01 - Björk & Moby (Live @ Roskilde!).mp3")

    private fun check(expected: String, input: String, ext: Boolean = true) =
        assertEquals(expected, FilenameSanitizer.sanitize(input, preserveExtension = ext))
}
