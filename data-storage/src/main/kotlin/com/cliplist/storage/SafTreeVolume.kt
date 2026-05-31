package com.cliplist.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.cliplist.scan.RenameOutcome
import com.cliplist.scan.StorageVolume
import com.cliplist.scan.VolumeNode
import com.cliplist.scan.VolumeWriteResult
import java.io.IOException

/**
 * SAF-backed StorageVolume. Uses bulk DocumentsContract queries for performance
 * (avoids the slow DocumentFile.listFiles() which does one IPC per child).
 *
 * The treeUri must have been granted via takePersistableUriPermission before use.
 */
class SafTreeVolume(
    private val context: Context,
    private val treeUri: Uri
) : StorageVolume {

    private inner class SafNode(
        val documentId: String,
        override val name: String,
        override val isDirectory: Boolean
    ) : VolumeNode

    override val rootNode: VolumeNode by lazy {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val name = context.contentResolver.query(
            docUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else rootId } ?: rootId
        SafNode(rootId, name, isDirectory = true)
    }

    override fun children(node: VolumeNode): List<VolumeNode> {
        node as SafNode
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, node.documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val result = mutableListOf<VolumeNode>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id   = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2) ?: ""
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                result.add(SafNode(id, name, isDir))
            }
        }
        return result
    }

    override fun writeFile(directory: VolumeNode, name: String, content: ByteArray, mimeType: String): VolumeWriteResult {
        directory as SafNode
        val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, directory.documentId)
        return try {
            val newUri = DocumentsContract.createDocument(
                context.contentResolver, dirUri, mimeType, name
            ) ?: return VolumeWriteResult.Failure("createDocument returned null for $name in ${directory.name}")
            context.contentResolver.openOutputStream(newUri, "wt")?.use { it.write(content) }
                ?: return VolumeWriteResult.Failure("openOutputStream returned null for $name")
            VolumeWriteResult.Success
        } catch (e: IOException) {
            VolumeWriteResult.Failure(e.message ?: "IOException writing $name")
        }
    }

    override fun deleteFile(directory: VolumeNode, fileName: String): Boolean {
        directory as SafNode
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directory.documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        var targetId: String? = null
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (name.equals(fileName, ignoreCase = true)) {
                    targetId = id
                    break
                }
            }
        }
        val id = targetId ?: return false
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: Exception) {
            Log.e("SafTreeVolume", "delete $fileName: ${e.message}")
            false
        }
    }

    override fun renameNode(node: VolumeNode, newName: String): RenameOutcome {
        node as SafNode
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, node.documentId)
        return try {
            val newUri = DocumentsContract.renameDocument(context.contentResolver, docUri, newName)
                ?: return RenameOutcome.Failure("renameDocument returned null for ${node.name}")
            // renameDocument may return a NEW document URI; derive the new id from it.
            RenameOutcome.Renamed(SafNode(DocumentsContract.getDocumentId(newUri), newName, node.isDirectory))
        } catch (e: Exception) {
            Log.e("SafTreeVolume", "rename ${node.name} -> $newName: ${e.message}")
            RenameOutcome.Failure(e.message ?: "rename failed for ${node.name}")
        }
    }
}
