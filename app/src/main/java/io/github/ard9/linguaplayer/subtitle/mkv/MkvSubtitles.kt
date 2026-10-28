package io.github.ard9.linguaplayer.subtitle.mkv

import io.github.ard9.linguaplayer.subtitle.SubtitleLine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
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
internal const val ID_CUE_TRACK = 0xF7L
internal const val ID_CUE_RELATIVE_POSITION = 0xF0L
internal const val ID_CUE_DURATION = 0xB2L
internal const val ID_DURATION = 0x4489L
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
    val label: String get() = label(Locale.ENGLISH, "Track $number")

    /** [label] with the language named in [display]; [unnamed] if the track has no name or language. */
    fun label(display: Locale, unnamed: String): String {
        val lang = languageName(language, display)
        return when {
            name.isNotBlank() && lang.isNotBlank() && !name.contains(lang, ignoreCase = true) -> "$lang ($name)"
            name.isNotBlank() -> name
            lang.isNotBlank() -> lang
            else -> unnamed
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

        /** "eng", "per", "fa-IR" → "English", "Persian" (named in [display]); "" for unknown. */
        fun languageName(code: String, display: Locale): String {
            val base = code.substringBefore('-').lowercase(Locale.ROOT)
            if (base.isBlank() || base == "und") return ""
            val two = if (base.length == 3) THREE_TO_TWO[base] ?: return base else base
            return Locale(two).getDisplayLanguage(display).ifBlank { base }
        }
    }
}

/** The file has no index of its subtitle lines, and reading them all would mean downloading it. */
class NoSubtitleIndexException : IOException("The subtitle lines are not indexed in this file")

