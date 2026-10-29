package io.github.ard9.linguaplayer.stream

import java.io.File

/**
 * Pieces ("chunks") of online films kept on disk, so bytes already downloaded are not
 * downloaded again when playback jumps back. All films share [maxBytes]; the least recently
 * used chunks are removed first. Each film has its own folder, named by a key that does not
 * contain its address. Thread-safe.
 */
class ChunkStore(private val root: File, private val maxBytes: Long, val chunkSize: Int = DEFAULT_CHUNK) {
    /** Every chunk file, least recently used first. */
    private val lru = LinkedHashMap<File, Long>(16, 0.75f, true)
    private var totalBytes = 0L
    private var loaded = false

    private fun load() {
        if (loaded) return
        loaded = true
        root.mkdirs()
        root.listFiles().orEmpty().filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles { f -> f.name.endsWith(SUFFIX) }.orEmpty().toList() }
            .sortedBy { it.lastModified() }
            .forEach { lru[it] = it.length(); totalBytes += it.length() }
        trim()
    }

    /**
     * Prepares the film [key] of [totalLength] bytes. Chunks kept for a file of another
     * length are dropped (the file at that address changed).
     */
    @Synchronized
    fun prepare(key: String, totalLength: Long) {
        load()
        val dir = File(root, key)
        val meta = File(dir, META)
        if (meta.isFile && meta.readText().trim().toLongOrNull() == totalLength) return
        dir.listFiles().orEmpty().forEach { file ->
            lru.remove(file)?.let { totalBytes -= it }
            file.delete()
        }
        dir.mkdirs()
        meta.writeText(totalLength.toString())
    }

    /** The length [prepare] recorded for film [key], or null if it was never prepared. */
    @Synchronized
    fun knownLength(key: String): Long? {
        load()
        val meta = File(File(root, key), META)
        return if (meta.isFile) meta.readText().trim().toLongOrNull() else null
    }

    /** Whether chunk [index] of film [key] is kept with the [expectedSize] (no read, no reordering). */
    @Synchronized
    fun has(key: String, index: Long, expectedSize: Int): Boolean {
        load()
        val file = chunkFile(key, index)
        return lru.containsKey(file) && file.length() == expectedSize.toLong()
    }

    /** Chunk [index] of film [key], if kept and of the [expectedSize]. */
    @Synchronized
    fun get(key: String, index: Long, expectedSize: Int): ByteArray? {
        load()
        val file = chunkFile(key, index)
        if (lru[file] == null) return null // Also marks it as recently used.
        if (file.length() != expectedSize.toLong()) {
            remove(file)
            return null
        }
        return try {
            file.readBytes()
        } catch (e: Exception) {
            remove(file)
            null
        }
    }

    @Synchronized
    fun put(key: String, index: Long, data: ByteArray) {
        load()
        if (data.size > maxBytes) return
        val dir = File(root, key).apply { mkdirs() }
        val file = chunkFile(key, index)
        val tmp = File(dir, "${file.name}.tmp")
        try {
            tmp.writeBytes(data)
            if (!tmp.renameTo(file)) {
                tmp.delete()
                return
            }
        } catch (e: Exception) {
            tmp.delete() // Disk full or similar: just not cached.
            return
        }
        lru.remove(file)?.let { totalBytes -= it }
        lru[file] = data.size.toLong()
        totalBytes += data.size
        trim()
    }

    /** Bytes kept for all films. */
    @Synchronized
    fun size(): Long {
        load()
        return totalBytes
    }

    private fun trim() {
        val it = lru.entries.iterator()
        while (totalBytes > maxBytes && it.hasNext()) {
            val (file, size) = it.next()
            it.remove()
            totalBytes -= size
            file.delete()
        }
    }

    private fun remove(file: File) {
        lru.remove(file)?.let { totalBytes -= it }
        file.delete()
    }

    private fun chunkFile(key: String, index: Long) = File(File(root, key), "$index$SUFFIX")

    companion object {
        const val DEFAULT_CHUNK = 256 * 1024
        private const val SUFFIX = ".chunk"
        private const val META = "length"
    }
}
