package io.github.ard9.linguaplayer.stream

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Where the proxy gets the film's bytes from. */
interface Upstream {
    /** The bytes from [from] (or from 0 when the server cannot start elsewhere: see [UpstreamResponse.start]). */
    fun open(from: Long): UpstreamResponse
}

/** An open response: [body] starts at byte [start] of a file of [totalLength] bytes. */
class UpstreamResponse(
    val body: InputStream,
    val start: Long,
    val totalLength: Long,
    val contentType: String?,
    private val onClose: () -> Unit = {},
) : Closeable {
    override fun close() {
        try {
            body.close()
        } catch (e: IOException) {
            // Already gone.
        } finally {
            onClose()
        }
    }
}

/** A film on an http(s) server; follows redirects to http(s) only. */
class HttpUpstream(private val url: String) : Upstream {
    override fun open(from: Long): UpstreamResponse {
        var current = URL(url)
        repeat(MAX_REDIRECTS) {
            val connection = current.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Range", "bytes=$from-")
            val code = connection.responseCode
            val type = connection.contentType
            when {
                code == HttpURLConnection.HTTP_PARTIAL -> {
                    val range = parseContentRange(connection.getHeaderField("Content-Range"))
                    if (range == null) {
                        connection.disconnect()
                        throw IOException("Partial answer without a usable Content-Range")
                    }
                    return UpstreamResponse(connection.inputStream, range.first, range.second, type) { connection.disconnect() }
                }
                code == HttpURLConnection.HTTP_OK -> {
                    // No ranges: the whole file from the start; the caller skips ahead.
                    val length = connection.getHeaderField("Content-Length")?.toLongOrNull()
                    if (length == null || length <= 0) {
                        connection.disconnect()
                        throw IOException("The server does not say how long the file is")
                    }
                    return UpstreamResponse(connection.inputStream, 0, length, type) { connection.disconnect() }
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

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val TIMEOUT_MS = 15_000
        private const val USER_AGENT = "LinguaPlayer"

        /** "bytes 100-199/1000" → (100, 1000); null if not usable. */
        fun parseContentRange(value: String?): Pair<Long, Long>? {
            val match = Regex("""bytes\s+(\d+)-(\d+)/(\d+)""").find(value ?: return null) ?: return null
            val start = match.groupValues[1].toLongOrNull() ?: return null
            val total = match.groupValues[3].toLongOrNull() ?: return null
            return if (total > 0 && start < total) start to total else null
        }
    }
}
