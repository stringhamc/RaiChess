package com.raichess.ui.practice

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveGenerator
import com.raichess.data.repository.DailyRepository
import com.raichess.data.repository.GameRepository
import com.raichess.data.repository.LessonRepository
import com.raichess.data.repository.PlayerProfileRepository
import com.raichess.data.repository.PracticeRepository
import com.raichess.data.repository.PuzzleRepository
import com.raichess.domain.model.LanFormat
import com.raichess.domain.model.PracticeRating
import com.raichess.domain.model.ThemeTag
import com.raichess.domain.usecase.DrillCoach
import com.raichess.domain.usecase.DrillSelector
import com.raichess.domain.usecase.LessonPlanner
import com.raichess.domain.usecase.PuzzleDrill
import com.raichess.domain.usecase.WeaknessProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One drill's on-screen phase. */
enum class DrillPhase { SOLVING, SOLVED, FAILED }

data class PracticeUiState(
    val loading: Boolean = true,
    val source: DrillSelector.Source = DrillSelector.Source.MIXED,
    /** Empty queue after loading = nothing to practice yet. */
    val queueEmpty: Boolean = false,
    /** FEN piece chars indexed a1=0..h8=63, as GameScreen renders. */
    val squares: List<Char?> = emptyList(),
    val playerIsWhite: Boolean = true,
    val phase: DrillPhase = DrillPhase.SOLVING,
    /** "Find the best move for White" / result feedback. */
    val prompt: String = "",
    /** "Puzzle · 850" or "From your games". */
    val sourceLabel: String = "",
    val selectedSquare: Int? = null,
    val legalTargets: Set<Int> = emptySet(),
    /** Squares to flash on reveal (the expected move). Render coach-amber. */
    val revealHighlights: Set<Int> = emptySet(),
    /**
     * The expected move once the coaching ladder reveals it (3rd miss or
     * 2nd hint), from→to ordinals. Render as the coach-amber arrow.
     */
    val coachArrow: Pair<Int, Int>? = null,
    /**
     * The opponent's scripted reply in a multi-move line, from→to
     * ordinals. Render as the teal arrow so the line stays followable.
     */
    val replyArrow: Pair<Int, Int>? = null,
    /** Squares of the player's last wrong try. Render coach-crimson. */
    val wrongSquares: Set<Int> = emptySet(),
    val solvedCount: Int = 0,
    val attemptedCount: Int = 0,
    /** Consecutive solves this session, for streak encouragement. */
    val solvedStreak: Int = 0,
    /** Adaptive puzzle-solving rating (null until first load). */
    val practiceRating: Int? = null,
    /** Active lesson title (Lesson source only). */
    val lessonTitle: String? = null,
    /** "3 of 8 solved" for the active lesson. */
    val lessonProgressText: String? = null,
    /** The active lesson's concept intro (coach voice), when it has one. */
    val lessonIntro: String? = null,
    /** True when every lesson in the current plan is complete. */
    val lessonComplete: Boolean = false,
    /**
     * Title of the lesson unit the player just finished, or null. Drives
     * the celebration card — completing a lesson is the biggest earned
     * milestone in practice and shouldn't render like a routine solve.
     */
    val lessonJustCompletedTitle: String? = null
)

/**
 * Drives the practice screen (Phase D): a queue of puzzle and own-mistake
 * drills built by [DrillSelector], played on a tap-to-move board. Puzzle
 * lines come from the bundled answer key; mistake drills grade against the
 * stored engine best move. No engine runs here at all.
 */
class PracticeViewModel(application: Application) : AndroidViewModel(application) {

    private val puzzleRepository = PuzzleRepository(application)
    private val practiceRepository = PracticeRepository(application)
    private val gameRepository = GameRepository(application)
    private val profileRepository = PlayerProfileRepository(application)
    private val lessonRepository = LessonRepository(application)
    private val dailyRepository = DailyRepository(application)

