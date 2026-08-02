package com.raichess.domain.usecase

/**
 * The named skill ladder (Phase 1 of the beginner-to-intermediate pivot):
 * four steps from board safety to conversion technique, following the
 * consensus teaching order (Steps Method / Silman / Heisman — tactics
 * volume first, endgames by rating, openings last). Each step is a set of
 * lesson units drawn from the bundled puzzle themes, with a short
 * concept intro in the coach's voice shown before drilling starts.
 *
 * Pure and static: progress lives in LessonRepository's solve counts,
 * keyed by the stable unit ids below — reshuffling the ladder must never
 * lose progress.
 */
object Curriculum {

    data class Step(
        /** Stable id ("step1"). */
        val id: String,
        val title: String,
        val subtitle: String,
        /** Rating from which this step is the natural entry point. */
        val floorRating: Int,
        val units: List<LessonPlanner.Lesson>
    )

    val STEPS: List<Step> = listOf(
        Step(
            id = "step1",
            title = "Safety & checkmate",
            subtitle = "See free pieces and one-move mates",
            floorRating = 0,
            units = listOf(
                LessonPlanner.Lesson(
                    id = "step1:mate1",
                    title = "Checkmate in one",
                    description = "Spot the finish the moment it appears",
                    themes = setOf("mateIn1", "backRankMate"),
                    intro = "Every won game ends with mate — train yourself to " +
                        "see it the instant it's on the board. Scan your checks " +
                        "first: a check the defender can't meet is the game."
                ),
                LessonPlanner.Lesson(
                    id = "step1:hanging",
                    title = "Free pieces",
                    description = "Take what's undefended, protect your own",
                    themes = setOf("hangingPiece"),
                    intro = "Most games at this level are decided by pieces left " +
                        "where they can be taken. Before every move ask two " +
                        "questions: what of theirs is free — and what of mine?"
                ),
                LessonPlanner.Lesson(
                    id = "step1:promotion",
                    title = "Pawns become queens",
                    description = "Push, promote, win",
                    themes = setOf("promotion", "advancedPawn"),
                    intro = "A pawn on the sixth or seventh rank is worth more " +
                        "than it looks. Learn when a runner can't be stopped — " +
                        "and when your opponent's can't."
                )
            )
        ),
        Step(
            id = "step2",
            title = "Winning material",
            subtitle = "The tactical toolbox: forks, pins, skewers",
            floorRating = 800,
            units = listOf(
                LessonPlanner.Lesson(
                    id = "step2:fork",
                    title = "The fork",
                    description = "One piece, two targets",
                    themes = setOf("fork"),
                    intro = "A fork attacks two things at once so only one can " +
                        "be saved. Knights are the classic forkers — watch for " +
                        "squares that touch king and queen at the same time."
                ),
                LessonPlanner.Lesson(
                    id = "step2:pinskewer",
                    title = "Pins & skewers",
                    description = "Pieces stuck on a line",
                    themes = setOf("pin", "skewer"),
                    intro = "When two enemy pieces share a line, the front one " +
                        "can be frozen (a pin) or forced to move aside (a " +
                        "skewer). Either way, the piece behind pays."
                ),
                LessonPlanner.Lesson(
                    id = "step2:discovered",
                    title = "Discovered attacks",
                    description = "Move one piece, unleash another",
                    themes = setOf("discoveredAttack", "doubleCheck"),
                    intro = "Moving one piece can open a second piece's attack — " +
                        "two threats from a single move. A discovered check is " +
                        "the strongest version: the moving piece acts for free."
                ),
                LessonPlanner.Lesson(
                    id = "step2:mate2",
                    title = "Mate in two",
                    description = "Force the king into the net",
                    themes = setOf("mateIn2", "smotheredMate"),
                    intro = "Two-move mates are about forcing: the first move " +
                        "leaves the defender only replies that lose. Check every " +
                        "forcing move — checks, captures, threats — in that order."
                )
            )
        ),
        Step(
            id = "step3",
            title = "Sharper tactics & endgames",
            subtitle = "Combinations, and the endgames that convert them",
            floorRating = 1100,
            units = listOf(
                LessonPlanner.Lesson(
                    id = "step3:defender",
                    title = "Remove the defender",
                    description = "Capture, deflect, or overload the guard",
                    themes = setOf("capturingDefender", "deflection", "attraction"),
                    intro = "When a target is defended, attack the defender " +
                        "instead: capture it, drag it away, or give it a second " +
                        "job it can't do. The target falls a move later."
                ),
                LessonPlanner.Lesson(
                    id = "step3:trapped",
                    title = "Traps & x-rays",
                    description = "Pieces with nowhere to go",
                    themes = setOf("trappedPiece", "xRayAttack"),
                    intro = "A piece with no safe squares is already lost — you " +
                        "just have to attack it. And sliders threaten through " +
                        "pieces, not only past them: count the x-rays."
                ),
                LessonPlanner.Lesson(
                    id = "step3:pawnend",
                    title = "Pawn endings",
                    description = "Opposition, key squares, breakthroughs",
                    themes = setOf("pawnEndgame"),
                    intro = "King and pawn endings are exact — one tempo decides " +
                        "them. Learn opposition and key squares and you'll win " +
                        "won endings instead of drawing them."
                ),
                LessonPlanner.Lesson(
                    id = "step3:matenet",
                    title = "Mating nets",
                    description = "Three moves, no escape",
                    themes = setOf("mateIn3", "exposedKing"),
                    intro = "Longer mates are nets, not lightning: each move " +
                        "takes squares away from the king. An exposed king plus " +
                        "patient checks usually ends one way."
                )
            )
        ),
        Step(
            id = "step4",
            title = "Calculation & technique",
            subtitle = "Longer lines, quiet moves, clean conversions",
            floorRating = 1400,
            units = listOf(
                LessonPlanner.Lesson(
                    id = "step4:attack",
                    title = "Sacrifice & attack",
                    description = "Invest material, cash in the king",
                    themes = setOf("sacrifice", "kingsideAttack", "intermezzo"),
                    intro = "A sacrifice trades material for something sharper — " +
                        "open lines to the king, or a tempo the defender doesn't " +
                        "have. Calculate to the end before you spend."
                ),
                LessonPlanner.Lesson(
                    id = "step4:rookend",
                    title = "Rook endings",
                    description = "The endgame you'll actually get",
                    themes = setOf("rookEndgame"),
                    intro = "Half of all endgames are rook endgames. Active rook, " +
                        "king in front of the pawn, and know your Lucena from " +
                        "your Philidor — technique here wins whole tournaments."
                ),
                LessonPlanner.Lesson(
                    id = "step4:quiet",
                    title = "Quiet & defensive moves",
                    description = "The strongest move isn't always a check",
                    themes = setOf("quietMove", "defensiveMove", "zugzwang"),
                    intro = "The hardest moves to find don't capture or check — " +
                        "they take away every good reply. Look for the move that " +
                        "leaves your opponent wishing it were your turn."
                ),
                LessonPlanner.Lesson(
                    id = "step4:minorend",
                    title = "Piece endings",
                    description = "Knights, bishops, and queens in the endgame",
                    themes = setOf("knightEndgame", "bishopEndgame", "queenEndgame"),
                    intro = "Each piece has an endgame personality: knights blockade, " +
                        "bishops want open diagonals, queens check forever. Learn " +
                        "which trades turn your advantage into a win."
                )
            )
        )
    )

    /** The step whose band a rating falls into. */
    fun stepForRating(rating: Int): Step = STEPS.last { rating >= it.floorRating }

    fun isComplete(step: Step, solvesById: Map<String, Int>): Boolean =
        step.units.all { (solvesById[it.id] ?: 0) >= it.targetSolves }

    /**
     * The step the player should be working: their rating-band step,
     * advanced past any steps already completed (a finished step never
     * comes back; a strong newcomer never grinds beginner units).
     */
    fun activeStep(rating: Int, solvesById: Map<String, Int>): Step {
        val entry = stepForRating(rating)
        return STEPS.dropWhile { it !== entry }
            .firstOrNull { !isComplete(it, solvesById) }
            ?: STEPS.last()
    }
}
