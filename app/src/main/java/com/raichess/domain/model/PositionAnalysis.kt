package com.raichess.domain.model

/**
 * An engine's verdict on a single position: the evaluation for the side to
 * move plus the best continuation it found. Produced by
 * [com.raichess.data.engine.ChessEngine.analyze].
 *
 * Exactly one of [scoreCp] / [mateIn] is non-null (UCI reports one or the
 * other). Both are from the side-to-move's perspective: positive means the
 * side to move is better / delivering mate.
 */
data class PositionAnalysis(
    /** Evaluation in centipawns, side-to-move perspective. */
    val scoreCp: Int?,
    /** Moves until mate (positive: side to move mates; negative: gets mated). */
    val mateIn: Int?,
    /** Best move in lowercase LAN (e.g. "e2e4", "e7e8q"), null if none. */
    val bestMoveLan: String?,
    /** Principal variation in LAN, starting with [bestMoveLan]. May be empty. */
    val pv: List<String> = emptyList(),
    /** Search depth the verdict came from. */
    val depth: Int = 0
) {
    /**
     * The evaluation as a bounded centipawn number, mapping mate scores to
     * ±[cap]. Capping keeps centipawn-loss math meaningful: a swing from a
     * +40000 mate score to +900 is not a 391-pawn blunder.
     */
    fun effectiveCp(cap: Int = MoveClassifier.EVAL_CAP_CP): Int = when {
        mateIn != null -> if (mateIn > 0) cap else -cap
        scoreCp != null -> scoreCp.coerceIn(-cap, cap)
        else -> 0
    }
}
