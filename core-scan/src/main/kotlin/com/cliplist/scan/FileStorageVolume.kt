package com.cliplist.scan

import java.io.File

/**
 * A filesystem-backed [StorageVolume] (java.io.File), used for `file://` folders. The app picks
 * this for file URIs and the SAF-backed volume for `content://` URIs (see StorageVolumes.forUri).
 * Primarily exercised by instrumented tests that stub the folder picker, but it's a self-contained,
 * pure-JVM implementation.
 */
class FileStorageVolume(private val root: File) : StorageVolume {

    override val rootNode: VolumeNode = FileNode(root)

    override fun children(node: VolumeNode): List<VolumeNode> =
        (node as FileNode).file.listFiles()?.map { FileNode(it) } ?: emptyList()

    override fun writeFile(
        directory: VolumeNode,
        name: String,
        content: ByteArray,
        mimeType: String,
    ): VolumeWriteResult {
        val dir = (directory as FileNode).file
        return try {
            if (!dir.exists()) dir.mkdirs()
            File(dir, name).writeBytes(content)
            VolumeWriteResult.Success
        } catch (e: Exception) {
            VolumeWriteResult.Failure(e.message ?: "write failed")
        }
    }

    override fun readFile(directory: VolumeNode, fileName: String): ByteArray? {
        val f = File((directory as FileNode).file, fileName)
        return if (f.isFile) f.readBytes() else null
    }

    override fun deleteFile(directory: VolumeNode, fileName: String): Boolean =
        File((directory as FileNode).file, fileName).delete()

    override fun renameNode(node: VolumeNode, newName: String): RenameOutcome {
        val f = (node as FileNode).file
        val target = File(f.parentFile, newName)
        return if (f.renameTo(target)) RenameOutcome.Renamed(FileNode(target))
        else RenameOutcome.Failure("rename failed: ${f.name} -> $newName")
    }

    /** The filesystem path of [node], so a file-based AudioProbe can open it. */
    fun pathOf(node: VolumeNode): String = (node as FileNode).file.absolutePath
}

private class FileNode(val file: File) : VolumeNode {
    override val name: String get() = file.name
    override val isDirectory: Boolean get() = file.isDirectory
    override val size: Long get() = if (file.isFile) file.length() else 0L
    override val lastModified: Long get() = file.lastModified()
}
