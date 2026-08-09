package com.raichess.data.engine

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import com.raichess.data.diagnostics.EngineDiagnostics
import com.raichess.domain.model.PositionAnalysis
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Human-like opponent: a Maia neural net (CSSLab's per-rating predictors
 * of real human moves) served by a native lc0 binary, probed the way Maia
 * is designed to be used — `go nodes 1`, one evaluation, the move straight
 * from the policy head. No search means no engine-shaped play: it makes
 * the mistakes a human at its rating makes. Calibration (CI, vs Stockfish
 * anchors and RaiEngine) showed maia-1100 crushing our synthetic "1100"
 * 12-0 while measuring near its label — this is the band's real opponent.
 *
 * Process plumbing mirrors [StockfishNativeEngine] exactly (same
 * executable-in-jniLibs trick, same generation-scoped reader, same
 * released/FAILED teardown split, same RaiEngine fallback contract). The
 * weights ship as an asset and are copied to filesDir on first use —
 * lc0 needs a real file path.
 *
 * Never used as an analyzer: hints and post-game analysis stay
 * Stockfish-quality (see EngineFactory.createAnalyzer); [analyze] simply
 * delegates to the fallback as a safety net.
 */
class MaiaEngine(
    context: Context,
    /** Net band (1100..1500 in 100s) — see [netBandFor]. */
    private val band: Int,
    private val fallback: ChessEngine
) : ChessEngine {

    private val appContext = context.applicationContext
    private val output = LinkedBlockingQueue<String>()

    @Volatile private var process: Process? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var state = State.UNINITIALIZED
    // Set once, by close() ONLY — permanent teardown. Failed inits stay
    // retryable (same released/FAILED split as the other native engine).
    @Volatile private var released = false
    @Volatile private var everFellBack = false
    @Volatile private var fallbackActive = false
    private var initAttempts = 0
    @Volatile private var attemptGeneration = 0

    private enum class State { UNINITIALIZED, READY, FAILED }

    override val activeEngineLabel: String
        get() = when {
            state == State.READY && everFellBack -> "Maia $band (recovered)"
            state == State.READY -> "Maia $band"
            everFellBack || state == State.FAILED -> "RaiEngine (fallback)"
            else -> "Maia $band"
        }

    override fun warmUp() {
        try {
            ensureReady()
        } catch (e: Exception) {
            Log.w(TAG, "warmUp failed", e)
        }
    }

    override fun selectMove(board: Board): Move? {
        return try {
            if (!ensureReady()) {
                if (released) return null
                return fallbackMove(board, "maia unavailable (init failed)")
            }
            if (released) return null

            output.clear()
            send("position fen ${board.fen}")
            // nodes=1: no search, pure policy — the human-likeness contract
            send("go nodes 1")

            val best = awaitToken(BESTMOVE_TIMEOUT_MS) { it.startsWith("bestmove") }
            if (best == null) {
                if (released) return null
                return fallbackMove(board, "no bestmove within ${BESTMOVE_TIMEOUT_MS}ms")
            }
            StockfishWasmEngine.parseUciBestMove(board, best)
                ?: fallbackMove(board, "unparseable bestmove: $best")
        } catch (e: Exception) {
            Log.w(TAG, "selectMove failed; using RaiEngine fallback", e)
            fallbackMove(board, "selectMove threw: ${e.javaClass.simpleName}")
        }
    }

    /** Maia is a move-picker, not an analyst — see class doc. */
    override fun analyze(board: Board, moveTimeMs: Long): PositionAnalysis? =
        fallback.analyze(board, moveTimeMs)

    private fun fallbackMove(board: Board, cause: String): Move? {
        if (!fallbackActive) {
            EngineDiagnostics.record(appContext, "moves now served by RaiEngine: $cause")
            fallbackActive = true
        }
        everFellBack = true
        return fallback.selectMove(board)
    }

    @Synchronized
    private fun ensureReady(): Boolean {
        if (released) return false
        when (state) {
            State.READY -> return true
            State.FAILED -> {
                if (initAttempts >= MAX_INIT_ATTEMPTS) return false
                state = State.UNINITIALIZED
            }
            State.UNINITIALIZED -> Unit
        }
        initAttempts++
        val initStartedAt = SystemClock.elapsedRealtime()

        output.clear()
        attemptGeneration++
        val generation = attemptGeneration

        val binary = binaryFile(appContext)
        if (!binary.exists()) return fail("lc0 binary missing at ${binary.name}")
        val weights = try {
            ensureWeightsExtracted(appContext, band)
        } catch (e: Exception) {
            Log.w(TAG, "weights extraction failed", e)
            return fail("weights extraction threw: ${e.javaClass.simpleName}")
        } ?: return fail("weights asset missing for maia-$band")

        try {
            val proc = ProcessBuilder(binary.absolutePath)
                .redirectErrorStream(true)
                .start()
            process = proc
            writer = BufferedWriter(OutputStreamWriter(proc.outputStream))
            startReader(proc, generation)
        } catch (e: Exception) {
            Log.w(TAG, "process start failed", e)
            return fail("process start threw: ${e.javaClass.simpleName}")
        }

        send("uci")
        if (awaitToken(HANDSHAKE_TIMEOUT_MS) { it == "uciok" } == null) {
            return fail("no uciok within ${HANDSHAKE_TIMEOUT_MS}ms")
        }
        send("setoption name WeightsFile value ${weights.absolutePath}")
        send("setoption name Threads value 1")
        // The isready after WeightsFile is where the net actually loads —
        // budget it like a model load, not a round-trip
        send("isready")
        if (awaitToken(WEIGHTS_READY_TIMEOUT_MS) { it == "readyok" } == null) {
            return fail("net not loaded within ${WEIGHTS_READY_TIMEOUT_MS}ms")
        }

        state = State.READY
        fallbackActive = false
        val elapsed = SystemClock.elapsedRealtime() - initStartedAt
        EngineDiagnostics.record(
            appContext,
            if (initAttempts > 1 || everFellBack) {
                "maia-$band recovered (attempt $initAttempts, ${elapsed}ms)"
            } else {
                "maia-$band ready (${elapsed}ms)"
            }
        )
        return true
    }

    private fun startReader(proc: Process, generation: Int) {
        val thread = Thread {
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (generation != attemptGeneration) break
                        output.offer(line.trim())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "reader thread ended", e)
            }
            if (generation == attemptGeneration && !released) {
                output.offer(ERROR_SENTINEL)
            }
        }
        thread.isDaemon = true
        thread.name = "maia-lc0-reader"
        thread.start()
    }

    /** Must only be called from inside [ensureReady]'s lock. */
    private fun fail(reason: String): Boolean {
        Log.w(TAG, "maia unavailable ($reason); using RaiEngine fallback")
        EngineDiagnostics.record(
            appContext,
            "maia-$band init failed (attempt $initAttempts/$MAX_INIT_ATTEMPTS): $reason"
        )
        state = State.FAILED
        attemptGeneration++
        destroyProcess()
        return false
    }

    private fun destroyProcess() {
        val proc = process ?: return
        process = null
        writer = null
        try {
            proc.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "process teardown failed", e)
        }
    }

    override fun close() {
        released = true
        output.offer(ERROR_SENTINEL)
        destroyProcess()
    }

    private fun send(cmd: String) {
        val w = writer ?: return
        w.write(cmd)
        w.newLine()
        w.flush()
    }

    private fun awaitToken(timeoutMs: Long, match: (String) -> Boolean): String? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            if (released) return null
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) return null
            val slice = remaining.coerceAtMost(POLL_SLICE_MS)
            val line = output.poll(slice, TimeUnit.MILLISECONDS) ?: continue
            if (line == ERROR_SENTINEL) return null
            if (match(line)) return line
        }
    }

    companion object {
        private const val TAG = "MaiaEngine"

        /** Nets bundled as assets (see the CI weights step). */
        val BUNDLED_BANDS = intArrayOf(1100, 1200, 1300, 1400, 1500)

        /**
         * The nearest bundled net for a target ELO, or null when the ELO is
         * outside Maia's serving range (below, RaiEngine's weakness is the
         * point; above, Stockfish's skill levels take over). Pure, tested.
         */
        fun netBandFor(targetElo: Int): Int? {
            if (targetElo < MAIA_MIN_ELO || targetElo >= MAIA_MAX_ELO_EXCLUSIVE) return null
            return (((targetElo + 50) / 100) * 100)
                .coerceIn(BUNDLED_BANDS.first(), BUNDLED_BANDS.last())
        }

        const val MAIA_MIN_ELO = 1100
        const val MAIA_MAX_ELO_EXCLUSIVE = 1600

        /** The lc0 executable the CI step packages into jniLibs. */
        fun binaryFile(context: Context): File =
            File(context.applicationInfo.nativeLibraryDir, "liblc0.so")

        private fun assetPath(band: Int) = "maia/maia-$band.pb.gz"

        /** True when this APK carries both the lc0 binary and the net. */
        fun isAvailable(context: Context, band: Int): Boolean {
            if (!binaryFile(context).exists()) return false
            return try {
                context.assets.open(assetPath(band)).use { }
                true
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Copy the net from assets to a real file (lc0 wants a path).
         * Idempotent; returns null when the asset doesn't exist. Writes
         * via a temp file so a killed copy can't leave a truncated net
         * behind for every later game.
         */
        fun ensureWeightsExtracted(context: Context, band: Int): File? {
            val dir = File(context.filesDir, "maia").apply { mkdirs() }
            val target = File(dir, "maia-$band.pb.gz")
            if (target.exists()) return target
            return try {
                val tmp = File(dir, "maia-$band.pb.gz.tmp")
                context.assets.open(assetPath(band)).use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (tmp.renameTo(target)) target else null
            } catch (e: Exception) {
                Log.w(TAG, "no bundled weights for maia-$band", e)
                null
            }
        }

        private const val ERROR_SENTINEL = "__error__"
        private const val HANDSHAKE_TIMEOUT_MS = 5000L
        // The net load lands on the isready after WeightsFile: a ~4MB
        // model parse on a low-end phone deserves patience
        private const val WEIGHTS_READY_TIMEOUT_MS = 20_000L
        // nodes=1 is a single forward pass (~ms) but the FIRST eval may
        // initialize the backend; generous grace costs nothing when fast
        private const val BESTMOVE_TIMEOUT_MS = 10_000L
        private const val POLL_SLICE_MS = 200L
        private const val MAX_INIT_ATTEMPTS = 2
    }
}
