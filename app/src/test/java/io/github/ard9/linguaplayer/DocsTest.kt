package io.github.ard9.linguaplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the agent docs usable: AGENTS.md links to docs that exist, every doc is linked, and no
 * doc grows past the length an agent can read comfortably (split it instead).
 */
class DocsTest {
    // Unit tests run in the app module folder.
    private val root = File("..").canonicalFile
    private val agents = File(root, "AGENTS.md")
    private val docs = File(root, "docs")

    private fun linked(): Set<String> =
        Regex("`(docs/[\\w.-]+\\.md)`").findAll(agents.readText()).map { it.groupValues[1] }.toSet()

    @Test
    fun `every doc named in AGENTS md exists`() {
        assertTrue("Missing ${agents.path}", agents.isFile)
        val missing = linked().filterNot { File(root, it).isFile }
        assertEquals("Named in AGENTS.md but missing", emptyList<String>(), missing)
    }

    @Test
    fun `every doc is listed in AGENTS md`() {
        val files = docs.listFiles { f -> f.extension == "md" }.orEmpty().map { "docs/${it.name}" }.toSet()
        assertEquals("In docs/ but not listed in AGENTS.md", emptySet<String>(), files - linked())
    }

    @Test
    fun `no doc is too long`() {
        val long = (listOf(agents) + docs.listFiles { f -> f.extension == "md" }.orEmpty())
            .filter { it.readLines().size > MAX_LINES }
            .map { "${it.name}: ${it.readLines().size} lines" }
        assertEquals("Split these docs (max $MAX_LINES lines)", emptyList<String>(), long)
    }

    private companion object {
        const val MAX_LINES = 200
    }
}
