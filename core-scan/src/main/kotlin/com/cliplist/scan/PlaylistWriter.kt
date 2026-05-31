package com.cliplist.scan

import com.cliplist.format.M3uSerializer
import com.cliplist.format.SerializerOptions

data class WriteReport(val written: Int, val failed: Int, val errors: List<String>)

class PlaylistWriter(private val volume: StorageVolume) {
    /**
     * Writes one playlist per folder. [onProgress] is invoked after each folder with
     * (foldersDone, totalFolders) so the UI can show live progress. Default is a no-op.
     */
    fun execute(plan: ScanPlan, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): WriteReport {
        var written = 0
        var failed = 0
        val errors = mutableListOf<String>()
        val total = plan.folders.size

        plan.folders.forEachIndexed { index, fp ->
            fp.existingPlaylistName?.let { volume.deleteFile(fp.folder, it) }
            // Files already sorted by planner; pass alphabetize=false to preserve order.
            val bytes = M3uSerializer.serialize(fp.audioFiles, SerializerOptions(alphabetize = false))
            when (val result = volume.writeFile(fp.folder, fp.playlistName, bytes)) {
                is VolumeWriteResult.Success -> written++
                is VolumeWriteResult.Failure -> {
                    failed++
                    errors.add("${fp.folder.name}/${fp.playlistName}: ${result.message}")
                }
            }
            onProgress(index + 1, total)
        }

        return WriteReport(written = written, failed = failed, errors = errors)
    }
}
