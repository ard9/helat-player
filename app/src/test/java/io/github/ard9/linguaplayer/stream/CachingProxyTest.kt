package io.github.ard9.linguaplayer.stream

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class CachingProxyTest {
    @get:Rule
    val temp = TemporaryFolder()

    /** A film of [size] bytes in memory; counts how often and how much it is downloaded. */
    private class FakeUpstream(size: Int, private val ranges: Boolean = true) : Upstream {
        val data = ByteArray(size) { (it * 31 + it / 7).toByte() }
        val opens = AtomicInteger()
        val sent = AtomicLong()
        override fun open(from: Long): UpstreamResponse {
            opens.incrementAndGet()
            val start = if (ranges) from else 0L
            val body = object : ByteArrayInputStream(data, start.toInt(), data.size - start.toInt()) {
                override fun read(b: ByteArray, off: Int, len: Int): Int =
                    super.read(b, off, minOf(len, 10_000)).also { if (it > 0) sent.addAndGet(it.toLong()) }
            }
            return UpstreamResponse(body, start, data.size.toLong(), "video/x-matroska")
        }
    }

    private val chunk = 4 * 1024
    private val proxies = mutableListOf<CachingProxy>()

    @After
    fun stop() = proxies.forEach { it.close() }

    private fun proxy(upstream: Upstream, store: ChunkStore = ChunkStore(temp.root, 1L shl 30, chunk)) =
        CachingProxy(upstream, store, "film", "film.mkv").start().also { proxies.add(it) }

    private fun get(url: String, range: String? = null, method: String = "GET"): Triple<Int, Map<String, String>, ByteArray> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        if (range != null) c.setRequestProperty("Range", range)
        val code = c.responseCode
        val headers = c.headerFields.filterKeys { it != null }.mapValues { it.value.first() }
        val body = if (code < 400) c.inputStream.use { it.readBytes() } else ByteArray(0)
        c.disconnect()
        return Triple(code, headers, body)
    }

    @Test
    fun `a proxy trusting the known length serves kept parts without asking the server`() {
        val store = ChunkStore(temp.root, 1L shl 30, chunk)
        val up = FakeUpstream(50_000)
        get(proxy(up, store).url, "bytes=8192-24575") // The player reads (and keeps) this part.
        val opens = up.opens.get()
        val clipper = CachingProxy(up, store, "film", "film.mkv", trustKnownLength = true).start().also { proxies.add(it) }
        val (code, headers, body) = get(clipper.url, "bytes=10000-20000")
        assertEquals(206, code)
        assertEquals("bytes 10000-20000/50000", headers["Content-Range"])
        assertArrayEquals(up.data.copyOfRange(10_000, 20_001), body)
        assertEquals("no request to the server", opens, up.opens.get())
        // A part that is not kept is still downloaded.
        assertArrayEquals(up.data.copyOfRange(40_000, 45_000), get(clipper.url, "bytes=40000-44999").third)
        assertTrue(up.opens.get() > opens)
    }

    private fun readAheadProxy(up: Upstream, store: ChunkStore) =
        CachingProxy(up, store, "film", "film.mkv", readAhead = CachingProxy.ReadAhead(lowBytes = 10L * chunk, highBytes = 30L * chunk))
            .start().also { proxies.add(it) }

    /** Waits until [up] has sent nothing for a while (the read-ahead is resting). */
    private fun waitQuiet(up: FakeUpstream) {
        var last = -1L
        repeat(100) {
            val now = up.sent.get()
            if (now == last) return
            last = now
            Thread.sleep(150)
        }
        throw AssertionError("still downloading")
    }

    @Test
    fun `reads ahead of the player in a burst, then rests`() {
        val up = FakeUpstream(100 * chunk)
        val store = ChunkStore(temp.root, 1L shl 30, chunk)
        val p = readAheadProxy(up, store)
        val (code, _, body) = get(p.url, "bytes=0-${chunk - 1}")
        assertEquals(206, code)
        assertArrayEquals(up.data.copyOfRange(0, chunk), body)
        waitQuiet(up)
        // About 30 chunks ahead of the player are kept, not the whole film.
        assertTrue("kept ${store.size()}", store.size() in 30L * chunk..33L * chunk)
        // Reading what was read ahead needs no download.
        val sent = up.sent.get()
        assertArrayEquals(up.data.copyOfRange(5 * chunk, 20 * chunk), get(p.url, "bytes=${5 * chunk}-${20 * chunk - 1}").third)
        assertEquals(sent, up.sent.get())
    }

    @Test
    fun `a new burst starts when the player nears the end of what is kept`() {
        val up = FakeUpstream(100 * chunk)
        val store = ChunkStore(temp.root, 1L shl 30, chunk)
        val p = readAheadProxy(up, store)
        get(p.url, "bytes=0-${chunk - 1}")
        waitQuiet(up)
        val before = store.size()
        // 25 chunks in: fewer than 10 kept ahead, so it downloads until 30 are.
        assertArrayEquals(up.data.copyOfRange(25 * chunk, 26 * chunk), get(p.url, "bytes=${25 * chunk}-${26 * chunk - 1}").third)
        waitQuiet(up)
        assertTrue("kept ${store.size()} after $before", store.size() >= 54L * chunk)
        assertTrue(store.size() < 100L * chunk)
    }

    @Test
    fun `with read-ahead, a jump elsewhere and a whole range still come back right`() {
        val up = FakeUpstream(70 * chunk + 123)
        val p = readAheadProxy(up, ChunkStore(temp.root, 1L shl 30, chunk))
        assertArrayEquals(up.data.copyOfRange(60 * chunk + 7, up.data.size), get(p.url, "bytes=${60 * chunk + 7}-").third)
        assertArrayEquals(up.data.copyOfRange(3_000, 50 * chunk), get(p.url, "bytes=3000-${50 * chunk - 1}").third)
    }

    @Test
    fun `with read-ahead, a server that cannot start in the middle works`() {
        val up = FakeUpstream(40 * chunk, ranges = false)
        val p = readAheadProxy(up, ChunkStore(temp.root, 1L shl 30, chunk))
        assertArrayEquals(up.data.copyOfRange(20 * chunk, 40 * chunk), get(p.url, "bytes=${20 * chunk}-").third)
    }

    @Test
    fun `serves the whole film`() {
        val up = FakeUpstream(50_000)
        val (code, headers, body) = get(proxy(up).url)
        assertEquals(200, code)
        assertEquals("50000", headers["Content-Length"])
        assertArrayEquals(up.data, body)
    }

    @Test
    fun `serves a range, and the second time from the phone`() {
        val up = FakeUpstream(50_000)
        val p = proxy(up)
        val (code, headers, body) = get(p.url, "bytes=10000-20999")
        assertEquals(206, code)
        assertEquals("bytes 10000-20999/50000", headers["Content-Range"])
        assertArrayEquals(up.data.copyOfRange(10_000, 21_000), body)
        val opens = up.opens.get()
        val sent = up.sent.get()
        assertArrayEquals(up.data.copyOfRange(12_345, 20_000), get(p.url, "bytes=12345-19999").third)
        assertEquals("nothing downloaded again", sent, up.sent.get())
        assertEquals(opens, up.opens.get())
    }

    @Test
    fun `an open range runs to the end`() {
        val up = FakeUpstream(30_001)
        val body = get(proxy(up).url, "bytes=29000-").third
        assertArrayEquals(up.data.copyOfRange(29_000, 30_001), body)
    }

    @Test
    fun `kept chunks and downloaded ones mix in one answer`() {
        val up = FakeUpstream(60_000)
        val p = proxy(up)
        get(p.url, "bytes=20000-29999")
        assertArrayEquals(up.data.copyOfRange(5_000, 45_000), get(p.url, "bytes=5000-44999").third)
    }

    @Test
    fun `works with a server that cannot start in the middle`() {
        val up = FakeUpstream(40_000, ranges = false)
        assertArrayEquals(up.data.copyOfRange(30_000, 40_000), get(proxy(up).url, "bytes=30000-").third)
    }

    @Test
    fun `a new proxy for the same film uses the chunks kept before`() {
        val up = FakeUpstream(40_000)
        val store = ChunkStore(temp.root, 1L shl 30, chunk)
        get(proxy(up, store).url)
        val sent = up.sent.get()
        assertArrayEquals(up.data, get(proxy(up, store).url).third)
        // Only the first request (to learn the length) touches the network.
        assertTrue(up.sent.get() - sent <= chunk * 2)
    }

    @Test
    fun `other paths, methods and ranges are refused`() {
        val p = proxy(FakeUpstream(10_000))
        val base = p.url.substringBeforeLast('/').substringBeforeLast('/')
        assertEquals(404, get("$base/wrong/film.mkv").first)
        assertEquals(405, get(p.url, method = "POST").first)
        assertEquals(416, get(p.url, "bytes=10000-").first)
        val (code, headers, body) = get(p.url, method = "HEAD")
        assertEquals(200, code)
        assertEquals("10000", headers["Content-Length"])
        assertEquals(0, body.size)
    }

    @Test
    fun `the address stays on the phone`() {
        val url = proxy(FakeUpstream(1_000)).url
        assertTrue(url, url.startsWith("http://127.0.0.1:"))
        // A long random secret in the path.
        assertTrue(url.split('/')[3].length >= 32)
    }
}
