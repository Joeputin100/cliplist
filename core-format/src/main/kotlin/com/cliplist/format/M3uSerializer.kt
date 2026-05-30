package com.cliplist.format

import java.nio.charset.StandardCharsets

data class SerializerOptions(val alphabetize: Boolean)

object M3uSerializer {
    // Byte-exact Clip Sport format per reference/FORMAT.md:
    // UTF-8, no BOM, no headers, bare filenames, CRLF after every entry including the last.
    fun serialize(filenames: List<String>, options: SerializerOptions): ByteArray {
        val ordered = if (options.alphabetize) filenames.sorted() else filenames
        return ordered.joinToString(separator = "") { "$it\r\n" }
            .toByteArray(StandardCharsets.UTF_8)
    }
}
