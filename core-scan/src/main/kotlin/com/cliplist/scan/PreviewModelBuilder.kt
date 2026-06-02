package com.cliplist.scan

/** Turns the engine outputs into a flat, display-ready [PreviewModel]. Pure; no I/O. */
object PreviewModelBuilder {
    fun build(
        scan: ScanPlan,
        rename: RenamePlan,
        totalDurationMs: Long = 0L,
        unreadable: List<String> = emptyList(),
    ): PreviewModel {
        val playlists = scan.folders.map { fp ->
            PlaylistRow(
                folderName = fp.folder.name,
                trackCount = fp.audioFiles.size,
                playlistName = fp.playlistName,
                action = if (fp.existingPlaylistName != null) PlaylistAction.REPLACE else PlaylistAction.NEW
            )
        }
        val renames = rename.ops.map { op ->
            RenameRow(op.parentPath, op.oldName, op.newName, op.isDirectory)
        }
        val warnings = buildList {
            scan.warnings.forEach { w ->
                when (w) {
                    is PlanWarning.TooManyTracksInFolder ->
                        add("\"${w.folderName}\" has ${w.count} tracks (Clip Sport max is 1000).")
                    is PlanWarning.TooManyPlaylists ->
                        add("${w.count} playlists — Clip Sport handles about 50.")
                }
            }
            rename.collisions.forEach { c ->
                val where = c.parentPath.ifEmpty { "the root folder" }
                add("Skipped in $where: ${c.sources.joinToString(", ")} → \"${c.targetName}\" would collide.")
            }
        }
        return PreviewModel(
            playlists = playlists,
            totalTracks = scan.folders.sumOf { it.audioFiles.size },
            renames = renames,
            warnings = warnings,
            withinLimits = scan.warnings.isEmpty(),
            totalDurationMs = totalDurationMs,
            unreadable = unreadable
        )
    }
}
