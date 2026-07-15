package com.raichess.domain.model

/**
 * Machine-detectable labels for *why* a graded move lost ground, plus the
 * game phase it happened in. Stored per position row (CSV in the themes
 * column) as immutable observations; the weakness profile aggregates them
 * with recency decay, so interpretation can always be recomputed as the
 * taxonomy grows — add new tags, re-analyze, never migrate.
 */
enum class ThemeTag(val id: String, val isPhase: Boolean = false) {
    /** The moved piece landed where it can be won (attacked and under-defended). */
    HANGING_PIECE("hanging_piece"),
    /** The move let the opponent's best reply win significant material elsewhere. */
    ALLOWED_TACTIC("allowed_tactic"),
    /** The move gave the opponent a forced mate. */
    ALLOWED_MATE("allowed_mate"),
    /** A forced mate was available and not played. */
    MISSED_MATE("missed_mate"),
    /** The engine's best move won significant material and wasn't played. */
    MISSED_CAPTURE("missed_capture"),
    /**
     * Game phase at the moment of the mistake. Phase tags attach to *every*
     * graded mistake (exactly one per observation), unlike the substantive
     * tags above, which only fire when a detector recognizes the pattern —
     * so the profile ranks the two kinds separately (see WeaknessProfiler).
     */
    OPENING("opening", isPhase = true),
    MIDDLEGAME("middlegame", isPhase = true),
    ENDGAME("endgame", isPhase = true);

    companion object {
        private val byId = entries.associateBy { it.id }

        fun toCsv(tags: Set<ThemeTag>): String =
            tags.sortedBy { it.ordinal }.joinToString(",") { it.id }

        /** Unknown ids are skipped, so old rows survive future taxonomy changes. */
        fun fromCsv(csv: String): Set<ThemeTag> =
            csv.split(',')
                .mapNotNull { byId[it.trim()] }
                .toSet()
    }
}
