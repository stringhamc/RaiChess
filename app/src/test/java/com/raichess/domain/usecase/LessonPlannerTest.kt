package com.raichess.domain.usecase

import com.raichess.domain.model.ThemeTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonPlannerTest {

    private fun stat(theme: ThemeTag, score: Double) =
        ThemeStat(theme = theme, score = score, occurrences = 3, avgLossCp = 250.0)

    @Test
    fun `weakness lessons come first, worst weakness leading`() {
        val plan = LessonPlanner.buildPlan(
            WeaknessProfile(
                weaknesses = listOf(
                    stat(ThemeTag.HANGING_PIECE, 3.0),
                    stat(ThemeTag.MISSED_MATE, 1.5)
                ),
                phases = listOf(stat(ThemeTag.ENDGAME, 2.0))
            )
        )
        assertEquals("weakness:hanging_piece", plan[0].id)
        assertEquals("weakness:missed_mate", plan[1].id)
        assertEquals(ThemeTag.HANGING_PIECE, plan[0].weaknessTheme)
        assertTrue(plan[0].themes.isNotEmpty())
    }

    @Test
    fun `phases follow weaknesses, weakest phase first then the rest`() {
        val plan = LessonPlanner.buildPlan(
            WeaknessProfile(
                weaknesses = listOf(stat(ThemeTag.HANGING_PIECE, 1.0)),
                phases = listOf(stat(ThemeTag.ENDGAME, 2.0))
            )
        )
        val phaseIds = plan.drop(1).map { it.id }
        assertEquals(
            listOf("phase:endgame", "phase:opening", "phase:middlegame"),
            phaseIds
        )
    }

    @Test
    fun `weakness lessons are capped`() {
        val plan = LessonPlanner.buildPlan(
            WeaknessProfile(
                weaknesses = listOf(
                    stat(ThemeTag.HANGING_PIECE, 3.0),
                    stat(ThemeTag.MISSED_MATE, 2.0),
                    stat(ThemeTag.MISSED_CAPTURE, 1.0)
                ),
                phases = emptyList()
            )
        )
        assertEquals(
            LessonPlanner.MAX_WEAKNESS_LESSONS,
            plan.count { it.id.startsWith("weakness:") }
        )
    }

    @Test
    fun `empty profile gets the fundamentals curriculum`() {
        val plan = LessonPlanner.buildPlan(WeaknessProfile.EMPTY)
        assertTrue(plan.isNotEmpty())
        assertEquals("core:tactics", plan[0].id)
        assertTrue(plan.any { it.id == "phase:endgame" })
    }

    @Test
    fun `active lesson is the first below target and null when done`() {
        val plan = LessonPlanner.buildPlan(WeaknessProfile.EMPTY)
        assertEquals(plan[0].id, LessonPlanner.activeLesson(plan, emptyMap())?.id)

        val firstDone = mapOf(plan[0].id to LessonPlanner.TARGET_SOLVES)
        assertEquals(plan[1].id, LessonPlanner.activeLesson(plan, firstDone)?.id)

        val allDone = plan.associate { it.id to it.targetSolves }
        assertNull(LessonPlanner.activeLesson(plan, allDone))
    }

    @Test
    fun `solve counts round-trip through the prefs codec`() {
        val solves = mapOf("weakness:hanging_piece" to 3, "phase:endgame" to 8)
        assertEquals(solves, LessonPlanner.decodeSolves(LessonPlanner.encodeSolves(solves)))
        assertTrue(LessonPlanner.decodeSolves(null).isEmpty())
        assertTrue(LessonPlanner.decodeSolves("").isEmpty())
        assertTrue(LessonPlanner.decodeSolves("garbage").isEmpty())
    }
}
