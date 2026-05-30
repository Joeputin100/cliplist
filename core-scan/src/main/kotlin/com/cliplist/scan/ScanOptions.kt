package com.cliplist.scan

data class ScanOptions(
    val recursive: Boolean,
    val alphabetize: Boolean,
    val audioExtensions: Set<String> = AudioExtensions.DEFAULT
)
