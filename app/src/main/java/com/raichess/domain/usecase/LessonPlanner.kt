package com.raichess.domain.usecase

import com.raichess.domain.model.ThemeTag

/**
 * Builds an ordered lesson plan: first FIX the top weaknesses the
 * analysis keeps seeing in the player's own games, then CLIMB the named
 * curriculum ladder (see [Curriculum]) — the active step for the
 * player's rating, advanced as steps complete.
 *
 * Pure and recomputed each time (the same recompute-don't-migrate
 * principle as WeaknessProfiler); only the per-lesson solve counts
 * persist (LessonRepository), keyed by stable lesson ids so a plan
 * reshuffle never loses progress.
 */
object LessonPlanner {

    /** Solves needed to complete one lesson. */
    const val TARGET_SOLVES = 8

    /** Weakness lessons per plan — focus beats coverage. */
    const val MAX_WEAKNESS_LESSONS = 2

    data class Lesson(
        /** Stable id ("weakness:hanging_piece", "step2:fork"). */
        val id: String,
        val title: String,
        val description: String,
        /** Lichess puzzle themes this lesson draws from. */
        val themes: Set<String>,
        /** Tag matching the player's own stored mistakes, when applicable. */
        val weaknessTheme: ThemeTag? = null,
        val targetSolves: Int = TARGET_SOLVES,
        /** Short concept teaching, coach voice, shown before drilling. */
        val intro: String? = null
    )

    private val WEAKNESS_TITLES = mapOf(
        ThemeTag.HANGING_PIECE to "Stop hanging pieces",
        ThemeTag.ALLOWED_TACTIC to "Defend against tactics",
        ThemeTag.ALLOWED_MATE to "See mate threats coming",
        ThemeTag.MISSED_MATE to "Finish with mate",
        ThemeTag.MISSED_CAPTURE to "Take what's offered"
    )

    /**
     * The ordered plan: weakness lessons first (worst first, capped), then
     * the units of the player's active curriculum step (see
     * [Curriculum.activeStep]).
     */
    fun buildPlan(
        profile: WeaknessProfile,
        rating: Int,
        solvesById: Map<String, Int>
    ): List<Lesson> {
        val weaknessLessons = profile.weaknesses
            .take(MAX_WEAKNESS_LESSONS)
            .mapNotNull { stat ->
                val themes = DrillSelector.WEAKNESS_TO_LICHESS_THEMES[stat.theme]
                    ?: return@mapNotNull null
                Lesson(
                    id = "weakness:${stat.theme.id}",
                    title = WEAKNESS_TITLES[stat.theme] ?: "Fix: ${stat.theme.id}",
                    description = "Seen ${stat.occurrences}× in your recent games",
                    themes = themes,
                    weaknessTheme = stat.theme
                )
            }
        return weaknessLessons + Curriculum.activeStep(rating, solvesById).units
    }

    /** First lesson not yet solved to target, or null when the plan is done. */
    fun activeLesson(plan: List<Lesson>, solvesById: Map<String, Int>): Lesson? =
        plan.firstOrNull { (solvesById[it.id] ?: 0) < it.targetSolves }

    /**
     * Solve-count codec for SharedPreferences ("id=count;id=count").
     * Lesson ids contain ':', so '=' and ';' are the delimiters.
     */
    fun encodeSolves(solves: Map<String, Int>): String =
        solves.entries.joinToString(";") { "${it.key}=${it.value}" }

    fun decodeSolves(raw: String?): Map<String, Int> =
        raw?.takeIf { it.isNotEmpty() }
            ?.split(';')
            ?.mapNotNull { entry ->
                val key = entry.substringBeforeLast('=', "")
                val count = entry.substringAfterLast('=', "").toIntOrNull()
                if (key.isEmpty() || count == null) null else key to count
            }
            ?.toMap()
            ?: emptyMap()
}
