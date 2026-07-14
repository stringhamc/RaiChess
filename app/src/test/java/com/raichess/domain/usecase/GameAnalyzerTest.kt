package com.raichess.domain.usecase

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import com.raichess.data.engine.ChessEngine
import com.raichess.domain.model.MoveClassification
import com.raichess.domain.model.PositionAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives GameAnalyzer with a scripted engine so the eval-perspective and
 * centipawn-loss math is checked against hand-computed expectations.
 */
class GameAnalyzerTest {

    /** Returns the queued analyses in order; fails the test if over-consumed. */
    private class ScriptedEngine(analyses: List<PositionAnalysis?>) : ChessEngine {
        private val queue = analyses.toMutableList()
        var calls = 0
            private set

        override fun selectMove(board: Board): Move? = error("not an opponent")
        override val activeEngineLabel = "Scripted"
        override fun analyze(board: Board, moveTimeMs: Long): PositionAnalysis? {
            calls++
            check(queue.isNotEmpty()) { "engine asked for more analyses than scripted" }
            return queue.removeAt(0)
        }
    }

    private fun cp(score: Int, best: String) =
        PositionAnalysis(scoreCp = score, mateIn = null, bestMoveLan = best, depth = 12)

    @Test
    fun `best move by the player is graded BEST with zero loss`() {
        // 1. e4 e5 — player is White. Evals are side-to-move perspective:
        // start +20 (White), after e4 -30 for Black (White +30), then +10.
        val engine = ScriptedEngine(
            listOf(
                cp(20, "e2e4"),   // before White's move
                cp(-30, "e7e5"),  // before Black's move
                cp(10, "g1f3")    // final position (game not over)
            )
        )
        val report = GameAnalyzer(engine).analyze(listOf("e2e4", "e7e5"), playerIsWhite = true)!!

        assertEquals(3, engine.calls)
        assertEquals(2, report.moves.size)

        val white = report.moves[0]
        assertTrue(white.isPlayerMove)
        assertEquals(0, white.centipawnLoss)
        assertEquals(MoveClassification.BEST, white.classification)
        assertEquals(20, white.evaluationCp) // White to move: already White's view

        val black = report.moves[1]
        assertEquals(false, black.isPlayerMove)
        assertNull(black.centipawnLoss)
        assertNull(black.classification)
        assertEquals(30, black.evaluationCp) // Black to move -30 → White's view +30

        assertEquals(0.0, report.acpl, 1e-9)
        assertEquals(100.0, report.accuracy, 1e-9)
    }

    @Test
    fun `losing eval swings are graded and terminal mate needs no engine call`() {
        // Fool's mate: 1. f3 e5 2. g4?? Qh4# — player is White.
        val engine = ScriptedEngine(
            listOf(
                cp(20, "e2e4"),    // before f3: +20, best e4 → f3 loses 70
                cp(50, "e7e5"),    // before e5 (Black's move, ungraded)
                cp(-80, "d2d4"),   // before g4: -80 for White
                PositionAnalysis(scoreCp = null, mateIn = 1, bestMoveLan = "d8h4", depth = 15)
                // no 5th analysis: the final position is checkmate
            )
        )
        val report = GameAnalyzer(engine)
            .analyze(listOf("f2f3", "e7e5", "g2g4", "d8h4"), playerIsWhite = true)!!

        assertEquals(4, engine.calls)

        val f3 = report.moves[0]
        // before +20, after: Black's +50 → White -50; loss 70 → inaccuracy
        assertEquals(70, f3.centipawnLoss)
        assertEquals(MoveClassification.INACCURACY, f3.classification)

        val g4 = report.moves[2]
        // before -80; after: mate-in-1 for Black (+1000 capped) → White -1000
        assertEquals(920, g4.centipawnLoss)
        assertEquals(MoveClassification.BLUNDER, g4.classification)

        val qh4 = report.moves[3]
        assertEquals(false, qh4.isPlayerMove)
        // Black to move with mate in 1: +1000 for Black → -1000 White's view
        assertEquals(-1000, qh4.evaluationCp)

        assertEquals((70 + 920) / 2.0, report.acpl, 1e-9)
    }

    @Test
    fun `no player moves yields neutral accuracy`() {
        // Player is Black and resigned after White's first move
        val engine = ScriptedEngine(listOf(cp(20, "e2e4"), cp(-20, "e7e5")))
        val report = GameAnalyzer(engine).analyze(listOf("e2e4"), playerIsWhite = false)!!
        assertNull(report.moves[0].centipawnLoss)
        assertEquals(50.0, report.accuracy, 1e-9)
    }

    @Test
    fun `aborts when the engine cannot analyze a position`() {
        val engine = ScriptedEngine(listOf(cp(20, "e2e4"), null))
        assertNull(GameAnalyzer(engine).analyze(listOf("e2e4", "e7e5"), playerIsWhite = true))
    }

    @Test
    fun `aborts on a move that is not legal`() {
        val engine = ScriptedEngine(listOf(cp(20, "e2e4")))
        assertNull(GameAnalyzer(engine).analyze(listOf("e2e5"), playerIsWhite = true))
    }

    @Test
    fun `rejects an empty game`() {
        val engine = ScriptedEngine(emptyList())
        assertNull(GameAnalyzer(engine).analyze(emptyList(), playerIsWhite = true))
    }
}
