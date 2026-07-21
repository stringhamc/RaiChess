package com.raichess.domain.model

/**
 * Player-facing rendering of LAN moves, shared by every coaching surface
 * (live "Why?", game review, drill reveals) so they can't drift apart.
 */
object LanFormat {

    /** "e7e8q" → "e7 → e8 (=Q)"; malformed input is returned untouched. */
    fun arrow(lan: String): String {
        if (lan.length < 4) return lan
        val base = "${lan.substring(0, 2)} → ${lan.substring(2, 4)}"
        val promotion = lan.getOrNull(4)?.uppercaseChar()
        return if (promotion != null) "$base (=$promotion)" else base
    }
}
