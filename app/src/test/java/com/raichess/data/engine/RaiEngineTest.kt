package com.raichess.data.engine

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.MoveGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RaiEngineTest {

    @Test
    fun `returns a legal move from the starting position`() {
        val board = Board()
        val engine = RaiEngine(targetElo = 1200, random = Random(42))
        val move = engine.selectMove(board)

        val legalMoves = MoveGenerator.generateLegalMoves(board)
        assertTrue("move $move should be legal", legalMoves.contains(move))
    }

    @Test
    fun `board is unchanged after move selection`() {
        val board = Board()
        val fenBefore = board.fen
        RaiEngine(targetElo = 2000, random = Random(7)).selectMove(board)
        assertEquals(fenBefore, board.fen)
    }

    @Test
    fun `returns null when the game is over`() {
        val board = Board()
        // Fool's mate: white is checkmated
        board.loadFromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")
        assertNull(RaiEngine(targetElo = 1500).selectMove(board))
    }

    @Test
    fun `stalemate is a draw with no legal moves`() {
        val board = Board()
        // Classic queen stalemate: black to move, not in check, no legal moves
        board.loadFromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertTrue("stalemate should register as a draw", board.isDraw)
        assertTrue("stalemate is not checkmate", !board.isMated)
        assertNull(RaiEngine(targetElo = 1500).selectMove(board))
    }

    @Test
    fun `strong engine finds mate in one`() {
        val board = Board()
        // Back-rank mate available: Ra8#
        board.loadFromFen("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1")
        val engine = RaiEngine(targetElo = 2800, random = Random(1))
        val move = checkNotNull(engine.selectMove(board))

        board.doMove(move)
        assertTrue("expected a mating move, got $move", board.isMated)
    }

    @Test
    fun `strong engine captures a hanging queen`() {
        val board = Board()
        // Black queen on d5 can be captured by the c4 pawn
        board.loadFromFen("rnb1kbnr/ppp1pppp/8/3q4/2P5/8/PP1PPPPP/RNBQKBNR w KQkq - 0 3")
        val engine = RaiEngine(targetElo = 2400, random = Random(3))
        val move = checkNotNull(engine.selectMove(board))
        assertEquals("c4d5", move.toString())
    }

    @Test
    fun `search depth scales with target elo`() {
        assertTrue(RaiEngine(900).searchDepth < RaiEngine(2500).searchDepth)
    }

    @Test
    fun `blunder chance decreases with target elo`() {
        assertTrue(RaiEngine(900).blunderChance > RaiEngine(1800).blunderChance)
        assertEquals(0.0, RaiEngine(2400).blunderChance, 0.0001)
    }

    @Test
    fun `weak engine still plays legal moves`() {
        val board = Board()
        val engine = RaiEngine(targetElo = 800, random = Random(99))
        repeat(10) {
            val move = engine.selectMove(board) ?: return
            val legalMoves = MoveGenerator.generateLegalMoves(board)
            assertTrue("move $move should be legal", legalMoves.contains(move))
            board.doMove(move)
        }
    }
}
