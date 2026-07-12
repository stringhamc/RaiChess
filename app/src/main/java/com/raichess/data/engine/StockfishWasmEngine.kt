package com.raichess.data.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
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
    private val moveTimeMs = config.thinkingTimeMs.coerceIn(200L, 1500L)

    @Volatile private var webView: WebView? = null
    @Volatile private var state = State.UNINITIALIZED

    private enum class State { UNINITIALIZED, READY, FAILED }

    override fun selectMove(board: Board): Move? {
        if (!ensureReady()) return fallback.selectMove(board)

        output.clear()
        send("ucinewgame")
        send("position fen ${board.fen}")
        send("go movetime $moveTimeMs")

        val best = awaitToken(moveTimeMs + BESTMOVE_GRACE_MS) { it.startsWith("bestmove") }
            ?: return fallback.selectMove(board)
        return parseBestMove(best, board) ?: fallback.selectMove(board)
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
        if (ready != READY_SENTINEL) return fail()

        if (!handshake()) return fail()

        // Apply strength limits for this ELO (UCI_LimitStrength / UCI_Elo / Skill Level)
        config.getUciCommands().forEach { send(it) }
        send("setoption name Threads value 1")
        send("setoption name Hash value 16")
        if (!isReady()) return fail()

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

    private fun fail(): Boolean {
        state = State.FAILED
        destroyWebView()
        return false
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
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
        }
        webView = view
        view.loadUrl("https://appassets.androidplatform.net/assets/stockfish/engine.html")
    }

    private fun destroyWebView() {
        val view = webView ?: return
        webView = null
        mainHandler.post {
            view.removeJavascriptInterface("AndroidEngine")
            view.destroy()
        }
    }

    override fun close() {
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

    private fun parseBestMove(line: String, board: Board): Move? {
        val lan = line.split(" ").getOrNull(1)?.lowercase() ?: return null
        if (lan == "(none)" || lan.length < 4) return null
        // Only trust a move Stockfish reports that is actually legal here.
        // Case-insensitive so a promotion (e7e8q) matches regardless of how
        // chesslib renders the promotion piece char.
        return MoveGenerator.generateLegalMoves(board)
            .firstOrNull { it.toString().lowercase() == lan }
    }

    private inner class Bridge {
        @JavascriptInterface fun onReady() { output.offer(READY_SENTINEL) }
        @JavascriptInterface fun onMessage(line: String) { output.offer(line.trim()) }
        @JavascriptInterface fun onError(message: String) { output.offer(ERROR_SENTINEL) }
    }

    companion object {
        private const val READY_SENTINEL = "__ready__"
        private const val ERROR_SENTINEL = "__error__"
        private const val INIT_TIMEOUT_MS = 8000L
        private const val HANDSHAKE_TIMEOUT_MS = 5000L
        private const val BESTMOVE_GRACE_MS = 4000L
    }
}
