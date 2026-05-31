package com.cliplist.scan

/** One folder that had playlists written, shown in the Results folder list. */
data class ResultFolderRow(val folderName: String, val trackCount: Int)

/** Final outcome of a generation run, shown on the Results screen. */
data class ResultModel(
    val playlistsWritten: Int,
    val playlistsFailed: Int,
    val renamesApplied: Int,
    val renamesFailed: Int,
    val errors: List<String>,
    val folders: List<ResultFolderRow>,
    val destination: String,
) {
    val totalFailed: Int get() = playlistsFailed + renamesFailed
    val totalTracks: Int get() = folders.sumOf { it.trackCount }
    val allSucceeded: Boolean get() = totalFailed == 0
}

object ResultModelBuilder {
    fun build(write: WriteReport, rename: RenameExecution?, scan: ScanPlan, destination: String): ResultModel =
        ResultModel(
            playlistsWritten = write.written,
            playlistsFailed = write.failed,
            renamesApplied = rename?.applied?.size ?: 0,
            renamesFailed = rename?.failed?.size ?: 0,
            errors = write.errors + (rename?.failed?.map { "${it.op.oldName}: ${it.message}" } ?: emptyList()),
            folders = scan.folders.map { ResultFolderRow(it.folder.name, it.audioFiles.size) },
            destination = destination,
        )
}
