package com.cliplist.format

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class M3uSerializerTest {

    @Test
    fun `caseA golden master - ascii filenames alphabetized`() {
        val filenames = listOf("03 - Gamma.mp3", "01 - Alpha.mp3", "02 - Beta.mp3")
        val actual = M3uSerializer.serialize(filenames, SerializerOptions(alphabetize = true))
        assertArrayEquals(golden("caseA-Rock.m3u"), actual)
    }

    @Test
    fun `caseB golden master - utf8 accented filenames`() {
        val filenames = listOf("Résumé.mp3", "Naïve.mp3")
        val actual = M3uSerializer.serialize(filenames, SerializerOptions(alphabetize = true))
        assertArrayEquals(golden("caseB-Cafe.m3u"), actual)
    }

    @Test
    fun `alphabetize false - preserves input order`() {
        val filenames = listOf("03 - Gamma.mp3", "01 - Alpha.mp3")
        val actual = M3uSerializer.serialize(filenames, SerializerOptions(alphabetize = false))
        val expected = "03 - Gamma.mp3\r\n01 - Alpha.mp3\r\n".toByteArray(Charsets.UTF_8)
        assertArrayEquals(expected, actual)
    }

    @Test
    fun `single entry ends with CRLF`() {
        val actual = M3uSerializer.serialize(listOf("song.mp3"), SerializerOptions(alphabetize = true))
        assertArrayEquals("song.mp3\r\n".toByteArray(Charsets.UTF_8), actual)
    }

    @Test
    fun `empty list produces empty ByteArray`() {
        val actual = M3uSerializer.serialize(emptyList(), SerializerOptions(alphabetize = true))
        assertArrayEquals(ByteArray(0), actual)
    }

    @Test
    fun `sort is case-sensitive code-point order - uppercase before lowercase`() {
        // Matches Java Collections.sort(List<String>) behaviour confirmed in FORMAT.md.
        // 'Z' (U+005A = 90) < 'a' (U+0061 = 97), so Zeppelin sorts before abe.
        val filenames = listOf("abe.mp3", "Zeppelin.mp3")
        val actual = M3uSerializer.serialize(filenames, SerializerOptions(alphabetize = true))
        val expected = "Zeppelin.mp3\r\nabe.mp3\r\n".toByteArray(Charsets.UTF_8)
        assertArrayEquals(expected, actual)
    }

    @Test
    fun `no EXTM3U or EXTINF header lines`() {
        val actual = M3uSerializer.serialize(listOf("song.mp3"), SerializerOptions(alphabetize = true))
        val text = actual.toString(Charsets.UTF_8)
        assert(!text.contains("#EXTM3U")) { "Must not contain #EXTM3U header" }
        assert(!text.contains("#EXTINF")) { "Must not contain #EXTINF metadata" }
    }

    @Test
    fun `output is UTF-8 - accented char uses multi-byte sequence`() {
        val actual = M3uSerializer.serialize(listOf("Café.mp3"), SerializerOptions(alphabetize = false))
        // UTF-8 for é is 0xC3 0xA9; Latin-1 would be lone 0xE9.
        assert(actual.contains(0xC3.toByte()) && actual.contains(0xA9.toByte())) {
            "é must be encoded as UTF-8 bytes C3 A9, not Latin-1 E9"
        }
    }

    private fun golden(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("golden/$name")!!.readBytes()
}
