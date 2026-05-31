package com.cliplist.scan

/**
 * Computes the file/folder renames implied by [RenameOptions] over the whole tree under the
 * volume root. Walks recursively (cleaning applies to the entire selected folder). Detects
 * collisions — names that would clash inside one directory — and EXCLUDES them so a rename
 * never overwrites another file. Ops are ordered deepest-first so a directory is only renamed
 * after its contents.
 */
class RenamePlanner {
    private data class Walked(val dir: VolumeNode, val path: String, val depth: Int)

    fun plan(volume: StorageVolume, options: RenameOptions): RenamePlan {
        val ops = mutableListOf<RenameOp>()
        val collisions = mutableListOf<RenameCollision>()

        val queue = ArrayDeque<Walked>()
        queue.addLast(Walked(volume.rootNode, path = "", depth = 0))

        while (queue.isNotEmpty()) {
            val (dir, dirPath, depth) = queue.removeFirst()
            val children = volume.children(dir)

            // Always recurse into subdirectories — cleaning is recursive over the selected tree.
            children.filter { it.isDirectory }.forEach { sub ->
                val childPath = if (dirPath.isEmpty()) sub.name else "$dirPath/${sub.name}"
                queue.addLast(Walked(sub, childPath, depth + 1))
            }

            // Desired new name per child (null = leave alone).
            val desired: Map<VolumeNode, String?> =
                children.associateWith { RenameRules.desiredName(it, options) }
            // What each child's name WOULD be after planning, lowercased (FAT32 is case-insensitive).
            val finalCounts: Map<String, Int> =
                children.groupingBy { (desired[it] ?: it.name).lowercase() }.eachCount()

            val excluded = linkedMapOf<String, MutableList<String>>() // target -> source oldNames
            for (child in children) {
                val newName = desired[child] ?: continue
                if (finalCounts.getValue(newName.lowercase()) > 1) {
                    excluded.getOrPut(newName) { mutableListOf() }.add(child.name)
                } else {
                    ops.add(RenameOp(child, dirPath, child.name, newName, depth + 1))
                }
            }
            excluded.forEach { (target, sources) ->
                collisions.add(RenameCollision(dirPath, target, sources))
            }
        }

        ops.sortByDescending { it.depth }
        return RenamePlan(ops, collisions)
    }
}
