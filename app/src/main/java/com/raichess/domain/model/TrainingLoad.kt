package com.raichess.domain.model

/**
 * The coach's read on the player's recent training pattern, Garmin-style:
 * a status, not a streak (owner feedback: streaks punish rest, and rest
 * is part of training). Rest after work is RECOVERY, not a broken chain;
 * regular work is PRODUCTIVE; a long lapse is DETRAINING; a huge single
 * day is OVERREACHING — quality beats volume.
 */
enum class TrainingStatus {
    /** No training for several days — skills rust. */
    DETRAINING,
    /** A rest day after recent work — endorsed, not penalized. */
    RECOVERY,
    /** Occasional light work — holding steady, not progressing. */
    MAINTAINING,
    /** Regular work across the week — how improvement happens. */
    PRODUCTIVE,
    /** An unusually heavy day — time to stop while sharp. */
    OVERREACHING
}

/**
 * Pure training-load math over a per-day activity log (epoch day →
 * activities that day; an activity is a finished game or a solved
 * drill). Days are supplied by the caller so every rule tests without a
 * clock. Null status until the first activity ever — no verdict on a
 * brand-new player.
 */
object TrainingLoad {

    /** Solves/games that complete the daily goal (the Train tile's "tiny win"). */
    const val DAILY_GOAL = 3

    /** Days of history kept; the status window is the last 7. */
    const val RETENTION_DAYS = 14

    /** Days without training before the status turns DETRAINING. */
    const val DETRAINING_GAP_DAYS = 4

    /** Active days in the last 7 that read as PRODUCTIVE on their own. */
    const val PRODUCTIVE_ACTIVE_DAYS = 4

    /** Single-day activity count that reads as OVERREACHING. */
    const val OVERREACHING_DAY_COUNT = 25

    /** Record one activity, trimming history beyond [RETENTION_DAYS]. */
    fun record(dayCounts: Map<Long, Int>, today: Long): Map<Long, Int> =
        (dayCounts + (today to (dayCounts[today] ?: 0) + 1))
            .filterKeys { it > today - RETENTION_DAYS }

    fun countToday(dayCounts: Map<Long, Int>, today: Long): Int =
        dayCounts[today] ?: 0

    fun status(dayCounts: Map<Long, Int>, today: Long): TrainingStatus? {
        val lastActive = dayCounts.filterValues { it > 0 }.keys.maxOrNull() ?: return null
        val week = (today - 6)..today
        val activeDays = week.count { (dayCounts[it] ?: 0) > 0 }
        val weekTotal = week.sumOf { dayCounts[it] ?: 0 }
        val todayCount = dayCounts[today] ?: 0
        return when {
            todayCount >= OVERREACHING_DAY_COUNT -> TrainingStatus.OVERREACHING
            today - lastActive >= DETRAINING_GAP_DAYS -> TrainingStatus.DETRAINING
            // Nothing yet today, but real work recently: that's rest, and
            // rest is endorsed
            todayCount == 0 && activeDays >= 2 -> TrainingStatus.RECOVERY
            activeDays >= PRODUCTIVE_ACTIVE_DAYS ||
                (activeDays >= 3 && weekTotal >= 4 * DAILY_GOAL) -> TrainingStatus.PRODUCTIVE
            else -> TrainingStatus.MAINTAINING
        }
    }
}
