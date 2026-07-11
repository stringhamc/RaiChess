package com.raichess.ui.game

import com.raichess.R

/**
 * Maps board snapshot chars (FEN piece letters, uppercase = white) to the
 * generated minimal vector piece drawables.
 */
object ChessPieceIcons {

    fun forChar(piece: Char): Int = when (piece) {
        'K' -> R.drawable.ic_piece_wk
        'Q' -> R.drawable.ic_piece_wq
        'R' -> R.drawable.ic_piece_wr
        'B' -> R.drawable.ic_piece_wb
        'N' -> R.drawable.ic_piece_wn
        'P' -> R.drawable.ic_piece_wp
        'k' -> R.drawable.ic_piece_bk
        'q' -> R.drawable.ic_piece_bq
        'r' -> R.drawable.ic_piece_br
        'b' -> R.drawable.ic_piece_bb
        'n' -> R.drawable.ic_piece_bn
        'p' -> R.drawable.ic_piece_bp
        // boardSnapshot() only ever yields valid FEN piece chars; a stray
        // char signals a bug elsewhere, so fail loudly rather than hide it
        else -> throw IllegalArgumentException("Not a FEN piece char: $piece")
    }

    fun contentDescription(piece: Char): String {
        val color = if (piece.isUpperCase()) "white" else "black"
        val name = when (piece.lowercaseChar()) {
            'k' -> "king"
            'q' -> "queen"
            'r' -> "rook"
            'b' -> "bishop"
            'n' -> "knight"
            else -> "pawn"
        }
        return "$color $name"
    }
}
