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
    // Identifies which init attempt owns the async createWebView post. A
    // WebView construction can stall for many seconds (provider updating)
    // and outlive its attempt's timeout; when it finally lands during a
    // retry, the stale generation tells it to destroy itself instead of
    // leaking, overwriting the newer attempt's WebView, or feeding stale
    // tokens into the shared output queue. Written only inside the
    // synchronized ensureReady; read on the main thread.
    @Volatile private var attemptGeneration = 0

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
            if (!ensureReady()) {
                // Torn down (new game closed this instance): return null
                // rather than burning a fallback search on a stale board —
                // ensureReady's released-guard must not read as "failed"
                if (released) return null
                return fallbackMove(board)
            }
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
            if (!ensureReady()) {
                // Same released-vs-failed distinction as selectMove
                if (released) return null
                return fallbackAnalysis(board, moveTimeMs)
            }
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
        // The retry runs on the SAME full budgets as the first attempt: it
        // is the last chance before FAILED becomes permanent, and every
        // budget guards one of the slow transient causes the retry exists
        // to rescue (WebView provider updating lands in the init wait, the
        // cold WASM compile in the uciok wait) — shrinking any of them
        // makes the retry systematically worse at exactly its job. Known
        // tradeoff: the one retried move can stall up to ~32s worst case,
        // behind the visible "thinking…" state; in practice a failing step
        // dies far sooner, and a second compile usually hits the WebView's
        // code cache and is much faster than the first.

        output.clear()
        attemptGeneration++
        val generation = attemptGeneration
        mainHandler.post { createWebView(generation) }

        // engine.html calls AndroidEngine.onReady() once the worker is created
        val ready = awaitToken(INIT_TIMEOUT_MS) { it == READY_SENTINEL || it == ERROR_SENTINEL }
        if (ready != READY_SENTINEL) return fail("worker did not start within ${INIT_TIMEOUT_MS}ms")

        if (!handshake()) return fail("uci handshake failed")

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
        if (!isReady()) return fail("engine not ready after options")

        Log.i(TAG, "Stockfish WASM ready (targetElo band, movetime=${moveTimeMs}ms)")
        state = State.READY
        return true
    }

    private fun handshake(): Boolean {
        send("uci")
        // uciok arrives only after the cold WASM compile — allow for that.
        if (awaitToken(UCIOK_TIMEOUT_MS) { it == "uciok" } == null) return false
        return isReady()
    }

    private fun isReady(): Boolean {
        send("isready")
        return awaitToken(HANDSHAKE_TIMEOUT_MS) { it == "readyok" } != null
    }

    /**
     * Must only be called from inside [ensureReady]'s lock: the
     * generation/attempt counters it mutates are unsynchronized fields
     * whose safety depends entirely on that single-writer discipline.
     */
    private fun fail(reason: String): Boolean {
        Log.w(TAG, "Stockfish unavailable ($reason); using RaiEngine fallback")
        state = State.FAILED
        // Invalidate this attempt's in-flight createWebView post right away:
        // without the bump, a construction that outlived this timeout could
        // still go live before any retry re-bumps the generation
        attemptGeneration++
        // NOT `released = true`: released is close()'s permanent teardown
        // signal, and a failed attempt must stay retryable (see ensureReady)
        destroyWebView()
        return false
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(generation: Int) {
        // Stale post: the attempt that queued this already timed out (or
        // close() ran). Bail before doing any work — and never touch the
        // shared output queue, which belongs to a newer attempt now.
        if (released || generation != attemptGeneration) return
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
            view.addJavascriptInterface(Bridge(generation), "AndroidEngine")
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
            // Construction itself can stall past the attempt's timeout. If
            // this attempt was failed (or the engine closed) meanwhile, a
            // retry may already own the queue: destroy quietly — no queue
            // signal, no webView overwrite, no leak.
            if (released || generation != attemptGeneration) {
                view.destroy()
                return
            }
            webView = view
            view.loadUrl("https://$ASSET_HOST/assets/stockfish/engine.html")
        } catch (e: Throwable) {
            Log.w(TAG, "WebView construction failed", e)
            // Only the attempt that owns the queue may fail it
            if (generation == attemptGeneration) output.offer(ERROR_SENTINEL)
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

    /**
     * Generation-scoped like createWebView itself: teardown of an
     * abandoned attempt's WebView is posted, not synchronous, so its JS
     * side can fire a late callback in the window before that runs. A
     * stale bridge must not write into the queue a newer attempt owns.
     */
    private inner class Bridge(private val generation: Int) {
        private fun current() = generation == attemptGeneration

        @JavascriptInterface fun onReady() {
            if (current()) output.offer(READY_SENTINEL)
        }

        @JavascriptInterface fun onMessage(line: String) {
            if (current()) output.offer(line.trim())
        }

        @JavascriptInterface fun onError(message: String) {
            Log.w(TAG, "engine JS error: $message")
            if (current()) output.offer(ERROR_SENTINEL)
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
