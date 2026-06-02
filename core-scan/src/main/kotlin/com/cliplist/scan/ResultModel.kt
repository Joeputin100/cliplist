package com.cliplist.scan

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One folder that had playlists written, shown in the Results folder list. */
@Serializable
data class ResultFolderRow(val folderName: String, val trackCount: Int)

/** Final outcome of a generation run, shown on the Results screen. */
@Serializable
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

/** Serializes a [ResultModel] so a background worker can hand the rich result back to the UI
 *  across a process boundary (written to a file on success, read when the work completes). */
object ResultModelCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(model: ResultModel): String = json.encodeToString(model)
    fun decode(text: String?): ResultModel? = text?.let {
        runCatching { json.decodeFromString<ResultModel>(it) }.getOrNull()
    }
}
