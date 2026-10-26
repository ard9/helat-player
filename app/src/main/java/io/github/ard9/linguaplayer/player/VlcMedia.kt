package io.github.ard9.linguaplayer.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import java.io.IOException

/** A libVLC media plus the file descriptor that must stay open while it is used. */
class OpenedMedia(val media: Media, private val descriptor: ParcelFileDescriptor?) {
    fun close() {
        try {
            descriptor?.close()
        } catch (e: IOException) {
            // Nothing useful to do.
        }
    }
}

/** Opens local files (content://) through a file descriptor and links directly. */
fun openVlcMedia(context: Context, libVlc: LibVLC, uri: Uri): OpenedMedia {
    if (uri.scheme == "content") {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Cannot open the file")
        return OpenedMedia(Media(libVlc, descriptor.fileDescriptor), descriptor)
    }
    val media = Media(libVlc, uri)
    media.addOption(":network-caching=1500")
    return OpenedMedia(media, null)
}
