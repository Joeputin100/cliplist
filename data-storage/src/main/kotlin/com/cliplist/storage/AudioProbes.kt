package com.cliplist.storage

import android.content.Context
import android.media.MediaMetadataRetriever
import com.cliplist.scan.AudioProbe
import com.cliplist.scan.FileStorageVolume
import com.cliplist.scan.ProbeResult
import com.cliplist.scan.StorageVolume
import com.cliplist.scan.VolumeNode

/** Reads a track's duration via MediaMetadataRetriever over a SAF document URI. */
class SafAudioProbe(private val context: Context, private val volume: SafTreeVolume) : AudioProbe {
    override fun probe(node: VolumeNode): ProbeResult =
        readDuration { it.setDataSource(context, volume.documentUri(node)) }
}

/** Reads a track's duration via MediaMetadataRetriever over a filesystem path (file:// volumes). */
class FileAudioProbe(private val volume: FileStorageVolume) : AudioProbe {
    override fun probe(node: VolumeNode): ProbeResult =
        readDuration { it.setDataSource(volume.pathOf(node)) }
}

/** Picks the [AudioProbe] matching the volume implementation. */
object AudioProbes {
    fun forVolume(context: Context, volume: StorageVolume): AudioProbe = when (volume) {
        is SafTreeVolume -> SafAudioProbe(context, volume)
        is FileStorageVolume -> FileAudioProbe(volume)
        else -> object : AudioProbe {
            override fun probe(node: VolumeNode) = ProbeResult(0, false)
        }
    }
}

/**
 * Shared duration read: success -> (durationMs, readable=true); any throw or missing/zero duration
 * -> (0, readable=false). Never throws — an unreadable file is data, not an error.
 */
private inline fun readDuration(setSource: (MediaMetadataRetriever) -> Unit): ProbeResult {
    val mmr = MediaMetadataRetriever()
    return try {
        setSource(mmr)
        val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        if (dur != null && dur > 0) ProbeResult(dur, true) else ProbeResult(0, false)
    } catch (e: Exception) {
        ProbeResult(0, false)
    } finally {
        try { mmr.release() } catch (ignored: Exception) { /* ignore */ }
    }
}
