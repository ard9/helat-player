package io.github.ard9.linguaplayer.subtitle.mkv

import io.github.ard9.linguaplayer.subtitle.SubtitleLine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.util.zip.Inflater

internal const val ID_EBML = 0x1A45DFA3L
internal const val ID_SEGMENT = 0x18538067L
internal const val ID_SEEKHEAD = 0x114D9B74L
internal const val ID_SEEK = 0x4DBBL
internal const val ID_SEEK_ID = 0x53ABL
internal const val ID_SEEK_POSITION = 0x53ACL
internal const val ID_INFO = 0x1549A966L
internal const val ID_TIMESTAMP_SCALE = 0x2AD7B1L
internal const val ID_TRACKS = 0x1654AE6BL
internal const val ID_TRACK_ENTRY = 0xAEL
internal const val ID_TRACK_NUMBER = 0xD7L
internal const val ID_TRACK_TYPE = 0x83L
internal const val ID_CODEC_ID = 0x86L
internal const val ID_LANGUAGE = 0x22B59CL
internal const val ID_LANGUAGE_IETF = 0x22B59DL
internal const val ID_NAME = 0x536EL
internal const val ID_FLAG_DEFAULT = 0x88L
internal const val ID_CONTENT_ENCODINGS = 0x6D80L
internal const val ID_CONTENT_ENCODING = 0x6240L
internal const val ID_CONTENT_COMPRESSION = 0x5034L
internal const val ID_COMPRESSION_ALGO = 0x4254L
internal const val ID_COMPRESSION_SETTINGS = 0x4255L
internal const val ID_CUES = 0x1C53BB6BL
internal const val ID_CUE_POINT = 0xBBL
internal const val ID_CUE_TIME = 0xB3L
internal const val ID_CUE_TRACK_POSITIONS = 0xB7L
internal const val ID_CUE_CLUSTER_POSITION = 0xF1L
internal const val ID_CLUSTER = 0x1F43B675L
internal const val ID_CLUSTER_TIMESTAMP = 0xE7L
internal const val ID_SIMPLE_BLOCK = 0xA3L
internal const val ID_BLOCK_GROUP = 0xA0L
internal const val ID_BLOCK = 0xA1L
internal const val ID_BLOCK_DURATION = 0x9BL

/** Elements that sit directly in the Segment; they end an unknown-size parent. */
internal val TOP_LEVEL_IDS = setOf(
    ID_SEEKHEAD, ID_INFO, ID_TRACKS, ID_CUES, ID_CLUSTER,
    0x1941A469L, // Attachments
    0x1043A770L, // Chapters
    0x1254C367L, // Tags
)

/** A subtitle track inside a Matroska/WebM file. */
data class MkvSubtitleTrack(
    val number: Long,
    val codec: String,
    val language: String,
    val name: String,
    val isDefault: Boolean,
    internal val compression: Int?,
    internal val compressionSettings: ByteArray,
) {
    /** Text tracks can be read; picture tracks (PGS, VobSub) cannot. */
    val isText: Boolean get() = codec in TEXT_CODECS

    val isPersian: Boolean get() = language.lowercase(Locale.ROOT).substringBefore('-') in PERSIAN_CODES

    /** A short name for choosing a track, e.g. "English" or "Persian (Forced)". */
    val label: String
        get() {
            val lang = languageName(language)
            return when {
                name.isNotBlank() && lang.isNotBlank() && !name.contains(lang, ignoreCase = true) -> "$lang ($name)"
                name.isNotBlank() -> name
                lang.isNotBlank() -> lang
                else -> "Track $number"
            }
        }

    private companion object {
        val TEXT_CODECS = setOf("S_TEXT/UTF8", "S_TEXT/ASS", "S_TEXT/SSA", "S_TEXT/WEBVTT", "D_WEBVTT/SUBTITLES")
        val PERSIAN_CODES = setOf("per", "fas", "fa")

        /** Matroska uses ISO 639-2 codes; the "bibliographic" ones differ from Java's. */
        private val BIBLIOGRAPHIC = mapOf(
            "per" to "fa", "ger" to "de", "fre" to "fr", "chi" to "zh", "cze" to "cs", "dut" to "nl",
            "gre" to "el", "rum" to "ro", "slo" to "sk", "alb" to "sq", "arm" to "hy", "baq" to "eu",
            "bur" to "my", "geo" to "ka", "ice" to "is", "mac" to "mk", "may" to "ms", "wel" to "cy",
        )

        private val THREE_TO_TWO: Map<String, String> by lazy {
            Locale.getISOLanguages().associateBy { two ->
                runCatching { Locale(two).isO3Language }.getOrDefault("")
            } + BIBLIOGRAPHIC
        }

        /** "eng", "per", "fa-IR" → "English", "Persian"; "" for unknown. */
        fun languageName(code: String): String {
            val base = code.substringBefore('-').lowercase(Locale.ROOT)
            if (base.isBlank() || base == "und") return ""
            val two = if (base.length == 3) THREE_TO_TWO[base] ?: return base else base
            return Locale(two).getDisplayLanguage(Locale.ENGLISH).ifBlank { base }
        }
    }
}

