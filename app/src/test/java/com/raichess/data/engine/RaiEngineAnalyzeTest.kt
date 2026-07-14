package com.raichess.data.engine

import com.github.bhlangonijr.chesslib.Board
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RaiEngine.analyze is the offline fallback analyzer, so it must be honest
 * (full strength, no blunder roll) even when the engine instance itself is
 * configured as a weak opponent.
 */
class RaiEngineAnalyzeTest {

    @Test
    fun `finds a hanging queen at full strength even on a beginner instance`() {
        // White rook on d2, black queen hanging on d5
        val board = Board().apply { loadFromFen("k7/8/8/3q4/8/8/3R4/K7 w - - 0 1") }
        val originalFen = board.fen

        // Weakest band: selectMove would blunder 60% of the time — analyze must not
        val analysis = RaiEngine(RaiEngine.MIN_ELO).analyze(board, moveTimeMs = 0)!!

        assertEquals("d2d5", analysis.bestMoveLan)
        assertTrue((analysis.scoreCp ?: 0) > 300 || analysis.mateIn != null)
        assertEquals(RaiEngine.ANALYZE_DEPTH, analysis.depth)
        assertEquals("board must be left untouched", originalFen, board.fen)
    }

    @Test
    fun `reports mate in one`() {
        // Rh8# — the b6 king covers the a7/b7 escape squares
        val board = Board().apply { loadFromFen("k7/8/1K6/8/8/8/8/7R w - - 0 1") }
        val analysis = RaiEngine(1200).analyze(board, moveTimeMs = 0)!!

        assertEquals("h1h8", analysis.bestMoveLan)
        assertEquals(1, analysis.mateIn)
        assertNull(analysis.scoreCp)
    }

    @Test
    fun `returns null on a terminal position`() {
        // Fool's mate final position: White to move, checkmated
        val board = Board().apply {
            loadFromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 3")
        }
        assertNull(RaiEngine(1200).analyze(board, moveTimeMs = 0))
    }
}
