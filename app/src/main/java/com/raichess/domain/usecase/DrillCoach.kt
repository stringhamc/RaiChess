package com.raichess.domain.usecase

import com.raichess.domain.model.CoachPersonality
import com.raichess.domain.model.LanFormat
import com.raichess.domain.model.ThemeTag

/**
 * Escalating in-drill coaching (field request): a wrong try shouldn't end
 * the drill — it starts a conversation. Three tiers per expected move:
 *
 *  1. first miss  — "not that one", try again; nothing is taught yet
 *  2. second miss — general guidance from the drill's themes: what KIND
 *     of idea to hunt for, never the move itself
 *  3. third miss  — the move, drawn as an arrow on the board
 *
 * The Hint button climbs the same ladder without spending a miss (one tap
 * for guidance, another for the arrow). Any arrow — earned by misses or
 * bought by hints — means the position wasn't cleanly solved, so spaced
 * repetition brings it back sooner.
 *
 * Conversational lines (misses, reveals, solves, the failure sign-off)
 * come in one variant per [CoachPersonality]; the personality changes the
 * delivery, never the information — every reveal names the move, every
 * solve is a solve. Guidance content stays persona-free: what to look for
 * is teaching, not chatter.
 *
 * Pure text/threshold policy, testable without Android; the ladder state
 * itself lives in PracticeViewModel.
 */
object DrillCoach {

    /** How much has been given away for the current expected move. */
    enum class Assist { NONE, GUIDANCE, REVEAL }

    /** Ladder tier a total of [misses] wrong tries has earned. */
    fun assistForMisses(misses: Int): Assist = when {
        misses >= 3 -> Assist.REVEAL
        misses >= 2 -> Assist.GUIDANCE
        else -> Assist.NONE
    }

    /** Tier 1: acknowledge the miss, don't teach yet. */
    fun tryAgain(
        misses: Int,
        persona: CoachPersonality = CoachPersonality.MENTOR
    ): String = when (persona) {
        CoachPersonality.MENTOR ->
            if (misses <= 1) "Not that one — try again."
            else "Still not it — take another look."
        CoachPersonality.FIREBRAND ->
            if (misses <= 1) "Nope! Shake it off — you've got this."
            else "Still hiding from you — hunt it down!"
        CoachPersonality.SAGE ->
            if (misses <= 1) "No. Look again."
            else "Still no. Slow down — the position will tell you."
    }

    /** Tier 3: the answer, spoken while the arrow points at it. */
    fun reveal(
        expectedLan: String,
        persona: CoachPersonality = CoachPersonality.MENTOR
    ): String {
        val move = LanFormat.arrow(expectedLan)
        return when (persona) {
            CoachPersonality.MENTOR -> "Here it is: $move — follow the arrow."
            CoachPersonality.FIREBRAND -> "Here's the money move: $move — play it!"
            CoachPersonality.SAGE -> "Observe: $move. Play it, and remember it."
        }
    }

    /** A clean solve — no misses, no help. [streak] ≥ 3 earns extra noise. */
    fun solvedClean(
        streak: Int,
        persona: CoachPersonality = CoachPersonality.MENTOR
    ): String = when (persona) {
        CoachPersonality.MENTOR ->
            if (streak >= 3) "Solved! $streak in a row!" else "Solved!"
        CoachPersonality.FIREBRAND ->
            if (streak >= 3) "$streak in a row — you're on fire!"
            else "Boom — that's what I'm talking about!"
        CoachPersonality.SAGE ->
            if (streak >= 3) "Correct. $streak in a row — discipline."
            else "Correct."
    }

    /** Solved after misses or bought guidance — the grind deserves its own line. */
    fun solvedEarned(persona: CoachPersonality = CoachPersonality.MENTOR): String =
        when (persona) {
            CoachPersonality.MENTOR -> "There it is — you worked for that one."
            CoachPersonality.FIREBRAND -> "Yes! You ground that one out — those count double."
            CoachPersonality.SAGE -> "Correct — eventually. The struggle is where the learning is."
        }

    /** Solved with a stored near-best alternative, not the engine's first pick. */
    fun solvedAsGood(
        bestLan: String,
        persona: CoachPersonality = CoachPersonality.MENTOR
    ): String {
        val move = LanFormat.arrow(bestLan)
        return when (persona) {
            CoachPersonality.MENTOR ->
                "Solved! The engine liked $move — yours is just as good."
            CoachPersonality.FIREBRAND ->
                "Solved! The engine liked $move — yours lands just as hard."
            CoachPersonality.SAGE ->
                "Acceptable. The engine preferred $move, but yours holds."
        }
    }

    /** The drill is over unsolved; spaced repetition will bring it back. */
    fun failed(persona: CoachPersonality = CoachPersonality.MENTOR): String =
        when (persona) {
            CoachPersonality.MENTOR -> "It'll come back around."
            CoachPersonality.FIREBRAND -> "We'll get it next lap — it's coming back around."
            CoachPersonality.SAGE -> "Unsolved. It will return; be ready."
        }

    /** Opens the walkthrough recap, right after the revealed move lands. */
    fun walkthroughOpener(persona: CoachPersonality = CoachPersonality.MENTOR): String =
        when (persona) {
            CoachPersonality.MENTOR -> "That's the one."
            CoachPersonality.FIREBRAND -> "There it is!"
            CoachPersonality.SAGE -> "So you see it now."
        }

