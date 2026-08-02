package com.raichess.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.raichess.domain.model.TrainingLoad
import com.raichess.domain.model.TrainingStatus

/**
 * Persists the per-day activity log behind [TrainingLoad] (epoch-UTC day
 * → activities that day, retained ~two weeks). "day=count;" codec, same
 * delimiter style as LessonPlanner's solves. Epoch-UTC days are stable
 * and simple; the midnight-timezone nuance isn't worth clock complexity.
 */
class DailyRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun today(): Long = System.currentTimeMillis() / MILLIS_PER_DAY

    private fun dayCounts(): Map<Long, Int> =
        prefs.getString(KEY_DAY_COUNTS, null)
            ?.takeIf { it.isNotEmpty() }
            ?.split(';')
            ?.mapNotNull { entry ->
                val day = entry.substringBefore('=', "").toLongOrNull()
                val count = entry.substringAfter('=', "").toIntOrNull()
                if (day == null || count == null) null else day to count
            }
            ?.toMap()
            ?: emptyMap()

    /** Record one training activity (finished game / solved drill). */
    fun recordActivity() {
        val updated = TrainingLoad.record(dayCounts(), today())
        prefs.edit()
            .putString(
                KEY_DAY_COUNTS,
                updated.entries.joinToString(";") { "${it.key}=${it.value}" }
            )
            .apply()
    }

    /** The coach's current read on the training pattern (null = no history). */
    fun trainingStatus(): TrainingStatus? = TrainingLoad.status(dayCounts(), today())

    /** Activities recorded today. */
    fun countToday(): Int = TrainingLoad.countToday(dayCounts(), today())

    companion object {
        private const val PREFS_NAME = "raichess_daily"
        private const val KEY_DAY_COUNTS = "day_counts"
        private const val MILLIS_PER_DAY = 86_400_000L
    }
}
