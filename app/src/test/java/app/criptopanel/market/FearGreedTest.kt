package app.criptopanel.market

import app.criptopanel.decodeFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FearGreedTest {
    @Test fun `labels follow alternative me bands`() {
        val cases = mapOf(
            0 to "Miedo extremo", 24 to "Miedo extremo", 25 to "Miedo", 44 to "Miedo", 45 to "Neutral",
            55 to "Neutral", 56 to "Codicia", 75 to "Codicia", 76 to "Codicia extrema", 100 to "Codicia extrema",
        )
        cases.forEach { (value, label) -> assertEquals("$value", label, fearGreedLabel(value)) }
    }

    @Test fun `fixture maps newest as today and history oldest first`() {
        val fg = decodeFixture<FearGreedResponseDto>("fng.json").toDomain()!!
        val raw = decodeFixture<FearGreedResponseDto>("fng.json").data
        assertEquals(raw[0].value!!.toInt(), fg.value)
        assertEquals(raw[1].value!!.toInt(), fg.yesterday)
        assertEquals(3, fg.history.size)
        assertEquals(fg.history.sortedBy { it.epochSeconds }, fg.history)
    }

    @Test fun `malformed points are skipped`() {
        val dto = FearGreedResponseDto(listOf(FearGreedDto("abc", "1"), FearGreedDto("40", "2"), FearGreedDto("50", null)))
        val fg = dto.toDomain()!!
        assertEquals(40, fg.value)
        assertNull(fg.yesterday)
    }

    @Test fun `no points gives null`() = assertNull(FearGreedResponseDto().toDomain())
}
