package com.raichess.calibration

import java.util.concurrent.TimeUnit

/**
 * Minimal blocking UCI client over a local engine process, used only by the
 * opt-in calibration harness ([RaiEngineCalibrationTest]) — the app itself
 * never spawns desktop processes. Single-threaded by design: every command
 * waits for its reply before the next is sent.
 */
class UciProcessClient(enginePath: String) : AutoCloseable {

    private val process = ProcessBuilder(enginePath)
        .redirectErrorStream(true)
        .start()
    private val writer = process.outputStream.bufferedWriter()
    private val reader = process.inputStream.bufferedReader()

    /**
     * UCI handshake plus strength limiting to [elo] via UCI_Elo (supported
     * by Stockfish 11+; modern builds floor at 1320).
     */
    fun startLimitedTo(elo: Int) {
        send("uci")
        waitFor("uciok")
        send("setoption name UCI_LimitStrength value true")
        send("setoption name UCI_Elo value $elo")
        send("isready")
        waitFor("readyok")
    }

    fun newGame() {
        send("ucinewgame")
        send("isready")
        waitFor("readyok")
    }

    /**
     * Search [fen] for [moveTimeMs] and return the chosen move in lowercase
     * LAN, or null when the engine has no move (mate/stalemate).
     */
    fun bestMove(fen: String, moveTimeMs: Long): String? {
        send("position fen $fen")
        send("go movetime $moveTimeMs")
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
