package com.raichess.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.raichess.domain.model.DailyStreak

/**
 * Persists the daily-habit state (see [DailyStreak]): last active day,
 * consecutive-day streak, and today's activity count. Epoch-UTC days —
 * simple and stable; the midnight-timezone nuance isn't worth clock
 * complexity for a gentle streak.
 */
class DailyRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun today(): Long = System.currentTimeMillis() / MILLIS_PER_DAY

    private fun state() = DailyStreak.State(
        lastDay = prefs.getLong(KEY_LAST_DAY, 0),
        streak = prefs.getInt(KEY_STREAK, 0),
        todayCount = prefs.getInt(KEY_TODAY_COUNT, 0)
    )

    /** Record one training activity (finished game / solved drill). */
    fun recordActivity() {
        val updated = DailyStreak.onActivity(state(), today())
        prefs.edit()
            .putLong(KEY_LAST_DAY, updated.lastDay)
            .putInt(KEY_STREAK, updated.streak)
            .putInt(KEY_TODAY_COUNT, updated.todayCount)
            .apply()
    }

    /** The streak to display right now (alive through yesterday). */
    fun displayStreak(): Int = DailyStreak.displayStreak(state(), today())

    /** Activities recorded today. */
    fun countToday(): Int = DailyStreak.countToday(state(), today())

    companion object {
        private const val PREFS_NAME = "raichess_daily"
        private const val KEY_LAST_DAY = "last_day"
        private const val KEY_STREAK = "streak"
        private const val KEY_TODAY_COUNT = "today_count"
        private const val MILLIS_PER_DAY = 86_400_000L
    }
}
