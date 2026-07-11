package com.raichess.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.raichess.domain.model.EloCalculator
import com.raichess.domain.model.EloStats
import com.raichess.domain.model.GameResult

/**
 * Persists the player's ELO rating and game record.
 *
 * SharedPreferences-backed for the MVP; will migrate to Room when the
 * game-history database (Phase 1 roadmap) is implemented.
 */
class PlayerProfileRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getStats(): EloStats {
        val gamesPlayed = prefs.getInt(KEY_GAMES, 0)
        return EloStats(
            currentElo = prefs.getInt(KEY_ELO, EloCalculator.DEFAULT_STARTING_ELO),
            peakElo = prefs.getInt(KEY_PEAK_ELO, EloCalculator.DEFAULT_STARTING_ELO),
            gamesPlayed = gamesPlayed,
            wins = prefs.getInt(KEY_WINS, 0),
            losses = prefs.getInt(KEY_LOSSES, 0),
            draws = prefs.getInt(KEY_DRAWS, 0),
            confidenceInterval = EloCalculator.getConfidenceInterval(gamesPlayed),
            totalUndos = prefs.getInt(KEY_TOTAL_UNDOS, 0)
        )
    }

    /** Lifetime count of Training-mode undos, a rough blunder-awareness signal. */
    fun incrementLifetimeUndos() {
        prefs.edit()
            .putInt(KEY_TOTAL_UNDOS, prefs.getInt(KEY_TOTAL_UNDOS, 0) + 1)
            .apply()
    }

    /**
     * Record a finished game and return the updated stats plus the ELO delta.
     *
     * @param moveAccuracy player's accuracy for the game (50.0 = neutral until
     *   post-game analysis is implemented)
     */
    fun recordResult(
        result: GameResult,
        opponentElo: Int,
        moveAccuracy: Double = 50.0
    ): Pair<EloStats, Int> {
        val stats = getStats()
        val newElo = EloCalculator.calculateNewElo(
            currentElo = stats.currentElo,
            opponentElo = opponentElo,
            result = result,
            moveAccuracy = moveAccuracy
        )
        val delta = newElo - stats.currentElo
        val updated = stats.withGameResult(newElo, result)

        prefs.edit()
            .putInt(KEY_ELO, updated.currentElo)
            .putInt(KEY_PEAK_ELO, updated.peakElo)
            .putInt(KEY_GAMES, updated.gamesPlayed)
            .putInt(KEY_WINS, updated.wins)
            .putInt(KEY_LOSSES, updated.losses)
            .putInt(KEY_DRAWS, updated.draws)
            .apply()

        return updated to delta
    }

    companion object {
        private const val PREFS_NAME = "raichess_profile"
        private const val KEY_ELO = "elo"
        private const val KEY_PEAK_ELO = "peak_elo"
        private const val KEY_GAMES = "games_played"
        private const val KEY_WINS = "wins"
        private const val KEY_LOSSES = "losses"
        private const val KEY_DRAWS = "draws"
        private const val KEY_TOTAL_UNDOS = "total_undos"
    }
}
