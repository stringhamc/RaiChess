package com.raichess.domain.usecase

import com.raichess.domain.model.CoachPersonality
import com.raichess.domain.model.EloCalculator
import com.raichess.domain.model.EloStats
import com.raichess.domain.model.GameResult
import com.raichess.domain.model.MoveClassifier
import com.raichess.domain.model.ThemeTag
import com.raichess.domain.model.TrainingStatus

/**
 * The coach's voice: turns the player's data (rating, weakness profile,
 * lesson plan) into personal talking points — "let's work on your
 * endgames", "you've been leaving pieces en prise" — plus one suggested
 * next action. Pure and deterministic: same profile, same words, so the
 * coach doesn't contradict itself between screens.
 *
 * Tone rules: always "we"/"let's" (coach and player are on the same
 * side), always grounded in an observation the player can verify ("I've
 * counted it 4× in your recent games"), never scolding — a weakness is
 * the next thing to train, not a failing.
 */
object CoachAdvisor {

    /** What the coach suggests doing next. */
    enum class Action { PLAY_GAME, START_LESSON, REVIEW_GAMES }

    data class Advice(
        /** Short personal lead ("Let's keep your pieces safe."). */
        val headline: String,
        /** The observation behind it, one or two sentences. */
        val detail: String,
        /** Extra talking points: calibration, streaks, lesson progress. */
        val focuses: List<String>,
        val action: Action,
        val actionLabel: String
    )

    fun advise(
        stats: EloStats?,
        profile: WeaknessProfile,
        plan: List<LessonPlanner.Lesson>,
        solves: Map<String, Int>,
        /** True when the player's most recent finished game was a loss. */
        lastGameWasLoss: Boolean = false,
        /** The coach's read on recent training load (null = no history). */
        trainingStatus: TrainingStatus? = null
    ): Advice {
        val gamesPlayed = stats?.gamesPlayed ?: 0
        if (gamesPlayed == 0) return welcome(plan)

        val lesson = LessonPlanner.activeLesson(plan, solves)
        val weakness = profile.weaknesses.firstOrNull()
        val phase = profile.phases.firstOrNull()

        val (headline, detail) = when {
            weakness != null -> weaknessTalk(weakness)
            phase != null -> phaseTalk(phase)
            else -> "Looking sharp." to
                "No recurring weakness stands out in your recent games. " +
                "Keep playing — every game teaches me more about your style."
        }

        val focuses = buildList {
            statusTalk(trainingStatus)?.let { add(it) }
            if (stats != null && gamesPlayed < EloCalculator.PROVISIONAL_GAMES) {
                add(
                    "We're still placing your rating (game ${gamesPlayed + 1} of " +
                        "${EloCalculator.PROVISIONAL_GAMES}) — expect big swings " +
                        "while I find your level."
                )
            }
            if (stats != null && stats.winStreak >= 2) {
                add(
                    "${stats.winStreak} wins in a row — whatever you're doing, " +
                        "keep doing it."
                )
            }
            if (lesson != null) {
                val done = (solves[lesson.id] ?: 0).coerceAtMost(lesson.targetSolves)
                add("Current lesson: ${lesson.title} — $done of ${lesson.targetSolves} solved.")
            } else if (plan.isNotEmpty()) {
                add("Your training plan is complete — new games will seed the next one.")
            }
            // When a substantive weakness leads, the phase still gets a
            // mention, so "work on your openings" advice isn't crowded out
            if (weakness != null && phase != null) {
                add(
                    "Most of it happens in ${phaseName(phase.theme)} — " +
                        "we'll aim your practice there."
                )
            }
        }

        // A fresh loss outranks the standing plan: reviewing it while it's
        // recent is the most coach-like move available, and the lesson is
        // still listed as a focus above
        return when {
            lastGameWasLoss -> Advice(
                headline, detail,
                focuses + "That last loss is worth a look together — losses teach fastest.",
                Action.REVIEW_GAMES, "Review your last game"
            )
            lesson != null ->
                Advice(headline, detail, focuses, Action.START_LESSON, "Continue: ${lesson.title}")
            else ->
                Advice(headline, detail, focuses, Action.PLAY_GAME, "Play a game")
        }
    }

