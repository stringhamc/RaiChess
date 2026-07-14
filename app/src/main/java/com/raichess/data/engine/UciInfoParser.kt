package com.raichess.data.engine

/**
 * Parses UCI `info` lines from a search into the fields the analysis
 * pipeline needs. Pure string handling — unit-testable without a WebView.
 */
object UciInfoParser {

    /**
     * One parsed `info` line. Exactly one of [scoreCp]/[scoreMate] is
     * non-null (that's the precondition for [parse] returning non-null).
     */
    data class UciInfo(
        val depth: Int,
        /** MultiPV rank; UCI omits the token for single-PV search, so 1. */
        val multipv: Int,
        val scoreCp: Int?,
        val scoreMate: Int?,
        /** True for `lowerbound`/`upperbound` scores (fail-high/low, not exact). */
        val isBound: Boolean,
        /** Principal variation in LAN as reported, possibly empty. */
        val pv: List<String>
    )

    /**
     * Parse a single engine output line. Returns null for anything that is
     * not an `info` line carrying a score — those are the only lines the
     * analyzer cares about.
     */
    fun parse(line: String): UciInfo? {
        val tokens = line.trim().split(Regex("\\s+"))
        if (tokens.firstOrNull() != "info") return null

        var depth = 0
        var multipv = 1
        var scoreCp: Int? = null
        var scoreMate: Int? = null
        var isBound = false
        var pv: List<String> = emptyList()

        var i = 1
        while (i < tokens.size) {
            when (tokens[i]) {
                "depth" -> depth = tokens.getOrNull(i + 1)?.toIntOrNull() ?: 0
                "multipv" -> multipv = tokens.getOrNull(i + 1)?.toIntOrNull() ?: 1
                "score" -> {
                    when (tokens.getOrNull(i + 1)) {
                        "cp" -> scoreCp = tokens.getOrNull(i + 2)?.toIntOrNull()
                        "mate" -> scoreMate = tokens.getOrNull(i + 2)?.toIntOrNull()
                    }
                    if (tokens.getOrNull(i + 3) == "lowerbound" ||
                        tokens.getOrNull(i + 3) == "upperbound"
                    ) {
                        isBound = true
                    }
                }
                // pv is always the last field on an info line, so consume the rest
                "pv" -> {
                    pv = tokens.subList(i + 1, tokens.size)
                    i = tokens.size
                }
            }
            i++
        }

        if (scoreCp == null && scoreMate == null) return null
        return UciInfo(depth, multipv, scoreCp, scoreMate, isBound, pv)
    }
}
