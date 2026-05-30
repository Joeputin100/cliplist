package com.cliplist.scan

interface VolumeNode {
    val name: String
    val isDirectory: Boolean
}

sealed class VolumeWriteResult {
    object Success : VolumeWriteResult()
    data class Failure(val message: String) : VolumeWriteResult()
}

interface StorageVolume {
    val rootNode: VolumeNode
    fun children(node: VolumeNode): List<VolumeNode>
    fun writeFile(directory: VolumeNode, name: String, content: ByteArray): VolumeWriteResult
    fun deleteFile(directory: VolumeNode, fileName: String): Boolean
}