    private var queue: List<DrillSelector.Drill> = emptyList()
    private var queueIndex = 0
    private var loadJob: Job? = null
    private var activeLessonUnit: LessonPlanner.Lesson? = null
    private var lessonJustCompleted = false
    private var activePuzzle: PuzzleDrill? = null
    private var activeMistake: DrillSelector.MistakeDrill? = null
    private var board = Board()

    // The coaching ladder (see DrillCoach): misses and the assist tier
    // reset per expected move; revealUsed is sticky for the whole drill —
    // once the coach has shown a move, finishing isn't a clean solve.
    private var missCount = 0
    private var assist = DrillCoach.Assist.NONE
    private var revealUsed = false

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(PracticeUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<PracticeUiState> = _uiState

    init {
        loadQueue(DrillSelector.Source.MIXED)
    }

    fun setSource(source: DrillSelector.Source) {
        if (source == _uiState.value.source && !_uiState.value.queueEmpty) return
        loadQueue(source)
    }

    private fun loadQueue(source: DrillSelector.Source) {
        _uiState.value = _uiState.value.copy(loading = true, source = source)
        // Cancel any in-flight load so rapid source switching can't let a
        // slower, stale load finish last and overwrite the newer queue
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val mistakes = try {
                gameRepository.mistakeDrills()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "mistake drills unavailable", e)
                emptyList()
            }
            val puzzles = puzzleRepository.getPuzzles()
            val progress = try {
                practiceRepository.progressById()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "drill progress unavailable", e)
                emptyMap()
            }
            val profile = try {
                gameRepository.weaknessProfile()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "weakness profile unavailable", e)
                WeaknessProfile.EMPTY
            }
            // A newer loadQueue cancelled this job while it was suspended
            // above: bail before touching queue state
            ensureActive()
            val targetRating = profileRepository.getPracticeRating()
            lessonJustCompleted = false

            if (source == DrillSelector.Source.LESSON) {
                val solves = try {
                    lessonRepository.getSolves()
                } catch (e: Exception) {
                    Log.w(TAG, "lesson progress unavailable", e)
                    emptyMap()
                }
                val plan = LessonPlanner.buildPlan(profile, targetRating, solves)
                val lesson = LessonPlanner.activeLesson(plan, solves)
                activeLessonUnit = lesson
                if (lesson == null) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        queueEmpty = true,
                        lessonComplete = true,
                        lessonTitle = null,
                        lessonProgressText = null,
                        lessonIntro = null,
                        lessonJustCompletedTitle = null,
                        practiceRating = targetRating
                    )
                    return@launch
                }
                queue = withContext(Dispatchers.Default) {
                    DrillSelector.buildLessonQueue(
                        lesson = lesson,
                        mistakes = mistakes,
                        puzzles = puzzles,
                        progressById = progress,
                        targetRating = targetRating,
                        nowMs = System.currentTimeMillis()
                    )
                }
                queueIndex = 0
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    queueEmpty = queue.isEmpty(),
                    lessonComplete = false,
                    lessonTitle = lesson.title,
                    lessonProgressText =
                        "${(solves[lesson.id] ?: 0)} of ${lesson.targetSolves} solved",
                    lessonIntro = lesson.intro,
                    lessonJustCompletedTitle = null,
                    practiceRating = targetRating
                )
                if (queue.isNotEmpty()) startDrill(queue[0])
                return@launch
            }

            activeLessonUnit = null
            // Off the main thread: the sort is trivial for the seed set but
            // the fetch script can grow the asset to thousands of puzzles
            queue = withContext(Dispatchers.Default) {
                DrillSelector.buildQueue(
                    source = source,
                    mistakes = mistakes,
                    puzzles = puzzles,
                    progressById = progress,
                    targetRating = targetRating,
                    weaknesses = profile.weaknesses.map { it.theme },
                    nowMs = System.currentTimeMillis(),
                    weakPhases = profile.phases.map { it.theme }
                )
            }
            queueIndex = 0
            _uiState.value = _uiState.value.copy(
                loading = false,
                queueEmpty = queue.isEmpty(),
                lessonComplete = false,
                lessonTitle = null,
                lessonProgressText = null,
                lessonIntro = null,
                lessonJustCompletedTitle = null,
                practiceRating = targetRating
            )
            if (queue.isNotEmpty()) startDrill(queue[0])
        }
    }

    fun nextDrill() {
        // A completed lesson advances the plan: reload picks the next unit
        if (lessonJustCompleted) {
            loadQueue(DrillSelector.Source.LESSON)
            return
        }
        if (queue.isEmpty()) return
        if (queueIndex + 1 >= queue.size) {
            // Full pass done — rebuild instead of wrapping, so due-ness and
            // ordering reflect the results recorded during this session
            loadQueue(_uiState.value.source)
            return
        }
        queueIndex++
        startDrill(queue[queueIndex])
    }

    private fun startDrill(drill: DrillSelector.Drill) {
        activePuzzle = null
        activeMistake = null
        missCount = 0
        assist = DrillCoach.Assist.NONE
        revealUsed = false
        val puzzle = drill.puzzle
        if (puzzle != null) {
            val running = PuzzleDrill(puzzle)
            if (running.isFinished) { // corrupt data: skip it
                skipCorruptDrill(drill)
                return
            }
            activePuzzle = running
            board = running.boardCopy()
            _uiState.value = _uiState.value.copy(
                squares = boardSnapshot(),
                playerIsWhite = running.solverSide == Side.WHITE,
                phase = DrillPhase.SOLVING,
                prompt = promptFor(running.solverSide),
                sourceLabel = if (puzzle.playerMoveCount >= 2) {
                    "Puzzle · ${puzzle.rating} · ${puzzle.playerMoveCount}-move line"
                } else {
                    "Puzzle · ${puzzle.rating}"
                },
                selectedSquare = null,
                legalTargets = emptySet(),
                revealHighlights = emptySet(),
                coachArrow = null,
                replyArrow = null,
                wrongSquares = emptySet()
            )
        } else {
            val mistake = drill.mistake ?: return
            // Same fail-closed treatment as corrupt puzzles: a malformed
            // stored FEN must skip the drill, not crash the screen
            board = try {
                Board().apply { loadFromFen(mistake.fen) }
            } catch (e: Exception) {
                Log.w(TAG, "corrupt mistake FEN, skipping drill", e)
                skipCorruptDrill(drill)
                return
            }
            activeMistake = mistake
            _uiState.value = _uiState.value.copy(
                squares = boardSnapshot(),
                playerIsWhite = board.sideToMove == Side.WHITE,
                phase = DrillPhase.SOLVING,
                prompt = promptFor(board.sideToMove),
                sourceLabel = "From your games",
                selectedSquare = null,
                legalTargets = emptySet(),
                revealHighlights = emptySet(),
                coachArrow = null,
                replyArrow = null,
                wrongSquares = emptySet()
            )
        }
    }

    /** Drop a corrupt entry and move on to whatever now sits at its slot. */
    private fun skipCorruptDrill(drill: DrillSelector.Drill) {
        queue = queue.filterNot { it === drill }
        if (queue.isEmpty()) {
            _uiState.value = _uiState.value.copy(queueEmpty = true)
        } else {
            queueIndex %= queue.size
            startDrill(queue[queueIndex])
        }
    }

    fun onSquareTapped(index: Int) {
        val state = _uiState.value
        if (state.loading || state.queueEmpty || state.phase != DrillPhase.SOLVING) return

        val selected = state.selectedSquare
        if (selected != null && index in state.legalTargets) {
            submitMove(selected, index)
            return
        }
        val piece = board.getPiece(Square.squareAt(index))
        if (piece != Piece.NONE && piece.pieceSide == board.sideToMove) {
            val targets = MoveGenerator.generateLegalMoves(board)
                .filter { it.from == Square.squareAt(index) }
                .map { it.to.ordinal }
                .toSet()
            _uiState.value = state.copy(selectedSquare = index, legalTargets = targets)
        } else {
            _uiState.value = state.copy(selectedSquare = null, legalTargets = emptySet())
        }
    }

    private fun submitMove(fromIndex: Int, toIndex: Int) {
        val from = Square.squareAt(fromIndex)
        val to = Square.squareAt(toIndex)
        val matches = MoveGenerator.generateLegalMoves(board)
            .filter { it.from == from && it.to == to }
        if (matches.isEmpty()) return
        // Multiple matches means promotion. Unlike live play (auto-queen),
        // a drill knows its answer key — prefer the expected promotion so
        // underpromotion solutions from real Lichess data stay solvable
        // through the tap-to-move UI, falling back to queen otherwise.
        val expected = activePuzzle?.expectedLan ?: activeMistake?.bestMoveLan?.lowercase()
        val move = matches.firstOrNull { it.toString().lowercase() == expected }
            ?: matches.firstOrNull { it.promotion.pieceType == PieceType.QUEEN }
            ?: matches.first()

        val puzzle = activePuzzle
        val mistake = activeMistake
        when {
            puzzle != null -> handlePuzzleMove(puzzle, move)
            mistake != null -> handleMistakeMove(mistake, move)
        }
    }

    private fun handlePuzzleMove(drill: PuzzleDrill, move: Move) {
        when (val outcome = drill.submit(move.toString().lowercase())) {
            is PuzzleDrill.Outcome.Continue -> {
                board = drill.boardCopy()
                // A new expected move: the ladder starts over (revealUsed
                // stays sticky for the drill's final scoring)
                missCount = 0
                assist = DrillCoach.Assist.NONE
                _uiState.value = _uiState.value.copy(
                    squares = boardSnapshot(),
                    selectedSquare = null,
                    legalTargets = emptySet(),
                    prompt = "Keep going — ${sideName(drill.solverSide)} to move",
                    coachArrow = null,
                    replyArrow = lanSquares(outcome.opponentReplyLan),
                    wrongSquares = emptySet()
                )
            }
            PuzzleDrill.Outcome.Solved -> {
                board = drill.boardCopy()
                finishDrill(solved = !revealUsed, walkedThrough = revealUsed)
            }
            is PuzzleDrill.Outcome.Wrong -> onMiss(move, outcome.expectedLan)
        }
    }

    private fun handleMistakeMove(mistake: DrillSelector.MistakeDrill, move: Move) {
        val played = move.toString().lowercase()
        when {
            played == mistake.bestMoveLan.lowercase() -> {
                board.doMove(move)
                finishDrill(solved = !revealUsed, walkedThrough = revealUsed)
            }
            // Open positions often have several fine moves; any stored
            // near-best alternative solves the drill (analyzer v4 mining)
            played in mistake.acceptableLans -> {
                board.doMove(move)
                finishDrill(
                    solved = !revealUsed,
                    walkedThrough = revealUsed,
                    solvedNote = "Solved! The engine liked " +
                        "${LanFormat.arrow(mistake.bestMoveLan)} — yours is just as good."
                )
            }
            else -> onMiss(move, mistake.bestMoveLan.lowercase())
        }
    }

    /**
     * A wrong try climbs the coaching ladder (DrillCoach): try-again →
     * general guidance → the move itself as an arrow. The wrong try is
     * marked on the board in coach-crimson; the position never changes,
     * so the player iterates instead of watching the drill end.
     */
    private fun onMiss(move: Move, expectedLan: String) {
        missCount++
        val escalated = maxOf(assist, DrillCoach.assistForMisses(missCount))
        val prompt = when {
            escalated == DrillCoach.Assist.REVEAL -> {
                revealUsed = true
                DrillCoach.reveal(expectedLan)
            }
            escalated != assist -> currentGuidance()
            else -> DrillCoach.tryAgain(missCount)
        }
        assist = escalated
        _uiState.value = _uiState.value.copy(
            prompt = prompt,
            selectedSquare = null,
            legalTargets = emptySet(),
            wrongSquares = setOf(move.from.ordinal, move.to.ordinal),
            coachArrow = if (assist == DrillCoach.Assist.REVEAL) {
                lanSquares(expectedLan)
            } else {
                _uiState.value.coachArrow
            }
        )
    }

    /**
     * The Hint button climbs the same ladder without spending a miss: one
     * tap buys guidance, the next the arrow. An arrow bought here scores
     * the drill exactly like one earned by misses.
     */
    fun onHint() {
        val state = _uiState.value
        if (state.loading || state.queueEmpty || state.phase != DrillPhase.SOLVING) return
        val expected = activePuzzle?.expectedLan
            ?: activeMistake?.bestMoveLan?.lowercase()
            ?: return
        assist = if (assist == DrillCoach.Assist.NONE) {
            DrillCoach.Assist.GUIDANCE
        } else {
            DrillCoach.Assist.REVEAL
        }
        if (assist == DrillCoach.Assist.REVEAL) {
            revealUsed = true
            _uiState.value = state.copy(
                prompt = DrillCoach.reveal(expected),
                coachArrow = lanSquares(expected),
                selectedSquare = null,
                legalTargets = emptySet()
            )
        } else {
            _uiState.value = state.copy(
                prompt = currentGuidance(),
                selectedSquare = null,
                legalTargets = emptySet()
            )
        }
    }

    /** Tier-2 text for the active drill, from its themes. */
    private fun currentGuidance(): String {
        val puzzle = activePuzzle?.puzzle
        return if (puzzle != null) {
            DrillCoach.guidance(puzzle.themes, multiMove = puzzle.playerMoveCount >= 2)
        } else {
            val mistake = activeMistake
            DrillCoach.guidance(mistake?.themes ?: emptySet(), mistake?.punishLan)
        }
    }

    /** "e2e4" → (from, to) square ordinals; null for malformed input. */
    private fun lanSquares(lan: String): Pair<Int, Int>? {
        fun square(file: Char, rank: Char): Int? {
            val f = file - 'a'
            val r = rank - '1'
            return if (f in 0..7 && r in 0..7) r * 8 + f else null
        }
        if (lan.length < 4) return null
        val from = square(lan[0], lan[1]) ?: return null
        val to = square(lan[2], lan[3]) ?: return null
        return from to to
    }

    private fun finishDrill(
        solved: Boolean,
        solvedNote: String? = null,
        /**
         * True when the coach revealed a move along the way: the line was
         * completed together, which records as a failure for spaced
         * repetition but reads as a lesson, not a loss.
         */
        walkedThrough: Boolean = false
    ) {
        val state = _uiState.value
        val drillId = activePuzzle?.let { "puzzle:${it.puzzle.id}" }
            ?: activeMistake?.id ?: return
        // startFen, not puzzle.fen: the drilled position is one ply after
        // the raw Lichess FEN (the setup move is already applied)
        val fen = activePuzzle?.startFen ?: activeMistake?.fen ?: return
        viewModelScope.launch {
            try {
                practiceRepository.recordDrillResult(drillId, fen, solved)
            } catch (e: Exception) {
                // Progress bookkeeping must never break the drill flow
                Log.w(TAG, "failed to record drill result", e)
            }
        }
        // Solved drills count toward the daily goal and streak
        if (solved) dailyRepository.recordActivity()
        // Rated puzzles move the adaptive practice rating; own-mistake
        // drills have no rating to grade against
        val newRating = activePuzzle?.puzzle?.rating?.let { puzzleRating ->
            PracticeRating.updated(
                current = profileRepository.getPracticeRating(),
                puzzleRating = puzzleRating,
                solved = solved
            ).also { profileRepository.setPracticeRating(it) }
        }
        // The revealed move was just played, so its squares are the ones
        // the coach's arrow pointed at — keep them lit for the recap
        val reveal = if (walkedThrough) {
            state.coachArrow?.let { setOf(it.first, it.second) } ?: emptySet()
        } else {
            emptySet()
        }
        val streak = if (solved) state.solvedStreak + 1 else 0

        // Lesson bookkeeping: solves advance the active unit; completing
        // it flags the next "Next" tap to load the following lesson
        var lessonProgress: String? = null
        var lessonDoneTitle: String? = null
        val lesson = activeLessonUnit
        if (solved && state.source == DrillSelector.Source.LESSON && lesson != null) {
            val solves = try {
                lessonRepository.recordSolve(lesson.id)
            } catch (e: Exception) {
                Log.w(TAG, "failed to record lesson solve", e)
                null
            }
            if (solves != null) {
                lessonProgress =
                    "${solves.coerceAtMost(lesson.targetSolves)} of ${lesson.targetSolves} solved"
                if (solves >= lesson.targetSolves) {
                    lessonJustCompleted = true
                    lessonDoneTitle = lesson.title
                }
            }
        }

        _uiState.value = state.copy(
            squares = boardSnapshot(),
            phase = if (solved) DrillPhase.SOLVED else DrillPhase.FAILED,
            prompt = when {
                lessonDoneTitle != null -> "Tap Next for your next lesson."
                walkedThrough -> walkedPrompt()
                solved && solvedNote != null -> solvedNote
                solved && (missCount > 0 || assist != DrillCoach.Assist.NONE) ->
                    "There it is — you worked for that one."
                solved -> if (streak >= 3) "Solved! $streak in a row!" else "Solved!"
                else -> "It'll come back around."
            },
            lessonJustCompletedTitle = lessonDoneTitle,
            lessonProgressText = lessonProgress ?: state.lessonProgressText,
            selectedSquare = null,
            legalTargets = emptySet(),
            revealHighlights = reveal,
            coachArrow = if (walkedThrough) state.coachArrow else null,
            replyArrow = null,
            wrongSquares = emptySet(),
            solvedCount = state.solvedCount + if (solved) 1 else 0,
            attemptedCount = state.attemptedCount + 1,
            solvedStreak = streak,
            practiceRating = newRating ?: state.practiceRating
        )
    }

    private fun boardSnapshot(): List<Char?> = (0 until 64).map { index ->
        val piece = board.getPiece(Square.squareAt(index))
        if (piece == Piece.NONE) null else fenChar(piece)
    }

    private fun fenChar(piece: Piece): Char? {
        val char = when (piece.pieceType) {
            PieceType.PAWN -> 'p'
            PieceType.KNIGHT -> 'n'
            PieceType.BISHOP -> 'b'
            PieceType.ROOK -> 'r'
            PieceType.QUEEN -> 'q'
            PieceType.KING -> 'k'
            else -> return null
        }
        return if (piece.pieceSide == Side.WHITE) char.uppercaseChar() else char
    }

    /**
     * Walked-through recap: the line is complete on the board, so the
     * lesson is context, not the answer. Own-mistake drills name WHICH
     * move the original game mistake was, why it was one, and — when the
     * analyzer recorded it — the concrete tactic it allowed (field
     * request: "allowed a tactic that wins material" without naming the
     * tactic read as an arbitrary claim). Spaced repetition will bring
     * the position back, so a walkthrough is part of the loop, not an
     * ending.
     */
    private fun walkedPrompt(): String {
        val mistake = activeMistake
        val why = mistake?.let { ThemeTag.explain(it.themes) }
        return if (mistake != null && why != null) {
            val threat = DrillCoach.threatClause(mistake.themes, mistake.punishLan)
            "That's the one. In your game you played " +
                "${LanFormat.arrow(mistake.playedLan)}, which $why$threat. " +
                "It'll come back around."
        } else {
            "Line complete — we walked through it together. It'll come back around."
        }
    }

    private fun promptFor(side: Side) = "Find the best move for ${sideName(side)}"

    private fun sideName(side: Side) = if (side == Side.WHITE) "White" else "Black"

    companion object {
        private const val TAG = "PracticeViewModel"
    }
}
