package com.cliplist.scan

/** A file as seen in the current listing (name + size + last-modified — no file open required). */
data class FileStat(val name: String, val size: Long, val lastModified: Long)

data class MetadataDiffResult(
    val reusable: Map<String, TrackMeta>,
    val toProbe: List<FileStat>,
)

object MetadataDiff {
    /** A cached track is reusable iff name + size + lastModified all match; everything else is probed. */
    fun compute(inventory: List<FileStat>, cache: FolderMetaCache?): MetadataDiffResult {
        val cached = cache?.tracks?.associateBy { it.name } ?: emptyMap()
        val reusable = LinkedHashMap<String, TrackMeta>()
        val toProbe = mutableListOf<FileStat>()
        for (fs in inventory) {
            val c = cached[fs.name]
            if (c != null && c.size == fs.size && c.lastModified == fs.lastModified) reusable[fs.name] = c
            else toProbe.add(fs)
        }
        return MetadataDiffResult(reusable, toProbe)
    }
}
