package io.github.ard9.linguaplayer.subtitle.mkv

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.github.ard9.linguaplayer.LinguaApp
import io.github.ard9.linguaplayer.stream.CachedByteSource
import io.github.ard9.linguaplayer.stream.streamKey
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Random access to the bytes of a media file, local or remote. */
interface ByteSource {
    /** True for network sources, where every byte read costs a download. */
    val isRemote: Boolean

    /** A stream of the bytes from [from]; [toInclusive] limits it when known. */
    fun open(from: Long, toInclusive: Long? = null): InputStream
}

class RangeNotSupportedException : IOException("The server does not allow reading part of the file")

fun byteSourceFor(context: Context, uri: Uri): ByteSource? = when (uri.scheme) {
    "content", "file" -> LocalByteSource(context, uri)
    // Online: the parts the player has kept are read from the phone (see CachedByteSource).
    "http", "https" -> HttpByteSource(uri.toString()).let { http ->
        (context.applicationContext as? LinguaApp)
            ?.let { app -> CachedByteSource(app.streamStore, streamKey(uri.toString()), http) }
            ?: http
    }
    else -> null
}

private class LocalByteSource(private val context: Context, private val uri: Uri) : ByteSource {
    override val isRemote = false

    override fun open(from: Long, toInclusive: Long?): InputStream {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Cannot open the file")
        val stream = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
        try {
            stream.channel.position(from)
        } catch (e: Exception) {
            stream.close()
            throw IOException("This file cannot be read from a position", e)
        }
        return if (toInclusive == null) stream else LimitedInputStream(stream, toInclusive - from + 1)
    }
}

internal class HttpByteSource(private val url: String) : ByteSource {
    override val isRemote = true

    override fun open(from: Long, toInclusive: Long?): InputStream {
        var current = URL(url)
        repeat(MAX_REDIRECTS) {
            val connection = current.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Range", "bytes=$from-${toInclusive ?: ""}")
            val code = connection.responseCode
            when {
                code == HttpURLConnection.HTTP_PARTIAL -> return connection.inputStream.closing(connection)
                // A full response is only usable when the start was asked for.
                code == HttpURLConnection.HTTP_OK && from == 0L -> {
                    val body = connection.inputStream.closing(connection)
                    return if (toInclusive == null) body else LimitedInputStream(body, toInclusive + 1)
                }
                code == HttpURLConnection.HTTP_OK -> {
                    connection.disconnect()
                    throw RangeNotSupportedException()
                }
                code in 300..399 -> {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    val next = URL(current, location ?: throw IOException("Redirect without a location"))
                    if (next.protocol != "http" && next.protocol != "https") throw IOException("Unsupported redirect")
                    current = next
                }
                else -> {
                    connection.disconnect()
                    throw IOException("Server answered $code")
                }
            }
        }
        throw IOException("Too many redirects")
    }

    private fun InputStream.closing(connection: HttpURLConnection): InputStream =
        object : FilterInputStream(this) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    connection.disconnect()
                }
            }
        }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val TIMEOUT_MS = 15_000
        const val USER_AGENT = "LinguaPlayer"
    }
}

private class LimitedInputStream(input: InputStream, private var left: Long) : FilterInputStream(input) {
    override fun read(): Int {
        if (left <= 0) return -1
        val b = super.read()
        if (b >= 0) left--
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (left <= 0) return -1
        val n = super.read(b, off, minOf(len.toLong(), left).toInt())
        if (n > 0) left -= n
        return n
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(minOf(n, left))
        left -= skipped
        return skipped
    }
}
