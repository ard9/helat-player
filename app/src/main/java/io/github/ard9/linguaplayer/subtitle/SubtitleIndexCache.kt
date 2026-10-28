package io.github.ard9.linguaplayer.subtitle

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Keeps the subtitle lines of whole films (for searching) in the app's cache folder, so a film
 * is read only once. Files are named by a hash of the video's address, so links with access
 * tokens are not written in clear. Only the [MAX_FILES] most recent films are kept.
 */
class SubtitleIndexCache(private val folder: File) {
    /** The lines saved for [uri], if they were saved for exactly these [tracks]. */
    fun load(uri: String, tracks: Set<Long>): Map<Long, List<SubtitleLine>>? {
        val file = fileFor(uri)
        if (!file.isFile || file.length() > MAX_BYTES) return null
        return try {
            val root = JSONObject(file.readText())
            if (root.optInt("format") != FORMAT) return null
            val saved = root.getJSONObject("tracks")
            val result = saved.keys().asSequence().associate { key ->
                val array = saved.getJSONArray(key)
                key.toLong() to (0 until array.length()).map { i ->
                    val line = array.getJSONArray(i)
                    SubtitleLine(line.getLong(0), line.getLong(1), line.getString(2))
                }
            }
            if (result.keys != tracks) return null
            file.setLastModified(System.currentTimeMillis())
            result
        } catch (e: Exception) {
            file.delete() // Damaged; it is only a cache.
            null
        }
    }

    fun save(uri: String, lines: Map<Long, List<SubtitleLine>>) {
        folder.mkdirs()
        val tracks = JSONObject()
        lines.forEach { (track, list) ->
            tracks.put(track.toString(), JSONArray().apply {
                list.forEach { put(JSONArray().put(it.startMs).put(it.endMs).put(it.text)) }
            })
        }
        val text = JSONObject().put("format", FORMAT).put("tracks", tracks).toString()
        if (text.length > MAX_BYTES) return
        val target = fileFor(uri)
        val tmp = File(folder, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) tmp.delete()
        trim()
    }

    private fun trim() {
        val files = folder.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedByDescending { it.lastModified() }
        files.drop(MAX_FILES).forEach { it.delete() }
    }

    private fun fileFor(uri: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(folder, "$hash.json")
    }

    companion object {
        const val FORMAT = 1
        const val MAX_FILES = 20
        const val MAX_BYTES = 20L * 1024 * 1024
    }
}
