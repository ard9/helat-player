package io.github.ard9.linguaplayer.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import io.github.ard9.linguaplayer.LinguaApp
import io.github.ard9.linguaplayer.stream.CachingProxy
import io.github.ard9.linguaplayer.stream.HttpUpstream
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import java.io.Closeable
import java.io.IOException
import java.security.MessageDigest

/**
 * A libVLC media plus what must stay open while it is used: the file descriptor of a local
 * file, or the caching proxy of an online film.
 */
class OpenedMedia(val media: Media, private val resource: Closeable?, val proxy: CachingProxy? = null) {
    fun close() {
        try {
            resource?.close()
            proxy?.close()
        } catch (e: IOException) {
            // Nothing useful to do.
        }
    }
}

/**
 * Opens local files (content://) through a file descriptor. Online films go through a
 * [CachingProxy] on the phone, so parts already downloaded are not downloaded again when
 * playback jumps back; if the proxy cannot start, the link is played directly.
 */
fun openVlcMedia(context: Context, libVlc: LibVLC, uri: Uri, useCache: Boolean = true): OpenedMedia {
    if (uri.scheme == "content") {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Cannot open the file")
        return OpenedMedia(Media(libVlc, descriptor.fileDescriptor), descriptor)
    }
    val proxy = if (useCache) openStreamProxy(context, uri) else null
    val media = Media(libVlc, proxy?.let { Uri.parse(it.url) } ?: uri)
    media.addOption(":network-caching=1500")
    return OpenedMedia(media, null, proxy)
}

/**
 * A [CachingProxy] for an online film ([uri] http or https), sharing the kept parts with every
 * other reader of the same film; null for other addresses or if it cannot start (then read the
 * link directly). The caller closes it. [trustKnownLength]: see [CachingProxy].
 */
fun openStreamProxy(context: Context, uri: Uri, trustKnownLength: Boolean = false): CachingProxy? {
    if (uri.scheme != "http" && uri.scheme != "https") return null
    val app = context.applicationContext as? LinguaApp ?: return null
    return try {
        CachingProxy(
            upstream = HttpUpstream(uri.toString()),
            store = app.streamStore,
            key = sha256(uri.toString()),
            fileName = uri.lastPathSegment.orEmpty(),
            trustKnownLength = trustKnownLength,
        ).start()
    } catch (e: Exception) {
        Log.w("VlcMedia", "Reading the link directly; the cache could not start", e)
        null
    }
}

private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
