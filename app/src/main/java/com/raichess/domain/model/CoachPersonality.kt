package com.raichess.domain.model

/**
 * Rai's selectable delivery style (field request: "add personality to the
 * coach"). Personality colors the coach's *conversational* moments — the
 * miss ladder, solve celebrations, game-over reactions — while everything
 * factual (mistake narration, guidance content, hints) keeps one shared
 * voice: the styles may never disagree about the chess, only about how
 * loudly they say it.
 *
 * [MENTOR] is the default and preserves the app's original wording
 * exactly, so choosing never to touch the setting changes nothing.
 */
enum class CoachPersonality(
    /** Player-facing name in Settings. */
    val label: String,
    /** One-line description under the name. */
    val tagline: String
) {
    /** Warm, encouraging, "we're in this together" — the original voice. */
    MENTOR("Warm", "Encouraging, patient, on your side"),

    /** High-energy hype: big celebrations, everything is momentum. */
    FIREBRAND("Fired up", "High energy, big celebrations"),

    /** Dry old-school trainer: terse, exacting, quietly proud. */
    SAGE("Old school", "Terse, dry, demanding");

    companion object {
        /** Stored-name lookup that survives renames: unknown → default. */
        fun fromName(name: String?): CoachPersonality =
            entries.firstOrNull { it.name == name } ?: MENTOR
    }
}
