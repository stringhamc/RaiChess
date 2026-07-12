package com.raichess.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoPenaltyTest {

    @Test
    fun `zero undos is neutral accuracy`() {
        assertEquals(50.0, UndoPenalty.accuracyAfterUndos(0), 0.0001)
    }

    @Test
    fun `each undo costs five accuracy points`() {
        assertEquals(45.0, UndoPenalty.accuracyAfterUndos(1), 0.0001)
        assertEquals(40.0, UndoPenalty.accuracyAfterUndos(2), 0.0001)
        assertEquals(30.0, UndoPenalty.accuracyAfterUndos(4), 0.0001)
    }

    @Test
    fun `accuracy floors at ten after eight undos`() {
        assertEquals(10.0, UndoPenalty.accuracyAfterUndos(8), 0.0001)
        assertEquals(10.0, UndoPenalty.accuracyAfterUndos(20), 0.0001)
    }

    @Test
    fun `negative undo count treated as zero`() {
        assertEquals(50.0, UndoPenalty.accuracyAfterUndos(-3), 0.0001)
    }

    @Test
    fun `penalty is monotonic non-increasing`() {
        var previous = UndoPenalty.accuracyAfterUndos(0)
        for (undos in 1..12) {
            val current = UndoPenalty.accuracyAfterUndos(undos)
            assertTrue("accuracy should never increase with undos", current <= previous)
            previous = current
        }
    }

    @Test
    fun `heavy undo usage reduces the elo gain from a win`() {
        val cleanWin = EloCalculator.calculateNewElo(1200, 1200, GameResult.WIN, 50.0)
        val undoWin = EloCalculator.calculateNewElo(
            1200, 1200, GameResult.WIN, UndoPenalty.accuracyAfterUndos(8)
        )
        assertTrue("undos should cost rating on a win", undoWin < cleanWin)
        assertTrue("penalty should be capped near 4 ELO", cleanWin - undoWin <= 4)
    }

    @Test
    fun `penalty does not change a loss due to score clamping`() {
        // Documented EloCalculator behavior: the adjusted score clamps at 0,
        // so undos cannot make a loss cost more. This test locks that in.
        val cleanLoss = EloCalculator.calculateNewElo(1200, 1200, GameResult.LOSS, 50.0)
        val undoLoss = EloCalculator.calculateNewElo(
            1200, 1200, GameResult.LOSS, UndoPenalty.accuracyAfterUndos(8)
        )
        assertEquals(cleanLoss, undoLoss)
    }

    @Test
    fun `can undo gate covers the state machine`() {
        // Enabled: training, playing, player's turn, AI idle, 2+ moves
        assertTrue(canUndo(GameMode.TRAINING, true, true, false, 2))
        assertTrue(canUndo(GameMode.TRAINING, true, true, false, 3))

        // Rated mode never allows undo
        assertFalse(canUndo(GameMode.RATED, true, true, false, 4))
        // Game start as white: nothing to undo
        assertFalse(canUndo(GameMode.TRAINING, true, true, false, 0))
        // Playing black with only the AI opener on the board
        assertFalse(canUndo(GameMode.TRAINING, true, true, false, 1))
        // AI thinking
        assertFalse(canUndo(GameMode.TRAINING, true, false, true, 2))
        // Game over
        assertFalse(canUndo(GameMode.TRAINING, false, false, false, 10))
    }
}
