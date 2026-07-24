package com.raichess.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.raichess.domain.usecase.LessonPlanner

/**
 * Persists per-lesson solve counts (the only lesson state that is stored
 * — the plan itself is recomputed from the profile, see LessonPlanner).
 * Keyed by stable lesson ids so plan reshuffles never lose progress.
 */
class LessonRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSolves(): Map<String, Int> =
        LessonPlanner.decodeSolves(prefs.getString(KEY_SOLVES, null))

    /** Increment a lesson's solve count and return the new count. */
    fun recordSolve(lessonId: String): Int {
        val solves = getSolves().toMutableMap()
        val updated = (solves[lessonId] ?: 0) + 1
        solves[lessonId] = updated
        prefs.edit().putString(KEY_SOLVES, LessonPlanner.encodeSolves(solves)).apply()
        return updated
    }

    companion object {
        private const val PREFS_NAME = "raichess_lessons"
        private const val KEY_SOLVES = "solves"
    }
}
