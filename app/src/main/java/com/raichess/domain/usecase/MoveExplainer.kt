package com.raichess.domain.usecase

import com.raichess.domain.model.LanFormat
import kotlin.math.abs

/**
 * Says what a move *does* in plain board terms (field request: a drill
 * revealed the engine's move without saying why it works, or what the
 * game move actually allowed). Facts only, read straight off the board —
 * it captures, attacks, forks, gives check, rescues a threatened piece —
 * never intent the geometry can't back up. Silent (null) when nothing
 * concrete applies; callers then fall back to wording that needs none.
 *
 * Pure FEN-array geometry ([BoardGeometry]), no engine calls.
 */
object MoveExplainer {

    /** At most this many facts per sentence: one clear reason beats a list. */
    private const val MAX_FACTS = 2

    /**
     * Why [bestLan] works from [fenBefore], as a sentence:
     * "d5 → c5 gets your queen out of danger and saves your knight on c6."
     */
    fun whyBest(fenBefore: String, bestLan: String): String? {
        val before = HintAdvisor.parseFenBoard(fenBefore) ?: return null
        val after = BoardGeometry.applied(before, bestLan) ?: return null
        val facts = facts(before, after, bestLan, enemyOwner = "the", defensive = true)
        if (facts.isEmpty()) return null
        return "${LanFormat.arrow(bestLan)} ${facts.joinToString(" and ")}."
    }

    /**
     * What the opponent's [replyLan] did against the game move [playedLan]
     * from [fenBefore], as a clause: "a1 → a2 takes your queen". No
     * trailing period — callers fold it into their own sentence.
     */
    fun whyPunished(fenBefore: String, playedLan: String, replyLan: String): String? {
        val before = HintAdvisor.parseFenBoard(fenBefore) ?: return null
        val mid = BoardGeometry.applied(before, playedLan) ?: return null
        val after = BoardGeometry.applied(mid, replyLan) ?: return null
        val facts = facts(mid, after, replyLan, enemyOwner = "your", defensive = false)
        if (facts.isEmpty()) return null
        return "${LanFormat.arrow(replyLan)} ${facts.joinToString(" and ")}"
    }

    /**
     * The move's facts, most concrete first. [enemyOwner] names whose the
     * mover's targets are ("the rook" for the player's move, "your rook"
     * for the opponent's); [defensive] adds escape/rescue facts, which
     * only make sense from the player's side.
     */
    private fun facts(
        before: List<Char?>,
        after: List<Char?>,
        lan: String,
        enemyOwner: String,
        defensive: Boolean
    ): List<String> {
        val from = HintAdvisor.squareOrdinal(lan.take(2)) ?: return emptyList()
        val to = HintAdvisor.squareOrdinal(lan.drop(2).take(2)) ?: return emptyList()
        val mover = before[from] ?: return emptyList()
        val landed = after[to] ?: return emptyList()
        val white = mover.isUpperCase()
        fun isEnemy(c: Char?) = c != null && c.isUpperCase() != white
        val facts = mutableListOf<String>()

        if (mover.lowercaseChar() == 'k' && abs(from % 8 - to % 8) == 2) {
            facts.add("castles")
        }

        val victim = before[to]?.takeIf { isEnemy(it) }
        if (victim != null) {
            val undefended = !BoardGeometry.squareAttacked(before, to, byWhite = !white)
            facts.add(
                when {
                    undefended -> "wins $enemyOwner undefended ${name(victim)}"
                    // Defended, so say what it costs: the recapture is coming
                    BoardGeometry.value(victim) > BoardGeometry.value(mover) -> {
                        val price = if (enemyOwner == "your") "a" else "your"
                        "wins $enemyOwner ${name(victim)} for $price ${name(mover)}"
                    }
                    else -> "takes $enemyOwner ${name(victim)}"
                }
            )
        }

        if (lan.length > 4) facts.add("promotes to a ${name(landed)}")

        // New targets: enemy pieces this piece now hits that it didn't
        // before, and that can't simply be defended (loose, or worth more
        // than the attacker). The enemy king counts toward a fork.
        val check = BoardGeometry.inCheck(after, whiteKing = !white)
        val hitBefore = if (from == to) emptySet() else BoardGeometry.attacksFrom(before, from)
        val targets = BoardGeometry.attacksFrom(after, to)
            .filter { s -> s != to && s !in hitBefore && isEnemy(after[s]) }
            .filter { s -> after[s]!!.lowercaseChar() != 'k' }
            .filter { s ->
                !BoardGeometry.squareAttacked(after, s, byWhite = !white) ||
                    BoardGeometry.value(after[s]!!) > BoardGeometry.value(landed)
            }
            .sortedByDescending { BoardGeometry.value(after[it]!!) }
        // Only a check the moved piece gives itself joins a fork; a
        // discovered check comes from another piece
        val enemyKing = after.indexOfFirst { it == if (white) 'k' else 'K' }
        val checksDirectly = check && enemyKing in BoardGeometry.attacksFrom(after, to)
        val forked = targets.map { "$enemyOwner ${name(after[it]!!)}" }
            .let { if (checksDirectly) listOf("$enemyOwner king") + it else it }
        when {
            forked.size >= 2 -> {
                val second = forked[1].removePrefix("$enemyOwner ")
                facts.add("forks ${forked[0]} and $second")
            }
            targets.size == 1 -> {
                val target = targets[0]
                facts.add(
                    "attacks $enemyOwner ${name(after[target]!!)} on ${BoardGeometry.squareName(target)}"
                )
                if (check && !checksDirectly) facts.add("uncovers check")
            }
            check -> facts.add(if (checksDirectly) "gives check" else "uncovers check")
        }

        if (defensive) {
            if (BoardGeometry.underThreat(before, from) && !BoardGeometry.underThreat(after, to)) {
                facts.add("gets your ${name(landed)} out of danger")
            }
            val rescued = before.indices.firstOrNull { s ->
                s != from && before[s] != null && !isEnemy(before[s]) && after[s] == before[s] &&
                    BoardGeometry.underThreat(before, s) && !BoardGeometry.underThreat(after, s)
            }
            if (rescued != null) {
                val piece = name(before[rescued]!!)
                // "Saves", not "protects": the threat may be gone because
                // the move defended, blocked, or removed the attacker
                facts.add("saves your $piece on ${BoardGeometry.squareName(rescued)}")
            }
        }

        return facts.take(MAX_FACTS)
    }

    private fun name(piece: Char): String = HintAdvisor.pieceName(piece)
}
