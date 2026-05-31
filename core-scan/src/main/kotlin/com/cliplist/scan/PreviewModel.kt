package com.cliplist.scan

/** Whether a folder's playlist is created fresh or replaces an existing one. */
enum class PlaylistAction { NEW, REPLACE }

data class PlaylistRow(
    val folderName: String,
    val trackCount: Int,
    val playlistName: String,
    val action: PlaylistAction
)

data class RenameRow(
    val parentPath: String,
    val oldName: String,
    val newName: String,
    val isDirectory: Boolean
)

/** Everything the Preview screen renders — derived purely from a ScanPlan + RenamePlan. */
data class PreviewModel(
    val playlists: List<PlaylistRow>,
    val totalTracks: Int,
    val renames: List<RenameRow>,
    val warnings: List<String>,
    val withinLimits: Boolean
)
