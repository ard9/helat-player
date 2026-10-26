package io.github.ard9.linguaplayer.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

/**
 * Stores cards in a JSON file and their audio clips in app-private storage.
 * The data set is small, so the whole list is kept in memory.
 */
class CardRepository(context: Context) {
    private val file = File(context.filesDir, "cards.json")
    private val clipsDir = File(context.filesDir, "clips").apply { mkdirs() }
    private val imagesDir = File(context.filesDir, "images").apply { mkdirs() }

    private val _cards = MutableStateFlow(load())
    val cards: StateFlow<List<Card>> = _cards

    /** Where a new clip with this card id should be written. */
    fun newClipFile(id: String, extension: String): File = File(clipsDir, "$id.$extension")

    fun audioFile(card: Card): File = File(clipsDir, File(card.audioFileName).name)

    /** Where the picture of a new card with this id should be written. */
    fun newImageFile(id: String): File = File(imagesDir, "$id.jpg")

    fun imageFile(card: Card): File? = card.imageFileName?.let { File(imagesDir, File(it).name) }

    @Synchronized
    fun add(card: Card) = save(_cards.value + card)

    @Synchronized
    fun update(card: Card) = save(_cards.value.map { if (it.id == card.id) card else it })

    @Synchronized
    fun delete(card: Card) {
        audioFile(card).delete()
        imageFile(card)?.delete()
        save(_cards.value.filterNot { it.id == card.id })
    }

    private fun save(list: List<Card>) {
        val json = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        _cards.value = list
    }

    private fun load(): List<Card> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            List(array.length()) { Card.fromJson(array.getJSONObject(it)) }
        } catch (e: Exception) {
            // Keep the broken file instead of silently overwriting the user's cards.
            Log.e(TAG, "Could not read cards file", e)
            file.renameTo(File(file.parentFile, "cards.corrupt-${System.currentTimeMillis()}.json"))
            emptyList()
        }
    }

    private companion object {
        const val TAG = "CardRepository"
    }
}
