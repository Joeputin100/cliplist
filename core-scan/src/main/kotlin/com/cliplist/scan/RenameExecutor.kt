package com.cliplist.scan

/** A rename that was actually applied; keeps the POST-rename node so undo can target it. */
data class AppliedRename(
    val node: VolumeNode,
    val parentPath: String,
    val oldName: String,
    val newName: String
)

data class RenameFailure(val op: RenameOp, val message: String)

data class RenameExecution(
    val applied: List<AppliedRename>,
    val failed: List<RenameFailure>
)

data class UndoResult(val reverted: Int, val failed: List<RenameFailure>)

class RenameExecutor(private val volume: StorageVolume) {
    /** Applies plan ops in order (already deepest-first). Never throws; collects failures. */
    fun execute(plan: RenamePlan): RenameExecution {
        val applied = mutableListOf<AppliedRename>()
        val failed = mutableListOf<RenameFailure>()
        for (op in plan.ops) {
            when (val r = volume.renameNode(op.node, op.newName)) {
                is RenameOutcome.Renamed ->
                    applied.add(AppliedRename(r.node, op.parentPath, op.oldName, op.newName))
                is RenameOutcome.Failure ->
                    failed.add(RenameFailure(op, r.message))
            }
        }
        return RenameExecution(applied, failed)
    }
}
