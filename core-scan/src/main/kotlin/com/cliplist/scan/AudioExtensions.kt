package com.cliplist.scan

object AudioExtensions {
    val DEFAULT: Set<String> = setOf(
        "mp3", "ogg", "oga", "wav", "m4a", "aac", "alac", "flac",
        "wma", "ac3", "opus", "aa", "aax"
    )

    fun isAudio(filename: String, extensions: Set<String> = DEFAULT): Boolean {
        val dot = filename.lastIndexOf('.')
        if (dot <= 0) return false  // dot at 0 means hidden file like ".mp3", not an audio track
        return filename.substring(dot + 1).lowercase() in extensions
    }
}
