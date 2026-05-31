package com.cliplist.scan

/** Final outcome of a generation run, shown on the Results screen. */
data class ResultModel(
    val playlistsWritten: Int,
    val playlistsFailed: Int,
    val renamesApplied: Int,
    val renamesFailed: Int,
    val errors: List<String>,
) {
    val totalFailed: Int get() = playlistsFailed + renamesFailed
    val allSucceeded: Boolean get() = totalFailed == 0
}

object ResultModelBuilder {
    fun build(write: WriteReport, rename: RenameExecution?): ResultModel =
        ResultModel(
            playlistsWritten = write.written,
            playlistsFailed = write.failed,
            renamesApplied = rename?.applied?.size ?: 0,
            renamesFailed = rename?.failed?.size ?: 0,
            errors = write.errors + (rename?.failed?.map { "${it.op.oldName}: ${it.message}" } ?: emptyList()),
        )
}
