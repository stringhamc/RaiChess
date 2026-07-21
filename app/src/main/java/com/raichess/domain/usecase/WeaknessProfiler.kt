package com.raichess.domain.usecase

import com.raichess.domain.model.ThemeTag
import kotlin.math.pow

/**
 * One tagged mistake, positioned by how many games ago it happened
 * (0 = the most recent game with mistakes).
 */
data class MistakeObservation(
    val gamesAgo: Int,
    val themes: Set<ThemeTag>,
    val lossCp: Int,
    /** Times this exact position was drilled in Practice. */
    val timesDrilled: Int = 0,
    /** Success rate of those drills, 0..1. */
    val drillSuccessRate: Double = 0.0
)

/** Aggregated standing of one theme in the player's current profile. */
data class ThemeStat(
    val theme: ThemeTag,
    /** Recency-weighted frequency — the ranking key. */
    val score: Double,
    /** Raw occurrence count, unweighted (for display: "5 times in your last N games"). */
    val occurrences: Int,
    /** Average severity of the mistakes carrying this tag, in centipawns. */
    val avgLossCp: Double
)

/**
 * The player's current profile, ranked worst-first within each list.
 *
 * [weaknesses] and [phases] are ranked separately because they aren't
 * comparable: a phase tag attaches to every graded mistake, while the
 * substantive detectors are deliberately conservative — mixed together,
 * "middlegame" would top the list by construction and crowd out the
 * actionable weakness a hint should target.
 */
data class WeaknessProfile(
    /** Substantive mistake types (hanging pieces, missed mates, ...). */
    val weaknesses: List<ThemeStat>,
    /** Where in the game the mistakes happen. */
    val phases: List<ThemeStat>
) {
    companion object {
        val EMPTY = WeaknessProfile(emptyList(), emptyList())
    }
}

/**
 * Derives the player's weakness profile from stored mistake observations
 * (Phase B of the coaching roadmap). The core design rule: observations are
 * immutable, the profile is recomputed — so a player who stops hanging
 * pieces sees that weakness *decay out* rather than follow them forever.
 *
 * Each observation is weighted 0.5^(gamesAgo / [HALF_LIFE_GAMES]): a
 * mistake twenty games ago counts half as much as one from the latest game,
 * forty games ago a quarter, and so on. Pure math, no storage or Android.
 */
object WeaknessProfiler {

    /** Games for an observation's weight to halve. */
    const val HALF_LIFE_GAMES = 20.0

    /** Drill reps before mastery starts discounting an observation. */
    const val MASTERY_MIN_REPS = 2

    /**
     * Cap on the mastery discount: a fully-mastered drill still leaves
     * 40% of the observation's weight, because solving a position you've
     * seen isn't the same as not making the mistake in a live game.
     */
    const val MASTERY_MAX_DISCOUNT = 0.6

    /** Themes never observed are absent from both lists. */
    fun build(observations: List<MistakeObservation>): WeaknessProfile {
        data class Acc(var score: Double = 0.0, var count: Int = 0, var lossSum: Long = 0)

        val byTheme = HashMap<ThemeTag, Acc>()
        for (obs in observations) {
            // Practicing a mistake to mastery closes the loop: drilled
            // observations count less, so the profile reflects what the
            // player has actually worked on, not just what they once did
            val mastery = if (obs.timesDrilled >= MASTERY_MIN_REPS) {
                MASTERY_MAX_DISCOUNT * obs.drillSuccessRate.coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            val weight = 0.5.pow(obs.gamesAgo / HALF_LIFE_GAMES) * (1.0 - mastery)
            for (theme in obs.themes) {
                val acc = byTheme.getOrPut(theme) { Acc() }
                acc.score += weight
                acc.count++
                acc.lossSum += obs.lossCp
            }
        }
        val ranked = byTheme.entries
            .map { (theme, acc) ->
                ThemeStat(
                    theme = theme,
                    score = acc.score,
                    occurrences = acc.count,
                    avgLossCp = acc.lossSum.toDouble() / acc.count
                )
            }
            .sortedWith(compareByDescending<ThemeStat> { it.score }.thenBy { it.theme.ordinal })
        val (phases, weaknesses) = ranked.partition { it.theme.isPhase }
        return WeaknessProfile(weaknesses = weaknesses, phases = phases)
    }
}
