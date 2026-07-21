package com.raichess.domain.usecase

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Side
import com.raichess.domain.model.Puzzle

/**
 * State machine for playing through one puzzle's solution line
 * (Lichess convention — see [Puzzle]). Construction applies the setup
 * move; the caller then feeds player moves and applies the returned
 * opponent replies to its UI board.
 *
 * chesslib-backed but engine-free: the solution line IS the answer key,
 * so no analysis runs during puzzle drills.
 */
class PuzzleDrill(val puzzle: Puzzle) {

    sealed class Outcome {
        /** Right move; the opponent replied — keep going. */
        data class Continue(val opponentReplyLan: String) : Outcome()

        /** Right move and the line is finished. */
        object Solved : Outcome()

        /** Wrong move (not applied); the expected move for reveal. */
        data class Wrong(val expectedLan: String) : Outcome()
    }

    private val board = Board().apply { loadFromFen(puzzle.fen) }

    /** Index into puzzle.moves of the next expected PLAYER move. */
    private var nextIndex = 1

    /** True once the line is complete or construction failed. */
    var isFinished: Boolean = false
        private set

    /** The side the player is solving for. */
    val solverSide: Side

    /**
     * FEN of the position the player is asked to solve (after the setup
     * move) — the right FEN to persist for this drill, unlike
     * [Puzzle.fen], which is one ply earlier.
     */
    val startFen: String

    init {
        val setup = GameAnalyzer.lanToLegalMove(board, puzzle.moves[0])
        if (setup != null) {
            board.doMove(setup)
            solverSide = board.sideToMove
        } else {
            // Corrupt data: fail closed rather than crash
            solverSide = board.sideToMove
            isFinished = true
        }
        startFen = board.fen
    }

    /** FEN of the current drill position (after setup / replies). */
    val currentFen: String get() = board.fen

    /** The move the line expects next from the player, or null when done. */
    val expectedLan: String? get() = if (isFinished) null else puzzle.moves[nextIndex]

    /** A defensive copy of the current position for move generation. */
    fun boardCopy(): Board = Board().apply { loadFromFen(board.fen) }

    /**
     * Submit the player's move in LAN. Correct moves are applied (with the
     * opponent's scripted reply, when the line continues); wrong moves
     * leave the position untouched so the player sees the reveal in place.
     *
     * A corrupt continuation (a scripted move that isn't legal in the
     * position) ends the drill as Solved: everything the player did was
     * right, and Continue on a finished drill would crash the next submit.
     */
    fun submit(moveLan: String): Outcome {
        check(!isFinished) { "drill already finished" }
        val expected = puzzle.moves[nextIndex]
        if (moveLan.lowercase() != expected) {
            isFinished = true
            return Outcome.Wrong(expected)
        }
        if (!applyLan(expected)) return Outcome.Solved
        nextIndex++
        if (nextIndex >= puzzle.moves.size) {
            isFinished = true
            return Outcome.Solved
        }
        val reply = puzzle.moves[nextIndex]
        if (!applyLan(reply)) return Outcome.Solved
        nextIndex++
        if (nextIndex >= puzzle.moves.size) {
            // Defensive: a well-formed line never ends on an opponent move
            // (PuzzleCsv enforces even length), but never loop forever
            isFinished = true
            return Outcome.Solved
        }
        return Outcome.Continue(reply)
    }

    /** Applies [lan], or fails the drill closed if it isn't legal. */
    private fun applyLan(lan: String): Boolean {
        val move = GameAnalyzer.lanToLegalMove(board, lan)
        if (move == null) {
            isFinished = true
            return false
        }
        board.doMove(move)
        return true
    }
}
