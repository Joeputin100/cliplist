package com.cliplist.scan

/** Pure heuristics over SAF identifiers. */
object StorageHeuristics {

    /** The system documents provider that serves internal storage, SD cards and USB drives. */
    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /**
     * The FAT volume UUID (e.g. "1704-050E") of the removable volume a SAF tree lives on, or null
     * when the tree is not on removable media. A tree document id looks like "<volume>:<path>" —
     * internal shared storage uses the reserved volume ids "primary"/"home", while removable
     * volumes use their filesystem UUID. Anything served by a non-system provider (cloud drives,
     * network shares) is never removable media, whatever its document id looks like.
     */
    fun removableVolumeUuid(authority: String?, treeDocumentId: String): String? {
        if (authority != EXTERNAL_STORAGE_AUTHORITY) return null
        val volume = treeDocumentId.substringBefore(':')
        val internal = volume.equals("primary", ignoreCase = true) ||
            volume.equals("home", ignoreCase = true)
        return if (internal || volume.isEmpty()) null else volume
    }
}
