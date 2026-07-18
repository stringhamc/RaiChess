package com.raichess.domain.model

/**
 * One tactics puzzle in the Lichess puzzle-database convention:
 * [fen] is the position BEFORE the opponent's setup move, [moves] is the
 * full line in lowercase LAN — moves[0] is the opponent's setup move that
 * creates the puzzle, moves[1] the player's first solution move, then
 * alternating. The solver is the side NOT to move in [fen].
 *
 * The bundled asset uses the exact Lichess CSV schema (CC0 public domain),
 * so the seed set can be replaced or extended with real Lichess puzzles
 * via tools/fetch_lichess_puzzles.py without code changes.
 */
data class Puzzle(
    val id: String,
    val fen: String,
    val moves: List<String>,
    /** Lichess-calibrated difficulty (~600 easy .. ~2800 brutal). */
    val rating: Int,
    /** Lichess theme ids ("mateIn1", "hangingPiece", ...). */
    val themes: Set<String>
) {
    /** Number of moves the player must find. */
    val playerMoveCount: Int get() = moves.size / 2
}

/**
 * Parser for the Lichess puzzle CSV
 * (PuzzleId,FEN,Moves,Rating,RatingDeviation,Popularity,NbPlays,Themes,GameUrl,OpeningTags).
 * Pure string handling — unit-testable without Android.
 */
object PuzzleCsv {

    /** Parse the full file (header line skipped); malformed rows are dropped. */
    fun parse(csv: String): List<Puzzle> =
        csv.lineSequence()
            .drop(1)
            .mapNotNull { parseLine(it) }
            .toList()

    fun parseLine(line: String): Puzzle? {
        if (line.isBlank()) return null
        val fields = splitCsv(line)
        if (fields.size < 8) return null
        val id = fields[0].trim()
        val fen = fields[1].trim()
        val moves = fields[2].trim().split(' ').filter { it.isNotBlank() }
        val rating = fields[3].trim().toIntOrNull() ?: return null
        val themes = fields[7].trim().split(' ').filter { it.isNotBlank() }.toSet()
        // A puzzle needs the setup move plus at least one player move, and
        // the line must end on a player move — an even total, since
        // moves[0] is the opponent's setup
        if (id.isEmpty() || fen.isEmpty()) return null
        if (moves.size < 2 || moves.size % 2 != 0) return null
        return Puzzle(
            id = id,
            fen = fen,
            moves = moves.map { it.lowercase() },
            rating = rating,
            themes = themes
        )
    }

    /** Minimal CSV field splitter with double-quote support. */
    private fun splitCsv(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"'); i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    fields.add(current.toString()); current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}
