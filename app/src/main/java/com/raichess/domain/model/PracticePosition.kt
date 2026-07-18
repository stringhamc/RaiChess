package com.raichess.domain.model

/**
 * A position saved for practice, mirroring the practice_positions schema
 * in TECHNICAL_PLAN.md so the future Room migration is a field-for-field
 * mapping. MISTAKE_CORRECTION entries are currently produced by
 * Training-mode undos: an undo means the player noticed a miss/blunder
 * only after moving, which makes the pre-mistake position ideal training
 * material.
 */
data class PracticePosition(
    val id: String,
    val sourceGameId: Long? = null,
    val sourceMoveNumber: Int,
    val fen: String,
    val category: PracticeCategory,
    val difficulty: Int = 0,
    val timesPracticed: Int = 0,
    val successRate: Double = 0.0,
    val lastPracticed: Long? = null,
    val createdAt: Long
)

enum class PracticeCategory {
    TACTICS,
    ENDGAME,
    OPENING,
    MISTAKE_CORRECTION
}

/**
 * Pure list-maintenance logic for the stored practice positions,
 * separated from persistence for unit testing.
 */
object PracticePositionStore {
    const val MAX_POSITIONS = 200

    /**
     * Insert [new] into [existing], newest first.
     *
     * Positions are deduplicated by FEN: repeatedly undoing in the same
     * spot refreshes the existing entry (new createdAt) instead of
     * spamming duplicates, while practice progress (timesPracticed,
     * successRate, lastPracticed) is preserved. The oldest entries beyond
     * [MAX_POSITIONS] are evicted.
     */
    /**
     * Spaced-repetition bookkeeping for one drill attempt: running success
     * average, attempt count, recency. Creates the progress row on first
     * attempt. Pure, so the averaging math is testable without Room.
     */
    fun updatedProgress(
        previous: PracticePosition?,
        drillId: String,
        fen: String,
        solved: Boolean,
        nowMs: Long
    ): PracticePosition {
        if (previous == null) {
            return PracticePosition(
                id = drillId,
                sourceGameId = null,
                sourceMoveNumber = 0,
                fen = fen,
                category = PracticeCategory.TACTICS,
                timesPracticed = 1,
                successRate = if (solved) 1.0 else 0.0,
                lastPracticed = nowMs,
                createdAt = nowMs
            )
        }
        return previous.copy(
            timesPracticed = previous.timesPracticed + 1,
            successRate = (previous.successRate * previous.timesPracticed +
                (if (solved) 1.0 else 0.0)) / (previous.timesPracticed + 1),
            lastPracticed = nowMs
        )
    }

    fun withPosition(
        existing: List<PracticePosition>,
        new: PracticePosition
    ): List<PracticePosition> {
        val previous = existing.firstOrNull { it.fen == new.fen }
        val merged = if (previous != null) {
            new.copy(
                timesPracticed = previous.timesPracticed,
                successRate = previous.successRate,
                lastPracticed = previous.lastPracticed
            )
        } else {
            new
        }
        return (listOf(merged) + existing.filter { it.fen != new.fen })
            .take(MAX_POSITIONS)
    }
}
