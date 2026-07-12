package com.raichess.domain.model

/**
 * ELO rating calculator for RaiChess.
 * Uses a modified ELO system that accounts for move accuracy in addition to game results.
 */
object EloCalculator {

    const val K_FACTOR = 32 // Standard K-factor for rating changes
    const val DEFAULT_STARTING_ELO = 600 // beginner-friendly start; rating climbs with wins
    const val MIN_ELO = 400
    const val MAX_ELO = 3000

    /**
     * Calculate new ELO rating after a game
     *
     * @param currentElo Player's current ELO rating
     * @param opponentElo Opponent's ELO rating (Stockfish configured ELO)
     * @param result Game result from player's perspective
     * @param moveAccuracy Player's move accuracy percentage (0.0 - 100.0)
     * @return New ELO rating
     */
    fun calculateNewElo(
        currentElo: Int,
        opponentElo: Int,
        result: GameResult,
        moveAccuracy: Double
    ): Int {
        // Expected score using standard ELO formula
        val expectedScore = calculateExpectedScore(currentElo, opponentElo)

        // Actual score based on game result
        val actualScore = when (result) {
            GameResult.WIN -> 1.0
            GameResult.DRAW -> 0.5
            GameResult.LOSS -> 0.0
        }

        // Accuracy modifier: rewards good play even in losses
        // Range: -0.5 to +0.5 (for accuracy 0% to 100%)
        val accuracyBonus = ((moveAccuracy - 50) / 100.0).coerceIn(-0.5, 0.5)

        // Adjust actual score with accuracy (30% weight on accuracy)
        val adjustedActualScore = (actualScore + accuracyBonus * 0.3).coerceIn(0.0, 1.0)

        // Calculate rating change
        val ratingChange = (K_FACTOR * (adjustedActualScore - expectedScore)).toInt()

        // Apply change and clamp to valid range
        return (currentElo + ratingChange).coerceIn(MIN_ELO, MAX_ELO)
    }

    /**
     * Calculate expected score against an opponent
     *
     * @param playerElo Player's ELO rating
     * @param opponentElo Opponent's ELO rating
     * @return Expected score (0.0 to 1.0)
     */
    fun calculateExpectedScore(playerElo: Int, opponentElo: Int): Double {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentElo - playerElo) / 400.0))
    }

    /**
     * Get win probability as percentage
     */
    fun getWinProbability(playerElo: Int, opponentElo: Int): Double {
        return calculateExpectedScore(playerElo, opponentElo) * 100.0
    }

    /**
     * Calculate confidence interval for new players
     * First 20 games have wider confidence intervals
     *
     * @param gamesPlayed Number of games played
     * @return +/- confidence value
     */
    fun getConfidenceInterval(gamesPlayed: Int): Int {
        return when {
            gamesPlayed < 5 -> 150
            gamesPlayed < 10 -> 100
            gamesPlayed < 20 -> 50
            gamesPlayed < 50 -> 25
            else -> 0
        }
    }
}

/**
 * Game result from player's perspective
 */
enum class GameResult {
    WIN,
    DRAW,
    LOSS;

    companion object {
        fun fromPgnResult(result: String, playerColor: PlayerColor): GameResult {
            return when (result) {
                "1-0" -> if (playerColor == PlayerColor.WHITE) WIN else LOSS
                "0-1" -> if (playerColor == PlayerColor.BLACK) WIN else LOSS
                "1/2-1/2" -> DRAW
                else -> DRAW // Unfinished games count as draw
            }
        }
    }
}

/**
 * Player color
 */
enum class PlayerColor {
    WHITE,
    BLACK;

    fun opposite(): PlayerColor = when (this) {
        WHITE -> BLACK
        BLACK -> WHITE
    }
}

/**
 * ELO configuration for Stockfish.
 * Maps a target ELO to the UCI parameters used by [StockfishWasmEngine]:
 * [skillLevel] (0–20) governs playing strength and [thinkingTimeMs] seeds the
 * per-move search budget (the engine then coerces it into its own bounds).
 * RaiEngine does not read this — it derives its own scaling from the target ELO.
 */
