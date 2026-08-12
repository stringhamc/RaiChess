package com.raichess.calibration

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.MoveGenerator
import com.raichess.data.engine.EngineFactory
import com.raichess.data.engine.RaiEngine
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Phase-1 spike for human-like opponents: measures what the Maia neural
 * nets (CSSLab, per-rating human-move predictors) actually play like when
 * probed the intended way (lc0, `go nodes 1` — pure policy, no search).
 *
 * Two measurements per bundled net:
 * 1. vs Stockfish at known UCI_Elo anchors → implied rating, same
 *    methodology as [RaiEngineCalibrationTest], so the two tables are
 *    directly comparable.
 * 2. vs RaiEngine at the net's nominal rating → is our synthetic band
 *    stronger, weaker, or on par with the human-like reference?
 *
 * Opt-in and CI-only (needs desktop lc0 + weights + Stockfish): run via
 * the calibrate-maia workflow, or locally with
 * `-Draichess.calibrate.maia=true -Dlc0.path=... -Dmaia.weights.dir=...
 *  -Dstockfish.path=...`.
 */
class MaiaCalibrationTest {

    @Test
    fun `measure implied elo of maia nets against stockfish anchors and raiengine`() {
        assumeTrue(
            "maia calibration is opt-in: run with -Draichess.calibrate.maia=true",
            System.getProperty("raichess.calibrate.maia") == "true"
        )
        val lc0Path = System.getProperty("lc0.path") ?: "lc0"
        val weightsDir = File(System.getProperty("maia.weights.dir") ?: ".")
        val stockfishPath = System.getProperty("stockfish.path") ?: "stockfish"
        val gamesPerPairing =
            (System.getProperty("raichess.calibrate.games") ?: "$DEFAULT_GAMES").toInt()

        val report = StringBuilder()
        report.appendLine("Maia calibration (lc0=$lc0Path, nodes=1)")
        report.appendLine("net       | opponent        | games | score | implied ELO")
        report.appendLine("----------+-----------------+-------+-------+------------")

        var totalGames = 0
        for (band in MAIA_BANDS) {
            val weights = File(weightsDir, "maia-$band.pb.gz")
            if (!weights.isFile) {
                report.appendLine("maia-$band | MISSING WEIGHTS ($weights) — skipped")
                continue
            }

            // vs Stockfish anchors: implied rating from match score
            for (anchor in ANCHORS) {
                UciProcessClient(listOf(lc0Path, "--weights=${weights.absolutePath}"))
                    .use { maia ->
                        maia.start()
                        UciProcessClient(stockfishPath).use { stockfish ->
                            stockfish.startLimitedTo(anchor)
                            var score = 0.0
                            repeat(gamesPerPairing) { game ->
                                score += playGame(
                                    white = { fen ->
                                        if (game % 2 == 0) maia.bestMoveNodes(fen, 1)
                                        else stockfish.bestMove(fen, SF_MOVE_TIME_MS)
                                    },
                                    black = { fen ->
                                        if (game % 2 == 0) stockfish.bestMove(fen, SF_MOVE_TIME_MS)
                                        else maia.bestMoveNodes(fen, 1)
                                    },
                                    scoredSideIsWhite = game % 2 == 0
                                )
                                totalGames++
                            }
                            val fraction = score / gamesPerPairing
                            report.appendLine(
                                "maia-%d | Stockfish %5d | %5d | %.3f | %11s".format(
                                    band, anchor, gamesPerPairing, fraction,
                                    impliedEloText(anchor, fraction)
                                )
                            )
                        }
                    }
            }

            // vs RaiEngine at the same nominal rating: >0.5 means Maia is
            // stronger than our synthetic band of the same label
            UciProcessClient(listOf(lc0Path, "--weights=${weights.absolutePath}"))
                .use { maia ->
                    maia.start()
                    var score = 0.0
                    repeat(gamesPerPairing) { game ->
                        val rai = RaiEngine(
                            targetElo = band,
                            random = Random(band * 7919 + game)
                        )
                        score += playGame(
                            white = { fen ->
                                if (game % 2 == 0) maia.bestMoveNodes(fen, 1)
                                else raiMove(rai, fen)
                            },
                            black = { fen ->
                                if (game % 2 == 0) raiMove(rai, fen)
                                else maia.bestMoveNodes(fen, 1)
                            },
                            scoredSideIsWhite = game % 2 == 0
                        )
                        totalGames++
                    }
                    report.appendLine(
                        "maia-%d | RaiEngine %5d | %5d | %.3f | %11s".format(
                            band, band, gamesPerPairing, score / gamesPerPairing,
                            "(vs same label)"
                        )
                    )
                }
        }

        report.appendLine()
        report.appendLine("Anchors floor at Stockfish's UCI_Elo minimum (1320).")
        println(report)
        assert(totalGames > 0) { "no games played — are the weights present?" }
    }

