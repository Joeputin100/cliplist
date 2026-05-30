package com.cliplist.scan

data class FolderPlan(
    val folder: VolumeNode,
    val audioFiles: List<String>,
    val existingPlaylistName: String?,
    val playlistName: String
)

sealed class PlanWarning {
    data class TooManyTracksInFolder(val folderName: String, val count: Int) : PlanWarning()
    data class TooManyPlaylists(val count: Int) : PlanWarning()
}

data class ScanPlan(
    val folders: List<FolderPlan>,
    val warnings: List<PlanWarning>
)

class PlaylistPlanner {
    fun plan(volume: StorageVolume, options: ScanOptions): ScanPlan {
        val queue = ArrayDeque<VolumeNode>()
        queue.addLast(volume.rootNode)
        val folderPlans = mutableListOf<FolderPlan>()

        while (queue.isNotEmpty()) {
            val folder = queue.removeFirst()
            val children = volume.children(folder)

            val subfolders = children.filter { it.isDirectory }
            val audioFiles = children.filter {
                !it.isDirectory && AudioExtensions.isAudio(it.name, options.audioExtensions)
            }
            val existingPlaylist = children.find {
                !it.isDirectory && it.name.equals("${folder.name}.m3u", ignoreCase = true)
            }

            if (options.recursive) subfolders.forEach { queue.addLast(it) }

            if (audioFiles.isNotEmpty()) {
                val fileNames = audioFiles.map { it.name }
                    .let { if (options.alphabetize) it.sorted() else it }
                folderPlans.add(FolderPlan(
                    folder = folder,
                    audioFiles = fileNames,
                    existingPlaylistName = existingPlaylist?.name,
                    playlistName = "${folder.name}.m3u"
                ))
            }
        }

        val warnings = mutableListOf<PlanWarning>()
        folderPlans.forEach { fp ->
            if (fp.audioFiles.size > 1000)
                warnings.add(PlanWarning.TooManyTracksInFolder(fp.folder.name, fp.audioFiles.size))
        }
        if (folderPlans.size > 50) warnings.add(PlanWarning.TooManyPlaylists(folderPlans.size))

        return ScanPlan(folders = folderPlans, warnings = warnings)
    }
}