data class EloConfiguration(
    val targetElo: Int,
    val skillLevel: Int,
    val thinkingTimeMs: Long
) {
    companion object {
        /**
         * Get Stockfish configuration for target ELO
         */
        fun forElo(elo: Int): EloConfiguration {
            return when {
                elo < 1000 -> EloConfiguration(
                    // Floor matches the opponent slider's 400 minimum
                    targetElo = elo.coerceIn(400, 999),
                    skillLevel = 0,
                    thinkingTimeMs = 500
                )
                elo < 1200 -> EloConfiguration(
                    targetElo = elo,
                    skillLevel = 3,
                    thinkingTimeMs = 800
                )
                elo < 1400 -> EloConfiguration(
                    targetElo = elo,
                    skillLevel = 6,
                    thinkingTimeMs = 1000
                )
                elo < 1600 -> EloConfiguration(
                    targetElo = elo,
                    skillLevel = 9,
                    thinkingTimeMs = 1500
                )
                elo < 1800 -> EloConfiguration(
                    targetElo = elo,
                    skillLevel = 12,
                    thinkingTimeMs = 2000
                )
                elo < 2000 -> EloConfiguration(
                    targetElo = elo,
                    skillLevel = 15,
                    thinkingTimeMs = 3000
                )
                elo < 2200 -> EloConfiguration(
                    targetElo = elo,
                    skillLevel = 18,
                    thinkingTimeMs = 4000
                )
                elo < 2400 -> EloConfiguration(
                    targetElo = elo,
                    skillLevel = 20,
                    thinkingTimeMs = 5000
                )
                elo < 2600 -> EloConfiguration(
                    targetElo = elo,
                    skillLevel = 20,
                    thinkingTimeMs = 7000
                )
                else -> EloConfiguration(
                    targetElo = elo.coerceIn(2600, 3000),
                    skillLevel = 20,
                    thinkingTimeMs = 10000
                )
            }
        }

        /**
         * Get recommended opponent ELO based on player's current ELO
         * Slightly above player's level for optimal learning
         */
        fun getRecommendedOpponentElo(playerElo: Int): Int {
            return (playerElo + 50).coerceIn(400, 2800)
        }
    }

    /**
     * Get UCI commands to configure the bundled Stockfish's strength.
     *
     * NOTE: the bundled Stockfish 10 (2019) WASM build predates the
     * UCI_Elo/UCI_LimitStrength options (added in SF11), so those are rejected
     * as "No such option" and must NOT be sent. Strength is governed solely by
     * `Skill Level` (0–20), which this build does support. [skillLevel] is
     * mapped from the target ELO in [forElo], monotonically increasing with the
     * requested rating so a higher slider still yields a stronger opponent.
     */
    fun getUciCommands(): List<String> {
        return listOf(
            "setoption name Skill Level value $skillLevel"
        )
    }
}

/**
 * ELO statistics and tracking
 */
data class EloStats(
    val currentElo: Int,
    val peakElo: Int,
    val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val confidenceInterval: Int,
    /** Lifetime Training-mode undos; a rough blunder-awareness signal. */
    val totalUndos: Int = 0
) {
    val winRate: Double
        get() = if (gamesPlayed > 0) wins.toDouble() / gamesPlayed * 100.0 else 0.0

    val drawRate: Double
        get() = if (gamesPlayed > 0) draws.toDouble() / gamesPlayed * 100.0 else 0.0

    val lossRate: Double
        get() = if (gamesPlayed > 0) losses.toDouble() / gamesPlayed * 100.0 else 0.0

    fun withGameResult(
        newElo: Int,
        result: GameResult
    ): EloStats {
        return copy(
            currentElo = newElo,
            peakElo = maxOf(peakElo, newElo),
            gamesPlayed = gamesPlayed + 1,
            wins = wins + if (result == GameResult.WIN) 1 else 0,
            losses = losses + if (result == GameResult.LOSS) 1 else 0,
            draws = draws + if (result == GameResult.DRAW) 1 else 0,
            confidenceInterval = EloCalculator.getConfidenceInterval(gamesPlayed + 1)
        )
    }
}
