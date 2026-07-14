package com.raichess.domain.usecase

import com.raichess.domain.model.ThemeTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeaknessProfilerTest {

    private fun obs(gamesAgo: Int, vararg themes: ThemeTag, lossCp: Int = 300) =
        MistakeObservation(gamesAgo, themes.toSet(), lossCp)

    @Test
    fun `weight halves at the half-life`() {
        val profile = WeaknessProfiler.build(
            listOf(
                obs(0, ThemeTag.HANGING_PIECE),
                obs(WeaknessProfiler.HALF_LIFE_GAMES.toInt(), ThemeTag.MISSED_CAPTURE)
            )
        )
        val hanging = profile.first { it.theme == ThemeTag.HANGING_PIECE }
        val missed = profile.first { it.theme == ThemeTag.MISSED_CAPTURE }
        assertEquals(1.0, hanging.score, 1e-9)
        assertEquals(0.5, missed.score, 1e-9)
    }

    @Test
    fun `recent mistakes outrank frequent-but-old ones`() {
        // Two hangs from the latest games vs. three missed captures ~4 half-lives ago:
        // the old habit has decayed below the current one.
        val profile = WeaknessProfiler.build(
            listOf(
                obs(0, ThemeTag.HANGING_PIECE),
                obs(1, ThemeTag.HANGING_PIECE),
                obs(80, ThemeTag.MISSED_CAPTURE),
                obs(80, ThemeTag.MISSED_CAPTURE),
                obs(80, ThemeTag.MISSED_CAPTURE)
            )
        )
        assertEquals(ThemeTag.HANGING_PIECE, profile.first().theme)
        val old = profile.first { it.theme == ThemeTag.MISSED_CAPTURE }
        assertEquals(3, old.occurrences) // raw count preserved for display
        assertTrue(old.score < 0.25)     // but the weight has decayed away
    }

    @Test
    fun `one observation feeds every theme it carries`() {
        val profile = WeaknessProfiler.build(
            listOf(obs(0, ThemeTag.HANGING_PIECE, ThemeTag.ENDGAME, lossCp = 500))
        )
        assertEquals(2, profile.size)
        assertTrue(profile.all { it.avgLossCp == 500.0 && it.occurrences == 1 })
    }

    @Test
    fun `average severity is per theme`() {
        val profile = WeaknessProfiler.build(
            listOf(
                obs(0, ThemeTag.HANGING_PIECE, lossCp = 900),
                obs(2, ThemeTag.HANGING_PIECE, lossCp = 300),
                obs(1, ThemeTag.MISSED_MATE, lossCp = 1000)
            )
        )
        assertEquals(600.0, profile.first { it.theme == ThemeTag.HANGING_PIECE }.avgLossCp, 1e-9)
        assertEquals(1000.0, profile.first { it.theme == ThemeTag.MISSED_MATE }.avgLossCp, 1e-9)
    }

    @Test
    fun `empty history yields an empty profile`() {
        assertTrue(WeaknessProfiler.build(emptyList()).isEmpty())
    }
}
