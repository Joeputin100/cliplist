package com.cliplist.scan

/** Pure heuristics over SAF identifiers. */
object StorageHeuristics {
    /**
     * A SAF tree document id looks like "<volume>:<path>", e.g. "primary:Music" (internal
     * shared storage) or "1A2B-3C4D:Music" (a removable SD card / USB volume). Internal
     * volumes use the reserved ids "primary"/"home"; anything else is removable.
     */
    fun isRemovableTreeDocumentId(treeDocumentId: String): Boolean {
        val volume = treeDocumentId.substringBefore(':')
        return !volume.equals("primary", ignoreCase = true) &&
               !volume.equals("home", ignoreCase = true)
    }
}
