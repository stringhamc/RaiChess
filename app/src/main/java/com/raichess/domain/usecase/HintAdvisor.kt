package com.raichess.domain.usecase

import com.raichess.domain.model.PositionAnalysis
import com.raichess.domain.model.ThemeTag

/**
 * Content for the progressive hint ladder (Phase C of the coaching
 * roadmap). Each rung reveals more so a hint teaches before it solves:
 *
 * 1. A nudge — personalized from the player's top weakness when the
 *    position doesn't offer a more specific cue (mate/material available).
 * 2. The piece to look at (highlights the from-square).
 * 3. The move itself (highlights both squares).
 *
 * Pure content selection over an already-computed [PositionAnalysis] —
 * no engine calls, no Android types, fully unit-testable.
 */
object HintAdvisor {

    const val MAX_LEVEL = 3

    /** Captures below this many centipawns don't trigger the material nudge. */
    private const val SIGNIFICANT_CAPTURE_CP = 300

    data class Hint(
        val text: String,
        /** Board square ordinals (a1=0 .. h8=63) to highlight. */
        val highlights: Set<Int>
    )

    /**
     * The hint for [level] (1..[MAX_LEVEL]), or null when no hint can be
     * given (no best move known, malformed data, level out of range).
     *
     * @param squares FEN piece chars indexed a1=0..h8=63 (the UI's snapshot)
     * @param topWeakness the player's current worst substantive theme, if any
     */
    fun hint(
        level: Int,
        analysis: PositionAnalysis,
        squares: List<Char?>,
        topWeakness: ThemeTag? = null
    ): Hint? {
        val best = analysis.bestMoveLan ?: return null
        if (best.length < 4) return null
        val fromLan = best.substring(0, 2)
        val toLan = best.substring(2, 4)
        val from = squareOrdinal(fromLan) ?: return null
        val to = squareOrdinal(toLan) ?: return null

        return when (level) {
            1 -> Hint(nudgeText(analysis, squares, to, topWeakness), emptySet())
            2 -> {
                val piece = squares.getOrNull(from)?.let { pieceName(it) } ?: "piece"
                Hint("Look at your $piece on $fromLan.", setOf(from))
            }
            3 -> Hint("Best move: $fromLan → $toLan.", setOf(from, to))
            else -> null
        }
    }

    /**
     * Rung 1: what's true about the position wins over the profile —
     * a mate or material cue is more useful than a general habit reminder.
     */
    private fun nudgeText(
        analysis: PositionAnalysis,
        squares: List<Char?>,
        bestMoveTarget: Int,
        topWeakness: ThemeTag?
    ): String {
        val mate = analysis.mateIn
        if (mate != null && mate > 0) {
            return "You have a forced mate — look for checks."
        }
        val capturedPiece = squares.getOrNull(bestMoveTarget)
        if (capturedPiece != null && pieceValue(capturedPiece) >= SIGNIFICANT_CAPTURE_CP) {
            return "You can win material this move — look for captures."
        }
        return when (topWeakness) {
            ThemeTag.HANGING_PIECE ->
                "Check that your pieces are defended before you commit — loose pieces have been costing you."
            ThemeTag.MISSED_CAPTURE ->
                "You've been letting captures slip by — is anything takeable?"
            ThemeTag.MISSED_MATE ->
                "You've missed forcing wins before — any checks worth a look?"
            ThemeTag.ALLOWED_TACTIC ->
                "Ask what your opponent's best reply would win — replies have been hurting you."
            ThemeTag.ALLOWED_MATE ->
                "Mind your king's safety before committing."
            else -> "There's a stronger move here — take another look."
        }
    }

    /** "e2" → 12 (a1=0..h8=63), or null if malformed. */
    fun squareOrdinal(lanSquare: String): Int? {
        if (lanSquare.length != 2) return null
        val file = lanSquare[0] - 'a'
        val rank = lanSquare[1] - '1'
        if (file !in 0..7 || rank !in 0..7) return null
        return rank * 8 + file
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

    private fun pieceValue(fenChar: Char): Int = when (fenChar.lowercaseChar()) {
        'p' -> 100
        'n' -> 320
        'b' -> 330
        'r' -> 500
        'q' -> 900
        else -> 0
    }
}
