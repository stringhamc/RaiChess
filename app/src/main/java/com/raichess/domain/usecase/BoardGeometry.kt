package com.raichess.domain.usecase

import kotlin.math.abs

/**
 * FEN-array board geometry (chars indexed a1=0..h8=63, as
 * [HintAdvisor.parseFenBoard] produces) shared by the coach's prose
 * builders: applying a move, and asking who attacks what. Pseudo-legal on
 * purpose — pins and x-rays are ignored — which is fine for explanations
 * that only name what is plainly on the board. Pure, no chesslib.
 */
internal object BoardGeometry {

    private val KNIGHT_JUMPS = listOf(
        1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2
    )
    private val ROOK_RAYS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val BISHOP_RAYS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)

    /** Ordinal → "e4". */
    fun squareName(square: Int): String = "${'a' + square % 8}${square / 8 + 1}"

    /**
     * The board after a LAN move, or null when malformed. Trusts the move
     * (it came from the engine or the game record) but handles the two
     * moves whose side effects reach beyond from→to: en passant (a pawn
     * capturing diagonally onto an empty square removes the bypassed
     * pawn) and castling (the king's two-file slide brings the rook across).
     */
    fun applied(board: List<Char?>, lan: String): List<Char?>? {
        val from = HintAdvisor.squareOrdinal(lan.take(2)) ?: return null
        val to = HintAdvisor.squareOrdinal(lan.drop(2).take(2)) ?: return null
        val piece = board[from] ?: return null
        val out = board.toMutableList()
        if (piece.lowercaseChar() == 'p' && from % 8 != to % 8 && board[to] == null) {
            out[(from / 8) * 8 + (to % 8)] = null
        }
        if (piece.lowercaseChar() == 'k' && abs(from % 8 - to % 8) == 2) {
            val rank = (from / 8) * 8
            if (to % 8 == 6) {
                out[rank + 5] = out[rank + 7]
                out[rank + 7] = null
            } else {
                out[rank + 3] = out[rank + 0]
                out[rank + 0] = null
            }
        }
        val promotion = lan.getOrNull(4)
        out[to] = when {
            promotion == null -> piece
            piece.isUpperCase() -> promotion.uppercaseChar()
            else -> promotion.lowercaseChar()
        }
        out[from] = null
        return out
    }

    /**
     * Squares the piece on [square] attacks (occupied ones included, so a
     * friendly piece there counts as defended). Empty for an empty square.
     */
    fun attacksFrom(board: List<Char?>, square: Int): Set<Int> {
        val piece = board[square] ?: return emptySet()
        val file = square % 8
        val rank = square / 8
        val out = mutableSetOf<Int>()
        fun add(f: Int, r: Int) {
            if (f in 0..7 && r in 0..7) out.add(r * 8 + f)
        }
        fun slide(rays: List<Pair<Int, Int>>) {
            for ((df, dr) in rays) {
                var f = file + df
                var r = rank + dr
                while (f in 0..7 && r in 0..7) {
                    out.add(r * 8 + f)
                    if (board[r * 8 + f] != null) break
                    f += df
                    r += dr
                }
            }
        }
        when (piece.lowercaseChar()) {
            'p' -> {
                // Pawns attack one rank toward the enemy
                val dr = if (piece.isUpperCase()) 1 else -1
                add(file - 1, rank + dr)
                add(file + 1, rank + dr)
            }
            'n' -> KNIGHT_JUMPS.forEach { (df, dr) -> add(file + df, rank + dr) }
            'k' -> (ROOK_RAYS + BISHOP_RAYS).forEach { (df, dr) -> add(file + df, rank + dr) }
            'b' -> slide(BISHOP_RAYS)
            'r' -> slide(ROOK_RAYS)
            'q' -> slide(ROOK_RAYS + BISHOP_RAYS)
        }
        return out
    }

    /** Squares of [byWhite]'s pieces that attack [square]. */
    fun attackers(board: List<Char?>, square: Int, byWhite: Boolean): List<Int> =
        board.indices.filter { s ->
            val piece = board[s]
            piece != null && piece.isUpperCase() == byWhite && square in attacksFrom(board, s)
        }

    /** Is [square] attacked by the side [byWhite] on [board]? */
    fun squareAttacked(board: List<Char?>, square: Int, byWhite: Boolean): Boolean =
        attackers(board, square, byWhite).isNotEmpty()

    /** Is White's king ([whiteKing]) or Black's in check on [board]? */
    fun inCheck(board: List<Char?>, whiteKing: Boolean): Boolean {
        val king = board.indexOfFirst { it == if (whiteKing) 'K' else 'k' }
        return king >= 0 && squareAttacked(board, king, byWhite = !whiteKing)
    }

    /** Material value in centipawns; kings are priceless. */
    fun value(piece: Char): Int = when (piece.lowercaseChar()) {
        'p' -> 100
        'n' -> 320
        'b' -> 330
        'r' -> 500
        'q' -> 900
        'k' -> 100_000
        else -> 0
    }

    /**
     * En prise: the (non-king) piece on [square] is attacked, and either
     * undefended or attacked by something cheaper — the same single-ply
     * exchange approximation ThemeTagger uses.
     */
    fun underThreat(board: List<Char?>, square: Int): Boolean {
        val piece = board[square] ?: return false
        if (piece.lowercaseChar() == 'k') return false
        val white = piece.isUpperCase()
        val attackers = attackers(board, square, byWhite = !white)
        if (attackers.isEmpty()) return false
        return !squareAttacked(board, square, byWhite = white) ||
            attackers.minOf { value(board[it]!!) } < value(piece)
    }
}