/**
 * Reads the built-in subtitle tracks of a Matroska/WebM file. Only the headers and the part
 * of the file around a given moment are read, so it also works for online files.
 * Blocking; use from a background thread.
 */
class MkvSubtitles private constructor(
    private val source: ByteSource,
    private val segmentStart: Long,
    private val timestampScaleNs: Long,
    /** All subtitle tracks in file order (the order players list them in). */
    val tracks: List<MkvSubtitleTrack>,
    /** (time in ms, cluster position relative to the segment), sorted by time. */
    private val cues: List<Pair<Long, Long>>,
    private val firstCluster: Long?,
) {
    val textTracks: List<MkvSubtitleTrack> get() = tracks.filter { it.isText }

    /**
     * Lines of every text track that start between [BEFORE_MS] before [atMs] and just after it.
     * Keyed by track number.
     */
    fun linesAround(atMs: Long): Map<Long, List<SubtitleLine>> {
        val wanted = textTracks.associateBy { it.number }
        if (wanted.isEmpty()) return emptyMap()
        val from = cues.lastOrNull { it.first <= atMs - BEFORE_MS }?.let { segmentStart + it.second }
            ?: firstCluster
            ?: cues.firstOrNull()?.let { segmentStart + it.second }
            ?: return emptyMap()
        val stopMs = atMs + AFTER_MS
        val raw = wanted.keys.associateWith { mutableListOf<RawLine>() }
        val budget = if (source.isRemote) REMOTE_CLUSTER_BUDGET else LOCAL_CLUSTER_BUDGET

        source.open(from).use { stream ->
            val r = EbmlReader(stream, from, budget)
            r.children(end = null, topLevel = true) { top ->
                when {
                    top.id != ID_CLUSTER -> top.size != EbmlReader.UNKNOWN_SIZE
                    else -> readCluster(r, top, stopMs, wanted, raw)
                }
            }
        }
        return raw.mapValues { (_, lines) -> finish(lines) }
    }

    /** Returns false once past [stopMs]. */
    private fun readCluster(
        r: EbmlReader,
        cluster: EbmlReader.Header,
        stopMs: Long,
        wanted: Map<Long, MkvSubtitleTrack>,
        raw: Map<Long, MutableList<RawLine>>,
    ): Boolean {
        var clusterTicks = 0L
        val end = if (cluster.size == EbmlReader.UNKNOWN_SIZE) null else cluster.payload + cluster.size
        return r.children(end) { child ->
            when (child.id) {
                ID_CLUSTER_TIMESTAMP -> {
                    clusterTicks = r.uint(child.size)
                    toMs(clusterTicks) <= stopMs
                }
                ID_SIMPLE_BLOCK -> {
                    readBlock(r, child.payload + child.size, wanted)?.let { (track, rel, data) ->
                        add(raw, wanted.getValue(track), clusterTicks + rel, null, data)
                    }
                    true
                }
                ID_BLOCK_GROUP -> {
                    var block: Triple<Long, Long, ByteArray>? = null
                    var duration: Long? = null
                    r.children(child.payload + child.size) { g ->
                        when (g.id) {
                            ID_BLOCK -> block = readBlock(r, g.payload + g.size, wanted)
                            ID_BLOCK_DURATION -> duration = r.uint(g.size)
                        }
                        true
                    }
                    block?.let { (track, rel, data) ->
                        add(raw, wanted.getValue(track), clusterTicks + rel, duration, data)
                    }
                    true
                }
                else -> true
            }
        }
    }

    /** Reads a block's data if it belongs to a wanted track: (track, relative ticks, data). */
    private fun readBlock(r: EbmlReader, end: Long, wanted: Map<Long, MkvSubtitleTrack>): Triple<Long, Long, ByteArray>? {
        val track = r.vint(false)
        if (track !in wanted) return null
        val b0 = r.byte()
        val b1 = r.byte()
        val relative = ((b0 shl 8) or b1).toShort().toLong()
        val flags = r.byte()
        if (flags and 0x06 != 0) return null // Laced blocks are not used for subtitles.
        return Triple(track, relative, r.bytes(end - r.position, MAX_BLOCK))
    }

    private fun add(raw: Map<Long, MutableList<RawLine>>, track: MkvSubtitleTrack, ticks: Long, duration: Long?, data: ByteArray) {
        val payload = try {
            when (track.compression) {
                null -> data
                0 -> inflate(data)
                3 -> track.compressionSettings + data
                else -> return
            }
        } catch (e: Exception) {
            return // A damaged block loses one line, not the whole search.
        }
        val text = SubtitleTextCleaner.clean(track.codec, String(payload, Charsets.UTF_8))
        if (text.isEmpty()) return
        raw.getValue(track.number).add(RawLine(toMs(ticks), duration?.let { toMs(ticks + it) }, text))
    }

    private fun finish(lines: List<RawLine>): List<SubtitleLine> {
        val sorted = lines.sortedBy { it.startMs }
        return sorted.mapIndexed { i, line ->
            val end = line.endMs ?: minOf(
                sorted.getOrNull(i + 1)?.startMs ?: Long.MAX_VALUE,
                line.startMs + DEFAULT_DURATION_MS,
            )
            SubtitleLine(line.startMs, maxOf(end, line.startMs + 1), line.text)
        }
    }

    private fun toMs(ticks: Long) = ticks * timestampScaleNs / 1_000_000

    private class RawLine(val startMs: Long, val endMs: Long?, val text: String)

    companion object {
        /** Start reading from a cluster at least this long before the moment. */
        private const val BEFORE_MS = 12_000L
        private const val AFTER_MS = 500L
        private const val DEFAULT_DURATION_MS = 4_000L
        private const val MAX_BLOCK = 256 * 1024
        private const val HEADER_BUDGET = 8L * 1024 * 1024
        private const val MAX_ELEMENT = 16 * 1024 * 1024
        private const val REMOTE_CLUSTER_BUDGET = 64L * 1024 * 1024
        private const val LOCAL_CLUSTER_BUDGET = 16L * 1024 * 1024 * 1024

        /** Reads the file's headers. Null if it is not a Matroska/WebM file. */
        fun open(source: ByteSource): MkvSubtitles? {
            var segmentStart = 0L
            var segmentEnd: Long? = null
            var scale = 1_000_000L
            var infoFound = false
            var tracks: List<MkvSubtitleTrack>? = null
            var cues: List<Pair<Long, Long>>? = null
            var firstCluster: Long? = null
            val seeks = mutableMapOf<Long, Long>()

            source.open(0).use { stream ->
                val r = EbmlReader(stream, 0, HEADER_BUDGET)
                val ebml = r.header() ?: return null
                if (ebml.id != ID_EBML || ebml.size == EbmlReader.UNKNOWN_SIZE) return null
                r.skip(ebml.size)
                val segment = r.header() ?: return null
                if (segment.id != ID_SEGMENT) return null
                segmentStart = segment.payload
                if (segment.size != EbmlReader.UNKNOWN_SIZE) segmentEnd = segment.payload + segment.size

                try {
                    r.children(segmentEnd, topLevel = true) { h ->
                        if (h.id == ID_CLUSTER) {
                            firstCluster = h.start
                            return@children false
                        }
                        if (h.size == EbmlReader.UNKNOWN_SIZE) return@children false
                        when (h.id) {
                            ID_SEEKHEAD -> parseSeekHead(payload(r, h), seeks)
                            ID_INFO -> {
                                infoFound = true
                                scale = parseScale(payload(r, h)) ?: scale
                            }
                            ID_TRACKS -> tracks = parseTracks(payload(r, h))
                            ID_CUES -> cues = parseCues(payload(r, h))
                        }
                        true
                    }
                } catch (e: IOException) {
                    // Headers before the first cluster can be huge (fonts); SeekHead covers the rest.
                    if (tracks == null && seeks.isEmpty()) throw e
                }
            }

            fun readAt(id: Long): ByteArray? {
                val position = seeks[id] ?: return null
                source.open(segmentStart + position).use { stream ->
                    val r = EbmlReader(stream, segmentStart + position, MAX_ELEMENT + 16L)
                    val h = r.header() ?: return null
                    if (h.id != id || h.size == EbmlReader.UNKNOWN_SIZE) return null
                    return r.bytes(h.size, MAX_ELEMENT)
                }
            }
            if (tracks == null) tracks = readAt(ID_TRACKS)?.let(::parseTracks)
            if (!infoFound) readAt(ID_INFO)?.let { scale = parseScale(it) ?: scale }
            if (cues == null) cues = runCatching { readAt(ID_CUES)?.let(::parseCues) }.getOrNull()

            val all = tracks ?: return null
            val scaleNs = scale
            return MkvSubtitles(
                source = source,
                segmentStart = segmentStart,
                timestampScaleNs = scaleNs,
                tracks = all,
                cues = cues.orEmpty()
                    .map { (ticks, position) -> ticks * scaleNs / 1_000_000 to position }
                    .sortedBy { it.first },
                firstCluster = firstCluster,
            )
        }

        private fun payload(r: EbmlReader, h: EbmlReader.Header) = r.bytes(h.size, MAX_ELEMENT)

        private fun reader(bytes: ByteArray) = EbmlReader(ByteArrayInputStream(bytes), 0, bytes.size.toLong())

        private fun parseSeekHead(bytes: ByteArray, into: MutableMap<Long, Long>) {
            val r = reader(bytes)
            r.children(bytes.size.toLong()) { seek ->
                if (seek.id == ID_SEEK) {
                    var id: Long? = null
                    var position: Long? = null
                    r.children(seek.payload + seek.size) { e ->
                        when (e.id) {
                            ID_SEEK_ID -> id = r.uint(e.size)
                            ID_SEEK_POSITION -> position = r.uint(e.size)
                        }
                        true
                    }
                    val i = id
                    val p = position
                    if (i != null && p != null && i !in into) into[i] = p
                }
                true
            }
        }

        private fun parseScale(bytes: ByteArray): Long? {
            val r = reader(bytes)
            var scale: Long? = null
            r.children(bytes.size.toLong()) { e ->
                if (e.id == ID_TIMESTAMP_SCALE) scale = r.uint(e.size).takeIf { it > 0 }
                true
            }
            return scale
        }

        private fun parseTracks(bytes: ByteArray): List<MkvSubtitleTrack> {
            val r = reader(bytes)
            val result = mutableListOf<MkvSubtitleTrack>()
            r.children(bytes.size.toLong()) { entry ->
                if (entry.id == ID_TRACK_ENTRY) parseTrack(r, entry.payload + entry.size)?.let(result::add)
                true
            }
            return result
        }

        /** Returns the track only if it is a subtitle track. */
        private fun parseTrack(r: EbmlReader, end: Long): MkvSubtitleTrack? {
            var number = 0L
            var type = 0L
            var codec = ""
            var language = "eng" // The Matroska default.
            var ietf: String? = null
            var name = ""
            var isDefault = true
            var compression: Int? = null
            var settings = ByteArray(0)
            r.children(end) { e ->
                when (e.id) {
                    ID_TRACK_NUMBER -> number = r.uint(e.size)
                    ID_TRACK_TYPE -> type = r.uint(e.size)
                    ID_CODEC_ID -> codec = r.string(e.size)
                    ID_LANGUAGE -> language = r.string(e.size)
                    ID_LANGUAGE_IETF -> ietf = r.string(e.size)
                    ID_NAME -> name = r.string(e.size)
                    ID_FLAG_DEFAULT -> isDefault = r.uint(e.size) == 1L
                    ID_CONTENT_ENCODINGS -> r.children(e.payload + e.size) { enc ->
                        if (enc.id == ID_CONTENT_ENCODING) r.children(enc.payload + enc.size) { c ->
                            if (c.id == ID_CONTENT_COMPRESSION) {
                                compression = 0 // zlib unless stated otherwise
                                r.children(c.payload + c.size) { z ->
                                    when (z.id) {
                                        ID_COMPRESSION_ALGO -> compression = r.uint(z.size).toInt()
                                        ID_COMPRESSION_SETTINGS -> settings = r.bytes(z.size, 4096)
                                    }
                                    true
                                }
                            }
                            true
                        }
                        true
                    }
                }
                true
            }
            if (type != TRACK_TYPE_SUBTITLE) return null
            return MkvSubtitleTrack(number, codec, ietf ?: language, name, isDefault, compression, settings)
        }

        private fun parseCues(bytes: ByteArray): List<Pair<Long, Long>> {
            val r = reader(bytes)
            val result = mutableListOf<Pair<Long, Long>>()
            r.children(bytes.size.toLong()) { point ->
                if (point.id == ID_CUE_POINT) {
                    var time: Long? = null
                    var position: Long? = null
                    r.children(point.payload + point.size) { e ->
                        when (e.id) {
                            ID_CUE_TIME -> time = r.uint(e.size)
                            ID_CUE_TRACK_POSITIONS -> r.children(e.payload + e.size) { p ->
                                if (p.id == ID_CUE_CLUSTER_POSITION) position = r.uint(p.size)
                                true
                            }
                        }
                        true
                    }
                    val t = time
                    val p = position
                    if (t != null && p != null) result.add(t to p)
                }
                true
            }
            return result
        }

        private fun inflate(data: ByteArray): ByteArray {
            val inflater = Inflater()
            try {
                inflater.setInput(data)
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (!inflater.finished()) {
                    val n = inflater.inflate(buffer)
                    if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_BLOCK) throw IOException("Subtitle block too large")
                }
                return out.toByteArray()
            } finally {
                inflater.end()
            }
        }

        private const val TRACK_TYPE_SUBTITLE = 0x11L
    }
}

/** Turns the stored text of a subtitle block into plain text. */
object SubtitleTextCleaner {
    private val ASS_TAG = Regex("""\{[^}]*\}""")
    private val ASS_DRAWING = Regex("""\{[^}]*\\p[1-9]""")
    private val HTML_TAG = Regex("<[^>]*>")

    fun clean(codec: String, stored: String): String {
        var text = stored
        if (codec == "S_TEXT/ASS" || codec == "S_TEXT/SSA") {
            // ReadOrder, Layer, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            val parts = text.split(",", limit = 9)
            if (parts.size == 9) text = parts[8]
            if (ASS_DRAWING.containsMatchIn(text)) return ""
            text = text.replace(ASS_TAG, "").replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
        }
        text = text.replace(HTML_TAG, "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        return text.replace("\r", "").lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }
}
