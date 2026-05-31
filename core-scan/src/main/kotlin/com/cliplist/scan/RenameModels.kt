package com.cliplist.scan

/** One planned rename of a file or folder. */
data class RenameOp(
    val node: VolumeNode,
    val parentPath: String,   // "/"-joined path of the containing directory ("" = root)
    val oldName: String,
    val newName: String,
    val depth: Int            // directory distance from root; ops apply deepest-first
) {
    val isDirectory: Boolean get() = node.isDirectory
}

/** Two or more names in one directory would resolve to the same target; excluded for safety. */
data class RenameCollision(
    val parentPath: String,
    val targetName: String,
    val sources: List<String>   // current names that wanted this target
)

/** Safe, deepest-first rename ops plus the collisions that were deliberately NOT planned. */
data class RenamePlan(
    val ops: List<RenameOp>,
    val collisions: List<RenameCollision>
)
