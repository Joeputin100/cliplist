package com.cliplist.scan

interface VolumeNode {
    val name: String
    val isDirectory: Boolean
}

sealed class VolumeWriteResult {
    object Success : VolumeWriteResult()
    data class Failure(val message: String) : VolumeWriteResult()
}

/** Outcome of a rename. On success it carries the updated node — a node's identity/URI may
 *  change when renamed (SAF returns a new document URI), so callers must use the returned node. */
sealed class RenameOutcome {
    data class Renamed(val node: VolumeNode) : RenameOutcome()
    data class Failure(val message: String) : RenameOutcome()
}

interface StorageVolume {
    val rootNode: VolumeNode
    fun children(node: VolumeNode): List<VolumeNode>
    fun writeFile(directory: VolumeNode, name: String, content: ByteArray, mimeType: String): VolumeWriteResult
    fun deleteFile(directory: VolumeNode, fileName: String): Boolean
    fun renameNode(node: VolumeNode, newName: String): RenameOutcome
}
