package app.criptopanel.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Collator
import java.util.Locale

class LearnContentTest {
    @Test fun `ten lessons with unique ids`() {
        assertEquals(10, LESSONS.size)
        assertEquals(LESSONS.size, LESSONS.map { it.id }.toSet().size)
    }

    @Test fun `every lesson is complete`() {
        LESSONS.forEach { l ->
            assertTrue(l.id, l.blocks.isNotEmpty() && l.summary.isNotEmpty() && l.minutes > 0)
            assertEquals(l.id, 3, l.quiz.size)
            l.quiz.forEach { q ->
                assertTrue(q.text, q.answer in q.options.indices && q.options.size >= 2 && q.why.isNotBlank())
            }
            val texts = l.blocks.flatMap { b ->
                when (b) {
                    is Block.Heading -> listOf(b.text)
                    is Block.Paragraph -> listOf(b.text)
                    is Block.Bullets -> b.items
                    is Block.Callout -> listOf(b.text)
                    is Block.Table -> b.rows.flatten().drop(1)
                }
            }
            assertTrue(l.id, texts.all { it.isNotBlank() })
            l.blocks.filterIsInstance<Block.Table>().forEach { t -> assertTrue(l.id, t.rows.all { it.size == t.rows.first().size }) }
        }
    }

    @Test fun `glossary sorted without duplicates`() {
        val es = Collator.getInstance(Locale("es", "ES"))
        val names = GLOSSARY.map { it.name }
        assertEquals(names.sortedWith(es), names)
        assertEquals(names.size, names.map { it.lowercase() }.toSet().size)
        assertTrue(GLOSSARY.size >= 40)
    }

    @Test fun `glossary search matches names and definitions`() {
        assertEquals(listOf("DEX"), searchGlossary("dex").map { it.name }.filter { it == "DEX" })
        assertTrue(searchGlossary("frase").any { it.name == "Frase semilla" })
        assertEquals(GLOSSARY, searchGlossary(" "))
    }
}
