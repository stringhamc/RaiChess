package com.raichess.domain.usecase

import com.raichess.domain.model.LanFormat
import com.raichess.domain.model.ThemeTag
import com.raichess.domain.model.WinProbability
import kotlin.math.abs

/**
 * Turns a graded move into prose about the *chess* that went wrong — the
 * tactic it allows, the piece it hangs, the capture it declines — instead
 * of the centipawn bill ("It cost about 3.2 pawns", field feedback: the
 * number names the size of the mistake but teaches nothing about it).
 *
 * When no tagged pattern applies (positional drift), the fallback still
 * talks chess: how the game's standing shifted (win-probability bands, in
 * words) and what kind of move the engine's choice was — a capture, a
 * check, castling — so the player leaves with a play to focus on, not a
 * score. Numbers never appear.
 *
 * Pure content over already-computed analysis — FEN-array geometry (same
 * board representation as [HintAdvisor]), no engine calls, no chesslib,
 * deterministic. Shared by the live "Why?" and the game review so the
 * coach's voice can't drift apart.
 */
object MistakeNarrator {

    /**
     * The full player-facing explanation for a graded move.
     *
     * @param fenBefore position the move was played from
     * @param moveLan the move actually played
     * @param bestLan the engine's preferred move, or null when unknown
     * @param replyLan the opponent's best answer to the played move — the
     *   punishment the mistake invites — or null when not analyzed
     * @param themes tags already attached to this mistake
     * @param evalBeforeCp eval before the move, player's perspective, capped
     * @param evalAfterCp eval after the move, player's perspective, capped
     */
    fun narrate(
        fenBefore: String,
        moveLan: String,
        bestLan: String?,
        replyLan: String?,
        themes: Set<ThemeTag>,
        evalBeforeCp: Int,
        evalAfterCp: Int
    ): String {
        val geometry = Geometry.of(fenBefore, moveLan)
        val better = bestLan
            ?.takeIf { it != moveLan }
            ?.let { " Better was ${LanFormat.arrow(it)}." }
            ?: ""
        // Mirrors ThemeTag.explainPriority: mates outrank material, and the
        // hanging piece outranks the tactic elsewhere.
        return when {
            ThemeTag.ALLOWED_MATE in themes -> {
                val start = replyLan?.let { " — ${LanFormat.arrow(it)} starts it" } ?: ""
                "This walks into a forced mate$start.$better"
            }
            ThemeTag.MISSED_MATE in themes -> {
                val start = bestLan?.let { " — ${LanFormat.arrow(it)} starts it" } ?: ""
                "You had a forced mate on the board$start."
            }
            ThemeTag.HANGING_PIECE in themes -> {
                val landed = geometry?.movedPieceName ?: "piece"
                val square = moveLan.drop(2).take(2)
                val punish = replyLan
                    ?.takeIf { it.drop(2).take(2) == square }
                    ?.let { " — ${LanFormat.arrow(it)} wins it" } ?: ""
                "This leaves your $landed on $square where it can simply be taken$punish.$better"
            }
            ThemeTag.MISSED_CAPTURE in themes -> {
                val victim = bestLan?.let { geometry?.victimOf(it, beforeMove = true) }
                val prize = victim?.let { "your opponent's $it" } ?: "material"
                val with = bestLan?.let { " with ${LanFormat.arrow(it)}" } ?: ""
                "You could have won $prize$with."
            }
            ThemeTag.ALLOWED_TACTIC in themes -> {
                val victim = replyLan?.let { geometry?.victimOf(it, beforeMove = false) }
                if (replyLan != null) {
                    val prize = victim?.let { "your $it" } ?: "material"
                    "This runs into ${LanFormat.arrow(replyLan)}, winning $prize.$better"
                } else {
                    "This allows a tactic that wins material.$better"
                }
            }
            else -> {
                val focus = bestLan
                    ?.takeIf { it != moveLan }
                    ?.let { geometry?.describeBest(it) ?: "Better was ${LanFormat.arrow(it)}." }
                shift(evalBeforeCp, evalAfterCp) + (focus?.let { " $it" } ?: "")
            }
        }
    }

