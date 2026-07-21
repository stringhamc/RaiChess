package com.raichess.data.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveGenerator
import com.raichess.domain.model.EloConfiguration
import com.raichess.domain.model.PositionAnalysis
import org.json.JSONObject
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Stockfish opponent backed by Stockfish.js (WASM) running in a headless
 * WebView. No NDK/native build: the engine is bundled as WASM assets and
 * driven over UCI through a JavaScript bridge.
 *
 * [selectMove] is synchronous (called off the UI thread by the ViewModel) and
 * blocks on the engine's `bestmove`. All WebView interaction is marshaled to
 * the main thread. If the engine fails to initialize or times out at any
 * point, it delegates to [fallback] so play never breaks.
 */
class StockfishWasmEngine(
    context: Context,
    targetElo: Int,
    private val fallback: ChessEngine,
    /**
     * When true this instance is an analyzer, not an opponent: the
     * skill-limiting UCI options are never sent, so searches run at full
     * strength. Use [EngineFactory.createAnalyzer] to obtain one.
     */
    private val analysisMode: Boolean = false
) : ChessEngine {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val output = LinkedBlockingQueue<String>()
    private val config = EloConfiguration.forElo(targetElo)
    // Strength is governed by the engine's Skill Level (see getUciCommands), so
    // move time is mostly a responsiveness knob: cap it at 3s so a phone never
    // grinds too long per move, with a floor so WASM has room to search.
    // Known tradeoff: Skill Level plateaus at 20 from ~2200 ELO up, so above
    // that the only remaining strength lever is think time — and with this cap,
    // every 2200+ setting plays at effectively full strength. That's an
    // intentional casual-play choice (the alternative is 7–10s moves), and the
    // affected band is already well above the target audience's level.
    private val moveTimeMs = config.thinkingTimeMs.coerceIn(300L, 3000L)

    @Volatile private var webView: WebView? = null
    @Volatile private var state = State.UNINITIALIZED
    // Set the first time a move is served by the RaiEngine fallback, so the
    // UI label can reflect that not every move came from Stockfish.
    @Volatile private var everFellBack = false
    // Failed init attempts so far; a transient failure gets retried (see
    // ensureReady) before the engine gives up for good.
    private var initAttempts = 0

    override val activeEngineLabel: String
        get() = when {
            state == State.READY && everFellBack -> "Stockfish (recovered)"
            state == State.READY -> "Stockfish"
            everFellBack || state == State.FAILED -> "RaiEngine (fallback)"
            else -> "Stockfish"
        }
    // Set once the engine is torn down (fail/close). Guards the async
    // createWebView post: if teardown wins the race, the freshly-built WebView
    // is destroyed immediately instead of leaking.
    @Volatile private var released = false

    private enum class State { UNINITIALIZED, READY, FAILED }

    // Not safe for concurrent callers: the clear()/send()/awaitToken() sequence
    // below shares one output queue, so overlapping selectMove calls would
    // steal each other's engine lines. GameViewModel drives this from a single
    // sequential coroutine (one move at a time), which this relies on.
    override fun selectMove(board: Board): Move? {
        return try {
            if (!ensureReady()) return fallbackMove(board)
            // Closed during/just after init: bail before doing search work.
            if (released) return null

            output.clear()
            send("ucinewgame")
            send("position fen ${board.fen}")
            send("go movetime $moveTimeMs")

            val best = awaitToken(moveTimeMs + BESTMOVE_GRACE_MS) { it.startsWith("bestmove") }
            if (best == null) {
                // close() unblocks the wait via ERROR_SENTINEL during teardown.
                // Past ensureReady(), only close() sets `released`, so this
                // means the game is being torn down — skip the wasted fallback
                // search against a board the ViewModel has likely replaced.
                if (released) return null
                Log.w(TAG, "no bestmove within timeout; using RaiEngine fallback")
                return fallbackMove(board)
            }
            parseUciBestMove(board, best) ?: fallbackMove(board)
        } catch (e: Exception) {
            // Any failure (incl. thread interruption) must not break play
            Log.w(TAG, "selectMove failed; using RaiEngine fallback", e)
            fallbackMove(board)
        }
    }

    /**
     * Full-strength evaluation of [board]: runs a normal search and keeps
     * the deepest exact-score `info` line seen before `bestmove`. Falls back
     * to [fallback]'s (coarser) analysis if the WASM bridge is unavailable.
     *
     * Same single-caller contract as [selectMove] — both share [output].
     */
    override fun analyze(board: Board, moveTimeMs: Long): PositionAnalysis? {
        return try {
            if (!ensureReady()) return fallbackAnalysis(board, moveTimeMs)
            if (released) return null

            // Keep the interface's "never strength-limited" promise on play
            // instances too: lift the Skill Level handicap for this search
            // and restore it afterwards, so in-game hints/coaching get an
            // honest eval without permanently strengthening the opponent.
            val liftSkillLimit = !analysisMode && config.skillLevel < MAX_SKILL_LEVEL
            if (liftSkillLimit) send("setoption name Skill Level value $MAX_SKILL_LEVEL")
            try {
                analyzeAtCurrentStrength(board, moveTimeMs)
            } finally {
                // Fire-and-forget restore, not synchronous: safe because
                // send() posts to one main-thread Handler (FIFO), so this is
                // guaranteed to run before any command a later engine call
                // enqueues. Don't "fix" this into a blocking wait.
                if (liftSkillLimit) config.getUciCommands().forEach { send(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "analyze failed; using fallback analyzer", e)
            fallbackAnalysis(board, moveTimeMs)
        }
    }

    private fun analyzeAtCurrentStrength(board: Board, moveTimeMs: Long): PositionAnalysis? {
        output.clear()
        // No ucinewgame between analyze() calls — including across
        // different games when one engine drains a backlog: the
        // transposition table is keyed by position, so entries from
        // another game are irrelevant or helpful, never wrong, and a
        // warm hash makes sequential analysis faster.
        send("position fen ${board.fen}")
        send("go movetime $moveTimeMs")

        // Track the best info line while waiting for the bestmove
        // terminator. Bound scores (fail-high/low) are only reported
        // mid-resolution, so exact scores are preferred; multipv is
        // always 1 here but filtered defensively for when hints add
        // MultiPV search.
        var lastInfo: UciInfoParser.UciInfo? = null
        val best = awaitToken(moveTimeMs + BESTMOVE_GRACE_MS) { line ->
            UciInfoParser.parse(line)?.let { info ->
                if (info.multipv == 1 && !info.isBound) lastInfo = info
            }
            line.startsWith("bestmove")
        }
        if (best == null) {
            if (released) return null
            Log.w(TAG, "no bestmove within analysis timeout; using fallback analyzer")
            return fallbackAnalysis(board, moveTimeMs)
        }

        val info = lastInfo ?: return fallbackAnalysis(board, moveTimeMs)
        // Re-validate the engine's move against the real legal set, as
        // selectMove does; "bestmove (none)" (mate/stalemate) → null.
        val bestMoveLan = parseUciBestMove(board, best)?.toString()?.lowercase()
        return PositionAnalysis(
            scoreCp = info.scoreCp,
            mateIn = info.scoreMate,
            bestMoveLan = bestMoveLan,
            pv = info.pv.map { it.lowercase() },
            depth = info.depth
        )
    }

    /**
     * Serve an analysis from the RaiEngine fallback. Deliberately does NOT
     * set [everFellBack]: that flag drives the UI's opponent-engine label,
     * which must reflect who is serving *moves* — a single slow coaching
     * analysis on a play instance must not brand the whole game
     * "RaiEngine (fallback)" while Stockfish keeps playing.
     */
    private fun fallbackAnalysis(board: Board, moveTimeMs: Long): PositionAnalysis? {
        return fallback.analyze(board, moveTimeMs)
    }

    /** Serve a move from the RaiEngine fallback and remember that we did. */
    private fun fallbackMove(board: Board): Move? {
        everFellBack = true
        return fallback.selectMove(board)
    }

    /** Build the WebView and complete the UCI handshake once. Thread-safe. */
    @Synchronized
    private fun ensureReady(): Boolean {
        if (released) return false
        when (state) {
            State.READY -> return true
            State.FAILED -> {
                // A transient failure (WebView provider updating, slow cold
                // WASM compile) shouldn't downgrade the whole game to
                // RaiEngine: retry on a later call before giving up for good
                if (initAttempts >= MAX_INIT_ATTEMPTS) return false
                state = State.UNINITIALIZED
            }
            State.UNINITIALIZED -> Unit
        }
        initAttempts++
        // A retry runs synchronously inside a move, so the fast steps
        // (worker start, isready round-trips) run on half budgets. The
        // uciok budget deliberately stays FULL: that's where the cold WASM
        // compile lands — the failure mode the retry exists to rescue —
        // and shrinking it would make the retry systematically worse at
        // exactly that. Worst case for a fully-failed retry is then
        // ~6+15+2.5+2.5s ≈ 26s of "thinking…", but in practice a step
        // either fails much sooner or succeeds (a second compile usually
        // hits the WebView's code cache and is far faster than the first).
        val retrying = initAttempts > 1
        val initBudget = if (retrying) INIT_TIMEOUT_MS / 2 else INIT_TIMEOUT_MS
        val uciokBudget = UCIOK_TIMEOUT_MS
        val readyBudget = if (retrying) HANDSHAKE_TIMEOUT_MS / 2 else HANDSHAKE_TIMEOUT_MS

        output.clear()
        mainHandler.post { createWebView() }

        // engine.html calls AndroidEngine.onReady() once the worker is created
        val ready = awaitToken(initBudget) { it == READY_SENTINEL || it == ERROR_SENTINEL }
        if (ready != READY_SENTINEL) return fail("worker did not start within ${initBudget}ms")

        if (!handshake(uciokBudget, readyBudget)) return fail("uci handshake failed")

        // Apply strength for this ELO. The bundled SF10 build only supports
        // `Skill Level` (UCI_Elo/UCI_LimitStrength came in SF11), so
        // getUciCommands() sends just that — see EloConfiguration. Analyzers
        // stay at full strength: an eval from a skill-capped search would
        // misgrade the player's moves.
        if (!analysisMode) {
            config.getUciCommands().forEach { send(it) }
        }
        send("setoption name Threads value 1")
        send("setoption name Hash value 16")
        if (!isReady(readyBudget)) return fail("engine not ready after options")

        Log.i(TAG, "Stockfish WASM ready (targetElo band, movetime=${moveTimeMs}ms)")
        state = State.READY
        return true
    }

    private fun handshake(uciokBudgetMs: Long, readyBudgetMs: Long): Boolean {
        send("uci")
        // uciok arrives only after the cold WASM compile — allow for that.
        if (awaitToken(uciokBudgetMs) { it == "uciok" } == null) return false
        return isReady(readyBudgetMs)
    }

    private fun isReady(budgetMs: Long = HANDSHAKE_TIMEOUT_MS): Boolean {
        send("isready")
        return awaitToken(budgetMs) { it == "readyok" } != null
    }

    private fun fail(reason: String): Boolean {
        Log.w(TAG, "Stockfish unavailable ($reason); using RaiEngine fallback")
        state = State.FAILED
        // NOT `released = true`: released is close()'s permanent teardown
        // signal, and a failed attempt must stay retryable (see ensureReady)
        destroyWebView()
        return false
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        // new WebView(...) can throw on devices where the WebView provider is
        // missing/updating/disabled. This runs on the main thread's loop, so
        // an uncaught throw here would crash the app rather than fall back —
        // catch it and signal an error so ensureReady() falls back cleanly.
        try {
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(appContext))
                .build()
            val view = WebView(appContext)
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            // Lock the WebView to local, first-party content. All assets are
            // served by WebViewAssetLoader via shouldInterceptRequest (which
            // short-circuits before the network layer), so blocking network
            // loads can't break asset/WASM loading — it just makes the
            // "no network, ever" guarantee robust regardless of the manifest.
            view.settings.blockNetworkLoads = true
            view.settings.allowFileAccess = false
            view.addJavascriptInterface(Bridge(), "AndroidEngine")
            view.webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                // The engine only ever loads our local asset origin. Refuse any
                // other navigation so a compromised/broken asset can't drive the
                // WebView elsewhere.
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val host = request.url.host
                    return host != ASSET_HOST
                }
            }
            // If teardown already happened while this post was queued, don't
            // leave a live WebView behind — destroy it, unblock any waiter, bail.
            if (released) {
                view.destroy()
                output.offer(ERROR_SENTINEL)
                return
            }
            webView = view
            view.loadUrl("https://$ASSET_HOST/assets/stockfish/engine.html")
        } catch (e: Throwable) {
            Log.w(TAG, "WebView construction failed", e)
            output.offer(ERROR_SENTINEL)
        }
    }

    private fun destroyWebView() {
        val view = webView ?: return
        webView = null
        mainHandler.post {
            try {
                view.removeJavascriptInterface("AndroidEngine")
                view.destroy()
            } catch (e: Throwable) {
                Log.w(TAG, "WebView teardown failed", e)
            }
        }
    }

    override fun close() {
        released = true
        // Wake any thread blocked in awaitToken (e.g. a selectMove awaiting
        // bestmove) so close() doesn't leave it hanging until timeout.
        output.offer(ERROR_SENTINEL)
        destroyWebView()
    }

    private fun send(cmd: String) {
        // JSONObject.quote yields a fully-escaped, double-quoted JS string
        // literal (handles quotes, backslashes, control chars, U+2028/U+2029) —
        // more correct than hand-escaping for evaluateJavascript, and safe if a
        // future caller ever passes less-constrained text through send().
        val literal = JSONObject.quote(cmd)
        mainHandler.post { webView?.evaluateJavascript("uciCmd($literal)", null) }
    }

    private fun awaitToken(timeoutMs: Long, match: (String) -> Boolean): String? {
        // elapsedRealtime is monotonic, so a wall-clock change (NTP, DST, user
        // edit) mid-wait can't skew the deadline. Poll in short slices and
        // re-check `released` each slice, so close() unblocks the wait promptly
        // even if its ERROR_SENTINEL was wiped by a racing output.clear().
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

    private inner class Bridge {
        @JavascriptInterface fun onReady() { output.offer(READY_SENTINEL) }
        @JavascriptInterface fun onMessage(line: String) { output.offer(line.trim()) }
        @JavascriptInterface fun onError(message: String) {
            Log.w(TAG, "engine JS error: $message")
            output.offer(ERROR_SENTINEL)
        }
    }

    companion object {
        /**
         * Parse a UCI "bestmove <lan>" line into a legal chesslib [Move] on
         * [board], or null. Case-insensitive (so a promotion like `e7e8q`
         * matches) and always re-validated against the actual legal moves, so
         * a malformed or illegal engine reply can never be applied. Pure —
         * unit-testable without a WebView.
         */
        fun parseUciBestMove(board: Board, bestmoveLine: String): Move? {
            val lan = bestmoveLine.split(" ").getOrNull(1)?.lowercase() ?: return null
            if (lan == "(none)" || lan.length < 4) return null
            return MoveGenerator.generateLegalMoves(board)
                .firstOrNull { it.toString().lowercase() == lan }
        }

        private const val TAG = "StockfishWasmEngine"
        /** Stockfish's Skill Level ceiling — full strength. */
        private const val MAX_SKILL_LEVEL = 20
        private const val ASSET_HOST = "appassets.androidplatform.net"
        private const val READY_SENTINEL = "__ready__"
        private const val ERROR_SENTINEL = "__error__"
        // Guards engine.html loading and the Worker object being constructed —
        // onReady() fires right after `new Worker(...)` returns, before the WASM
        // is compiled, so this is fast on any device; keep it comfortably ample.
        private const val INIT_TIMEOUT_MS = 12000L
        // The cold WASM compile actually lands here: the worker only answers
        // `uciok` once stockfish.js has loaded and instantiated the module, which
        // can be several seconds on a low-end device on the first game after
        // install. Too short silently, permanently downgrades the game to
        // RaiEngine, so this initial handshake gets a generous budget.
        private const val UCIOK_TIMEOUT_MS = 15000L
        // Post-handshake readiness (`isready`/`readyok`): the module is already
        // loaded by then, so these replies are fast.
        private const val HANDSHAKE_TIMEOUT_MS = 5000L
        private const val BESTMOVE_GRACE_MS = 4000L
        // Max blocking-poll granularity; bounds how long a wait can ignore a
        // teardown (released) signal.
        private const val POLL_SLICE_MS = 200L
        // Total init tries before FAILED becomes permanent for this game.
        private const val MAX_INIT_ATTEMPTS = 2
    }
}
