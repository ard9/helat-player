package io.github.ard9.linguaplayer.subtitle

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class SubtitleLine(val startMs: Long, val endMs: Long, val text: String)

/** Parses SRT and WebVTT files into timed lines. */
object SubtitleParser {
    private const val MAX_BYTES = 5 * 1024 * 1024

    private val TIMING = Regex(
        """((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})\s*-->\s*((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})"""
    )
    private val HTML_TAG = Regex("<[^>]*>")
    private val ASS_TAG = Regex("""\{\\[^}]*\}""")

    /** A subtitle file over [MAX_BYTES]. */
    class TooLargeException : IOException("File is larger than 5 MB")

    /** Reads a subtitle file, refusing files that are too large to be real subtitles. */
    fun readLimited(resolver: ContentResolver, uri: Uri): ByteArray {
        val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open the file")
        input.use {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val n = it.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) throw TooLargeException()
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }

    fun parse(bytes: ByteArray): List<SubtitleLine> = parseText(decode(bytes))

    fun parseText(text: String): List<SubtitleLine> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val result = mutableListOf<SubtitleLine>()
        var i = 0
        while (i < lines.size) {
            val match = TIMING.find(lines[i])
            if (match == null) {
                i++
                continue
            }
            val start = parseTime(match.groupValues[1])
            val end = parseTime(match.groupValues[2])
            i++
            val textLines = mutableListOf<String>()
            while (i < lines.size && lines[i].isNotBlank() && !startsNextBlock(lines, i)) {
                textLines.add(lines[i])
                i++
            }
            val cleaned = clean(textLines.joinToString("\n"))
            if (start != null && end != null && end > start && cleaned.isNotBlank()) {
                result.add(SubtitleLine(start, end, cleaned))
            }
        }
        return result.sortedBy { it.startMs }
    }

    /** True if line [i] is a timing line, or an SRT index followed by a timing line. */
    private fun startsNextBlock(lines: List<String>, i: Int): Boolean {
        if (TIMING.containsMatchIn(lines[i])) return true
        val trimmed = lines[i].trim()
        return trimmed.isNotEmpty() && trimmed.all { it.isDigit() } &&
            i + 1 < lines.size && TIMING.containsMatchIn(lines[i + 1])
    }

    private fun parseTime(value: String): Long? {
        val parts = value.replace(',', '.').split(':')
        if (parts.size !in 2..3) return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        val minutes = parts[parts.size - 2].toLongOrNull() ?: return null
        val secParts = parts.last().split('.')
        if (secParts.size != 2) return null
        val seconds = secParts[0].toLongOrNull() ?: return null
        val millis = secParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: return null
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
    }

    private fun clean(text: String): String = text
        .replace(ASS_TAG, "")
        .replace(HTML_TAG, "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .lines()
        .joinToString("\n") { it.trim() }
        .trim()

    /** UTF-8 (with or without BOM), UTF-16 with BOM, otherwise Windows-1252. */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 &&
            ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
                (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()))
        ) {
            return String(bytes, Charsets.UTF_16)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }
}

object SubtitleLookup {
    fun activeAt(lines: List<SubtitleLine>, positionMs: Long): SubtitleLine? =
        lines.lastOrNull { positionMs in it.startMs..it.endMs }

    /**
     * The line on screen at [positionMs] (the latest if several), or one that ended within the
     * last few seconds, for a Save tapped just after the line.
     */
    fun nearMoment(lines: List<SubtitleLine>, positionMs: Long): SubtitleLine? {
        lines.filter { positionMs >= it.startMs && positionMs <= it.endMs + 150 }
            .maxByOrNull { it.startMs }
            ?.let { return it }
        return lines.lastOrNull { it.endMs <= positionMs && positionMs - it.endMs <= 5_000 }
    }

    /** Up to [count] lines that are on screen at [fromMs] or come after it, in time order. */
    fun scene(lines: List<SubtitleLine>, fromMs: Long, count: Int): List<SubtitleLine> =
        lines.filter { it.endMs > fromMs }.sortedBy { it.startMs }.take(count)

    /** Lines that start in [fromMs, toMs), in time order. */
    fun between(lines: List<SubtitleLine>, fromMs: Long, toMs: Long): List<SubtitleLine> =
        lines.filter { it.startMs in fromMs until toMs }.sortedBy { it.startMs }

    /** Text of the lines that overlap [line] in time (e.g. its translation), or null. */
    fun overlapping(lines: List<SubtitleLine>, line: SubtitleLine): String? {
        val mid = (line.startMs + line.endMs) / 2
        val matches = lines.filter { it.startMs < line.endMs - 200 && it.endMs > line.startMs + 200 }
            .ifEmpty { lines.filter { mid in it.startMs..it.endMs } }
        return matches.joinToString("\n") { it.text }.ifBlank { null }
    }

    /** The line being spoken now, or the last one that already finished. */
    fun lineForSave(lines: List<SubtitleLine>, positionMs: Long): SubtitleLine? =
        activeAt(lines, positionMs) ?: lines.lastOrNull { it.endMs <= positionMs }
}
