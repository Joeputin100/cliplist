package com.cliplist.scan

import com.cliplist.format.M3uSerializer
import com.cliplist.format.SerializerOptions

data class WriteReport(val written: Int, val failed: Int, val errors: List<String>)

class PlaylistWriter(private val volume: StorageVolume) {
    fun execute(plan: ScanPlan): WriteReport {
        var written = 0
        var failed = 0
        val errors = mutableListOf<String>()

        for (fp in plan.folders) {
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
        }

        return WriteReport(written = written, failed = failed, errors = errors)
    }
}