    /**
     * Short consequence clause for the review badge, read after
     * "Blunder — " / "Mistake — ": the pattern when one was tagged, else
     * the game-standing shift. Never a number.
     */
    fun label(themes: Set<ThemeTag>, evalBeforeCp: Int, evalAfterCp: Int): String = when {
        ThemeTag.ALLOWED_MATE in themes -> "walked into mate"
        ThemeTag.MISSED_MATE in themes -> "missed a mate"
        ThemeTag.HANGING_PIECE in themes -> "hung a piece"
        ThemeTag.MISSED_CAPTURE in themes -> "missed free material"
        ThemeTag.ALLOWED_TACTIC in themes -> "allowed a tactic"
        else -> {
            val before = standing(evalBeforeCp)
            val after = standing(evalAfterCp)
            when {
                before == Standing.WINNING && after.atOrWorseThan(Standing.EVEN) ->
                    "gave a winning game back"
                before == Standing.BETTER && after.atOrWorseThan(Standing.EVEN) ->
                    "gave up your edge"
                before.atOrBetterThan(Standing.EVEN) && after == Standing.LOSING ->
                    "let the game slip into a loss"
                before.atOrBetterThan(Standing.EVEN) && after == Standing.WORSE ->
                    "let the game tip away"
                before == Standing.WORSE && after == Standing.LOSING ->
                    "slid into a lost position"
                else -> "gave ground"
            }
        }
    }

    // ---- game-standing shift ------------------------------------------------

    /**
     * Qualitative bands over win probability, declared best-first — so a
     * *worse* standing has a *higher* ordinal, which the two comparison
     * helpers below encapsulate (the enum's natural [Comparable] order
     * reads backwards for "better/worse" and is never used directly).
     */
    private enum class Standing { WINNING, BETTER, EVEN, WORSE, LOSING }

    private fun Standing.atOrWorseThan(other: Standing): Boolean =
        ordinal >= other.ordinal

    private fun Standing.atOrBetterThan(other: Standing): Boolean =
        ordinal <= other.ordinal

    private fun standing(evalCp: Int): Standing {
        val percent = WinProbability.percent(evalCp)
        return when {
            percent >= 75 -> Standing.WINNING
            percent >= 60 -> Standing.BETTER
            percent > 40 -> Standing.EVEN
            percent > 25 -> Standing.WORSE
            else -> Standing.LOSING
        }
    }

    /** One sentence on how the move changed who stands better, in words. */
    private fun shift(evalBeforeCp: Int, evalAfterCp: Int): String {
        val before = standing(evalBeforeCp)
        val after = standing(evalAfterCp)
        return when {
            before == Standing.WINNING && after == Standing.WINNING ->
                "This made the win harder work than it needed to be."
            before == Standing.WINNING && after == Standing.LOSING ->
                "This threw a winning position away."
            before == Standing.WINNING && after == Standing.WORSE ->
                "This turned a winning position against you."
            before == Standing.WINNING && after == Standing.EVEN ->
                "This gave a winning position back — it's anyone's game now."
            before == Standing.WINNING ->
                "This let much of a winning advantage slip."
            before == Standing.BETTER && after == Standing.LOSING ->
                "This handed your opponent a winning game."
            before == Standing.BETTER && after == Standing.WORSE ->
                "This handed your edge to your opponent."
            before == Standing.BETTER && after == Standing.EVEN ->
                "This let your advantage evaporate."
            before == Standing.EVEN && after == Standing.LOSING ->
                "This turned a level game into a losing one."
            before == Standing.EVEN && after == Standing.WORSE ->
                "This tipped a level game your opponent's way."
            before == Standing.WORSE && after == Standing.LOSING ->
                "This turned a tough spot into a losing one."
            before == Standing.LOSING ->
                "This made a difficult defense harder."
            // Same-band slips (the grader can flag a move that doesn't
            // change who stands better): each band gets its own texture
            before == Standing.BETTER ->
                "This blunted your edge — your opponent breathes easier now."
            before == Standing.EVEN ->
                "This gave your opponent the easier side of a level game."
            // Only WORSE→(WORSE or better) remains. Graded moves never
            // improve the standing (loss ≥ 0 on capped evals), so in
            // practice this is the WORSE→WORSE same-band slip.
            else ->
                "This dug the hole a little deeper."
        }
    }

    // ---- board geometry -----------------------------------------------------

