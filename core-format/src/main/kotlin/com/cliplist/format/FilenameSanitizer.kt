package com.cliplist.format

import java.text.Normalizer

object FilenameSanitizer {

    private val FAT32_ILLEGAL = Regex("""[\\/:*?"<>|]""")
    private val CONTROL_CHARS = Regex("""[\x00-\x1F\x7F]""")
    private val MULTI_SPACE = Regex(" {2,}")
    private val RESERVED = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    )

    /**
     * Sanitize a single file or folder name segment to ASCII/FAT32-safe form.
     * @param preserveExtension true for files (splits on last dot, keeps extension intact);
     *                          false for folders (treats the whole name as the stem).
     */
    fun sanitize(name: String, preserveExtension: Boolean = true): String {
        val (stem, ext) = if (preserveExtension) splitExtension(name) else name to ""
        val cleaned = cleanStem(stem).ifEmpty { "_" }
        return if (ext.isNotEmpty()) "$cleaned.$ext" else cleaned
    }

    private fun splitExtension(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) to name.substring(dot + 1)
        else name to ""
    }

    private fun cleanStem(raw: String): String {
        // 1. Pre-map common punctuation substitutes before NFD decomposition.
        val preprocessed = raw
            .replace('‘', '\'')   // left single quotation mark → apostrophe
            .replace('’', '\'')   // right single quotation mark → apostrophe
            .replace('“', '"')    // left double quotation mark → " (FAT32 will strip it)
            .replace('”', '"')    // right double quotation mark → "
            .replace('—', '-')    // em dash → hyphen
            .replace('–', '-')    // en dash → hyphen

        // 2. NFD decompose to split accented chars (é → e + combining acute),
        //    then strip all non-ASCII (removes combining marks and undecomposable chars).
        val ascii = Normalizer.normalize(preprocessed, Normalizer.Form.NFD)
            .replace(Regex("[^\\x00-\\x7F]"), "")

        // 3. Remove FAT32-illegal chars and control chars.
        val clean = FAT32_ILLEGAL.replace(ascii, "")
            .let { CONTROL_CHARS.replace(it, "") }

        // 4. Strip leading dots (FAT32/Android hides files starting with ".").
        val noDots = clean.trimStart('.')

        // 5. Collapse multiple spaces and trim ends.
        val trimmed = MULTI_SPACE.replace(noDots, " ").trim()

        // 6. Cap stem at 200 chars (leaves room for dot + extension in the full name).
        val capped = if (trimmed.length > 200) trimmed.take(200).trimEnd() else trimmed

        // 7. Reserved device names get an underscore suffix to stay unique.
        return if (capped.uppercase() in RESERVED) "${capped}_" else capped
    }
}
