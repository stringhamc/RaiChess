package com.raichess.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTagTest {

    @Test
    fun `csv round-trips`() {
        val tags = setOf(ThemeTag.HANGING_PIECE, ThemeTag.ENDGAME, ThemeTag.MISSED_MATE)
        assertEquals(tags, ThemeTag.fromCsv(ThemeTag.toCsv(tags)))
    }

    @Test
    fun `csv is stable and ordinal-ordered`() {
        assertEquals(
            "hanging_piece,missed_mate,endgame",
            ThemeTag.toCsv(setOf(ThemeTag.ENDGAME, ThemeTag.MISSED_MATE, ThemeTag.HANGING_PIECE))
        )
    }

    @Test
    fun `unknown ids and blanks are skipped on read`() {
        assertEquals(
            setOf(ThemeTag.HANGING_PIECE),
            ThemeTag.fromCsv("hanging_piece,super_future_tag, ,")
        )
        assertTrue(ThemeTag.fromCsv("").isEmpty())
    }

    @org.junit.Test
    fun `explain prefers mates and skips phase-only sets`() {
        assertEquals(
            ThemeTag.ALLOWED_MATE.explanation,
            ThemeTag.explain(setOf(ThemeTag.HANGING_PIECE, ThemeTag.ALLOWED_MATE))
        )
        assertEquals(
            ThemeTag.HANGING_PIECE.explanation,
            ThemeTag.explain(setOf(ThemeTag.HANGING_PIECE, ThemeTag.MIDDLEGAME))
        )
        assertEquals(null, ThemeTag.explain(setOf(ThemeTag.ENDGAME)))
        assertEquals(null, ThemeTag.explain(emptySet()))
    }

    @org.junit.Test
    fun `every substantive tag has an explanation`() {
        val substantive = ThemeTag.entries.filterNot { it.isPhase }
        assertTrue(substantive.all { it.explanation != null })
        assertTrue(ThemeTag.entries.filter { it.isPhase }.all { it.explanation == null })
    }
}
