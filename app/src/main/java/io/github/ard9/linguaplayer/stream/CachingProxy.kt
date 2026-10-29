package io.github.ard9.linguaplayer.stream

import java.util.concurrent.atomic.AtomicInteger
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.SynchronousQueue
import kotlin.math.max
import kotlin.math.min

/**
 * A small HTTP server on this phone (127.0.0.1 only) that plays an online film for libVLC and
 * keeps every downloaded chunk in a [ChunkStore]. Jumping back (Loop, Repeat, −10s) is then
 * read from the phone instead of the network.
 *
 * Only requests for [url], whose path holds a random secret, are answered; other apps on the
 * phone cannot use it without knowing that path. GET and HEAD with an optional single byte
 * range are supported, one request per connection.
 */
class CachingProxy(
    private val upstream: Upstream,
    private val store: ChunkStore,
    /** Names the film's folder in the store; must not contain the film's address. */
    private val key: String,
    fileName: String,
    /**
     * Use the length the store recorded for this film instead of asking the server first, so
     * parts already kept are served without any network request. Only for a film the player has
     * just opened (which checked the length); a file that changed is still noticed when a
     * missing part is downloaded.
     */
    trustKnownLength: Boolean = false,
) : Closeable {
    // 127.0.0.1 itself: on Android getLoopbackAddress() can be the IPv6 ::1, which would not
    // accept the IPv4 address given to the player.
    private val server = ServerSocket(0, BACKLOG, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
    private val secret = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    // ASCII only: the player percent-encodes other characters, and the path must match exactly.
    private val path = "/$secret/" + fileName.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "._-" }
        .ifEmpty { "media" }.take(80)
    private val pool: ExecutorService = ThreadPoolExecutor(
        0, MAX_CONNECTIONS, 30, TimeUnit.SECONDS, SynchronousQueue(),
    ) { r -> Thread(r, "stream-proxy").apply { isDaemon = true } }
    private val open = Collections.synchronizedSet(mutableSetOf<Socket>())
    @Volatile private var closed = false
    @Volatile private var total: Long? = if (trustKnownLength) store.knownLength(key) else null
    @Volatile private var contentType: String? = null
    private val requests = AtomicInteger()
    @Volatile private var lastProblem: String? = null

    /** What happened so far, for a message when playing through the proxy failed. */
    fun diagnostics(): String = "requests=${requests.get()}" + (lastProblem?.let { ", $it" } ?: "")

    /** The address to give libVLC. */
    val url: String = "http://127.0.0.1:${server.localPort}$path"

    private val acceptThread = Thread({ acceptLoop() }, "stream-proxy-accept").apply { isDaemon = true }

    fun start(): CachingProxy {
        acceptThread.start()
        return this
    }

    private fun acceptLoop() {
        while (!closed) {
            val socket = try {
                server.accept()
            } catch (e: IOException) {
                break // Closed.
            }
            open.add(socket)
            try {
                pool.execute { serve(socket) }
            } catch (e: RejectedExecutionException) {
                open.remove(socket)
                socket.close() // Too many at once; the player retries.
            }
        }
    }

    private fun serve(socket: Socket) {
        try {
            socket.soTimeout = CLIENT_TIMEOUT_MS
            requests.incrementAndGet()
            val request = readRequest(socket.getInputStream()) ?: run {
                lastProblem = "unreadable request"
                return
            }
            val out = BufferedOutputStream(socket.getOutputStream(), OUT_BUFFER)
            handle(request, out)
            out.flush()
        } catch (e: IOException) {
            // The player closed the connection (it does when seeking), or the network failed.
            lastProblem = "${e.javaClass.simpleName}: ${e.message}"
        } catch (e: RuntimeException) {
            lastProblem = "${e.javaClass.simpleName}: ${e.message}"
        } finally {
            open.remove(socket)
            try {
                socket.close()
            } catch (e: IOException) {
                // Already closed.
            }
        }
    }

    private class Request(val method: String, val target: String, val range: LongRange?, val rangeEnd: Long?)

    /** Reads the request line and headers (limited in size); null if not a usable request. */
    private fun readRequest(input: InputStream): Request? {
        val lines = mutableListOf<String>()
        val line = StringBuilder()
        var bytes = 0
        while (true) {
            val b = input.read()
            if (b < 0) return null
            if (++bytes > MAX_HEADER_BYTES) return null
            when (b) {
                '\r'.code -> continue
                '\n'.code -> {
                    if (line.isEmpty()) break
                    lines.add(line.toString())
                    line.setLength(0)
                }
                else -> line.append(b.toChar())
            }
        }
        val first = lines.firstOrNull()?.split(' ') ?: return null
        if (first.size < 3) return null
        val rangeHeader = lines.drop(1)
            .firstOrNull { it.startsWith("range:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()
        val match = rangeHeader?.let { Regex("""^bytes=(\d+)-(\d*)$""").find(it) }
        val start = match?.groupValues?.get(1)?.toLongOrNull()
        val end = match?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.toLongOrNull()
        return Request(first[0], first[1], start?.let { it..Long.MAX_VALUE }, end)
    }

    private fun handle(request: Request, out: OutputStream) {
        if (request.target != path) {
            lastProblem = "unknown path"
            return status(out, 404, "Not Found")
        }
        if (request.method != "GET" && request.method != "HEAD") return status(out, 405, "Method Not Allowed")
        val from = request.range?.first ?: 0L
        // Learn the length (and keep the response to use it) if this is the first request.
        var first: UpstreamResponse? = null
        val length = total ?: run {
            val response = upstream.open(chunkStart(from))
            first = response
            remember(response)
            response.totalLength
        }
        try {
            if (from >= length) {
                write(out, "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$length\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                return
            }
            val end = min(request.rangeEnd ?: (length - 1), length - 1)
            if (end < from) return status(out, 416, "Range Not Satisfiable")
            val head = StringBuilder()
            if (request.range != null) {
                head.append("HTTP/1.1 206 Partial Content\r\n")
                head.append("Content-Range: bytes $from-$end/$length\r\n")
            } else {
                head.append("HTTP/1.1 200 OK\r\n")
            }
            head.append("Content-Length: ${end - from + 1}\r\n")
            head.append("Content-Type: ${contentType ?: "application/octet-stream"}\r\n")
            head.append("Accept-Ranges: bytes\r\nConnection: close\r\n\r\n")
            write(out, head.toString())
            if (request.method == "GET") {
                val leftover = first
                first = null
                stream(from, end, length, leftover, out)
            }
        } finally {
            first?.close()
        }
    }

    private fun remember(response: UpstreamResponse) {
        total = response.totalLength
        contentType = response.contentType
        store.prepare(key, response.totalLength)
    }

    private fun chunkStart(position: Long) = position / store.chunkSize * store.chunkSize

    /**
     * Sends bytes [from]..[end] to the player: kept chunks from the store, missing ones from
     * the network (one connection for consecutive missing chunks), keeping each new chunk.
     */
    private fun stream(from: Long, end: Long, length: Long, initial: UpstreamResponse?, out: OutputStream) {
        val size = store.chunkSize
        var up = initial
        var upPos = initial?.start ?: -1L
        var pos = from
        try {
            while (pos <= end && !closed) {
                val index = pos / size
                val chunkStart = index * size
                val chunkLength = min(size.toLong(), length - chunkStart).toInt()
                val lastInChunk = min(end, chunkStart + chunkLength - 1)
                val kept = store.get(key, index, chunkLength)
                if (kept != null) {
                    up?.close()
                    up = null
                    out.write(kept, (pos - chunkStart).toInt(), (lastInChunk - pos + 1).toInt())
                    pos = lastInChunk + 1
                    continue
                }
                if (up == null || upPos > chunkStart) {
                    up?.close()
                    up = upstream.open(chunkStart)
                    upPos = up.start
                    if (up.totalLength != length) throw IOException("The file changed on the server")
                }
                // A server without ranges starts at 0: keep the chunks on the way.
                while (upPos < chunkStart) {
                    val skipped = readChunk(up.body, min(size.toLong(), length - upPos).toInt(), null, 0, -1)
                    store.put(key, upPos / size, skipped)
                    upPos += skipped.size
                }
                val chunk = readChunk(up.body, chunkLength, out, (pos - chunkStart).toInt(), (lastInChunk - chunkStart).toInt())
                store.put(key, index, chunk)
                upPos += chunk.size
                pos = lastInChunk + 1
            }
        } finally {
            up?.close()
        }
    }

    /**
     * Reads a whole chunk of [length] bytes, writing its bytes [writeFrom]..[writeTo] to [out]
     * as soon as they arrive, so playback does not wait for the whole chunk.
     */
    private fun readChunk(input: InputStream, length: Int, out: OutputStream?, writeFrom: Int, writeTo: Int): ByteArray {
        val chunk = ByteArray(length)
        var filled = 0
        while (filled < length) {
            val n = input.read(chunk, filled, length - filled)
            if (n < 0) throw EOFException("The server closed the file early")
            if (out != null) {
                val a = max(filled, writeFrom)
                val b = min(filled + n - 1, writeTo)
                if (a <= b) {
                    out.write(chunk, a, b - a + 1)
                    out.flush()
                }
            }
            filled += n
        }
        return chunk
    }

    private fun status(out: OutputStream, code: Int, text: String) =
        write(out, "HTTP/1.1 $code $text\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")

    private fun write(out: OutputStream, text: String) = out.write(text.toByteArray(Charsets.US_ASCII))

    override fun close() {
        closed = true
        try {
            server.close()
        } catch (e: IOException) {
            // Already closed.
        }
        synchronized(open) {
            open.forEach {
                try {
                    it.close()
                } catch (e: SocketException) {
                    // Already closed.
                }
            }
            open.clear()
        }
        pool.shutdownNow()
    }

    private companion object {
        const val BACKLOG = 8
        const val MAX_CONNECTIONS = 8
        const val MAX_HEADER_BYTES = 8 * 1024
        const val CLIENT_TIMEOUT_MS = 30_000
        const val OUT_BUFFER = 64 * 1024
    }
}
