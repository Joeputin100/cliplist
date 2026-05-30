package com.cliplist.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FilenameSanitizerTest {

    // ── Plain ASCII ──────────────────────────────────────────────────────
    @Test fun `plain ASCII filename is unchanged`() = check("hello.mp3", "hello.mp3")
    @Test fun `plain ASCII folder is unchanged`() = check("Rock", "Rock", ext = false)

    // ── Accented characters (NFD decompose + strip combining marks) ──────
    @Test fun `e-acute becomes e`() = check("Resume.mp3", "Résumé.mp3")
    @Test fun `i-diaeresis becomes i`() = check("Naive.mp3", "Naïve.mp3")
    @Test fun `accented folder name`() = check("Cafe", "Café", ext = false)
    @Test fun `German umlaut u becomes u`() = check("Uber.mp3", "Über.mp3")

    // ── Smart punctuation (pre-mapped before NFD) ────────────────────────
    @Test fun `right single quote becomes ASCII apostrophe`() =
        check("it's alright.mp3", "it’s alright.mp3")
    @Test fun `left single quote becomes ASCII apostrophe`() =
        check("it's alright.mp3", "it‘s alright.mp3")
    @Test fun `curly double quotes stripped (double-quote is FAT32-illegal)`() =
        check("hello.mp3", "“hello”.mp3")
    @Test fun `em dash becomes hyphen`() = check("AC-DC.mp3", "AC—DC.mp3")
    @Test fun `en dash becomes hyphen`() = check("A-B.mp3", "A–B.mp3")

    // ── Emoji (non-decomposable non-ASCII, stripped after NFD) ───────────
    @Test fun `emoji stripped from prefix`() = check("song.mp3", "🎵song.mp3")
    @Test fun `all-emoji stem becomes underscore`() =
        check("_.mp3", "🎵🎶.mp3")

    // ── FAT32-illegal characters ──────────────────────────────────────────
    @Test fun `pipe removed`() = check("ACDC.mp3", "AC|DC.mp3")
    @Test fun `colon removed`() = check("Track 1.mp3", "Track: 1.mp3")
    @Test fun `question mark removed`() = check("Why.mp3", "Why?.mp3")
    @Test fun `asterisk removed`() = check("star.mp3", "*star.mp3")
    @Test fun `double-quote removed`() = check("hello.mp3", "\"hello\".mp3")
    @Test fun `angle brackets removed`() = check("hello.mp3", "<hello>.mp3")
    @Test fun `backslash removed`() = check("AB.mp3", "A\\B.mp3")
    @Test fun `forward-slash removed`() = check("AB.mp3", "A/B.mp3")

    // ── Whitespace ────────────────────────────────────────────────────────
    @Test fun `multiple spaces collapsed to one`() = check("a b.mp3", "a   b.mp3")
    @Test fun `leading space trimmed`() = check("song.mp3", " song.mp3")
    @Test fun `trailing space trimmed`() = check("song.mp3", "song .mp3")

    // ── Reserved Windows/FAT32 device names (case-insensitive) ───────────
    @Test fun `CON reserved - gets underscore suffix`() = check("CON_.mp3", "CON.mp3")
    @Test fun `con lowercase also reserved`() = check("con_.mp3", "con.mp3")
    @Test fun `NUL reserved`() = check("NUL_.mp3", "NUL.mp3")
    @Test fun `AUX reserved`() = check("AUX_.mp3", "AUX.mp3")
    @Test fun `PRN reserved`() = check("PRN_.mp3", "PRN.mp3")
    @Test fun `COM1 reserved`() = check("COM1_.mp3", "COM1.mp3")
    @Test fun `LPT9 reserved`() = check("LPT9_.mp3", "LPT9.mp3")
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
    @Test fun `empty name returns underscore`() = check("_", "", ext = false)
    @Test fun `all-illegal chars in stem returns underscore`() = check("_.mp3", ":.mp3")
    @Test fun `leading dot stripped`() = check("hidden.mp3", ".hidden.mp3")

    private fun check(expected: String, input: String, ext: Boolean = true) =
        assertEquals(expected, FilenameSanitizer.sanitize(input, preserveExtension = ext))
}
