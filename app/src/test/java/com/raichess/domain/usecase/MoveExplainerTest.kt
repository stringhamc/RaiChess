package com.raichess.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoveExplainerTest {

    // Field report position: ...Qd5-a2 looks like it hangs the queen to Ra1
    private val fieldFen = "1r5r/p1p1k1pp/2n1bp2/3qP3/Q2P2P1/P1pP3P/4PP1N/RN2KBR1 b - - 0 25"

    @Test
    fun `a capture of something worth more wins it, and check is named`() {
        // Rd1xd8+: the queen is defended by the king, but worth more than the rook
        assertEquals(
            "d1 → d8 wins the queen for your rook and gives check.",
            MoveExplainer.whyBest("3qk3/8/8/8/8/8/8/3RK3 w - - 0 1", "d1d8")
        )
    }

    @Test
    fun `a check that also hits a loose piece is a fork`() {
        assertEquals(
            "d4 → c2 forks the king and rook.",
            MoveExplainer.whyBest("4k3/8/8/8/3n4/8/8/R3K3 b - - 0 1", "d4c2")
        )
    }

    @Test
    fun `stepping away from a pawn attack gets the piece out of danger`() {
        assertEquals(
            "e4 → c3 attacks the pawn on d5 and gets your knight out of danger.",
            MoveExplainer.whyBest("4k3/8/8/3p4/4N3/8/8/4K3 w - - 0 1", "e4c3")
        )
    }

    @Test
    fun `covering a threatened piece saves it`() {
        // The b5 knight hangs to the b8 rook... until Kc4 covers it
        assertEquals(
            "c3 → c4 saves your knight on b5.",
            MoveExplainer.whyBest("1r2k3/8/8/1N6/8/2K5/8/8 w - - 0 1", "c3c4")
        )
    }

    @Test
    fun `a quiet move with nothing concrete stays silent`() {
        assertNull(MoveExplainer.whyBest("4k3/8/8/8/8/8/8/4K3 w - - 0 1", "e1e2"))
    }

    @Test
    fun `the punishing reply names what it took`() {
        assertEquals(
            "a1 → a2 wins your queen for a rook",
            MoveExplainer.whyPunished(fieldFen, "d5a2", "a1a2")
        )
        assertEquals(
            "d5 → e4 wins your undefended knight",
            MoveExplainer.whyPunished("4k3/8/8/3p4/8/6N1/8/4K3 w - - 0 1", "g3e4", "d5e4")
        )
    }

    @Test
    fun `malformed input is silent`() {
        assertNull(MoveExplainer.whyBest("not a fen", "e2e4"))
        assertNull(MoveExplainer.whyPunished(fieldFen, "zz", "a1a2"))
    }

    @Test
    fun `castling and promotion are named`() {
        assertEquals(
            "e1 → g1 castles.",
            MoveExplainer.whyBest("4k3/8/8/8/8/8/8/4K2R w K - 0 1", "e1g1")
        )
        assertEquals(
            "b7 → b8 (=Q) promotes to a queen.",
            MoveExplainer.whyBest("8/1P5k/8/8/8/8/8/4K3 w - - 0 1", "b7b8q")
        )
    }

    @Test
    fun `a defended prize taken by the opponent says what it cost them`() {
        // Qd4-d8+ lands on a square the a8 rook takes; Rd1 recaptures
        assertEquals(
            "a8 → d8 wins your queen for a rook",
            MoveExplainer.whyPunished("r6k/8/8/8/3Q4/8/8/2KR4 w - - 0 1", "d4d8", "a8d8")
        )
    }
}