    /**
     * One-line reaction to a just-finished game, shown on the game-over
     * screen. Picks the most noteworthy thing about the result, delivered
     * in the selected [persona]'s voice — the same six situations rank the
     * same way in every style, only the delivery changes.
     */
    fun react(
        result: GameResult,
        newPeak: Boolean,
        winStreak: Int,
        calibrating: Boolean,
        persona: CoachPersonality = CoachPersonality.MENTOR
    ): String = when (persona) {
        CoachPersonality.MENTOR -> when {
            result == GameResult.WIN && newPeak ->
                "New peak — that's real progress. Noted."
            result == GameResult.WIN && winStreak >= 3 ->
                "That's $winStreak in a row — you're trending up."
            result == GameResult.WIN ->
                "Well played. Ready for the next one when you are."
            result == GameResult.DRAW ->
                "A solid hold. Let's find the half-point you left behind."
            calibrating ->
                "Good data — I'm still finding your level, and this helps."
            else ->
                "Every loss is practice material — let's see what this one teaches."
        }
        CoachPersonality.FIREBRAND -> when {
            result == GameResult.WIN && newPeak ->
                "New peak! Frame it — then let's beat it."
            result == GameResult.WIN && winStreak >= 3 ->
                "$winStreak straight! Nobody's slowing you down!"
            result == GameResult.WIN ->
                "That's a win — bank it and rack the next one!"
            result == GameResult.DRAW ->
                "So close to the full point — next time we take it."
            calibrating ->
                "Good rounds — I'm dialing in your level, and you're tougher than you look."
            else ->
                "They caught us this time. Next game's the rematch — let's go."
        }
        CoachPersonality.SAGE -> when {
            result == GameResult.WIN && newPeak ->
                "A new peak. Don't admire it too long."
            result == GameResult.WIN && winStreak >= 3 ->
                "$winStreak consecutive. Consistency is the real skill."
            result == GameResult.WIN ->
                "Won. Study it anyway — wins hide mistakes."
            result == GameResult.DRAW ->
                "A draw. Half points add up; go find the other half."
            calibrating ->
                "Useful. I learn your level fastest from games like this."
            else ->
                "Lost. Good — losses are the only honest teachers."
        }
    }

    /** First-run voice: no games yet, nothing to diagnose. */
    private fun welcome(plan: List<LessonPlanner.Lesson>): Advice {
        val opener = plan.firstOrNull()?.title ?: "the fundamentals"
        return Advice(
            headline = "Welcome — I'm Rai, your coach.",
            detail = "Play a game or two so I can see how you play. I watch " +
                "every move — win or lose — and build your training plan " +
                "from what actually happens on your board.",
            focuses = listOf("Until then, we'll warm up with $opener."),
            action = Action.PLAY_GAME,
            actionLabel = "Play your first game"
        )
    }

    private fun weaknessTalk(stat: ThemeStat): Pair<String, String> {
        val talk = WEAKNESS_TALK[stat.theme]
            ?: ("Let's train a pattern I keep seeing." to "One mistake type keeps recurring")
        // Severity in words, not centipawns — the count is an observation
        // the player can verify, "3.2 pawns" is engine bookkeeping
        val cost = when {
            stat.avgLossCp >= MoveClassifier.BLUNDER_THRESHOLD_CP ->
                " — and it's been swinging whole games"
            stat.avgLossCp >= MoveClassifier.MISTAKE_THRESHOLD_CP ->
                " — giving back real ground each time"
            else -> ""
        }
        val times = if (stat.occurrences == 1) "once" else "${stat.occurrences}×"
        return talk.first to
            "${talk.second}: I've counted it $times in your recent games$cost. " +
            "Let's train it out."
    }

    /**
     * The coach's line for each training status. Rest is endorsed, heavy
     * days are gently capped, lapses are invited back without guilt.
     */
    private fun statusTalk(status: TrainingStatus?): String? = when (status) {
        TrainingStatus.PRODUCTIVE ->
            "Training status: productive — steady, regular work. This is how improvement happens."
        TrainingStatus.RECOVERY ->
            "Rest day — good. Consolidation is training too; come back fresh."
        TrainingStatus.MAINTAINING ->
            "You're ticking over. One lesson today turns maintenance into progress."
        TrainingStatus.DETRAINING ->
            "It's been a few days — skills rust fast at this stage. A short session brings them right back."
        TrainingStatus.OVERREACHING ->
            "That's a lot in one day. Quality beats volume — stop while you're still sharp."
        null -> null
    }

    private fun phaseTalk(stat: ThemeStat): Pair<String, String> =
        "Let's work on ${phaseName(stat.theme)}." to
            "That's where your mistakes cluster right now — I'll steer your " +
            "drills toward that part of the game."

    // Fallback mirrors WEAKNESS_TALK's: a phase tag added to the taxonomy
    // without a PHASE_TALK entry must degrade the wording, not crash the
    // coach screen
    private fun phaseName(theme: ThemeTag): String =
        PHASE_TALK[theme] ?: "that part of the game"

    // headline to observation; the observation reads naturally before
    // ": I've counted it N× in your recent games"
    private val WEAKNESS_TALK = mapOf(
        ThemeTag.HANGING_PIECE to
            ("Let's keep your pieces safe." to
                "Pieces keep getting left where they can be taken"),
        ThemeTag.ALLOWED_TACTIC to
            ("Let's tighten your defense." to
                "Opponent tactics keep breaking through"),
        ThemeTag.ALLOWED_MATE to
            ("Let's spot mate threats sooner." to
                "Checkmate threats have been slipping past you"),
        ThemeTag.MISSED_MATE to
            ("Let's finish with mate." to
                "You've had forced mates on the board and let them go"),
        ThemeTag.MISSED_CAPTURE to
            ("Let's take what's offered." to
                "Winning captures keep going unclaimed")
    )

    private val PHASE_TALK = mapOf(
        ThemeTag.OPENING to "your openings",
        ThemeTag.MIDDLEGAME to "your middlegame",
        ThemeTag.ENDGAME to "your endgames"
    )
}
