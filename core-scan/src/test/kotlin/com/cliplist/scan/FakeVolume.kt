package com.cliplist.scan

class FakeVolume(root: FakeNode) : StorageVolume {
    override val rootNode: VolumeNode = root
    val writtenFiles = mutableMapOf<String, ByteArray>()   // "folderName/fileName" -> bytes
    val writtenMimes = mutableMapOf<String, String>()      // "folderName/fileName" -> mimeType
    val deletedFiles = mutableListOf<String>()             // "folderName/fileName"
    val failFiles = mutableSetOf<String>()                 // keys that return Failure on writeFile
    val renames = mutableListOf<Pair<String, String>>()    // (oldName, newName) in call order
    val renameFailNames = mutableSetOf<String>()           // current name -> force a rename Failure

    override fun children(node: VolumeNode): List<VolumeNode> =
        (node as FakeNode).children.toList()

    fun findFile(directory: VolumeNode, fileName: String): VolumeNode? =
        (directory as FakeNode).children.find { it.name.equals(fileName, ignoreCase = true) }

    override fun writeFile(directory: VolumeNode, name: String, content: ByteArray, mimeType: String): VolumeWriteResult {
        val key = "${directory.name}/$name"
        if (key in failFiles) return VolumeWriteResult.Failure("simulated write failure for $key")
        writtenFiles[key] = content
        writtenMimes[key] = mimeType
        return VolumeWriteResult.Success
    }

    override fun readFile(directory: VolumeNode, fileName: String): ByteArray? =
        writtenFiles["${directory.name}/$fileName"]

    override fun deleteFile(directory: VolumeNode, fileName: String): Boolean {
        deletedFiles.add("${directory.name}/$fileName")
        return true
    }

    override fun renameNode(node: VolumeNode, newName: String): RenameOutcome {
        node as FakeNode
        if (node.name in renameFailNames)
            return RenameOutcome.Failure("simulated rename failure for ${node.name}")
        renames.add(node.name to newName)
        node.name = newName
        return RenameOutcome.Renamed(node)
    }
}

data class FakeNode(
    override var name: String,
    override val isDirectory: Boolean,
    val children: MutableList<FakeNode> = mutableListOf(),
    override val size: Long = 0,
    override val lastModified: Long = 0,
) : VolumeNode

fun fakeDir(name: String, vararg children: FakeNode): FakeNode =
    FakeNode(name, isDirectory = true, children = children.toMutableList())

fun fakeFile(name: String, size: Long = 0, lastModified: Long = 0): FakeNode =
    FakeNode(name, isDirectory = false, size = size, lastModified = lastModified)
