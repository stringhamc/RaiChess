package com.raichess.data.engine

import com.github.bhlangonijr.chesslib.Board
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
