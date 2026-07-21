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
        val hanging = profile.weaknesses.first { it.theme == ThemeTag.HANGING_PIECE }
        val missed = profile.weaknesses.first { it.theme == ThemeTag.MISSED_CAPTURE }
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
        assertEquals(ThemeTag.HANGING_PIECE, profile.weaknesses.first().theme)
        val old = profile.weaknesses.first { it.theme == ThemeTag.MISSED_CAPTURE }
        assertEquals(3, old.occurrences) // raw count preserved for display
        assertTrue(old.score < 0.25)     // but the weight has decayed away
    }

    @Test
    fun `phase tags rank separately from substantive weaknesses`() {
        // Every observation carries a phase tag, so mixed ranking would put
        // MIDDLEGAME on top by construction — the split keeps the actionable
        // weakness first in its own list.
        val profile = WeaknessProfiler.build(
            listOf(
                obs(0, ThemeTag.HANGING_PIECE, ThemeTag.MIDDLEGAME),
                obs(1, ThemeTag.MIDDLEGAME),
                obs(2, ThemeTag.MIDDLEGAME)
            )
        )
        assertEquals(ThemeTag.HANGING_PIECE, profile.weaknesses.single().theme)
        assertEquals(ThemeTag.MIDDLEGAME, profile.phases.single().theme)
        assertEquals(3, profile.phases.single().occurrences)
    }

    @Test
    fun `one observation feeds every theme it carries`() {
        val profile = WeaknessProfiler.build(
            listOf(obs(0, ThemeTag.HANGING_PIECE, ThemeTag.ENDGAME, lossCp = 500))
        )
        assertEquals(1, profile.weaknesses.size)
        assertEquals(1, profile.phases.size)
        val all = profile.weaknesses + profile.phases
        assertTrue(all.all { it.avgLossCp == 500.0 && it.occurrences == 1 })
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
        assertEquals(
            600.0,
            profile.weaknesses.first { it.theme == ThemeTag.HANGING_PIECE }.avgLossCp,
            1e-9
        )
        assertEquals(
            1000.0,
            profile.weaknesses.first { it.theme == ThemeTag.MISSED_MATE }.avgLossCp,
            1e-9
        )
    }

    @Test
    fun `drilling a mistake to mastery discounts its weight`() {
        val undrilled = MistakeObservation(0, setOf(ThemeTag.HANGING_PIECE), 300)
        val mastered = undrilled.copy(
            timesDrilled = WeaknessProfiler.MASTERY_MIN_REPS,
            drillSuccessRate = 1.0
        )
        val plain = WeaknessProfiler.build(listOf(undrilled)).weaknesses.single().score
        val drilled = WeaknessProfiler.build(listOf(mastered)).weaknesses.single().score
        assertEquals(1.0, plain, 1e-9)
        assertEquals(1.0 - WeaknessProfiler.MASTERY_MAX_DISCOUNT, drilled, 1e-9)
    }

    @Test
    fun `too few drill reps earn no discount`() {
        val barelyDrilled = MistakeObservation(
            0, setOf(ThemeTag.HANGING_PIECE), 300,
            timesDrilled = WeaknessProfiler.MASTERY_MIN_REPS - 1,
            drillSuccessRate = 1.0
        )
        val score = WeaknessProfiler.build(listOf(barelyDrilled)).weaknesses.single().score
        assertEquals(1.0, score, 1e-9)
    }

    @Test
    fun `empty history yields an empty profile`() {
        val profile = WeaknessProfiler.build(emptyList())
        assertTrue(profile.weaknesses.isEmpty())
        assertTrue(profile.phases.isEmpty())
    }
}
