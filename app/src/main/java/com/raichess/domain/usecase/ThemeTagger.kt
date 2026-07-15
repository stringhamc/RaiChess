package com.raichess.domain.usecase

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.raichess.domain.model.MoveClassifier
import com.raichess.domain.model.PositionAnalysis
import com.raichess.domain.model.ThemeTag
import kotlin.math.min

/**
 * Rule-based mistake tagging (Phase B of the coaching roadmap): given a
 * graded player move, attach [ThemeTag]s explaining what kind of mistake it
 * was. Everything is detectable from board geometry plus the evals already
 * produced by GameAnalyzer — no extra engine calls.
 *
 * Deliberately conservative: tags are only attached to mistake-or-worse
 * moves (loss >= [MoveClassifier.MISTAKE_THRESHOLD_CP]), and each detector
 * prefers missing a tag over inventing one, because the weakness profile
 * amplifies whatever is stored here.
 */
object ThemeTagger {

    /** Material worth calling a "significant" win/loss when tagging. */
    private const val SIGNIFICANT_MATERIAL_CP = 300

    /** Plies before which a mistake counts as an opening mistake. */
    private const val OPENING_PLY_LIMIT = 20

    /** Non-pawn, non-king piece count at or below which it's an endgame. */
    private const val ENDGAME_PIECE_LIMIT = 6

    /**
     * Tag one played move.
     *
     * @param fenBefore position before the move
     * @param ply 0-based ply index of the move
     * @param moveLan the move actually played
     * @param analysis engine verdict on [fenBefore]
     * @param nextAnalysis engine verdict on the position after the move
     *   (side to move = opponent), or null when the move ended the game
     * @param lossCp centipawn loss already computed for the move
     */
    fun tag(
        fenBefore: String,
        ply: Int,
        moveLan: String,
        analysis: PositionAnalysis,
        nextAnalysis: PositionAnalysis?,
        lossCp: Int
    ): Set<ThemeTag> {
        if (lossCp < MoveClassifier.MISTAKE_THRESHOLD_CP) return emptySet()

        val board = Board().apply { loadFromFen(fenBefore) }
        val played = GameAnalyzer.lanToLegalMove(board, moveLan) ?: return emptySet()
        val playerSide = board.sideToMove
        val opponentSide = if (playerSide == Side.WHITE) Side.BLACK else Side.WHITE

        val tags = mutableSetOf(phaseOf(board, ply))

        // What was available and not taken (evaluated on the pre-move board).
        // Capture detection reads the piece on the move's destination, so an
        // en passant capture reads as capturing nothing — a known false
        // negative, the direction this tagger prefers to err in.
        val bestLan = analysis.bestMoveLan
        val playedBest = bestLan != null && bestLan == moveLan.lowercase()
        if (!playedBest && bestLan != null) {
            if ((analysis.mateIn ?: 0) > 0) tags.add(ThemeTag.MISSED_MATE)
            val best = GameAnalyzer.lanToLegalMove(board, bestLan)
            if (best != null &&
                pieceValue(board.getPiece(best.to), opponentSide) >= SIGNIFICANT_MATERIAL_CP
            ) {
                tags.add(ThemeTag.MISSED_CAPTURE)
            }
        }

        // What the move gave away (evaluated on the post-move board)
        board.doMove(played)

        if ((nextAnalysis?.mateIn ?: 0) > 0) tags.add(ThemeTag.ALLOWED_MATE)

        // Single-ply exchange approximation, not a full SEE: hanging means
        // attacked while undefended, or attacked by something cheaper (a
        // defended knight attacked by a pawn is still lost material). Pinned
        // "defenders" are counted as defending even though they couldn't
        // legally recapture — a false *negative*, which is the direction
        // this tagger prefers to err in.
        val movedPieceValue = pieceValue(board.getPiece(played.to), playerSide)
        val attackers = board.squareAttackedBy(played.to, opponentSide)
        val hanging = movedPieceValue > 0 && attackers != 0L && (
            board.squareAttackedBy(played.to, playerSide) == 0L ||
                minPieceValueOn(board, attackers) < movedPieceValue
            )
        if (hanging) tags.add(ThemeTag.HANGING_PIECE)

        val replyLan = nextAnalysis?.bestMoveLan
        if (replyLan != null) {
            val reply = GameAnalyzer.lanToLegalMove(board, replyLan)
            // A winning reply against the moved piece itself is the
            // HANGING_PIECE case; ALLOWED_TACTIC is for damage elsewhere.
            if (reply != null && reply.to != played.to &&
                pieceValue(board.getPiece(reply.to), playerSide) >= SIGNIFICANT_MATERIAL_CP
            ) {
                tags.add(ThemeTag.ALLOWED_TACTIC)
            }
        }

        return tags
    }

    /** Endgame wins over ply count: a queen trade on move 8 is still an endgame. */
    private fun phaseOf(board: Board, ply: Int): ThemeTag {
        var pieces = 0
        for (index in 0 until 64) {
            val piece = board.getPiece(Square.squareAt(index))
            if (piece == Piece.NONE) continue
            val type = piece.pieceType ?: continue
            if (type != PieceType.PAWN && type != PieceType.KING) pieces++
        }
        return when {
            pieces <= ENDGAME_PIECE_LIMIT -> ThemeTag.ENDGAME
            ply < OPENING_PLY_LIMIT -> ThemeTag.OPENING
            else -> ThemeTag.MIDDLEGAME
        }
    }

    /** Value of [piece] when it belongs to [side]; 0 for NONE or the other side. */
    private fun pieceValue(piece: Piece, side: Side): Int {
        if (piece == Piece.NONE || piece.pieceSide != side) return 0
        return when (piece.pieceType) {
            PieceType.PAWN -> 100
            PieceType.KNIGHT -> 320
            PieceType.BISHOP -> 330
            PieceType.ROOK -> 500
            PieceType.QUEEN -> 900
            PieceType.KING -> KING_VALUE
            else -> 0
        }
    }

    private fun minPieceValueOn(board: Board, squaresBitboard: Long): Int {
        var minValue = Int.MAX_VALUE
        for (index in 0 until 64) {
            if ((squaresBitboard ushr index) and 1L == 0L) continue
            val piece = board.getPiece(Square.squareAt(index))
            val side = piece.pieceSide ?: continue
            minValue = min(minValue, pieceValue(piece, side))
        }
        return minValue
    }

    private const val KING_VALUE = 20_000
}
