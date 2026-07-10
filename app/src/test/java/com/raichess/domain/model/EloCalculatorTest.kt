package com.raichess.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EloCalculatorTest {

    @Test
    fun `expected score is half for equal ratings`() {
        assertEquals(0.5, EloCalculator.calculateExpectedScore(1500, 1500), 0.001)
    }

    @Test
    fun `expected scores of both players sum to one`() {
        val a = EloCalculator.calculateExpectedScore(1400, 1700)
        val b = EloCalculator.calculateExpectedScore(1700, 1400)
        assertEquals(1.0, a + b, 0.001)
    }

    @Test
    fun `win against equal opponent increases rating`() {
        val newElo = EloCalculator.calculateNewElo(1200, 1200, GameResult.WIN, 50.0)
        assertTrue("expected rating gain, got $newElo", newElo > 1200)
    }

    @Test
    fun `loss against equal opponent decreases rating`() {
        val newElo = EloCalculator.calculateNewElo(1200, 1200, GameResult.LOSS, 50.0)
        assertTrue("expected rating loss, got $newElo", newElo < 1200)
    }

    @Test
    fun `high accuracy softens a loss`() {
        val sloppyLoss = EloCalculator.calculateNewElo(1200, 1200, GameResult.LOSS, 30.0)
        val accurateLoss = EloCalculator.calculateNewElo(1200, 1200, GameResult.LOSS, 90.0)
        assertTrue(accurateLoss > sloppyLoss)
    }

    @Test
    fun `rating never drops below minimum`() {
        val newElo = EloCalculator.calculateNewElo(
            EloCalculator.MIN_ELO, 2800, GameResult.LOSS, 0.0
        )
        assertTrue(newElo >= EloCalculator.MIN_ELO)
    }

    @Test
    fun `rating never exceeds maximum`() {
        val newElo = EloCalculator.calculateNewElo(
            EloCalculator.MAX_ELO, 800, GameResult.WIN, 100.0
        )
        assertTrue(newElo <= EloCalculator.MAX_ELO)
    }

    @Test
    fun `confidence interval shrinks with games played`() {
        assertTrue(
            EloCalculator.getConfidenceInterval(0) >
                EloCalculator.getConfidenceInterval(10)
        )
        assertEquals(0, EloCalculator.getConfidenceInterval(100))
    }

    @Test
    fun `pgn result maps to player perspective`() {
        assertEquals(GameResult.WIN, GameResult.fromPgnResult("1-0", PlayerColor.WHITE))
        assertEquals(GameResult.LOSS, GameResult.fromPgnResult("1-0", PlayerColor.BLACK))
        assertEquals(GameResult.WIN, GameResult.fromPgnResult("0-1", PlayerColor.BLACK))
        assertEquals(GameResult.DRAW, GameResult.fromPgnResult("1/2-1/2", PlayerColor.WHITE))
    }

    @Test
    fun `elo configuration depth increases with target elo`() {
        val low = EloConfiguration.forElo(900)
        val mid = EloConfiguration.forElo(1700)
        val high = EloConfiguration.forElo(2700)
        assertTrue(low.depth < mid.depth)
        assertTrue(mid.depth < high.depth)
    }

    @Test
    fun `recommended opponent is slightly above player`() {
        assertEquals(1250, EloConfiguration.getRecommendedOpponentElo(1200))
        assertEquals(2800, EloConfiguration.getRecommendedOpponentElo(2900))
    }

    @Test
    fun `stats update tracks record and peak`() {
        val stats = EloStats(
            currentElo = 1200, peakElo = 1200, gamesPlayed = 0,
            wins = 0, losses = 0, draws = 0, confidenceInterval = 150
        )
        val afterWin = stats.withGameResult(1216, GameResult.WIN)
        assertEquals(1216, afterWin.currentElo)
        assertEquals(1216, afterWin.peakElo)
        assertEquals(1, afterWin.wins)
        assertEquals(1, afterWin.gamesPlayed)

        val afterLoss = afterWin.withGameResult(1200, GameResult.LOSS)
        assertEquals(1200, afterLoss.currentElo)
        assertEquals(1216, afterLoss.peakElo)
        assertEquals(1, afterLoss.losses)
    }
}