    /**
     * Measures the RaiEngine → Maia seam: the app serves 950-1099 as
     * maia-1100 with a lapse rate ramping to zero (the "eased" band, see
     * EngineFactory.maiaSoftBlunderFor). Two questions per setting: how
     * strong is it against a fixed anchor, and does it sit ABOVE the top
     * of the compressed RaiEngine dial — i.e. is the seam monotonic?
     */
    @Test
    fun `measure the eased seam below maia's floor`() {
        assumeTrue(
            "maia calibration is opt-in: run with -Draichess.calibrate.maia=true",
            System.getProperty("raichess.calibrate.maia") == "true"
        )
        val lc0Path = System.getProperty("lc0.path") ?: "lc0"
        val weightsDir = File(System.getProperty("maia.weights.dir") ?: ".")
        val stockfishPath = System.getProperty("stockfish.path") ?: "stockfish"
        val gamesPerPairing =
            (System.getProperty("raichess.calibrate.games") ?: "$DEFAULT_GAMES").toInt()
        val weights = File(weightsDir, "maia-1100.pb.gz")
        assumeTrue("maia-1100 weights present", weights.isFile)

        val report = StringBuilder()
        report.appendLine("Eased-seam calibration (maia-1100 + lapse rate)")
        report.appendLine("setting     | opponent        | games | score")
        report.appendLine("------------+-----------------+-------+------")

        UciProcessClient(listOf(lc0Path, "--weights=${weights.absolutePath}")).use { maia ->
            maia.start()
            for (elo in intArrayOf(950, 1050)) {
                val eps = EngineFactory.maiaSoftBlunderFor(elo)
                // The same lapse roll the app applies (MaiaEngine)
                fun easedMove(fen: String, rnd: Random): String? {
                    if (rnd.nextDouble() < eps) {
                        val board = Board().apply { loadFromFen(fen) }
                        val legal = MoveGenerator.generateLegalMoves(board)
                        if (legal.isNotEmpty()) {
                            return legal[rnd.nextInt(legal.size)].toString().lowercase()
                        }
                    }
                    return maia.bestMoveNodes(fen, 1)
                }

                // vs the top of the compressed RaiEngine dial: monotonic
                // seam means this stays above 0.5
                var raiScore = 0.0
                repeat(gamesPerPairing) { game ->
                    val rnd = Random(elo * 31 + game)
                    val rai = RaiEngine(targetElo = 900, random = Random(elo * 977 + game))
                    raiScore += playGame(
                        white = { fen ->
                            if (game % 2 == 0) easedMove(fen, rnd) else raiMove(rai, fen)
                        },
                        black = { fen ->
                            if (game % 2 == 0) raiMove(rai, fen) else easedMove(fen, rnd)
                        },
                        scoredSideIsWhite = game % 2 == 0
                    )
                }
                report.appendLine(
                    "eased %4d | RaiEngine   900 | %5d | %.3f".format(
                        elo, gamesPerPairing, raiScore / gamesPerPairing
                    )
                )

                UciProcessClient(stockfishPath).use { stockfish ->
                    stockfish.startLimitedTo(1320)
                    var sfScore = 0.0
                    repeat(gamesPerPairing) { game ->
                        val rnd = Random(elo * 53 + game)
                        sfScore += playGame(
                            white = { fen ->
                                if (game % 2 == 0) easedMove(fen, rnd)
                                else stockfish.bestMove(fen, SF_MOVE_TIME_MS)
                            },
                            black = { fen ->
                                if (game % 2 == 0) stockfish.bestMove(fen, SF_MOVE_TIME_MS)
                                else easedMove(fen, rnd)
                            },
                            scoredSideIsWhite = game % 2 == 0
                        )
                    }
                    report.appendLine(
                        "eased %4d | Stockfish  1320 | %5d | %.3f".format(
                            elo, gamesPerPairing, sfScore / gamesPerPairing
                        )
                    )
                }
            }
        }
        println(report)
    }

    /** RaiEngine as a UCI-shaped move provider over a FEN. */
    private fun raiMove(rai: RaiEngine, fen: String): String? {
        val board = Board().apply { loadFromFen(fen) }
        return rai.selectMove(board)?.toString()?.lowercase()
    }

    /**
     * One game between two move providers; returns the scored side's
     * result (1 / 0.5 / 0). Move-limit games adjudicate as draws, same as
     * [RaiEngineCalibrationTest].
     */
    private fun playGame(
        white: (String) -> String?,
        black: (String) -> String?,
        scoredSideIsWhite: Boolean
    ): Double {
        val board = Board()
        var plies = 0
        while (plies < MAX_PLIES) {
            if (board.isMated) {
                val scoredToMove = (board.sideToMove == Side.WHITE) == scoredSideIsWhite
                return if (scoredToMove) 0.0 else 1.0
            }
            if (board.isDraw) return 0.5

            val provider = if (board.sideToMove == Side.WHITE) white else black
            val lan = provider(board.fen) ?: return 0.5
            val move = MoveGenerator.generateLegalMoves(board)
                .firstOrNull { it.toString().lowercase() == lan }
                ?: error("engine played illegal move $lan in ${board.fen}")
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
        // The bands the app would bundle first: 1100 covers the top of the
        // RaiEngine range, 1500 the middle of the Stockfish band
        private val MAIA_BANDS = intArrayOf(1100, 1500)

        private val ANCHORS = intArrayOf(1320, 1500)

        private const val DEFAULT_GAMES = 12
        private const val SF_MOVE_TIME_MS = 60L
        private const val MAX_PLIES = 240
        private const val CLAMP_MIN = 0.02
    }
}
