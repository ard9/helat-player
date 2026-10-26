package io.github.ard9.linguaplayer.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Something the player can open: a local file (content://) or a link (http/https). */
data class PlaybackSource(val uri: Uri, val title: String)

object SourceNames {
    /** Best-effort human readable name for a media URI. */
    fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            } catch (e: Exception) {
                // Permission may have expired; fall back to the URI itself.
            }
        }
        return uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: uri.toString()
    }
}
