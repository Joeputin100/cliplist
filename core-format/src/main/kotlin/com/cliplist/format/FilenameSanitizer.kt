package com.cliplist.format

import java.text.Normalizer

object FilenameSanitizer {

    // Stems may contain ONLY ASCII letters, digits and single spaces — the SanDisk Clip Sport
    // chokes on symbol characters, so everything else is substituted, deleted or spaced out.
    private val APOSTROPHE = Regex("'")
    private val NON_ASCII = Regex("[^\\x00-\\x7F]")
    private val ASCII_SYMBOLS = Regex("[^A-Za-z0-9 ]")
    private val WHITESPACE = Regex("\\s+")
    private val RESERVED = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    )

    // Symbols that carry a word's worth of meaning are spelled out instead of dropped.
    private val WORD_SUBSTITUTIONS = listOf(
        "&" to " and ",
        "+" to " plus ",
        "@" to " at ",
    )

    /**
     * Sanitize a single file or folder name segment to plain-ASCII Clip-Sport-safe form.
     * @param preserveExtension true for files (splits on last dot, keeps extension intact);
     *                          false for folders (treats the whole name as the stem).
     */
    fun sanitize(name: String, preserveExtension: Boolean = true): String {
        val (stem, ext) = if (preserveExtension) splitExtension(name) else name to ""
        val cleaned = cleanStem(stem).ifEmpty { "Untitled" }
        return if (ext.isNotEmpty()) "$cleaned.$ext" else cleaned
    }

    private fun splitExtension(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) to name.substring(dot + 1)
        else name to ""
    }

    private fun cleanStem(raw: String): String {
        // 1. Pre-map smart punctuation whose ASCII cousin separates (or joins) words the same
        //    way — plain non-ASCII deletion in step 4 would otherwise glue "AC—DC" into "ACDC".
        val preprocessed = raw
            .replace('‘', '\'')   // left single quotation mark → apostrophe
            .replace('’', '\'')   // right single quotation mark → apostrophe
            .replace('“', '"')    // left double quotation mark → "
            .replace('”', '"')    // right double quotation mark → "
            .replace('—', '-')    // em dash → hyphen
            .replace('–', '-')    // en dash → hyphen
            // Ligature letters NFKD can't decompose (they'd be deleted as non-ASCII).
            .replace("ß", "ss")
            .replace("æ", "ae").replace("Æ", "AE")
            .replace("œ", "oe").replace("Œ", "OE")

        // 2. NFKD decompose: é → e + combining acute, fullwidth ＆ → &, ellipsis … → "...".
        val decomposed = Normalizer.normalize(preprocessed, Normalizer.Form.NFKD)

        // 3. Spell out meaning-bearing symbols, then drop apostrophes so contractions
        //    stay joined ("don't" → "dont").
        val substituted = WORD_SUBSTITUTIONS.fold(decomposed) { acc, (symbol, word) ->
            acc.replace(symbol, word)
        }.let { APOSTROPHE.replace(it, "") }

        // 4. Delete non-ASCII outright (combining marks, emoji) so accents merge ("Résumé" →
        //    "Resume"), then turn every remaining symbol/control char into a space so
        //    separators keep words apart ("AC/DC" → "AC DC").
        val asciiOnly = NON_ASCII.replace(substituted, "")
        val lettersAndDigits = ASCII_SYMBOLS.replace(asciiOnly, " ")

        // 5. Collapse whitespace runs and trim ends.
        val trimmed = WHITESPACE.replace(lettersAndDigits, " ").trim()

        // 6. Cap stem at 200 chars (leaves room for dot + extension in the full name).
        val capped = if (trimmed.length > 200) trimmed.take(200).trimEnd() else trimmed

        // 7. Reserved device names get a digit suffix to stay distinct.
        return if (capped.uppercase() in RESERVED) "${capped}1" else capped
    }
}
