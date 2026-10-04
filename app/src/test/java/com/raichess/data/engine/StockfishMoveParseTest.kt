package com.raichess.data.engine

import com.github.bhlangonijr.chesslib.Board
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the one pure piece of StockfishWasmEngine — turning a UCI
 * "bestmove" line into a validated chesslib move — without a WebView.
 */
class StockfishMoveParseTest {

    @Test
    fun `parses a normal move from the start position`() {
        val board = Board()
        val move = StockfishWasmEngine.parseUciBestMove(board, "bestmove e2e4")
        assertEquals("e2e4", move?.toString()?.lowercase())
    }

    @Test
    fun `parses a promotion move regardless of case`() {
        val board = Board()
        // White pawn on e7 with e8 empty (black king on h8) so e8=Q is legal
        board.loadFromFen("7k/4P3/8/8/8/8/8/4K3 w - - 0 1")
        val move = StockfishWasmEngine.parseUciBestMove(board, "bestmove e7e8q")
        assertEquals("e7e8q", move?.toString()?.lowercase())
    }

    @Test
    fun `parses kingside castling`() {
        val board = Board()
        board.loadFromFen("rnbqk2r/pppp1ppp/5n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 1")
        val move = StockfishWasmEngine.parseUciBestMove(board, "bestmove e1g1")
        assertEquals("e1g1", move?.toString()?.lowercase())
    }

    @Test
    fun `returns null for none, malformed, and illegal moves`() {
        val board = Board()
        assertNull(StockfishWasmEngine.parseUciBestMove(board, "bestmove (none)"))
        assertNull(StockfishWasmEngine.parseUciBestMove(board, "bestmove"))
        assertNull(StockfishWasmEngine.parseUciBestMove(board, "bestmove zz"))
        // e2e5 is not a legal first move
        assertNull(StockfishWasmEngine.parseUciBestMove(board, "bestmove e2e5"))
    }

    @Test
    fun `a result whose line replays here belongs to this position`() {
        assertTrue(
            StockfishWasmEngine.isResultFor(
                Board(), "bestmove e2e4 ponder e7e5", listOf("e2e4", "e7e5", "g1f3")
            )
        )
        assertTrue(StockfishWasmEngine.isResultFor(Board(), "bestmove (none)", emptyList()))
    }

    @Test
    fun `a late result from another position's search is rejected`() {
        // The previous search was for Black after 1.e4: its move is
        // illegal here, and so is a PV that only fits that position
        assertFalse(StockfishWasmEngine.isResultFor(Board(), "bestmove e7e5", listOf("e7e5")))
        val board = Board().apply {
            loadFromFen("1r5r/p1p1k1pp/2n1bp2/3qP3/Q2P2P1/P1pP3P/4PP1N/RN2KBR1 b - - 0 25")
        }
        // d5c5 is legal, but White has no piece on e7 to continue the line
        assertFalse(
            StockfishWasmEngine.isResultFor(board, "bestmove d5c5", listOf("d5c5", "e7e5"))
        )
    }
}
