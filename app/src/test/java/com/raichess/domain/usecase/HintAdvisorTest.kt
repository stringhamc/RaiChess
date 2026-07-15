package com.raichess.domain.usecase

import com.raichess.domain.model.PositionAnalysis
import com.raichess.domain.model.ThemeTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HintAdvisorTest {

    /** Empty board with the given (square ordinal → FEN char) placements. */
    private fun squares(vararg placements: Pair<Int, Char>): List<Char?> {
        val board = arrayOfNulls<Char>(64)
        placements.forEach { (index, piece) -> board[index] = piece }
        return board.toList()
    }

    private fun cp(score: Int, best: String?) =
        PositionAnalysis(scoreCp = score, mateIn = null, bestMoveLan = best, depth = 12)

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
    fun `level one prefers the mate cue over everything`() {
        val analysis = PositionAnalysis(scoreCp = null, mateIn = 2, bestMoveLan = "d1h5", depth = 12)
        val hint = HintAdvisor.hint(1, analysis, squares(), ThemeTag.HANGING_PIECE)!!
        assertTrue(hint.text.contains("forced mate"))
        assertTrue(hint.highlights.isEmpty())
    }

    @Test
    fun `level one flags winnable material when the best move captures`() {
        // Best move lands on an enemy queen (h5 = ordinal 39)
        val hint = HintAdvisor.hint(
            1,
            cp(400, "d1h5"),
            squares(39 to 'q'),
            topWeakness = null
        )!!
        assertTrue(hint.text.contains("win material"))
    }

    @Test
    fun `level one personalizes from the top weakness when nothing tactical applies`() {
        val hint = HintAdvisor.hint(1, cp(50, "g1f3"), squares(), ThemeTag.HANGING_PIECE)!!
        assertTrue(hint.text.contains("defended"))

        val generic = HintAdvisor.hint(1, cp(50, "g1f3"), squares(), topWeakness = null)!!
        assertTrue(generic.text.contains("stronger move"))
    }

    @Test
    fun `an even trade does not claim to win material`() {
        // Best move takes a queen but the eval is level — it's a trade
        // (Qxq, recapture), not a win, so fall through to the generic nudge
        val hint = HintAdvisor.hint(1, cp(0, "d1d8"), squares(59 to 'q'), null)!!
        assertTrue(hint.text.contains("stronger move"))
    }

    @Test
    fun `pawn captures do not trigger the material cue`() {
        // Best move captures a pawn (100cp < the 300cp significance floor)
        val hint = HintAdvisor.hint(1, cp(80, "d4e5"), squares(36 to 'p'), null)!!
        assertTrue(hint.text.contains("stronger move"))
    }

    @Test
    fun `level two names the piece and highlights its square`() {
        // White knight on g1 (ordinal 6), best move g1f3
        val hint = HintAdvisor.hint(2, cp(30, "g1f3"), squares(6 to 'N'), null)!!
        assertEquals("Look at your knight on g1.", hint.text)
        assertEquals(setOf(6), hint.highlights)
    }

    @Test
    fun `level three reveals the move and both squares`() {
        val hint = HintAdvisor.hint(3, cp(30, "g1f3"), squares(6 to 'N'), null)!!
        assertEquals("Best move: g1 → f3.", hint.text)
        assertEquals(setOf(6, 21), hint.highlights)
    }

    @Test
    fun `no hint without a best move or for out-of-range levels`() {
        assertNull(HintAdvisor.hint(1, cp(0, null), squares(), null))
        assertNull(HintAdvisor.hint(0, cp(0, "e2e4"), squares(), null))
        assertNull(HintAdvisor.hint(4, cp(0, "e2e4"), squares(), null))
        assertNull(HintAdvisor.hint(1, cp(0, "xx"), squares(), null))
    }

    @Test
    fun `promotion lan parses its squares and names the promoted piece`() {
        // e7e8q: from=e7 (52), to=e8 (60)
        val hint = HintAdvisor.hint(3, cp(900, "e7e8q"), squares(52 to 'P'), null)!!
        assertEquals(setOf(52, 60), hint.highlights)
        assertEquals("Best move: e7 → e8 (promote to queen).", hint.text)
    }

    @Test
    fun `getting mated never claims the player has a forced mate`() {
        // mateIn < 0: the player is the one being mated — the mate cue must
        // not fire, and with nothing else applicable the nudge stays generic
        val losing = PositionAnalysis(scoreCp = null, mateIn = -2, bestMoveLan = "g8f8", depth = 12)
        val hint = HintAdvisor.hint(1, losing, squares(), null)!!
        assertTrue(hint.text.contains("stronger move"))
        assertTrue(!hint.text.contains("forced mate"))
    }
}