    /**
     * The pre- and post-move boards for one played move (FEN chars indexed
     * a1=0..h8=63, as [HintAdvisor.parseFenBoard] produces), or null when
     * the FEN or move doesn't parse — every caller degrades to wording
     * that needs no geometry.
     */
    private class Geometry(
        private val before: List<Char?>,
        private val after: List<Char?>,
        val movedPieceName: String?
    ) {
        /**
         * Name of the piece a LAN move captures ("rook"), or null for a
         * non-capture. [beforeMove] picks which board the capture happens
         * on: the pre-move board for the engine's unplayed best, the
         * post-move board for the opponent's reply. (An en passant reply
         * reads as capturing nothing — an accepted miss; the generic
         * "material" wording covers it.)
         */
        fun victimOf(lan: String, beforeMove: Boolean): String? {
            val to = HintAdvisor.squareOrdinal(lan.drop(2).take(2)) ?: return null
            return pieceName((if (beforeMove) before else after)[to])
        }

        /**
         * What kind of move the engine's choice was — said as the play to
         * focus on. Geometry only; intent ("keeps the initiative") would
         * be an invention, so the move's own character carries the lesson.
         */
        fun describeBest(bestLan: String): String {
            val arrow = LanFormat.arrow(bestLan)
            val from = HintAdvisor.squareOrdinal(bestLan.take(2))
            val to = HintAdvisor.squareOrdinal(bestLan.drop(2).take(2))
            if (from == null || to == null) return "The strongest plan started with $arrow."
            val mover = before[from] ?: return "The strongest plan started with $arrow."
            val victim = pieceName(before[to])
            val castles = mover.lowercaseChar() == 'k' && abs(from % 8 - to % 8) == 2
            // One fact per move, most concrete first: a capturing
            // promotion reads as the capture — what it wins is the lesson,
            // the promotion is how
            return when {
                castles -> "The move was $arrow — castle, and get your king to safety."
                victim != null -> "The move was $arrow, taking the $victim."
                bestLan.length > 4 -> "The move was $arrow — that pawn promotes."
                givesCheck(bestLan, moverIsWhite = mover.isUpperCase()) ->
                    "The move was $arrow, with check."
                else -> "The strongest plan started with $arrow."
            }
        }

        private fun givesCheck(lan: String, moverIsWhite: Boolean): Boolean {
            val position = applied(before, lan) ?: return false
            val king = position.indexOfFirst { it == if (moverIsWhite) 'k' else 'K' }
            return king >= 0 && squareAttacked(position, king, byWhite = moverIsWhite)
        }

        companion object {
            fun of(fenBefore: String, moveLan: String): Geometry? {
                val before = HintAdvisor.parseFenBoard(fenBefore) ?: return null
                val to = HintAdvisor.squareOrdinal(moveLan.drop(2).take(2)) ?: return null
                val after = applied(before, moveLan) ?: return null
                return Geometry(before, after, pieceName(after[to]))
            }

            /**
             * The board after a LAN move, or null when malformed. Trusts
             * the move (it came from the engine or the game record) but
             * handles the two moves whose side effects reach beyond
             * from→to: en passant (a pawn capturing diagonally onto an
             * empty square removes the bypassed pawn) and castling (the
             * king's two-file slide brings the rook across).
             */
            private fun applied(board: List<Char?>, lan: String): List<Char?>? {
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

            private val KNIGHT_JUMPS = listOf(
                1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2
            )
            private val ROOK_RAYS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
            private val BISHOP_RAYS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)

            /** Is [square] attacked by the side [byWhite] on [board]? */
            private fun squareAttacked(board: List<Char?>, square: Int, byWhite: Boolean): Boolean {
                val file = square % 8
                val rank = square / 8
                fun at(f: Int, r: Int): Char? =
                    if (f in 0..7 && r in 0..7) board[r * 8 + f] else null

                fun attacker(c: Char) = if (byWhite) c.uppercaseChar() else c.lowercaseChar()

                for ((df, dr) in KNIGHT_JUMPS) {
                    if (at(file + df, rank + dr) == attacker('n')) return true
                }
                for ((df, dr) in ROOK_RAYS + BISHOP_RAYS) {
                    if (at(file + df, rank + dr) == attacker('k')) return true
                }
                // Pawns attack one rank toward the enemy: a white attacker
                // sits one rank below the target square
                val pawnRank = if (byWhite) rank - 1 else rank + 1
                if (at(file - 1, pawnRank) == attacker('p') ||
                    at(file + 1, pawnRank) == attacker('p')
                ) {
                    return true
                }
                for ((rays, slider) in listOf(ROOK_RAYS to 'r', BISHOP_RAYS to 'b')) {
                    for ((df, dr) in rays) {
                        var f = file + df
                        var r = rank + dr
                        while (f in 0..7 && r in 0..7) {
                            val piece = board[r * 8 + f]
                            if (piece != null) {
                                if (piece == attacker(slider) || piece == attacker('q')) return true
                                break
                            }
                            f += df
                            r += dr
                        }
                    }
                }
                return false
            }

            /**
             * [HintAdvisor.pieceName]'s table, but null for empty squares
             * (callers use null as "not a capture" / "no piece to name").
             */
            private fun pieceName(piece: Char?): String? = piece
                ?.let { HintAdvisor.pieceName(it) }
                ?.takeIf { it != "piece" }
        }
    }
}
