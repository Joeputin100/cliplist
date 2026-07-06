package com.cliplist.app.functions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.service.AppFunction

/**
 * App Functions that My Playlist Creator exposes to on-device assistants such as Gemini.
 *
 * The class takes no constructor arguments, so the App Functions runtime can instantiate it
 * directly — no [androidx.appfunctions.service.AppFunctionConfiguration.Provider] registration is
 * required for this minimal surface.
 */
class PlaylistAppFunctions {

    /**
     * Summarize, in friendly language, the playlist this app would build from a music folder.
     *
     * Use this when the user asks what My Playlist Creator would do with one of their folders —
     * for example "what playlist would you make from my Road Trip folder?". The app names each
     * playlist after its folder and writes an `.m3u` file the SanDisk Clip Sport can read.
     *
     * @param appFunctionContext runtime context supplied by the App Functions host.
     * @param folderName the name of the music folder the user is asking about.
     * @param trackCount how many songs are currently in that folder.
     * @return a single, human-readable sentence describing the playlist that would be created.
     */
    @AppFunction(isDescribedByKDoc = true)
    fun summarizePlaylistCreation(
        appFunctionContext: AppFunctionContext,
        folderName: String,
        trackCount: Int,
    ): String = summarizePlaylist(folderName, trackCount)
}

/**
 * Pure, framework-free logic behind [PlaylistAppFunctions.summarizePlaylistCreation].
 *
 * Kept separate from the annotated entry point so it can be unit-tested on the JVM without the
 * App Functions runtime. Mirrors the app's real behavior: the playlist is named after the folder
 * with an `.m3u` extension.
 */
internal fun summarizePlaylist(folderName: String, trackCount: Int): String {
    val name = folderName.trim()
    return when {
        trackCount <= 0 ->
            "The folder \"$name\" doesn't have any songs yet, so there's nothing to put on a playlist."
        trackCount == 1 ->
            "I'll turn the folder \"$name\" into a playlist called \"$name.m3u\" with 1 song, ready for your Clip Sport."
        else ->
            "I'll turn the folder \"$name\" into a playlist called \"$name.m3u\" with $trackCount songs, ready for your Clip Sport."
    }
}
