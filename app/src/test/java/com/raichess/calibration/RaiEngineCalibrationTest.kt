package com.raichess.calibration

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.MoveGenerator
import com.raichess.data.engine.RaiEngine
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Cross-validates RaiEngine's heuristic ELO labels by playing each band
 * against a real Stockfish limited to known UCI_Elo anchor strengths, then
 * converting the match score into an implied rating with the ELO formula
 * (impliedDiff = -400 * log10(1/score - 1)).
 *
 * This is a measurement, not a pass/fail gate: it prints a report and only
 * asserts that games actually completed. It is OFF by default — a normal
 * `testDebugUnitTest` run skips it via the Assume gate — and runs on demand
 * through the calibrate-raiengine workflow (or locally with
 * `-Draichess.calibrate=true -Dstockfish.path=$(command -v stockfish)`),
 * because it needs a desktop Stockfish binary and several minutes of match
 * play. See build.gradle.kts testOptions for the property passthrough.
 */
class RaiEngineCalibrationTest {

    @Test
    fun `measure implied elo of each raiengine band against stockfish anchors`() {
        assumeTrue(
            "calibration is opt-in: run with -Draichess.calibrate=true",
            System.getProperty("raichess.calibrate") == "true"
        )
        val stockfishPath = System.getProperty("stockfish.path") ?: "stockfish"
        val gamesPerPairing =
            (System.getProperty("raichess.calibrate.games") ?: "$DEFAULT_GAMES").toInt()

        val report = StringBuilder()
        report.appendLine("RaiEngine calibration vs Stockfish ($stockfishPath)")
        report.appendLine("band | anchor | games | score | implied ELO")
        report.appendLine("-----+--------+-------+-------+------------")

        var totalGames = 0
        for (band in BANDS) {
            for (anchor in ANCHORS) {
                UciProcessClient(stockfishPath).use { stockfish ->
                    stockfish.startLimitedTo(anchor)
                    var score = 0.0
                    for (game in 0 until gamesPerPairing) {
                        // Seeded per game: reruns reproduce the same matches
                        val rai = RaiEngine(
                            targetElo = band,
                            random = Random(band * 10_000 + anchor + game)
                        )
                        score += playGame(rai, stockfish, raiIsWhite = game % 2 == 0)
                        totalGames++
                    }
                    val fraction = score / gamesPerPairing
                    report.appendLine(
                        "%4d | %6d | %5d | %.3f | %11s".format(
                            band, anchor, gamesPerPairing, fraction,
                            impliedEloText(anchor, fraction)
                        )
                    )
                }
            }
        }

        report.appendLine()
        report.appendLine(
            "Implied ELO outside [anchor-$MAX_MEASURABLE_DIFF, anchor+$MAX_MEASURABLE_DIFF] " +
                "saturates the formula (near 0%/100% scores) and reads as a bound, not a value."
        )
        println(report)
        assert(totalGames == BANDS.size * ANCHORS.size * gamesPerPairing)
    }

    /**
     * One game between [rai] and [stockfish]; returns RaiEngine's score
     * (1 win / 0.5 draw / 0 loss). Games hitting [MAX_PLIES] are adjudicated
     * as draws — with these settings that is rare and slightly favours the
     * weaker side, which for label validation is the conservative direction.
     */
    private fun playGame(
        rai: RaiEngine,
        stockfish: UciProcessClient,
        raiIsWhite: Boolean
    ): Double {
        val board = Board()
        stockfish.newGame()
        var plies = 0
        while (plies < MAX_PLIES) {
            if (board.isMated) {
                val raiToMove = (board.sideToMove == Side.WHITE) == raiIsWhite
                return if (raiToMove) 0.0 else 1.0
            }
            if (board.isDraw) return 0.5

            val raiToMove = (board.sideToMove == Side.WHITE) == raiIsWhite
            val move = if (raiToMove) {
                rai.selectMove(board) ?: return 0.5 // unreachable after the checks above
            } else {
                val lan = stockfish.bestMove(board.fen, SF_MOVE_TIME_MS) ?: return 0.5
                MoveGenerator.generateLegalMoves(board)
                    .firstOrNull { it.toString().lowercase() == lan }
                    ?: error("stockfish played illegal move $lan in ${board.fen}")
            }
            board.doMove(move)
            plies++
        }
        return 0.5
    }

    /** Match score -> implied rating; clamped so a shutout stays finite. */
    private fun impliedEloText(anchor: Int, fraction: Double): String {
        val clamped = fraction.coerceIn(CLAMP_MIN, 1.0 - CLAMP_MIN)
        val implied = anchor + (-400.0 * log10(1.0 / clamped - 1.0)).roundToInt()
        return when {
            fraction <= CLAMP_MIN -> "<= $implied"
            fraction >= 1.0 - CLAMP_MIN -> ">= $implied"
            else -> "$implied"
        }
    }

    companion object {
        // The RaiEngine band the app actually serves (below EngineFactory's
        // 1350 Stockfish floor), sampled at its curve breakpoints
        private val BANDS = intArrayOf(600, 800, 1000, 1100, 1300)

        // 1320 is modern Stockfish's UCI_Elo floor; 1500 gives a second
        // reference point so the two implied ratings can sanity-check each
        // other (both should land near the same value for a given band)
        private val ANCHORS = intArrayOf(1320, 1500)

        private const val DEFAULT_GAMES = 12
        private const val SF_MOVE_TIME_MS = 60L
        private const val MAX_PLIES = 240

        // Score clamp for the implied-ELO formula; 0.02 caps the measurable
        // difference at ~676 points either side of the anchor
        private const val CLAMP_MIN = 0.02
        private const val MAX_MEASURABLE_DIFF = 676
    }
}
