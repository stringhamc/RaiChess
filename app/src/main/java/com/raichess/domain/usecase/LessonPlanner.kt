package com.raichess.domain.usecase

import com.raichess.domain.model.ThemeTag

/**
 * Builds an ordered lesson plan from the player's profile: first FIX the
 * top weaknesses the analysis keeps seeing, then EXPAND skill by game
 * stage — weakest phase first, remaining phases after (opening →
 * middlegame → endgame). Players with no analyzed games yet get a
 * default fundamentals curriculum, so the Lesson tab is never empty.
 *
 * Pure and recomputed from the profile each time (the same
 * recompute-don't-migrate principle as WeaknessProfiler); only the
 * per-lesson solve counts persist (LessonRepository), keyed by stable
 * lesson ids so a plan reshuffle never loses progress.
 */
object LessonPlanner {

    /** Solves needed to complete one lesson. */
    const val TARGET_SOLVES = 8

    /** Weakness lessons per plan — focus beats coverage. */
    const val MAX_WEAKNESS_LESSONS = 2

    data class Lesson(
        /** Stable id ("weakness:hanging_piece", "phase:endgame"). */
        val id: String,
        val title: String,
        val description: String,
        /** Lichess puzzle themes this lesson draws from. */
        val themes: Set<String>,
        /** Tag matching the player's own stored mistakes, when applicable. */
        val weaknessTheme: ThemeTag? = null,
        val targetSolves: Int = TARGET_SOLVES
    )

    private val WEAKNESS_TITLES = mapOf(
        ThemeTag.HANGING_PIECE to "Stop hanging pieces",
        ThemeTag.ALLOWED_TACTIC to "Defend against tactics",
        ThemeTag.ALLOWED_MATE to "See mate threats coming",
        ThemeTag.MISSED_MATE to "Finish with mate",
        ThemeTag.MISSED_CAPTURE to "Take what's offered"
    )

    private val PHASE_LESSONS = mapOf(
        ThemeTag.OPENING to Lesson(
            id = "phase:opening",
            title = "Opening skills",
            description = "Sharpen how your games begin",
            themes = DrillSelector.PHASE_TO_LICHESS_THEMES.getValue(ThemeTag.OPENING)
        ),
        ThemeTag.MIDDLEGAME to Lesson(
            id = "phase:middlegame",
            title = "Middlegame skills",
            description = "Plans and tactics where most games are decided",
            themes = DrillSelector.PHASE_TO_LICHESS_THEMES.getValue(ThemeTag.MIDDLEGAME)
        ),
        ThemeTag.ENDGAME to Lesson(
            id = "phase:endgame",
            title = "Endgame technique",
            description = "Convert advantages when the board empties",
            themes = DrillSelector.PHASE_TO_LICHESS_THEMES.getValue(ThemeTag.ENDGAME)
        )
    )

    /** The full-phase order used to append phases the profile hasn't seen. */
    private val ALL_PHASES = listOf(ThemeTag.OPENING, ThemeTag.MIDDLEGAME, ThemeTag.ENDGAME)

    /** Fundamentals curriculum for a profile with no observations yet. */
    private val DEFAULT_PLAN = listOf(
        Lesson(
            id = "core:tactics",
            title = "Tactics fundamentals",
            description = "Forks, pins, and skewers — the bread and butter",
            themes = setOf("fork", "pin", "skewer", "discoveredAttack")
        ),
        Lesson(
            id = "core:mates",
            title = "Mating patterns",
            description = "Recognize the standard finishes",
            themes = setOf("mate", "mateIn1", "mateIn2", "backRankMate")
        )
    ) + ALL_PHASES.map { PHASE_LESSONS.getValue(it) }

    /**
     * The ordered plan: weakness lessons (worst first, capped), then phase
     * lessons weakest-observed phase first with unobserved phases after.
     */
    fun buildPlan(profile: WeaknessProfile): List<Lesson> {
        // No observations at all → fundamentals first; the appended phase
        // list below would otherwise make the plan non-empty by construction
        if (profile.weaknesses.isEmpty() && profile.phases.isEmpty()) return DEFAULT_PLAN
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
        val phaseOrder = (profile.phases.map { it.theme } + ALL_PHASES).distinct()
        val phaseLessons = phaseOrder.mapNotNull { PHASE_LESSONS[it] }
        return weaknessLessons + phaseLessons
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
