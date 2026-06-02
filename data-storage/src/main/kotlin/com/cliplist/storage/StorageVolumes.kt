package com.cliplist.storage

import android.content.Context
import android.net.Uri
import com.cliplist.scan.FileStorageVolume
import com.cliplist.scan.StorageVolume
import java.io.File

/** Picks the [StorageVolume] implementation by URI scheme: `content://` → SAF, `file://` → filesystem. */
object StorageVolumes {
    fun forUri(context: Context, uri: Uri): StorageVolume =
        if (uri.scheme == "file") FileStorageVolume(File(requireNotNull(uri.path) { "file uri has no path: $uri" }))
        else SafTreeVolume(context, uri)
}
