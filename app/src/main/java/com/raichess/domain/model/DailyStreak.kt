package com.raichess.domain.model

/**
 * Day-streak math for the daily habit layer: any training activity (a
 * finished game or a solved drill) counts toward the day, consecutive
 * active days build the streak, and a missed day resets it. Pure — days
 * are epoch-day longs supplied by the caller, so every rule is testable
 * without a clock.
 *
 * Deliberately gentle (research note: streak pressure backfires on
 * strugglers): the streak survives overnight — it shows as alive the day
 * after activity, and only reads zero once a full day has been skipped.
 */
object DailyStreak {

    /** Solves/games that complete the daily goal. */
    const val DAILY_GOAL = 3

    data class State(
        /** Epoch day of the most recent activity (0 = never). */
        val lastDay: Long = 0,
        /** Consecutive active days as of [lastDay]. */
        val streak: Int = 0,
        /** Activities recorded on [lastDay]. */
        val todayCount: Int = 0
    )

    fun onActivity(state: State, today: Long): State = when (state.lastDay) {
        today -> state.copy(todayCount = state.todayCount + 1)
        today - 1 -> State(lastDay = today, streak = state.streak + 1, todayCount = 1)
        else -> State(lastDay = today, streak = 1, todayCount = 1)
    }

    /** The streak to display now: alive through yesterday, dead after a gap. */
    fun displayStreak(state: State, today: Long): Int = when (state.lastDay) {
        today, today - 1 -> state.streak
        else -> 0
    }

    /** Activities recorded today (0 if the last activity was earlier). */
    fun countToday(state: State, today: Long): Int =
        if (state.lastDay == today) state.todayCount else 0
}
