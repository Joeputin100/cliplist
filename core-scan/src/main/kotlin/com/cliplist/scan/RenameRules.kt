package com.cliplist.scan

import com.cliplist.format.FilenameSanitizer

/** The two cleaning toggles from the Home screen. */
data class RenameOptions(
    val cleanNames: Boolean,    // rewrite VISIBLE file & folder names to ASCII Clip-Sport-safe
    val renameHidden: Boolean   // un-hide dot-prefixed names (strip leading dot + sanitize)
)

object RenameRules {
    /**
     * The name [node] should be renamed to, or null to leave it untouched.
     *
     * Hidden (dot-prefixed) names are only touched when [RenameOptions.renameHidden];
     * visible names only when [RenameOptions.cleanNames]. FilenameSanitizer strips the leading
     * dot, so a hidden name becomes visible ("literal rename"). Returns null on a no-op
     * (the sanitized name equals the current name).
     */
    fun desiredName(node: VolumeNode, options: RenameOptions): String? {
        val hidden = node.name.startsWith(".")
        val active = if (hidden) options.renameHidden else options.cleanNames
        if (!active) return null
        val candidate = FilenameSanitizer.sanitize(node.name, preserveExtension = !node.isDirectory)
        return if (candidate != node.name) candidate else null
    }
}
