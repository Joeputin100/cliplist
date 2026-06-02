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
                        add("\"${w.folderName}\" has ${w.count} songs — that's more than the Clip Sport fits in one playlist (around 1,000), so some may not show up.")
                    is PlanWarning.TooManyPlaylists ->
                        add("You're making ${w.count} playlists — the Clip Sport works best with about 50 or fewer.")
                }
            }
            rename.collisions.forEach { c ->
                val where = c.parentPath.ifEmpty { "your main folder" }
                add("In $where, tidying these names would leave them all called \"${c.targetName}\", so we left them as they are: ${c.sources.joinToString(", ")}.")
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
