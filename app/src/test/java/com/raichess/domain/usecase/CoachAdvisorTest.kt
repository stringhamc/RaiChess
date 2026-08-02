package com.raichess.domain.usecase

import com.raichess.domain.model.EloStats
import com.raichess.domain.model.GameResult
import com.raichess.domain.model.ThemeTag
import com.raichess.domain.model.TrainingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachAdvisorTest {

    private fun stats(
        gamesPlayed: Int,
        winStreak: Int = 0
    ) = EloStats(
        currentElo = 800, peakElo = 850, gamesPlayed = gamesPlayed,
        wins = gamesPlayed, losses = 0, draws = 0,
        confidenceInterval = 100, winStreak = winStreak
    )

    private fun themeStat(theme: ThemeTag, occurrences: Int = 4, avgLossCp: Double = 250.0) =
        ThemeStat(theme = theme, score = 2.0, occurrences = occurrences, avgLossCp = avgLossCp)

    private val defaultPlan = LessonPlanner.buildPlan(WeaknessProfile.EMPTY, 800, emptyMap())

    @Test
    fun `no games yet gets a welcome and a play action`() {
        val advice = CoachAdvisor.advise(null, WeaknessProfile.EMPTY, defaultPlan, emptyMap())
        assertTrue(advice.headline.contains("Welcome"))
        assertEquals(CoachAdvisor.Action.PLAY_GAME, advice.action)
        assertTrue(advice.focuses.isNotEmpty())
    }

    @Test
    fun `top weakness leads the talking points`() {
        val profile = WeaknessProfile(
            weaknesses = listOf(themeStat(ThemeTag.HANGING_PIECE)),
            phases = listOf(themeStat(ThemeTag.ENDGAME))
        )
        val advice = CoachAdvisor.advise(stats(15), profile, defaultPlan, emptyMap())
        assertEquals("Let's keep your pieces safe.", advice.headline)
        assertTrue("detail should cite the count, got: ${advice.detail}",
            advice.detail.contains("4×"))
        // The phase still gets airtime as a secondary focus
        assertTrue(advice.focuses.any { it.contains("your endgames") })
    }

    @Test
    fun `phase-only profile talks about the game stage`() {
        val profile = WeaknessProfile(
            weaknesses = emptyList(),
            phases = listOf(themeStat(ThemeTag.OPENING))
        )
        val advice = CoachAdvisor.advise(stats(15), profile, defaultPlan, emptyMap())
        assertTrue(advice.headline.contains("your openings"))
    }

    @Test
    fun `calibration and streak surface as focuses`() {
        val advice = CoachAdvisor.advise(
            stats(gamesPlayed = 3, winStreak = 3), WeaknessProfile.EMPTY,
            defaultPlan, emptyMap()
        )
        assertTrue(advice.focuses.any { it.contains("game 4 of") })
        assertTrue(advice.focuses.any { it.contains("3 wins in a row") })
    }

    @Test
    fun `active lesson drives the suggested action`() {
        val advice = CoachAdvisor.advise(stats(15), WeaknessProfile.EMPTY, defaultPlan, emptyMap())
        assertEquals(CoachAdvisor.Action.START_LESSON, advice.action)
        assertTrue(advice.actionLabel.startsWith("Continue:"))
        assertTrue(advice.focuses.any { it.startsWith("Current lesson:") })
    }

    @Test
    fun `finished plan falls back to playing and says so`() {
        val allSolved = defaultPlan.associate { it.id to it.targetSolves }
        val advice = CoachAdvisor.advise(stats(15), WeaknessProfile.EMPTY, defaultPlan, allSolved)
        assertEquals(CoachAdvisor.Action.PLAY_GAME, advice.action)
        assertTrue(advice.focuses.any { it.contains("plan is complete") })
    }

    @Test
    fun `clean profile stays encouraging`() {
        val advice = CoachAdvisor.advise(stats(25), WeaknessProfile.EMPTY, defaultPlan, emptyMap())
        assertEquals("Looking sharp.", advice.headline)
    }

    @Test
    fun `a fresh loss makes the coach suggest reviewing it`() {
        val advice = CoachAdvisor.advise(
            stats(15), WeaknessProfile.EMPTY, defaultPlan, emptyMap(),
            lastGameWasLoss = true
        )
        assertEquals(CoachAdvisor.Action.REVIEW_GAMES, advice.action)
        assertTrue(advice.focuses.any { it.contains("loss") })
        // The lesson stays visible as a focus even when the loss leads
        assertTrue(advice.focuses.any { it.startsWith("Current lesson:") })
    }

    @Test
    fun `reactions match the result and its context`() {
        assertTrue(
            CoachAdvisor.react(GameResult.WIN, newPeak = true, winStreak = 1, calibrating = false)
                .contains("peak")
        )
        assertTrue(
            CoachAdvisor.react(GameResult.WIN, newPeak = false, winStreak = 4, calibrating = false)
                .contains("4 in a row")
        )
        assertTrue(
            CoachAdvisor.react(GameResult.LOSS, newPeak = false, winStreak = 0, calibrating = true)
                .contains("finding your level")
        )
        assertTrue(
            CoachAdvisor.react(GameResult.LOSS, newPeak = false, winStreak = 0, calibrating = false)
                .contains("practice material")
        )
        assertTrue(
            CoachAdvisor.react(GameResult.DRAW, newPeak = false, winStreak = 0, calibrating = false)
                .contains("half-point")
        )
    }

    @Test
    fun `training status speaks in the coach's focuses`() {
        val productive = CoachAdvisor.advise(
            stats(15), WeaknessProfile.EMPTY, defaultPlan, emptyMap(),
            trainingStatus = TrainingStatus.PRODUCTIVE
        )
        assertTrue(productive.focuses.any { it.contains("productive") })

        // Rest is endorsed, never guilt-tripped
        val recovery = CoachAdvisor.advise(
            stats(15), WeaknessProfile.EMPTY, defaultPlan, emptyMap(),
            trainingStatus = TrainingStatus.RECOVERY
        )
        assertTrue(recovery.focuses.any { it.contains("Rest day — good") })

        val heavy = CoachAdvisor.advise(
            stats(15), WeaknessProfile.EMPTY, defaultPlan, emptyMap(),
            trainingStatus = TrainingStatus.OVERREACHING
        )
        assertTrue(heavy.focuses.any { it.contains("Quality beats volume") })

        // No history, no verdict
        val silent = CoachAdvisor.advise(
            stats(15), WeaknessProfile.EMPTY, defaultPlan, emptyMap(),
            trainingStatus = null
        )
        assertTrue(silent.focuses.none { it.contains("Training status") })
    }

    @Test
    fun `single occurrence reads as once, not 1x`() {
        val profile = WeaknessProfile(
            weaknesses = listOf(themeStat(ThemeTag.MISSED_MATE, occurrences = 1)),
            phases = emptyList()
        )
        val advice = CoachAdvisor.advise(stats(15), profile, defaultPlan, emptyMap())
        assertTrue(advice.detail.contains("once"))
        assertTrue(!advice.detail.contains("1×"))
    }
}
