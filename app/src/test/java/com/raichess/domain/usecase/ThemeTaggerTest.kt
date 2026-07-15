package com.raichess.domain.usecase

import com.raichess.domain.model.PositionAnalysis
import com.raichess.domain.model.ThemeTag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTaggerTest {

    private fun cp(score: Int, best: String?) =
        PositionAnalysis(scoreCp = score, mateIn = null, bestMoveLan = best, depth = 12)

    private fun mate(moves: Int, best: String?) =
        PositionAnalysis(scoreCp = null, mateIn = moves, bestMoveLan = best, depth = 12)

    @Test
    fun `below mistake threshold yields no tags`() {
        val tags = ThemeTagger.tag(
            fenBefore = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            ply = 0,
            moveLan = "e2e4",
            analysis = cp(20, "e2e4"),
            nextAnalysis = cp(-20, "e7e5"),
            lossCp = 40
        )
        assertTrue(tags.isEmpty())
    }

    @Test
    fun `queen moved onto a defended pawn's square is hanging`() {
        // After 1.e4 e5 (and ...g6): Qd1-h5?? hangs the queen to the g6 pawn
        val tags = ThemeTagger.tag(
            fenBefore = "rnbqkbnr/pppp1p1p/6p1/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 3",
            ply = 4,
            moveLan = "d1h5",
            analysis = cp(30, "g1f3"),
            nextAnalysis = cp(850, "g6h5"),
            lossCp = 800
        )
        assertTrue(ThemeTag.HANGING_PIECE in tags)
        assertTrue(ThemeTag.OPENING in tags)
    }

    @Test
    fun `not playing an available mate is missed mate`() {
        // Back rank: Ra1-a8 is mate; White shuffles the rook instead
        val tags = ThemeTagger.tag(
            fenBefore = "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 40",
            ply = 78,
            moveLan = "a1a2",
            analysis = mate(1, "a1a8"),
            nextAnalysis = cp(-400, "g8f8"),
            lossCp = 600
        )
        assertTrue(ThemeTag.MISSED_MATE in tags)
        assertTrue(ThemeTag.ENDGAME in tags)
    }

    @Test
    fun `not taking a free queen is missed capture`() {
        // White Rd2 can take the d5 queen; plays a2a3 instead
        val tags = ThemeTagger.tag(
            fenBefore = "k7/8/8/3q4/8/8/P2R4/K7 w - - 0 30",
            ply = 58,
            moveLan = "a2a3",
            analysis = cp(400, "d2d5"),
            nextAnalysis = cp(300, "d5d2"),
            lossCp = 700
        )
        assertTrue(ThemeTag.MISSED_CAPTURE in tags)
    }

    @Test
    fun `move that gives the opponent a forced mate is allowed mate`() {
        // Fool's mate: after 1.f3 e5, 2.g4?? allows Qh4#
        val tags = ThemeTagger.tag(
            fenBefore = "rnbqkbnr/pppp1ppp/8/4p3/8/5P2/PPPPP1PP/RNBQKBNR w KQkq - 0 2",
            ply = 2,
            moveLan = "g2g4",
            analysis = cp(-80, "d2d4"),
            nextAnalysis = mate(1, "d8h4"),
            lossCp = 920
        )
        assertTrue(ThemeTag.ALLOWED_MATE in tags)
        assertTrue(ThemeTag.OPENING in tags)
    }

    @Test
    fun `reply winning material elsewhere is allowed tactic, not hanging`() {
        // White pushes a pawn; the engine says Black's best reply Qh1xb1
        // wins the rook. The damage lands away from the moved pawn's
        // square, which is what makes it ALLOWED_TACTIC rather than
        // HANGING_PIECE. (Whether the capture is ultimately sound is the
        // engine's judgment — the tagger trusts the supplied analysis.)
        val tags = ThemeTagger.tag(
            fenBefore = "k7/8/8/8/8/8/P7/KR5q w - - 0 30",
            ply = 58,
            moveLan = "a2a3",
            analysis = cp(-200, "b1b8"),
            nextAnalysis = cp(450, "h1b1"),
            lossCp = 300
        )
        assertTrue(ThemeTag.ALLOWED_TACTIC in tags)
        assertFalse(ThemeTag.HANGING_PIECE in tags)
    }

    @Test
    fun `phase tags follow piece count then ply`() {
        // Full board late in the game: middlegame
        val middlegame = ThemeTagger.tag(
            fenBefore = "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
            ply = 30,
            moveLan = "e1g1",
            analysis = cp(30, "d2d3"),
            nextAnalysis = cp(80, "f8c5"),
            lossCp = 150
        )
        assertTrue(ThemeTag.MIDDLEGAME in middlegame)
        assertFalse(ThemeTag.OPENING in middlegame)
        assertFalse(ThemeTag.ENDGAME in middlegame)
    }
}
