package com.raichess.domain.model

/**
 * Captured pieces and material difference derived from a board snapshot
 * (the List of FEN chars indexed a1=0..h8=63 used by the game UI state).
 */
data class MaterialBalance(
    /** White pieces missing from the board (captured by black), highest value first. */
    val capturedWhitePieces: List<Char>,
    /** Black pieces missing from the board (captured by white), highest value first. */
    val capturedBlackPieces: List<Char>,
    /** On-board material difference in pawns; positive = white ahead. */
    val diff: Int
)

object MaterialCalculator {

    private val PIECE_VALUES = mapOf('p' to 1, 'n' to 3, 'b' to 3, 'r' to 5, 'q' to 9, 'k' to 0)
    private val INITIAL_COUNTS = mapOf('p' to 8, 'n' to 2, 'b' to 2, 'r' to 2, 'q' to 1)

    fun compute(squares: List<Char?>): MaterialBalance {
        val counts = mutableMapOf<Char, Int>()
        var diff = 0
        for (piece in squares) {
            if (piece == null) continue
            counts[piece] = (counts[piece] ?: 0) + 1
            val value = PIECE_VALUES[piece.lowercaseChar()] ?: 0
            diff += if (piece.isUpperCase()) value else -value
        }
        return MaterialBalance(
            capturedWhitePieces = missingPieces(counts, white = true),
            capturedBlackPieces = missingPieces(counts, white = false),
            diff = diff
        )
    }

    private fun missingPieces(counts: Map<Char, Int>, white: Boolean): List<Char> {
        // A promoted pawn leaves the pawn count one short without being a
        // capture, so count net promotions (pieces above their starting
        // number) and discount that many "missing" pawns — otherwise the
        // captured row shows a phantom pawn for the promoting side.
        val promotions = listOf('q', 'r', 'b', 'n').sumOf { type ->
            val piece = if (white) type.uppercaseChar() else type
            ((counts[piece] ?: 0) - INITIAL_COUNTS.getValue(type)).coerceAtLeast(0)
        }
        val missing = mutableListOf<Char>()
        // Highest value first for display
        for (type in listOf('q', 'r', 'b', 'n', 'p')) {
            val piece = if (white) type.uppercaseChar() else type
            var count = (INITIAL_COUNTS.getValue(type) - (counts[piece] ?: 0))
                .coerceAtLeast(0)
            if (type == 'p') count = (count - promotions).coerceAtLeast(0)
            repeat(count) { missing.add(piece) }
        }
        return missing
    }
}
