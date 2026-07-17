package com.raichess.domain.usecase

import com.raichess.domain.model.PositionAnalysis

/**
 * Content for the hint ladder: rung 1 points at the piece to look at
 * (name + square highlight), rung 2 shows the exact move, rung 3 shows
 * the move a deeper engine search settles on (the caller runs the longer
 * search and passes its result here). A text nudge rung was tried and
 * dropped — it read as encouragement rather than help; pointing at the
 * board is the hint.
 *
 * Pure content selection over an already-computed [PositionAnalysis] —
 * no engine calls, no Android/chesslib types, fully unit-testable.
 */
object HintAdvisor {

    const val MAX_LEVEL = 3

    /** The rung whose content requires a fresh, deeper engine search. */
    const val DEEP_LEVEL = 3

    data class Hint(
        val text: String,
        /** Board square ordinals (a1=0 .. h8=63) to highlight. */
        val highlights: Set<Int>
    )

    /**
     * The hint for [level] (1..[MAX_LEVEL]), or null when no hint can be
     * given (no best move known, malformed data, level out of range).
     *
     * @param fen the position the analysis was computed for
     */
    fun hint(level: Int, analysis: PositionAnalysis, fen: String): Hint? {
        val bestLan = analysis.bestMoveLan ?: return null
        if (bestLan.length < 4) return null
        val fromLan = bestLan.substring(0, 2)
        val toLan = bestLan.substring(2, 4)
        val from = squareOrdinal(fromLan) ?: return null
        val to = squareOrdinal(toLan) ?: return null

        return when (level) {
            1 -> {
                val piece = parseFenBoard(fen)?.getOrNull(from)
                    ?.let { pieceName(it) } ?: "piece"
                Hint("Look at your $piece on $fromLan.", setOf(from))
            }
            2 -> Hint(
                "Play $fromLan → $toLan${promotionSuffix(bestLan)}.",
                setOf(from, to)
            )
            DEEP_LEVEL -> Hint(
                "After a deeper look: play $fromLan → $toLan${promotionSuffix(bestLan)}.",
                setOf(from, to)
            )
            else -> null
        }
    }

    private fun promotionSuffix(bestLan: String): String =
        bestLan.getOrNull(4)?.let { " (promote to ${pieceName(it)})" } ?: ""

    /** "e2" → 12 (a1=0..h8=63), or null if malformed. */
    fun squareOrdinal(lanSquare: String): Int? {
        if (lanSquare.length != 2) return null
        val file = lanSquare[0] - 'a'
        val rank = lanSquare[1] - '1'
        if (file !in 0..7 || rank !in 0..7) return null
        return rank * 8 + file
    }

    /**
     * Parse a FEN's piece-placement field into FEN chars indexed a1=0..h8=63
     * (nulls for empty squares), or null if malformed.
     */
    fun parseFenBoard(fen: String): List<Char?>? {
        val placement = fen.trim().split(' ').firstOrNull() ?: return null
        val rankStrings = placement.split('/')
        if (rankStrings.size != 8) return null
        val board = arrayOfNulls<Char>(64)
        for ((i, rankString) in rankStrings.withIndex()) {
            val rank = 7 - i // FEN lists rank 8 first
            var file = 0
            for (c in rankString) {
                if (c.isDigit()) {
                    file += c - '0'
                } else {
                    if (file > 7) return null
                    board[rank * 8 + file] = c
                    file++
                }
            }
            if (file != 8) return null
        }
        return board.toList()
    }

    private fun pieceName(fenChar: Char): String = when (fenChar.lowercaseChar()) {
        'p' -> "pawn"
        'n' -> "knight"
        'b' -> "bishop"
        'r' -> "rook"
        'q' -> "queen"
        'k' -> "king"
        else -> "piece"
    }
}
