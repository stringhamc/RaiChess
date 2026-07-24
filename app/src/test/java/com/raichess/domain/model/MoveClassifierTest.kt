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
    fun `classification thresholds match the technical plan`() {
        // Good: within 0.3 pawns of best
        assertEquals(MoveClassification.GOOD, MoveClassifier.classify(0, playedEngineBest = false))
        assertEquals(MoveClassification.GOOD, MoveClassifier.classify(59, playedEngineBest = false))
        // Inaccuracy: 0.3-1.0 pawn loss
        assertEquals(MoveClassification.INACCURACY, MoveClassifier.classify(60, playedEngineBest = false))
        assertEquals(MoveClassification.INACCURACY, MoveClassifier.classify(99, playedEngineBest = false))
        // Mistake: 1.0-3.0 pawn loss
        assertEquals(MoveClassification.MISTAKE, MoveClassifier.classify(100, playedEngineBest = false))
        assertEquals(MoveClassification.MISTAKE, MoveClassifier.classify(299, playedEngineBest = false))
        // Blunder: 3.0+ pawn loss
        assertEquals(MoveClassification.BLUNDER, MoveClassifier.classify(300, playedEngineBest = false))
        assertEquals(MoveClassification.BLUNDER, MoveClassifier.classify(2000, playedEngineBest = false))
    }

    @Test
    fun `playing the engine best move is BEST regardless of loss`() {
        // In practice a best move has ~0 loss, but eval noise between
        // searches must not demote the engine's own first choice
        assertEquals(MoveClassification.BEST, MoveClassifier.classify(0, playedEngineBest = true))
        assertEquals(MoveClassification.BEST, MoveClassifier.classify(45, playedEngineBest = true))
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
