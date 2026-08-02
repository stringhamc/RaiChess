package com.raichess.calibration

import java.util.concurrent.TimeUnit

/**
 * Minimal blocking UCI client over a local engine process, used only by
 * the opt-in calibration harnesses ([RaiEngineCalibrationTest],
 * [MaiaCalibrationTest]) — the app itself never spawns desktop processes.
 * Single-threaded by design: every command waits for its reply before the
 * next is sent. Works with any UCI engine (Stockfish, lc0+Maia, ...);
 * engines that take their configuration on the command line (lc0's
 * `--weights=`) pass it via [command].
 */
class UciProcessClient(command: List<String>) : AutoCloseable {

    constructor(enginePath: String) : this(listOf(enginePath))

    private val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()
    private val writer = process.outputStream.bufferedWriter()
    private val reader = process.inputStream.bufferedReader()

    /** UCI handshake, then apply [options], then wait until ready. */
    fun start(options: Map<String, String> = emptyMap()) {
        send("uci")
        waitFor("uciok")
        options.forEach { (name, value) ->
            send("setoption name $name value $value")
        }
        send("isready")
        waitFor("readyok")
    }

    /**
     * Handshake plus strength limiting to [elo] via UCI_Elo (supported by
     * Stockfish 11+; modern builds floor at 1320).
     */
    fun startLimitedTo(elo: Int) = start(
        mapOf(
            "UCI_LimitStrength" to "true",
            "UCI_Elo" to "$elo"
        )
    )

    fun newGame() {
        send("ucinewgame")
        send("isready")
        waitFor("readyok")
    }

    /**
     * Search [fen] for [moveTimeMs] and return the chosen move in lowercase
     * LAN, or null when the engine has no move (mate/stalemate).
     */
    fun bestMove(fen: String, moveTimeMs: Long): String? =
        search(fen, "go movetime $moveTimeMs")

    /**
     * Fixed-node search. `nodes 1` is how Maia is meant to be played: one
     * evaluation of the root, move picked from the policy head alone.
     */
    fun bestMoveNodes(fen: String, nodes: Int): String? =
        search(fen, "go nodes $nodes")

    private fun search(fen: String, goCommand: String): String? {
        send("position fen $fen")
        send(goCommand)
        val line = waitFor("bestmove")
        return line.split(" ").getOrNull(1)?.lowercase()?.takeIf { it != "(none)" }
    }

    private fun send(command: String) {
        writer.write(command)
        writer.newLine()
        writer.flush()
    }

    private fun waitFor(prefix: String): String {
        while (true) {
            val line = reader.readLine()
                ?: error("engine process ended while waiting for '$prefix'")
            if (line.startsWith(prefix)) return line
        }
    }

    override fun close() {
        runCatching { send("quit") }
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
    }
}
