package io.github.ard9.linguaplayer.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup of all cards as one ZIP file: `cards.json` first, then `clips/…` and `images/…`.
 * Importing treats the file as untrusted: only files named by new cards are written, never
 * outside the given folders, never over existing files, and with size limits.
 */
object CardBackup {
    const val FORMAT = 1
    private const val CARDS_ENTRY = "cards.json"
    private const val MAX_CARDS_JSON = 20L * 1024 * 1024
    private const val MAX_FILE = 64L * 1024 * 1024

    /** Why a file cannot be restored; the UI turns [reason] into a message. */
    enum class Reason { NotABackup, Damaged, Newer, TooLarge }

    class InvalidBackupException(val reason: Reason) : IOException(reason.name)

    /** [added] are new cards whose files were restored; [skipped] were already present or incomplete. */
    class ImportResult(val added: List<Card>, val skipped: Int)

    fun export(out: OutputStream, cards: List<Card>, audioFile: (Card) -> File, imageFile: (Card) -> File?) {
        ZipOutputStream(out).use { zip ->
            val json = JSONObject()
                .put("format", FORMAT)
                .put("cards", JSONArray().apply { cards.forEach { put(it.toJson()) } })
            zip.putNextEntry(ZipEntry(CARDS_ENTRY))
            zip.write(json.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (card in cards) {
                addFile(zip, "clips/", audioFile(card))
                imageFile(card)?.let { addFile(zip, "images/", it) }
            }
        }
    }

    private fun addFile(zip: ZipOutputStream, folder: String, file: File) {
        if (!file.isFile) return
        zip.putNextEntry(ZipEntry(folder + file.name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /**
     * Restores cards that are not in [existingIds]. Files go to [clipsDir] and [imagesDir].
     * On any error, files written by this import are removed and the error is thrown.
     */
    fun import(input: InputStream, existingIds: Set<String>, clipsDir: File, imagesDir: File): ImportResult {
        val written = mutableListOf<File>()
        try {
            ZipInputStream(input).use { zip ->
                val first = zip.nextEntry
                if (first == null || first.name != CARDS_ENTRY) {
                    throw InvalidBackupException(Reason.NotABackup)
                }
                val all = parseCards(String(readLimited(zip, MAX_CARDS_JSON), Charsets.UTF_8))
                val candidates = all.filter { it.id !in existingIds }.distinctBy { it.id }

                // File name (as stored in the ZIP) -> where it may be written.
                val wanted = mutableMapOf<String, File>()
                for (card in candidates) {
                    safeName(card.audioFileName)?.let { wanted["clips/$it"] = File(clipsDir, it) }
                    card.imageFileName?.let(::safeName)?.let { wanted["images/$it"] = File(imagesDir, it) }
                }

                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = wanted.remove(entry.name) ?: continue
                    if (entry.isDirectory || target.exists()) continue // Never overwrite.
                    val tmp = File(target.parentFile, target.name + ".part")
                    written.add(tmp)
                    tmp.outputStream().use { out -> copyLimited(zip, out, MAX_FILE) }
                    if (!tmp.renameTo(target)) throw IOException("Could not save ${target.name}")
                    written.remove(tmp)
                    written.add(target)
                }

                val restored = written.toSet()
                // A card is kept only if its audio was restored by this import.
                val added = candidates.mapNotNull { card ->
                    val audio = safeName(card.audioFileName)?.let { File(clipsDir, it) }
                    if (audio == null || audio !in restored) return@mapNotNull null
                    val image = card.imageFileName?.let(::safeName)?.let { File(imagesDir, it) }
                    card.copy(
                        audioFileName = audio.name,
                        imageFileName = image?.takeIf { it in restored }?.name,
                    )
                }
                // Restored files of cards that were dropped are not needed.
                val keep = added.flatMap { card ->
                    listOfNotNull(File(clipsDir, card.audioFileName), card.imageFileName?.let { File(imagesDir, it) })
                }.toSet()
                written.filter { it !in keep }.forEach { it.delete() }
                return ImportResult(added, skipped = all.size - added.size)
            }
        } catch (e: Exception) {
            written.forEach { it.delete() }
            throw if (e is IOException) e else InvalidBackupException(Reason.Damaged)
        }
    }

    private fun parseCards(json: String): List<Card> {
        val root = JSONObject(json)
        if (root.optInt("format", 0) !in 1..FORMAT) throw InvalidBackupException(Reason.Newer)
        val array = root.getJSONArray("cards")
        return List(array.length()) { Card.fromJson(array.getJSONObject(it)) }
    }

    /** A plain file name, or null if it tries to reach another folder. */
    private fun safeName(name: String): String? {
        if (name.isBlank() || name.length > 200) return null
        if (name.contains('/') || name.contains('\\') || name == "." || name == "..") return null
        if (name.any { it.isISOControl() }) return null
        return name
    }

    private fun readLimited(input: InputStream, max: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        copyLimited(input, out, max)
        return out.toByteArray()
    }

    private fun copyLimited(input: InputStream, out: OutputStream, max: Long) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > max) throw InvalidBackupException(Reason.TooLarge)
            out.write(buffer, 0, n)
        }
    }
}
