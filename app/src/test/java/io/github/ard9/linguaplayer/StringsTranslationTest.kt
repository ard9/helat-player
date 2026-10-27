package io.github.ard9.linguaplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Every English UI string has a Persian translation with the same placeholders. */
class StringsTranslationTest {
    /** One string or plural item: its name (with the quantity for plurals) and text. */
    private data class Entry(val key: String, val text: String, val translatable: Boolean)

    private fun read(folder: String): List<Entry> {
        val file = File("src/main/res/$folder/strings.xml")
        assertTrue("Missing ${file.absolutePath}", file.isFile)
        val factory = DocumentBuilderFactory.newInstance().apply {
            // Plain resource files: no DTDs or external entities.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val root = factory.newDocumentBuilder().parse(file).documentElement
        val entries = mutableListOf<Entry>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            val translatable = node.getAttribute("translatable") != "false"
            when (node.tagName) {
                "string" -> entries += Entry(name, node.textContent, translatable)
                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val item = items.item(j) as Element
                        entries += Entry("$name/${item.getAttribute("quantity")}", item.textContent, translatable)
                    }
                }
            }
        }
        return entries
    }

    private val english = read("values")
    private val persian = read("values-fa")

    /** "%1$d", "%s", "%.1f" … in order of position, so the arguments match. */
    private fun placeholders(text: String): List<String> =
        Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[a-zA-Z]").findAll(text).map { it.value }
            .map { it.replaceFirst(Regex("^%\\d+\\$"), "%") }
            .sorted()
            .toList()

    @Test
    fun `every string is translated`() {
        val names = persian.map { it.key.substringBefore('/') }.toSet()
        val missing = english.filter { it.translatable }.map { it.key.substringBefore('/') }.toSet() - names
        assertEquals("Missing in values-fa", emptySet<String>(), missing)
    }

    @Test
    fun `no stale or untranslatable strings in the translation`() {
        val translatable = english.filter { it.translatable }.map { it.key.substringBefore('/') }.toSet()
        val extra = persian.map { it.key.substringBefore('/') }.toSet() - translatable
        assertEquals("Not (or no longer) translatable", emptySet<String>(), extra)
    }

    @Test
    fun `translations keep the placeholders`() {
        val byName = english.associateBy { it.key }
        for (entry in persian) {
            // Persian has the same plural quantities as English here (one, other).
            val source = byName[entry.key] ?: byName[entry.key.substringBefore('/') + "/other"] ?: continue
            assertEquals("Placeholders of ${entry.key}", placeholders(source.text), placeholders(entry.text))
        }
    }

    @Test
    fun `the placeholder pattern finds what it should`() {
        assertEquals(listOf("%.1f", "%d", "%s"), placeholders("%s and %1\$d at %.1f"))
    }
}
