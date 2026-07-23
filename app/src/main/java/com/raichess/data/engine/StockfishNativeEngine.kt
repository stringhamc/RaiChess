package com.raichess.data.engine

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import com.raichess.data.diagnostics.EngineDiagnostics
import com.raichess.domain.model.EloConfiguration
import com.raichess.domain.model.PositionAnalysis
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Stockfish opponent backed by a native binary: the CMake step builds
 * Stockfish from source into `libstockfish.so` (an executable in library
 * clothing — see src/main/cpp/CMakeLists.txt), Android extracts it to
 * nativeLibraryDir, and this class exec()s it and speaks UCI over
 * stdin/stdout. No WebView, no WASM, no JS bridge — this replaces
 * [StockfishWasmEngine] as the primary backend after field diagnostics
 * showed the 2019 stockfish.js build never finishing initialization on
 * some devices' WebViews. The WASM engine remains the backend for ABIs
 * without the binary (see [EngineFactory]).
 *
 * Contracts match the WASM engine exactly: [selectMove]/[analyze] are
 * synchronous, single-caller, called off the UI thread; any failure falls
 * back to [fallback] so play never breaks; lifecycle events land in
 * [EngineDiagnostics].
 */
class StockfishNativeEngine(
    context: Context,
    targetElo: Int,
    private val fallback: ChessEngine,
    /** True for analyzer instances: skill limiting is never applied. */
    private val analysisMode: Boolean = false
) : ChessEngine {

    private val appContext = context.applicationContext
    private val config = EloConfiguration.forElo(targetElo)
    private val moveTimeMs = config.thinkingTimeMs.coerceIn(300L, 3000L)
    private val output = LinkedBlockingQueue<String>()

    @Volatile private var process: Process? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var state = State.UNINITIALIZED
    // Set once, by close() ONLY — permanent teardown. Failed inits stay
    // retryable (same released/FAILED split as the WASM engine).
    @Volatile private var released = false
    @Volatile private var everFellBack = false
    // True while moves are served by the fallback; cleared on (re)init so
    // each engagement logs, not just the first.
    @Volatile private var fallbackActive = false
    private var initAttempts = 0
    // Scopes the reader thread to its init attempt, so a stale process's
    // late output can't pollute a retry's queue. Written only inside the
    // synchronized ensureReady (directly or via fail()).
    @Volatile private var attemptGeneration = 0

    private enum class State { UNINITIALIZED, READY, FAILED }

    override val activeEngineLabel: String
        get() = when {
            state == State.READY && everFellBack -> "Stockfish (recovered)"
            state == State.READY -> "Stockfish"
            everFellBack || state == State.FAILED -> "RaiEngine (fallback)"
            else -> "Stockfish"
        }

    /** Start the process early so move one doesn't pay for it. */
    override fun warmUp() {
        try {
            ensureReady()
        } catch (e: Exception) {
            // Best-effort by contract — the first real call retries/falls back
            Log.w(TAG, "warmUp failed", e)
        }
    }

    // Single-caller only: shares one output queue across the call, exactly
    // like the WASM engine. GameViewModel's engineLock guarantees this.
    override fun selectMove(board: Board): Move? {
        return try {
            if (!ensureReady()) {
                // Torn down (new game closed this instance): quiet null, not
                // a wasted fallback search on a stale board
                if (released) return null
                return fallbackMove(board, "native engine unavailable (init failed)")
            }
            if (released) return null

            output.clear()
            send("ucinewgame")
            send("position fen ${board.fen}")
            send("go movetime $moveTimeMs")

            val best = awaitToken(moveTimeMs + BESTMOVE_GRACE_MS) { it.startsWith("bestmove") }
            if (best == null) {
                if (released) return null
                Log.w(TAG, "no bestmove within timeout; using RaiEngine fallback")
                return fallbackMove(board, "no bestmove within ${moveTimeMs + BESTMOVE_GRACE_MS}ms")
            }
            StockfishWasmEngine.parseUciBestMove(board, best)
                ?: fallbackMove(board, "unparseable bestmove: $best")
        } catch (e: Exception) {
            Log.w(TAG, "selectMove failed; using RaiEngine fallback", e)
            fallbackMove(board, "selectMove threw: ${e.javaClass.simpleName}")
        }
    }

    override fun analyze(board: Board, moveTimeMs: Long): PositionAnalysis? {
        return try {
            if (!ensureReady()) {
                if (released) return null
                return fallbackAnalysis(board, moveTimeMs)
            }
            if (released) return null

            // Keep the interface's "never strength-limited" promise on play
            // instances: lift the skill cap for this search, restore after.
            // send() is synchronous on this thread, so ordering is trivial.
            val liftSkillLimit = !analysisMode && config.skillLevel < MAX_SKILL_LEVEL
            if (liftSkillLimit) send("setoption name Skill Level value $MAX_SKILL_LEVEL")
            try {
                analyzeAtCurrentStrength(board, moveTimeMs)
            } finally {
                if (liftSkillLimit) config.getUciCommands().forEach { send(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "analyze failed; using fallback analyzer", e)
            fallbackAnalysis(board, moveTimeMs)
        }
    }

    private fun analyzeAtCurrentStrength(board: Board, moveTimeMs: Long): PositionAnalysis? {
        output.clear()
        // No ucinewgame between analyses: a warm transposition table is
        // never wrong, only faster (same rationale as the WASM engine)
        send("position fen ${board.fen}")
        send("go movetime $moveTimeMs")

        var lastInfo: UciInfoParser.UciInfo? = null
        val best = awaitToken(moveTimeMs + BESTMOVE_GRACE_MS) { line ->
            UciInfoParser.parse(line)?.let { info ->
                if (info.multipv == 1 && !info.isBound) lastInfo = info
            }
            line.startsWith("bestmove")
        }
        if (best == null) {
            if (released) return null
            return fallbackAnalysis(board, moveTimeMs)
        }
        val info = lastInfo ?: return fallbackAnalysis(board, moveTimeMs)
        val bestMoveLan = StockfishWasmEngine.parseUciBestMove(board, best)
            ?.toString()?.lowercase()
        return PositionAnalysis(
            scoreCp = info.scoreCp,
            mateIn = info.scoreMate,
            bestMoveLan = bestMoveLan,
            pv = info.pv.map { it.lowercase() },
            depth = info.depth
        )
    }

    /**
     * Deliberately does NOT set [everFellBack]: the label reflects who
     * serves *moves*, and one slow coaching analysis must not brand the
     * game "fallback" (same rule as the WASM engine).
     */
    private fun fallbackAnalysis(board: Board, moveTimeMs: Long): PositionAnalysis? {
        return fallback.analyze(board, moveTimeMs)
    }

    private fun fallbackMove(board: Board, cause: String): Move? {
        // Transition-logged, not per-move, so a fallback game can't flood
        // the ring buffer past the init entries that explain it
        if (!fallbackActive) {
            EngineDiagnostics.record(appContext, "moves now served by RaiEngine: $cause")
            fallbackActive = true
        }
        everFellBack = true
        return fallback.selectMove(board)
    }

    /** Start the process and complete the UCI handshake once. Thread-safe. */
    @Synchronized
    private fun ensureReady(): Boolean {
        if (released) return false
        when (state) {
            State.READY -> return true
            State.FAILED -> {
                // A transient failure stays retryable, once
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
        if (!binary.exists()) return fail("binary missing at ${binary.name}")
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

        // Native startup is milliseconds, not a WASM compile — short budgets
        send("uci")
        if (awaitToken(HANDSHAKE_TIMEOUT_MS) { it == "uciok" } == null) {
            return fail("no uciok within ${HANDSHAKE_TIMEOUT_MS}ms")
        }
        if (!isReady()) return fail("engine not ready after handshake")

        if (!analysisMode) {
            config.getUciCommands().forEach { send(it) }
        }
        send("setoption name Threads value 1")
        send("setoption name Hash value 16")
        if (!isReady()) return fail("engine not ready after options")

        state = State.READY
        fallbackActive = false
        val elapsed = SystemClock.elapsedRealtime() - initStartedAt
        EngineDiagnostics.record(
            appContext,
            if (initAttempts > 1 || everFellBack) {
                "native stockfish recovered (attempt $initAttempts, ${elapsed}ms)"
            } else {
                "native stockfish ready (${elapsed}ms)"
            }
        )
        return true
    }

    /** Pump engine stdout into the shared queue, generation-scoped. */
    private fun startReader(proc: Process, generation: Int) {
        val thread = Thread {
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        // A stale attempt's process must not write into the
                        // queue a newer attempt owns
                        if (generation != attemptGeneration) break
                        output.offer(line.trim())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "reader thread ended", e)
            }
            // EOF = process died. Only the owning attempt may signal it.
            if (generation == attemptGeneration && !released) {
                output.offer(ERROR_SENTINEL)
            }
        }
        thread.isDaemon = true
        thread.name = "stockfish-native-reader"
        thread.start()
    }

    private fun isReady(): Boolean {
        send("isready")
        return awaitToken(HANDSHAKE_TIMEOUT_MS) { it == "readyok" } != null
    }

    /**
     * Must only be called from inside [ensureReady]'s lock: the attempt
     * counters it mutates rely on that single-writer discipline.
     */
    private fun fail(reason: String): Boolean {
        Log.w(TAG, "native Stockfish unavailable ($reason); using RaiEngine fallback")
        EngineDiagnostics.record(
            appContext,
            "native stockfish init failed (attempt $initAttempts/$MAX_INIT_ATTEMPTS): $reason"
        )
        state = State.FAILED
        // Invalidate this attempt's reader immediately; released stays
        // close()'s permanent signal
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
        // Wake any thread blocked in awaitToken
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
        // Monotonic deadline + short slices re-checking released, exactly
        // like the WASM engine's wait
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
        private const val TAG = "StockfishNativeEngine"

        /** The executable the CMake step packages into jniLibs. */
        fun binaryFile(context: Context): File =
            File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")

        /** True when this device's APK carries the native binary. */
        fun isAvailable(context: Context): Boolean = binaryFile(context).exists()

        private const val MAX_SKILL_LEVEL = 20
        private const val ERROR_SENTINEL = "__error__"
        private const val HANDSHAKE_TIMEOUT_MS = 5000L
        private const val BESTMOVE_GRACE_MS = 4000L
        private const val POLL_SLICE_MS = 200L
        private const val MAX_INIT_ATTEMPTS = 2
    }
}