    /** Closes the walkthrough recap — the lesson-not-a-loss sign-off. */
    fun walkthroughCloser(persona: CoachPersonality = CoachPersonality.MENTOR): String =
        when (persona) {
            CoachPersonality.MENTOR -> "It'll come back around."
            CoachPersonality.FIREBRAND -> "Next time it's all yours."
            CoachPersonality.SAGE -> "It will return; be ready."
        }

    /** Walkthrough recap when the mistake has no stored explanation to quote. */
    fun lineComplete(persona: CoachPersonality = CoachPersonality.MENTOR): String =
        when (persona) {
            CoachPersonality.MENTOR ->
                "Line complete — we walked through it together. It'll come back around."
            CoachPersonality.FIREBRAND ->
                "Line finished — we tag-teamed that one. Next time it's all yours."
            CoachPersonality.SAGE ->
                "The line is complete. We moved through it together; next time, alone."
        }

    /**
     * Tier-2 guidance keyed by Lichess puzzle themes, most specific first
     * so "mateIn1" beats the generic "mate" entry when both are tagged.
     */
    private val PUZZLE_GUIDANCE: List<Pair<String, String>> = listOf(
        "mateIn1" to "There's a checkmate in one — every check is a candidate.",
        "mateIn2" to "Force mate in two: check after check, until the king runs out of squares.",
        "backRankMate" to "The back rank is weak — look at your heavy pieces on the last row.",
        "mate" to "The king is the target — look for a forcing mating attack.",
        "hangingPiece" to "A piece is undefended — find the way to win it.",
        "fork" to "Look for one move that attacks two things at once.",
        "pin" to "Line a long-range piece up against the king or queen — a pinned piece can't run.",
        "skewer" to "Attack through one piece to win the one behind it.",
        "discoveredAttack" to "Moving one piece can unmask an attack from another.",
        "trappedPiece" to "A piece has nowhere safe to go — hunt it down.",
        "promotion" to "A pawn can go the distance here — think about promotion.",
        "advancedPawn" to "That far-advanced pawn is the story — push it or support it.",
        "defensiveMove" to "This one is about defense — find the move that keeps everything safe.",
        "endgame" to "Endgame technique: activate your king and think about passed pawns."
    )

    /**
     * Tier-2 general coaching for a puzzle: name the kind of idea to look
     * for. Multi-move lines fall back to the forcing-sequence frame when
     * nothing more specific matches.
     */
    fun guidance(puzzleThemes: Set<String>, multiMove: Boolean): String =
        PUZZLE_GUIDANCE.firstOrNull { (theme, _) -> theme in puzzleThemes }?.second
            ?: if (multiMove) {
                "Look for a way to force a series of moves — checks and captures " +
                    "that leave your opponent only one reply."
            } else {
                "Look at forcing moves first: checks, captures, and threats."
            }

    /**
     * Tier-2 general coaching for an own-game mistake drill, from the
     * analyzer's theme tags. Mates outrank material, matching
     * [ThemeTag.explain]'s priority.
     *
     * [punishLan] is the opponent's best reply to the game move — the
     * concrete tactic the mistake allowed (field request: "allowed a
     * tactic" without naming it read as an arbitrary claim). Naming the
     * threat is strong help, which is exactly what tier 2 is for; the
     * defense itself stays the player's job.
     */
    fun guidance(mistakeThemes: Set<ThemeTag>, punishLan: String? = null): String = when {
        ThemeTag.ALLOWED_MATE in mistakeThemes ->
            punishLan?.let {
                "Your game move let ${LanFormat.arrow(it)} deliver mate — " +
                    "find the move that shuts that threat down."
            } ?: "Your king was the problem here — find the move that shuts down the mate threat."
        ThemeTag.MISSED_MATE in mistakeThemes ->
            "There's a forced mate in this position — check every check."
        ThemeTag.HANGING_PIECE in mistakeThemes ->
            "Look for a way to keep every piece defended — where can this one land safely?"
        ThemeTag.MISSED_CAPTURE in mistakeThemes ->
            "You can win material here — look for a piece that's undefended or overworked."
        ThemeTag.ALLOWED_TACTIC in mistakeThemes ->
            punishLan?.let {
                "Your game move allowed ${LanFormat.arrow(it)}, winning material — " +
                    "find a move that doesn't."
            } ?: "A tactic was looming — find the move that sidesteps it."
        else ->
            "Compare your candidate moves: for each one, what's the opponent's best reply?"
    }

    /**
     * "(f3 → d4 was the threat)" — the concrete punishment named next to a
     * mistake explanation, or empty when it isn't tactic/mate shaped or
     * wasn't recorded. Appended by prompts that quote [ThemeTag.explain].
     */
    fun threatClause(mistakeThemes: Set<ThemeTag>, punishLan: String?): String {
        if (punishLan == null) return ""
        val threatShaped = ThemeTag.ALLOWED_TACTIC in mistakeThemes ||
            ThemeTag.ALLOWED_MATE in mistakeThemes
        return if (threatShaped) " (${LanFormat.arrow(punishLan)} was the threat)" else ""
    }
}
