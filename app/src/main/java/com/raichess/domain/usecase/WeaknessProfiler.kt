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
    val lossCp: Int
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

    /** Themes ranked worst-first; themes never observed are absent. */
    fun build(observations: List<MistakeObservation>): List<ThemeStat> {
        data class Acc(var score: Double = 0.0, var count: Int = 0, var lossSum: Long = 0)

        val byTheme = HashMap<ThemeTag, Acc>()
        for (obs in observations) {
            val weight = 0.5.pow(obs.gamesAgo / HALF_LIFE_GAMES)
            for (theme in obs.themes) {
                val acc = byTheme.getOrPut(theme) { Acc() }
                acc.score += weight
                acc.count++
                acc.lossSum += obs.lossCp
            }
        }
        return byTheme.entries
            .map { (theme, acc) ->
                ThemeStat(
                    theme = theme,
                    score = acc.score,
                    occurrences = acc.count,
                    avgLossCp = acc.lossSum.toDouble() / acc.count
                )
            }
            .sortedWith(compareByDescending<ThemeStat> { it.score }.thenBy { it.theme.ordinal })
    }
}
