package com.raichess.domain.model

/**
 * Streak math for the daily habit layer: any training activity (a
 * finished game or a solved drill) counts toward the day, and active
 * days build the streak. Pure — days are epoch-day longs supplied by
 * the caller, so every rule is testable without a clock.
 *
 * Rest-day tolerant by design (owner + research note: days off are
 * good, and streak pressure backfires): a single day off NEVER breaks
 * the streak — it counts active days, not consecutive calendar days.
 * Only two consecutive days off end the run. So the streak nudges
 * against lapsing, not against resting.
 */
object DailyStreak {

    /** Solves/games that complete the daily goal. */
    const val DAILY_GOAL = 3

    /** Consecutive days off allowed before the streak ends. */
    const val REST_DAYS_ALLOWED = 1

    data class State(
        /** Epoch day of the most recent activity (0 = never). */
        val lastDay: Long = 0,
        /** Active days in the current run, as of [lastDay]. */
        val streak: Int = 0,
        /** Activities recorded on [lastDay]. */
        val todayCount: Int = 0
    )

    fun onActivity(state: State, today: Long): State = when {
        state.lastDay == today ->
            state.copy(todayCount = state.todayCount + 1)
        // Within the rest-day allowance: the run continues (rest days
        // themselves don't count — this counts training days)
        today - state.lastDay <= REST_DAYS_ALLOWED + 1 ->
            State(lastDay = today, streak = state.streak + 1, todayCount = 1)
        else -> State(lastDay = today, streak = 1, todayCount = 1)
    }

    /**
     * The streak to display now: alive while today could still continue
     * the run (last activity within the rest-day allowance), zero once
     * too many days have been skipped.
     */
    fun displayStreak(state: State, today: Long): Int =
        if (state.lastDay >= today - (REST_DAYS_ALLOWED + 1)) state.streak else 0

    /** Activities recorded today (0 if the last activity was earlier). */
    fun countToday(state: State, today: Long): Int =
        if (state.lastDay == today) state.todayCount else 0
}