/** One CueTrackPositions entry: where a block of [track] at [ticks] is stored. */
internal class CueEntry(
    val ticks: Long,
    val track: Long?,
    val clusterPosition: Long,
    val relativePosition: Long?,
    val durationTicks: Long?,
)

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
    /** Every cue entry, with track and position inside the cluster when the file has them. */
    private val cueEntries: List<CueEntry>,
    private val firstCluster: Long?,
    /** Length of the film in ms, if the file says. */
    private val durationMs: Long?,
) {
    val textTracks: List<MkvSubtitleTrack> get() = tracks.filter { it.isText }

    /**
     * Lines of every text track that start between [BEFORE_MS] before [atMs] and just after it.
     * Keyed by track number.
     */
    fun linesAround(atMs: Long): Map<Long, List<SubtitleLine>> {
        val wanted = textTracks.associateBy { it.number }
        if (wanted.isEmpty()) return emptyMap()
        val from = clusterBefore(atMs - BEFORE_MS) ?: return emptyMap()
        val raw = wanted.keys.associateWith { mutableListOf<RawLine>() }
        scan(from, atMs + AFTER_MS, wanted, raw, partialOk = false) { false }
        return raw.mapValues { (_, lines) -> finish(lines) }
    }

    /**
     * Up to [count] lines of text track [track] that are on screen at [fromMs] or come after it,
     * reading at most [MAX_SCENE_MS] ahead. If the read limit is reached, the lines found so far
     * are returned.
     */
    fun linesFrom(fromMs: Long, track: Long, count: Int): List<SubtitleLine> {
        val wanted = textTracks.filter { it.number == track }.associateBy { it.number }
        if (wanted.isEmpty() || count <= 0) return emptyList()
        val from = clusterBefore(fromMs - BEFORE_MS) ?: return emptyList()
        val lines = mutableListOf<RawLine>()
        // One extra line, so a line without a stored duration can end where the next begins.
        scan(from, fromMs + MAX_SCENE_MS, wanted, mapOf(track to lines), partialOk = true) {
            lines.count { it.startMs >= fromMs } > count
        }
        return finish(lines).filter { it.endMs > fromMs }.take(count)
    }

    /**
     * Up to [max] lines of [track] that start in [fromMs, toMs), in time order. Like
     * [linesFrom], lines found before a read limit are returned.
     */
    fun linesBetween(fromMs: Long, toMs: Long, track: Long, max: Int): List<SubtitleLine> {
        val wanted = textTracks.filter { it.number == track }.associateBy { it.number }
        if (wanted.isEmpty() || toMs <= fromMs || max <= 0) return emptyList()
        val from = clusterBefore(fromMs - BEFORE_MS) ?: return emptyList()
        val lines = mutableListOf<RawLine>()
        // Read until a line starts after the range, so the last line knows where it ends.
        val stopMs = minOf(toMs, fromMs + MAX_SCENE_MS)
        scan(from, stopMs + BEFORE_MS, wanted, mapOf(track to lines), partialOk = true) {
            lines.any { it.startMs >= stopMs } || lines.count { it.startMs >= fromMs } > max
        }
        return finish(lines).filter { it.startMs in fromMs until stopMs }.take(max)
    }

    /**
     * Every line of every text track, keyed by track number, for searching the whole film.
     *
     * When the file's index (Cues) points at each subtitle block, as mkvmerge and ffmpeg write
     * it, only those small blocks are read: a few requests per line, not the film. Otherwise a
     * local file is read from start to end; for an online file that would download the whole
     * film, so [NoSubtitleIndexException] is thrown instead. [onProgress] gets 0..1.
     */
    fun allLines(
        onProgress: (Float) -> Unit = {},
        parallelism: Int = 1,
        blockWindow: Int = BLOCK_WINDOW,
    ): Map<Long, List<SubtitleLine>> {
        val wanted = textTracks.associateBy { it.number }
        if (wanted.isEmpty()) return emptyMap()
        val refs = subtitleCues(wanted.keys)
        val raw = when {
            canReadAllLinesCheaply -> linesFromCues(refs, wanted, onProgress, parallelism, blockWindow)
            !source.isRemote -> {
                val lines = wanted.keys.associateWith { mutableListOf<RawLine>() }
                val from = firstCluster ?: clusterBefore(0) ?: return emptyMap()
                scan(from, Long.MAX_VALUE, wanted, lines, partialOk = false, onClusterMs = { ms ->
                    durationMs?.takeIf { it > 0 }?.let { onProgress((ms.toFloat() / it).coerceIn(0f, 1f)) }
                }) { false }
                lines
            }
            else -> throw NoSubtitleIndexException()
        }
        onProgress(1f)
        return raw.mapValues { (_, lines) -> finish(lines.take(MAX_LINES_PER_TRACK)) }
    }

    /**
     * True when the cues point at every text track's blocks, so [allLines] reads only those
     * small blocks (and works online).
     */
    val canReadAllLinesCheaply: Boolean by lazy {
        val tracks = textTracks.map { it.number }.toSet()
        val refs = subtitleCues(tracks)
        tracks.isNotEmpty() && tracks.all { track -> refs.any { it.track == track } }
    }

    private fun subtitleCues(tracks: Set<Long>): List<CueEntry> =
        cueEntries.filter { it.track != null && it.track in tracks && it.relativePosition != null }

    /** Reads the blocks the cues point at, cluster by cluster; nearby blocks share one read. */
    private fun linesFromCues(
        refs: List<CueEntry>,
        wanted: Map<Long, MkvSubtitleTrack>,
        onProgress: (Float) -> Unit,
        parallelism: Int,
        blockWindow: Int,
    ): Map<Long, MutableList<RawLine>> {
        val done = AtomicInteger()
        val failed = AtomicInteger()
        val clusters = refs.groupBy { it.clusterPosition }
        val tasks = clusters.map { (cluster, inCluster) ->
            Callable {
                val found = mutableListOf<Pair<Long, RawLine>>()
                try {
                    val payload = clusterPayload(segmentStart + cluster)
                    for (run in runs(inCluster.sortedBy { it.relativePosition }, blockWindow)) {
                        found += readRun(payload, run, wanted, blockWindow)
                    }
                } catch (e: IOException) {
                    failed.addAndGet(inCluster.size) // A damaged part loses its lines, not the search.
                }
                onProgress(done.addAndGet(inCluster.size).toFloat() / refs.size)
                found
            }
        }
        val results = if (parallelism <= 1) {
            tasks.map { it.call() }
        } else {
            val pool = Executors.newFixedThreadPool(parallelism)
            try {
                pool.invokeAll(tasks).map {
                    try {
                        it.get()
                    } catch (e: ExecutionException) {
                        throw (e.cause as? RuntimeException) ?: IOException(e.cause)
                    }
                }
            } finally {
                pool.shutdownNow()
            }
        }
        // Mostly unreadable means something is wrong with the file or the connection.
        if (failed.get() > refs.size / 2) throw IOException("Could not read the subtitle lines")
        val lines = wanted.keys.associateWith { mutableListOf<RawLine>() }
        results.flatten().forEach { (track, line) -> lines.getValue(track).add(line) }
        return lines
    }

    /** Groups blocks that lie close together, so each group is one read. */
    private fun runs(sorted: List<CueEntry>, window: Int): List<List<CueEntry>> {
        val runs = mutableListOf<MutableList<CueEntry>>()
        for (entry in sorted) {
            val run = runs.lastOrNull()
            if (run != null && entry.relativePosition!! - run.first().relativePosition!! < window) {
                run.add(entry)
            } else {
                runs.add(mutableListOf(entry))
            }
        }
        return runs
    }

    /** Where the data of the cluster at [clusterStart] begins (after its id and size). */
    private fun clusterPayload(clusterStart: Long): Long =
        source.open(clusterStart, clusterStart + CLUSTER_HEADER_MAX - 1).use { stream ->
            val r = EbmlReader(stream, clusterStart, CLUSTER_HEADER_MAX.toLong())
            val h = r.header() ?: throw IOException("Missing cluster")
            if (h.id != ID_CLUSTER) throw IOException("Cue does not point at a cluster")
            h.payload
        }

    /** Reads the blocks of one run (positions relative to the cluster [payload]). */
    private fun readRun(payload: Long, run: List<CueEntry>, wanted: Map<Long, MkvSubtitleTrack>, window: Int): List<Pair<Long, RawLine>> {
        val from = payload + run.first().relativePosition!!
        val to = payload + run.last().relativePosition!! + window - 1
        val found = mutableListOf<Pair<Long, RawLine>>()
        source.open(from, to).use { stream ->
            val r = EbmlReader(stream, from, to - from + 1)
            for (cue in run) {
                val at = payload + cue.relativePosition!!
                if (at < r.position) continue
                r.skip(at - r.position)
                val h = r.header() ?: break
                val block = try {
                    readBlockElement(r, h, wanted)
                } catch (e: IOException) {
                    null // Larger than the window or damaged: this line is skipped.
                } ?: continue
                val (track, data, duration) = block
                val track0 = wanted.getValue(track)
                line(track0, cue.ticks, duration ?: cue.durationTicks, data)?.let { found.add(track to it) }
                if (h.size != EbmlReader.UNKNOWN_SIZE && r.position < h.payload + h.size) r.skip(h.payload + h.size - r.position)
            }
        }
        return found
    }

    /** A SimpleBlock or BlockGroup of a wanted track: (track, data, duration in ticks). */
    private fun readBlockElement(r: EbmlReader, h: EbmlReader.Header, wanted: Map<Long, MkvSubtitleTrack>): Triple<Long, ByteArray, Long?>? =
        when (h.id) {
            ID_SIMPLE_BLOCK -> readBlock(r, h.payload + h.size, wanted)?.let { (track, _, data) -> Triple(track, data, null) }
            ID_BLOCK_GROUP -> {
                var block: Triple<Long, Long, ByteArray>? = null
                var duration: Long? = null
                r.children(h.payload + h.size) { g ->
                    when (g.id) {
                        ID_BLOCK -> block = readBlock(r, g.payload + g.size, wanted)
                        ID_BLOCK_DURATION -> duration = r.uint(g.size)
                    }
                    true
                }
                block?.let { (track, _, data) -> Triple(track, data, duration) }
            }
            else -> null
        }

    /** File position of the cluster to start reading from for [ms]. */
    private fun clusterBefore(ms: Long): Long? =
        cues.lastOrNull { it.first <= ms }?.let { segmentStart + it.second }
            ?: firstCluster
            ?: cues.firstOrNull()?.let { segmentStart + it.second }

    /** Reads clusters from [from] until past [stopMs] or [done] says enough was found. */
    private fun scan(
        from: Long,
        stopMs: Long,
        wanted: Map<Long, MkvSubtitleTrack>,
        raw: Map<Long, MutableList<RawLine>>,
        partialOk: Boolean,
        onClusterMs: (Long) -> Unit = {},
        done: () -> Boolean,
    ) {
        val budget = if (source.isRemote) REMOTE_CLUSTER_BUDGET else LOCAL_CLUSTER_BUDGET
        try {
            source.open(from).use { stream ->
                val r = EbmlReader(stream, from, if (partialOk && source.isRemote) REMOTE_SCENE_BUDGET else budget)
                r.children(end = null, topLevel = true) { top ->
                    when {
                        top.id != ID_CLUSTER -> top.size != EbmlReader.UNKNOWN_SIZE
                        else -> readCluster(r, top, stopMs, wanted, raw, onClusterMs, done)
                    }
                }
            }
        } catch (e: EbmlReader.ReadLimitException) {
            if (!partialOk) throw e
        } catch (e: java.io.EOFException) {
            if (!partialOk) throw e
        }
    }

    /** Returns false once past [stopMs] or when [done]. */
    private fun readCluster(
        r: EbmlReader,
        cluster: EbmlReader.Header,
        stopMs: Long,
        wanted: Map<Long, MkvSubtitleTrack>,
        raw: Map<Long, MutableList<RawLine>>,
        onClusterMs: (Long) -> Unit,
        done: () -> Boolean,
    ): Boolean {
        var clusterTicks = 0L
        val end = if (cluster.size == EbmlReader.UNKNOWN_SIZE) null else cluster.payload + cluster.size
        return r.children(end) { child ->
            when (child.id) {
                ID_CLUSTER_TIMESTAMP -> {
                    clusterTicks = r.uint(child.size)
                    onClusterMs(toMs(clusterTicks))
                    toMs(clusterTicks) <= stopMs && !done()
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
        line(track, ticks, duration, data)?.let { raw.getValue(track.number).add(it) }
    }

    /** The line stored in a block, or null if it is empty or cannot be decoded. */
    private fun line(track: MkvSubtitleTrack, ticks: Long, duration: Long?, data: ByteArray): RawLine? {
        val payload = try {
            when (track.compression) {
                null -> data
                0 -> inflate(data)
                3 -> track.compressionSettings + data
                else -> return null
            }
        } catch (e: Exception) {
            return null // A damaged block loses one line, not the whole search.
        }
        val text = SubtitleTextCleaner.clean(track.codec, String(payload, Charsets.UTF_8))
        if (text.isEmpty()) return null
        return RawLine(toMs(ticks), duration?.let { toMs(ticks + it) }, text)
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
        /** A scene can be a few minutes of an online film. */
        private const val REMOTE_SCENE_BUDGET = 256L * 1024 * 1024
        private const val MAX_SCENE_MS = 15L * 60 * 1000
        private const val LOCAL_CLUSTER_BUDGET = 16L * 1024 * 1024 * 1024
        /** Bytes read for one subtitle block (and from there to the next nearby one). */
        private const val BLOCK_WINDOW = 16 * 1024
        /** A cluster id (4 bytes) and size (up to 8 bytes). */
        private const val CLUSTER_HEADER_MAX = 12
        private const val MAX_LINES_PER_TRACK = 20_000

        /** Reads the file's headers. Null if it is not a Matroska/WebM file. */
        fun open(source: ByteSource): MkvSubtitles? {
            var segmentStart = 0L
            var segmentEnd: Long? = null
            var scale = 1_000_000L
            var durationTicks: Double? = null
            var infoFound = false
            var tracks: List<MkvSubtitleTrack>? = null
            var cues: List<CueEntry>? = null
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
                                val info = payload(r, h)
                                scale = parseScale(info) ?: scale
                                durationTicks = parseDuration(info)
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
            if (!infoFound) readAt(ID_INFO)?.let {
                scale = parseScale(it) ?: scale
                durationTicks = parseDuration(it)
            }
            if (cues == null) cues = runCatching { readAt(ID_CUES)?.let(::parseCues) }.getOrNull()

            val all = tracks ?: return null
            val scaleNs = scale
            return MkvSubtitles(
                source = source,
                segmentStart = segmentStart,
                timestampScaleNs = scaleNs,
                tracks = all,
                cues = cues.orEmpty()
                    .map { it.ticks * scaleNs / 1_000_000 to it.clusterPosition }
                    .distinct()
                    .sortedBy { it.first },
                cueEntries = cues.orEmpty(),
                firstCluster = firstCluster,
                durationMs = durationTicks?.let { (it * scaleNs / 1_000_000).toLong() },
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

        /** Segment duration in timestamp ticks (a float), if present. */
        private fun parseDuration(bytes: ByteArray): Double? {
            val r = reader(bytes)
            var duration: Double? = null
            r.children(bytes.size.toLong()) { e ->
                if (e.id == ID_DURATION) {
                    val raw = r.bytes(e.size, 8)
                    duration = when (raw.size) {
                        4 -> java.lang.Float.intBitsToFloat(raw.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }).toDouble()
                        8 -> java.lang.Double.longBitsToDouble(raw.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) })
                        else -> null
                    }?.takeIf { it.isFinite() && it > 0 }
                }
                true
            }
            return duration
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

        private class Positions(val track: Long?, val cluster: Long?, val relative: Long?, val duration: Long?)

        private fun parseCues(bytes: ByteArray): List<CueEntry> {
            val r = reader(bytes)
            val result = mutableListOf<CueEntry>()
            r.children(bytes.size.toLong()) { point ->
                if (point.id == ID_CUE_POINT) {
                    var time: Long? = null
                    val positions = mutableListOf<Positions>()
                    r.children(point.payload + point.size) { e ->
                        when (e.id) {
                            ID_CUE_TIME -> time = r.uint(e.size)
                            ID_CUE_TRACK_POSITIONS -> {
                                var track: Long? = null
                                var cluster: Long? = null
                                var relative: Long? = null
                                var duration: Long? = null
                                r.children(e.payload + e.size) { p ->
                                    when (p.id) {
                                        ID_CUE_TRACK -> track = r.uint(p.size)
                                        ID_CUE_CLUSTER_POSITION -> cluster = r.uint(p.size)
                                        ID_CUE_RELATIVE_POSITION -> relative = r.uint(p.size)
                                        ID_CUE_DURATION -> duration = r.uint(p.size)
                                    }
                                    true
                                }
                                positions.add(Positions(track, cluster, relative, duration))
                            }
                        }
                        true
                    }
                    val t = time
                    if (t != null) {
                        positions.forEach { p ->
                            p.cluster?.let { result.add(CueEntry(t, p.track, it, p.relative, p.duration)) }
                        }
                    }
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
