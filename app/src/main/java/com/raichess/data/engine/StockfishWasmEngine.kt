package com.raichess.data.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
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
    private val fallback: ChessEngine
) : ChessEngine {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val output = LinkedBlockingQueue<String>()
    private val config = EloConfiguration.forElo(targetElo)
    // Strength is governed by the engine's Skill Level (see getUciCommands),
    // so move time is mostly a responsiveness knob. Scale it with the ELO tier
    // but cap it so the UI never waits too long, and give a floor so WASM has
    // room to search.
    private val moveTimeMs = config.thinkingTimeMs.coerceIn(300L, 3000L)

    @Volatile private var webView: WebView? = null
    @Volatile private var state = State.UNINITIALIZED
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
            if (!ensureReady()) return fallback.selectMove(board)

            output.clear()
            send("ucinewgame")
            send("position fen ${board.fen}")
            send("go movetime $moveTimeMs")

            val best = awaitToken(moveTimeMs + BESTMOVE_GRACE_MS) { it.startsWith("bestmove") }
            if (best == null) {
                Log.w(TAG, "no bestmove within timeout; using RaiEngine fallback")
                return fallback.selectMove(board)
            }
            parseUciBestMove(board, best) ?: fallback.selectMove(board)
        } catch (e: Exception) {
            // Any failure (incl. thread interruption) must not break play
            Log.w(TAG, "selectMove failed; using RaiEngine fallback", e)
            fallback.selectMove(board)
        }
    }

    /** Build the WebView and complete the UCI handshake once. Thread-safe. */
    @Synchronized
    private fun ensureReady(): Boolean {
        when (state) {
            State.READY -> return true
            State.FAILED -> return false
            State.UNINITIALIZED -> Unit
        }

        output.clear()
        mainHandler.post { createWebView() }

        // engine.html calls AndroidEngine.onReady() once the worker is created
        val ready = awaitToken(INIT_TIMEOUT_MS) { it == READY_SENTINEL || it == ERROR_SENTINEL }
        if (ready != READY_SENTINEL) return fail("worker did not start within ${INIT_TIMEOUT_MS}ms")

        if (!handshake()) return fail("uci handshake failed")

        // Apply strength for this ELO. The bundled SF10 build only supports
        // `Skill Level` (UCI_Elo/UCI_LimitStrength came in SF11), so
        // getUciCommands() sends just that — see EloConfiguration.
        config.getUciCommands().forEach { send(it) }
        send("setoption name Threads value 1")
        send("setoption name Hash value 16")
        if (!isReady()) return fail("engine not ready after options")

        Log.i(TAG, "Stockfish WASM ready (targetElo band, movetime=${moveTimeMs}ms)")
        state = State.READY
        return true
    }

    private fun handshake(): Boolean {
        send("uci")
        if (awaitToken(HANDSHAKE_TIMEOUT_MS) { it == "uciok" } == null) return false
        return isReady()
    }

    private fun isReady(): Boolean {
        send("isready")
        return awaitToken(HANDSHAKE_TIMEOUT_MS) { it == "readyok" } != null
    }

    private fun fail(reason: String): Boolean {
        Log.w(TAG, "Stockfish unavailable ($reason); using RaiEngine fallback")
        state = State.FAILED
        released = true
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
        val escaped = cmd.replace("\\", "\\\\").replace("'", "\\'")
        mainHandler.post { webView?.evaluateJavascript("uciCmd('$escaped')", null) }
    }

    private fun awaitToken(timeoutMs: Long, match: (String) -> Boolean): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return null
            val line = output.poll(remaining, TimeUnit.MILLISECONDS) ?: return null
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
        private const val ASSET_HOST = "appassets.androidplatform.net"
        private const val READY_SENTINEL = "__ready__"
        private const val ERROR_SENTINEL = "__error__"
        private const val INIT_TIMEOUT_MS = 8000L
        private const val HANDSHAKE_TIMEOUT_MS = 5000L
        private const val BESTMOVE_GRACE_MS = 4000L
    }
}
