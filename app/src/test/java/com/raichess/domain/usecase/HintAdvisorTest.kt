package com.raichess.domain.usecase

import com.raichess.domain.model.PositionAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HintAdvisorTest {

    private fun cp(score: Int, best: String?) =
        PositionAnalysis(scoreCp = score, mateIn = null, bestMoveLan = best, depth = 12)

    private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    @Test
    fun `square ordinals map lan squares to a1-h8 indexing`() {
        assertEquals(0, HintAdvisor.squareOrdinal("a1"))
        assertEquals(12, HintAdvisor.squareOrdinal("e2"))
        assertEquals(63, HintAdvisor.squareOrdinal("h8"))
        assertNull(HintAdvisor.squareOrdinal("i1"))
        assertNull(HintAdvisor.squareOrdinal("a9"))
        assertNull(HintAdvisor.squareOrdinal("e"))
    }

    @Test
    fun `fen board parsing places pieces at the right ordinals`() {
        val board = HintAdvisor.parseFenBoard(startFen)!!
        assertEquals('R', board[0])   // a1
        assertEquals('P', board[12])  // e2
        assertEquals('k', board[60])  // e8
        assertEquals(null, board[35]) // d5
        assertNull(HintAdvisor.parseFenBoard("not/a/fen"))
        assertNull(HintAdvisor.parseFenBoard("8/8/8/8/8/8/8/9 w - - 0 1"))
    }

    @Test
    fun `level one names the piece and highlights its square`() {
        val hint = HintAdvisor.hint(1, cp(30, "g1f3"), startFen)!!
        assertEquals("Look at your knight on g1.", hint.text)
        assertEquals(setOf(6), hint.highlights)
    }

    @Test
    fun `level two reveals the move and both squares`() {
        val hint = HintAdvisor.hint(2, cp(30, "g1f3"), startFen)!!
        assertEquals("Play g1 → f3.", hint.text)
        assertEquals(setOf(6, 21), hint.highlights)
    }

    @Test
    fun `promotion names the promoted piece`() {
        // e7e8q: from=e7 (52), to=e8 (60)
        val fen = "k7/4P3/8/8/8/8/8/K7 w - - 0 1"
        val hint = HintAdvisor.hint(2, cp(900, "e7e8q"), fen)!!
        assertEquals("Play e7 → e8 (promote to queen).", hint.text)
        assertEquals(setOf(52, 60), hint.highlights)
    }

    @Test
    fun `no hint without a best move or for out-of-range levels`() {
        assertNull(HintAdvisor.hint(1, cp(0, null), startFen))
        assertNull(HintAdvisor.hint(0, cp(0, "e2e4"), startFen))
        assertNull(HintAdvisor.hint(3, cp(0, "e2e4"), startFen))
        assertNull(HintAdvisor.hint(1, cp(0, "xx"), startFen))
    }
}
