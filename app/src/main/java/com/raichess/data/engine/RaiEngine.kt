package com.raichess.data.engine

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveGenerator
import kotlin.math.max
import kotlin.random.Random

/**
 * RaiEngine - built-in chess AI for RaiChess.
 *
 * A pure-Kotlin alpha-beta engine whose playing strength is scaled to a
 * target ELO (800-2800). It is the interim opponent until the Stockfish
 * NDK integration lands; the [selectMove] contract is designed so a
 * Stockfish-backed implementation can replace it without UI changes.
 *
 * Strength scaling:
 * - Search depth grows with target ELO (1-4 plies)
 * - Lower ELOs blunder: with some probability a random legal move is played
 * - Lower ELOs also pick randomly among near-best moves instead of the best
 */
class RaiEngine(
    targetElo: Int,
    private val random: Random = Random.Default
) {

    private val elo = targetElo.coerceIn(MIN_ELO, MAX_ELO)

    /** Full-move search depth in plies. */
    val searchDepth: Int = when {
        elo < 1000 -> 1
        elo < 1400 -> 2
        elo < 2000 -> 3
        else -> 4
    }

    /** Probability of playing a completely random legal move. */
    val blunderChance: Double = when {
        elo < 1000 -> 0.30
        elo < 1200 -> 0.20
        elo < 1400 -> 0.12
        elo < 1600 -> 0.07
        elo < 1800 -> 0.04
        elo < 2000 -> 0.02
        else -> 0.0
    }

    /** Moves within this many centipawns of the best are candidates at lower ELOs. */
    private val candidateWindow: Int = when {
        elo < 1200 -> 120
        elo < 1600 -> 60
        elo < 2000 -> 25
        else -> 0
    }

    /**
     * Select a move for the side to move on [board].
     * Returns null if the game is over (no legal moves).
     * The board is left in its original state.
     */
    fun selectMove(board: Board): Move? {
        val legalMoves = MoveGenerator.generateLegalMoves(board)
        if (legalMoves.isEmpty()) return null
        if (legalMoves.size == 1) return legalMoves.first()

        if (blunderChance > 0 && random.nextDouble() < blunderChance) {
            return legalMoves[random.nextInt(legalMoves.size)]
        }

        // Root alpha is kept candidateWindow below the best score seen, so
        // pruning never discards a move that could still land in the
        // near-best candidate set used for weaker-ELO move selection
        var alpha = -INFINITY
        val scored = legalMoves
            .sortedByDescending { captureValue(board, it) }
            .map { move ->
                board.doMove(move)
                val score = -negamax(board, searchDepth - 1, -INFINITY, -alpha, 1)
                board.undoMove()
                alpha = max(alpha, score - candidateWindow)
                move to score
            }

        val bestScore = scored.maxOf { it.second }
        val candidates = scored.filter { it.second >= bestScore - candidateWindow }
        return candidates[random.nextInt(candidates.size)].first
    }

    private fun negamax(board: Board, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
        if (board.isMated) return -(MATE_SCORE - ply)
        if (board.isDraw) return 0
        if (depth <= 0) return evaluate(board)

        var alpha = alphaIn
        val moves = MoveGenerator.generateLegalMoves(board)
            .sortedByDescending { captureValue(board, it) }

        var best = -INFINITY
        for (move in moves) {
            board.doMove(move)
            val score = -negamax(board, depth - 1, -beta, -alpha, ply + 1)
            board.undoMove()
            best = max(best, score)
            alpha = max(alpha, score)
            if (alpha >= beta) break
        }
        return best
    }

    /** Static evaluation in centipawns from the perspective of the side to move. */
    private fun evaluate(board: Board): Int {
        var score = 0
        for (index in 0 until 64) {
            val square = Square.squareAt(index)
            val piece = board.getPiece(square)
            if (piece == Piece.NONE) continue

            val side = piece.pieceSide ?: continue
            val type = piece.pieceType ?: continue
            var value = pieceValue(type)

            // Positional bonus from piece-square tables (white perspective tables)
            val tableIndex = if (side == Side.WHITE) {
                // Tables are listed rank 8 first; square index 0 is a1
                (7 - index / 8) * 8 + index % 8
            } else {
                index
            }
            value += when (type) {
                PieceType.PAWN -> PAWN_TABLE[tableIndex]
                PieceType.KNIGHT -> KNIGHT_TABLE[tableIndex]
                PieceType.BISHOP -> BISHOP_TABLE[tableIndex]
                else -> 0
            }

            score += if (side == board.sideToMove) value else -value
        }
        return score
    }

    private fun captureValue(board: Board, move: Move): Int {
        val captured = board.getPiece(move.to)
        if (captured == Piece.NONE) return 0
        val type = captured.pieceType ?: return 0
        return pieceValue(type)
    }

    private fun pieceValue(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 100
        PieceType.KNIGHT -> 320
        PieceType.BISHOP -> 330
        PieceType.ROOK -> 500
        PieceType.QUEEN -> 900
        else -> 0
    }

    companion object {
        // Selectable opponent strength range; intentionally narrower than
        // EloCalculator's 400-3000, which bounds the player's own rating
        const val MIN_ELO = 800
        const val MAX_ELO = 2800
        private const val INFINITY = 1_000_000
        private const val MATE_SCORE = 100_000

        // Piece-square tables (white perspective, rank 8 first)
        private val PAWN_TABLE = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5, 5, 10, 25, 25, 10, 5, 5,
            0, 0, 0, 20, 20, 0, 0, 0,
            5, -5, -10, 0, 0, -10, -5, 5,
            5, 10, 10, -20, -20, 10, 10, 5,
            0, 0, 0, 0, 0, 0, 0, 0
        )
        private val KNIGHT_TABLE = intArrayOf(
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20, 0, 0, 0, 0, -20, -40,
            -30, 0, 10, 15, 15, 10, 0, -30,
            -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30,
            -30, 5, 10, 15, 15, 10, 5, -30,
            -40, -20, 0, 5, 5, 0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50
        )
        private val BISHOP_TABLE = intArrayOf(
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 10, 10, 5, 0, -10,
            -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 10, 10, 10, 10, 0, -10,
            -10, 10, 10, 10, 10, 10, 10, -10,
            -10, 5, 0, 0, 0, 0, 5, -10,
            -20, -10, -10, -10, -10, -10, -10, -20
        )
    }
}
