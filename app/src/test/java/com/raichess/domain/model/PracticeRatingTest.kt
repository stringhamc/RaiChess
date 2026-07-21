package com.raichess.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeRatingTest {

    @Test
    fun `solving gains rating and failing loses it`() {
        assertTrue(PracticeRating.updated(800, 800, solved = true) > 800)
        assertTrue(PracticeRating.updated(800, 800, solved = false) < 800)
    }

    @Test
    fun `even-odds solve moves half the K factor`() {
        assertEquals(800 + PracticeRating.K_FACTOR / 2,
            PracticeRating.updated(800, 800, solved = true))
        assertEquals(800 - PracticeRating.K_FACTOR / 2,
            PracticeRating.updated(800, 800, solved = false))
    }

    @Test
    fun `harder puzzles pay more and cost less`() {
        val hardGain = PracticeRating.updated(800, 1100, solved = true) - 800
        val easyGain = PracticeRating.updated(800, 500, solved = true) - 800
        assertTrue(hardGain > easyGain)

        val hardLoss = 800 - PracticeRating.updated(800, 1100, solved = false)
        val easyLoss = 800 - PracticeRating.updated(800, 500, solved = false)
        assertTrue(hardLoss < easyLoss)
    }

    @Test
    fun `rating stays inside the ELO clamp`() {
        assertEquals(EloCalculator.MIN_ELO,
            PracticeRating.updated(EloCalculator.MIN_ELO, 3000, solved = false))
        assertEquals(EloCalculator.MAX_ELO,
            PracticeRating.updated(EloCalculator.MAX_ELO, 400, solved = true))
    }
}
