package com.cliplist.scan

data class ProbeResult(val durationMs: Long, val readable: Boolean)

/** Reads one audio file's duration/readability. Real impl uses MediaMetadataRetriever (Android). */
interface AudioProbe {
    fun probe(node: VolumeNode): ProbeResult
}

data class FolderAnalysis(
    val readableFiles: List<String>,
    val totalDurationMs: Long,
    val unreadable: List<String>,
)

/**
 * Per-folder metadata pass: reuse cached durations for unchanged files, probe the rest, and keep
 * mpc-metadata.json up to date. Unreadable files are excluded from [FolderAnalysis.readableFiles]
 * (they're auto-skipped from the playlist) but never deleted.
 */
class FolderMetadataAnalyzer(
    private val volume: StorageVolume,
    private val probe: AudioProbe,
) {
    companion object { const val CACHE_NAME = "mpc-metadata.json" }

    fun analyze(folder: VolumeNode, audioNodes: List<VolumeNode>): FolderAnalysis {
        val inventory = audioNodes.map { FileStat(it.name, it.size, it.lastModified) }
        val cache = MetadataCacheCodec.decode(volume.readFile(folder, CACHE_NAME))
        val diff = MetadataDiff.compute(inventory, cache)

        val nodeByName = audioNodes.associateBy { it.name }
        val fresh = diff.toProbe.associate { fs ->
            val r = probe.probe(nodeByName.getValue(fs.name))
            fs.name to TrackMeta(fs.name, fs.size, fs.lastModified, r.durationMs, r.readable)
        }
        // Merge in listing order.
        val merged = audioNodes.map { n -> diff.reusable[n.name] ?: fresh.getValue(n.name) }

        // Rewrite the cache only when something actually changed (keeps re-runs write-free).
        val changed = cache == null ||
            diff.toProbe.isNotEmpty() ||
            cache.tracks.map { it.name } != merged.map { it.name }
        if (changed) {
            volume.writeFile(
                folder,
                CACHE_NAME,
                MetadataCacheCodec.encode(FolderMetaCache(tracks = merged)),
                "application/json",
            )
        }

        val readable = merged.filter { it.readable }
        return FolderAnalysis(
            readableFiles = readable.map { it.name },
            totalDurationMs = readable.sumOf { it.durationMs },
            unreadable = merged.filterNot { it.readable }.map { it.name },
        )
    }
}
