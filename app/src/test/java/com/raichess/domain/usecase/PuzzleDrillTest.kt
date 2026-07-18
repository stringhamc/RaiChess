package com.raichess.domain.usecase

import com.github.bhlangonijr.chesslib.Side
import com.raichess.domain.model.Puzzle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleDrillTest {

    // Two-rook ladder mate-in-2 from the seed set: after ...b6 the player
    // finds Ra8+ (forced Kh7) then Rh1#
    private val mateInTwo = Puzzle(
        id = "t1",
        fen = "6k1/1p3pp1/8/5PP1/8/8/6K1/RR6 b - - 0 1",
        moves = listOf("b7b6", "a1a8", "g8h7", "b1h1"),
        rating = 1400,
        themes = setOf("mate", "mateIn2")
    )

    @Test
    fun `setup move is applied and the solver is the side to move`() {
        val drill = PuzzleDrill(mateInTwo)
        assertTrue(!drill.isFinished)
        assertEquals(Side.WHITE, drill.solverSide)
        assertTrue(drill.currentFen.contains(" w "))
    }

    @Test
    fun `full line walks to solved with the scripted reply`() {
        val drill = PuzzleDrill(mateInTwo)
        val first = drill.submit("a1a8")
        assertTrue(first is PuzzleDrill.Outcome.Continue)
        assertEquals("g8h7", (first as PuzzleDrill.Outcome.Continue).opponentReplyLan)
        val second = drill.submit("b1h1")
        assertEquals(PuzzleDrill.Outcome.Solved, second)
        assertTrue(drill.isFinished)
    }

    @Test
    fun `wrong move reveals the expected one and leaves the position`() {
        val drill = PuzzleDrill(mateInTwo)
        val fenBefore = drill.currentFen
        val outcome = drill.submit("b1b8")
        assertTrue(outcome is PuzzleDrill.Outcome.Wrong)
        assertEquals("a1a8", (outcome as PuzzleDrill.Outcome.Wrong).expectedLan)
        assertEquals(fenBefore, drill.currentFen)
        assertTrue(drill.isFinished)
    }

    @Test
    fun `two-ply puzzle solves in one move`() {
        val drill = PuzzleDrill(
            Puzzle("t2", "k7/4P3/8/8/8/8/8/K7 b - - 0 1",
                listOf("a8b7", "e7e8q"), 900, setOf("promotion"))
        )
        assertEquals(PuzzleDrill.Outcome.Solved, drill.submit("e7e8q"))
    }

    @Test
    fun `corrupt setup move fails closed`() {
        val drill = PuzzleDrill(
            Puzzle("bad", "k7/4P3/8/8/8/8/8/K7 b - - 0 1",
                listOf("h1h5", "e7e8q"), 900, setOf("promotion"))
        )
        assertTrue(drill.isFinished)
    }
}
