package com.cliplist.scan

/**
 * A [ScanPlan] with unreadable files excluded, plus the aggregate real listening time and the names
 * of files that couldn't be read. Unreadable files are dropped from playlists but never deleted.
 */
data class AnalyzedScan(
    val plan: ScanPlan,
    val totalDurationMs: Long,
    val unreadable: List<String>,
)

/**
 * Runs the per-folder metadata pass over a [ScanPlan]: probes each folder's audio (reusing the
 * mpc-metadata.json cache via [FolderMetadataAnalyzer]), filters every folder's audioFiles down to
 * the readable ones, and sums the real durations. Folders left with no readable audio are dropped.
 */
object MetadataPass {
    fun run(
        volume: StorageVolume,
        probe: AudioProbe,
        scanPlan: ScanPlan,
        audioExtensions: Set<String>,
    ): AnalyzedScan {
        val analyzer = FolderMetadataAnalyzer(volume, probe)
        var totalDurationMs = 0L
        val unreadable = mutableListOf<String>()
        val folders = scanPlan.folders.mapNotNull { fp ->
            val audioNodes = volume.children(fp.folder).filter {
                !it.isDirectory && AudioExtensions.isAudio(it.name, audioExtensions)
            }
            val analysis = analyzer.analyze(fp.folder, audioNodes)
            totalDurationMs += analysis.totalDurationMs
            unreadable += analysis.unreadable
            val readable = analysis.readableFiles.toSet()
            val keptFiles = fp.audioFiles.filter { it in readable }   // preserve plan order
            if (keptFiles.isEmpty()) null else fp.copy(audioFiles = keptFiles)
        }
        return AnalyzedScan(scanPlan.copy(folders = folders), totalDurationMs, unreadable)
    }
}
