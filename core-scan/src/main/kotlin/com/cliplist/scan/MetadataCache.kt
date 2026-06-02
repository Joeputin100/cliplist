package com.cliplist.scan

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TrackMeta(
    val name: String, val size: Long, val lastModified: Long,
    val durationMs: Long, val readable: Boolean,
)

@Serializable
data class FolderMetaCache(val schema: Int = 1, val tracks: List<TrackMeta> = emptyList())

object MetadataCacheCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(cache: FolderMetaCache): ByteArray =
        json.encodeToString(cache).toByteArray(Charsets.UTF_8)
    fun decode(bytes: ByteArray?): FolderMetaCache? = bytes?.let {
        runCatching { json.decodeFromString<FolderMetaCache>(it.toString(Charsets.UTF_8)) }.getOrNull()
    }
}
