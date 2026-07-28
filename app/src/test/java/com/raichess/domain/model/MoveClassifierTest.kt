package com.raichess.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MoveClassifierTest {

    @Test
    fun `centipawn loss is the eval drop and never negative`() {
        assertEquals(150, MoveClassifier.centipawnLoss(evalBeforeCp = 100, evalAfterCp = -50))
        assertEquals(0, MoveClassifier.centipawnLoss(evalBeforeCp = 10, evalAfterCp = 80))
        assertEquals(0, MoveClassifier.centipawnLoss(evalBeforeCp = 25, evalAfterCp = 25))
    }

    @Test
    fun `win drop is the winning-chances loss in percentage points`() {
        assertEquals(0, MoveClassifier.winDrop(evalBeforeCp = 0, evalAfterCp = 0))
        // Never negative even when the move improved the position
        assertEquals(0, MoveClassifier.winDrop(evalBeforeCp = -50, evalAfterCp = 100))
        // Equality to -900 is a collapse of most of the win chances
        val collapse = MoveClassifier.winDrop(evalBeforeCp = 0, evalAfterCp = -900)
        org.junit.Assert.assertTrue("expected a large drop, got $collapse", collapse >= 40)
    }

    @Test
    fun `near equality the cp thresholds still gate small losses`() {
        // Small cp losses stay GOOD regardless of position
        assertEquals(
            MoveClassification.GOOD,
            MoveClassifier.classify(evalBeforeCp = 0, evalAfterCp = -59, playedEngineBest = false)
        )
        // A hung queen at equality is a blunder on both scales
        assertEquals(
            MoveClassification.BLUNDER,
            MoveClassifier.classify(evalBeforeCp = 0, evalAfterCp = -900, playedEngineBest = false)
        )
    }

    @Test
    fun `a small opening-style swing is an inaccuracy, not a mistake`() {
        // Field feedback: 1.2 pawns at move 3 of an even game was labelled
        // "Mistake" — but it only moves the win chances ~11 points, which
        // is engine preference territory, not a serious error
        assertEquals(
            MoveClassification.INACCURACY,
            MoveClassifier.classify(evalBeforeCp = 20, evalAfterCp = -100, playedEngineBest = false)
        )
        // A 2.5-pawn swing at equality genuinely changes the game: mistake
        assertEquals(
            MoveClassification.MISTAKE,
            MoveClassifier.classify(evalBeforeCp = 0, evalAfterCp = -250, playedEngineBest = false)
        )
    }

    @Test
    fun `swings in decided positions are discounted`() {
        // Sloppy conversion while completely winning: +900 to +600 is
        // three pawns of eval but almost no win-chance change — not a
        // mistake worth nagging
        assertEquals(
            MoveClassification.GOOD,
            MoveClassifier.classify(evalBeforeCp = 900, evalAfterCp = 600, playedEngineBest = false)
        )
        // Same in a lost position: -600 to -900 changes nothing
        assertEquals(
            MoveClassification.GOOD,
            MoveClassifier.classify(evalBeforeCp = -600, evalAfterCp = -900, playedEngineBest = false)
        )
    }

    @Test
    fun `playing the engine best move is BEST regardless of loss`() {
        // In practice a best move has ~0 loss, but eval noise between
        // searches must not demote the engine's own first choice
        assertEquals(
            MoveClassification.BEST,
            MoveClassifier.classify(evalBeforeCp = 0, evalAfterCp = 0, playedEngineBest = true)
        )
        assertEquals(
            MoveClassification.BEST,
            MoveClassifier.classify(evalBeforeCp = 0, evalAfterCp = -45, playedEngineBest = true)
        )
    }

    @Test
    fun `accuracy is linear in acpl with a floor at zero`() {
        assertEquals(100.0, MoveClassifier.accuracyFromAcpl(0.0), 1e-9)
        assertEquals(75.0, MoveClassifier.accuracyFromAcpl(100.0), 1e-9)
        assertEquals(50.0, MoveClassifier.accuracyFromAcpl(200.0), 1e-9)
        assertEquals(0.0, MoveClassifier.accuracyFromAcpl(1_000_000.0), 1e-9)
    }

    @Test
    fun `lossBetween flips the opponent-perspective eval back to the mover`() {
        fun cp(score: Int) = PositionAnalysis(scoreCp = score, mateIn = null, bestMoveLan = null)
        // +20 for the mover before; opponent sees -30 after → mover +30: no loss
        assertEquals(0, MoveClassifier.lossBetween(cp(20), cp(-30)))
        // -80 before; opponent then mates → mover at -1000 capped: 920 lost
        val mated = PositionAnalysis(scoreCp = null, mateIn = 1, bestMoveLan = null)
        assertEquals(920, MoveClassifier.lossBetween(cp(-80), mated))
        // +200 before; opponent sees +200 after → mover -200: 400 lost
        assertEquals(400, MoveClassifier.lossBetween(cp(200), cp(200)))
    }

    @Test
    fun `effectiveCp caps runaway and mate scores`() {
        assertEquals(40, PositionAnalysis(scoreCp = 40, mateIn = null, bestMoveLan = "e2e4").effectiveCp())
        assertEquals(1000, PositionAnalysis(scoreCp = 5200, mateIn = null, bestMoveLan = "e2e4").effectiveCp())
        assertEquals(-1000, PositionAnalysis(scoreCp = -9999, mateIn = null, bestMoveLan = "e2e4").effectiveCp())
        assertEquals(1000, PositionAnalysis(scoreCp = null, mateIn = 3, bestMoveLan = "h5f7").effectiveCp())
        assertEquals(-1000, PositionAnalysis(scoreCp = null, mateIn = -2, bestMoveLan = "e8e7").effectiveCp())
    }
}
