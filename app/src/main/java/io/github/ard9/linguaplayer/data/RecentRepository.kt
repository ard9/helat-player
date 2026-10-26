package io.github.ard9.linguaplayer.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** The last few files and links the user opened. */
class RecentRepository(context: Context) {
    private val prefs = context.getSharedPreferences("recents", Context.MODE_PRIVATE)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<PlaybackSource>> = _items

    fun add(source: PlaybackSource) =
        save((listOf(source) + _items.value.filterNot { it.uri == source.uri }).take(MAX_ITEMS))

    fun remove(source: PlaybackSource) = save(_items.value.filterNot { it.uri == source.uri })

    private fun save(list: List<PlaybackSource>) {
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("uri", it.uri.toString()).put("title", it.title)) }
        prefs.edit().putString(KEY, array.toString()).apply()
        _items.value = list
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

    private companion object {
        const val KEY = "items"
        const val MAX_ITEMS = 10
    }
}
