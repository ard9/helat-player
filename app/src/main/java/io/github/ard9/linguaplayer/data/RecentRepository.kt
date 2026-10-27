package io.github.ard9.linguaplayer.data

import android.content.Intent
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** The last few files and links the user opened, and where playback stopped in each. */
class RecentRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val prefs = context.getSharedPreferences("recents", Context.MODE_PRIVATE)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<PlaybackSource>> = _items

    private val _positions = MutableStateFlow(loadPositions())
    /** Saved playback positions in ms, by URI string. */
    val positions: StateFlow<Map<String, Long>> = _positions

    fun add(source: PlaybackSource) =
        save((listOf(source) + _items.value.filterNot { it.uri == source.uri }).take(MAX_ITEMS))

    fun remove(source: PlaybackSource) {
        save(_items.value.filterNot { it.uri == source.uri })
        savePositions(_positions.value - source.uri.toString())
        releaseAccess(listOf(source))
    }

    /** Forgets every recent video and every saved position. */
    fun clear() {
        val removed = _items.value
        save(emptyList())
        savePositions(emptyMap())
        releaseAccess(removed)
    }

    /** Gives back the lasting read access to forgotten local files (kept only to reopen them). */
    private fun releaseAccess(sources: List<PlaybackSource>) {
        sources.filter { it.uri.scheme == "content" }.forEach { source ->
            try {
                resolver.releasePersistableUriPermission(source.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // There was no lasting access (e.g. opened with "Open with").
            }
        }
    }

    /** Remembers where playback stopped; forgets it near the start or the end. */
    fun savePosition(uri: Uri, positionMs: Long, lengthMs: Long) {
        val key = uri.toString()
        val keep = ResumePolicy.positionToKeep(positionMs, lengthMs)
        val updated = if (keep == null) _positions.value - key else _positions.value + (key to keep)
        if (updated != _positions.value) savePositions(updated)
    }

    private fun save(list: List<PlaybackSource>) {
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("uri", it.uri.toString()).put("title", it.title)) }
        prefs.edit().putString(KEY, array.toString()).apply()
        _items.value = list
    }

    private fun savePositions(map: Map<String, Long>) {
        // Only items still in the recent list are worth remembering.
        val known = _items.value.map { it.uri.toString() }.toSet()
        val kept = map.filterKeys { it in known }
        val json = JSONObject().apply { kept.forEach { (uri, ms) -> put(uri, ms) } }
        prefs.edit().putString(KEY_POSITIONS, json.toString()).apply()
        _positions.value = kept
    }

    private fun load(): List<PlaybackSource> = try {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        List(array.length()) {
            val o = array.getJSONObject(it)
            PlaybackSource(Uri.parse(o.getString("uri")), o.getString("title"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun loadPositions(): Map<String, Long> = try {
        val json = JSONObject(prefs.getString(KEY_POSITIONS, "{}") ?: "{}")
        json.keys().asSequence().associateWith { json.getLong(it) }.filterValues { it > 0 }
    } catch (e: Exception) {
        emptyMap()
    }

    private companion object {
        const val KEY = "items"
        const val KEY_POSITIONS = "positions"
        const val MAX_ITEMS = 10
    }
}

/** When a playback position is worth resuming from. */
object ResumePolicy {
    /** Too close to the start: just start again. */
    const val MIN_MS = 10_000L
    /** Within this of the end the video counts as watched. */
    const val END_MS = 30_000L

    fun positionToKeep(positionMs: Long, lengthMs: Long): Long? = when {
        positionMs < MIN_MS -> null
        lengthMs > 0 && positionMs > lengthMs - END_MS -> null
        else -> positionMs
    }
}
