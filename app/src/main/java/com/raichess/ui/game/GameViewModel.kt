package com.raichess.ui.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveConversionException
import com.github.bhlangonijr.chesslib.move.MoveGenerator
import com.github.bhlangonijr.chesslib.move.MoveList
import com.raichess.data.analysis.AnalysisCoordinator
import com.raichess.data.engine.ChessEngine
import com.raichess.data.engine.EngineFactory
import com.raichess.data.engine.RaiEngine
import com.raichess.data.repository.PlayerProfileRepository
import com.raichess.data.repository.PracticeRepository
import com.raichess.data.repository.SettingsRepository
import com.raichess.domain.model.CompletedGame
import com.raichess.domain.model.EloConfiguration
import com.raichess.domain.model.EloStats
import com.raichess.domain.model.GameMode
import com.raichess.domain.model.GameResult
import com.raichess.domain.model.MoveClassification
import com.raichess.domain.model.MoveClassifier
import com.raichess.domain.model.PgnBuilder
import com.raichess.domain.model.PlayerColor
import com.raichess.domain.model.PositionAnalysis
import com.raichess.domain.model.UndoPenalty
import com.raichess.domain.model.WinProbability
import com.raichess.domain.model.canUndo
import com.raichess.domain.usecase.HintAdvisor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Which screen of the game flow is showing. */
enum class GamePhase { SETUP, PLAYING, GAME_OVER }

/** How the game ended, from the player's perspective. */
enum class GameEnding { CHECKMATE_WIN, CHECKMATE_LOSS, DRAW, RESIGNED }

/** The most recent move, as board indices (a1=0 .. h8=63). */
data class LastMove(val from: Int, val to: Int)

data class GameUiState(
    val phase: GamePhase = GamePhase.SETUP,
    val playerStats: EloStats? = null,
    val opponentElo: Int = 1250,
    val playerColor: PlayerColor = PlayerColor.WHITE,
    val gameMode: GameMode = GameMode.RATED,
    /** Undos used this game (Training mode only). */
    val undoCount: Int = 0,
    /** Whether the Undo button is currently actionable. */
    val canUndo: Boolean = false,
    /** Increments only when a move is applied — the animation key. Undo and new game never animate. */
    val moveSeq: Int = 0,
    // Overwritten from SettingsRepository at construction; on by default
    val animationsEnabled: Boolean = true,
    /** FEN piece chars ('P', 'k', ...) or null for empty, indexed a1=0 .. h8=63. */
    val squares: List<Char?> = emptyList(),
    val selectedSquare: Int? = null,
    val legalTargets: Set<Int> = emptySet(),
    val lastMove: LastMove? = null,
    /** True when [lastMove] was played by the AI opponent (highlight distinctly). */
    val lastMoveByOpponent: Boolean = false,
    /** Label of the engine actually playing ("RaiEngine" / "Stockfish" / fallback). */
    val engineLabel: String = "RaiEngine",
    /** True when a hint could be offered (Training + a fresh analysis is cached). */
    val canHint: Boolean = false,
    /** Rung of the hint ladder revealed for the current position (0 = none). */
    val hintLevel: Int = 0,
    val hintText: String? = null,
    /** Squares highlighted by the current hint (a1=0 .. h8=63). */
    val hintHighlights: Set<Int> = emptySet(),
    /** Hints used this game (Training only; docks accuracy like undos). */
    val hintCount: Int = 0,
    /** True when the player's last move lost blunder-level ground (Training). */
    val coachWarning: Boolean = false,
    /** True while a rung-3 deep-hint search is in flight (blocks board input). */
    val isDeepHintRunning: Boolean = false,
    /** Live grade of the player's last move (Training; null until graded). */
    val lastMoveRating: MoveClassification? = null,
    /** Player's live winning chances 0–100 (Training; null until analyzed). */
    val winPercent: Int? = null,
    val moveHistorySan: List<String> = emptyList(),
    val isPlayerTurn: Boolean = false,
    val isAiThinking: Boolean = false,
    val isPlayerInCheck: Boolean = false,
    val ending: GameEnding? = null,
    val eloDelta: Int? = null
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlayerProfileRepository(application)
    private val practiceRepository = PracticeRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private var board = Board()
    private var moveList = MoveList()
    private var engine: ChessEngine? = null

    /**
     * The engine hints/coaching analyze with. In the Stockfish opponent
     * band this is the same instance as [engine] (its analyze() lifts the
     * skill cap). In the RaiEngine band a dedicated full-strength Stockfish
     * analyzer is created instead, so hints are always Stockfish-quality
     * when the WASM bridge works — still one WebView per game, since
     * RaiEngine opponents don't have one. Rated mode never analyzes, so it
     * shares [engine] (unused).
     */
    private var analysisEngine: ChessEngine? = null
    private var gameRecorded = false
    private var gameId = 0
    private var gameUndoCount = 0
    private var gameHintCount = 0

    // Serializes every engine call (analyze + selectMove): ChessEngine is
    // single-caller, and coaching adds analyze() calls that could otherwise
    // overlap a search (e.g. an undo's baseline refresh racing the next AI move)
    private val engineLock = Mutex()

    /**
     * A coach analysis tagged with the position it was computed for, so a
     * slow background analysis resolving after a fast player move can never
     * be applied to the wrong position — every consumer validates the FEN.
     */
    private data class CachedAnalysis(val fen: String, val analysis: PositionAnalysis)

    /** Analysis of the position the player is currently looking at (Training). */
    private var currentAnalysis: CachedAnalysis? = null

    private val _uiState = MutableStateFlow(
        repository.getStats().let { stats ->
            GameUiState(
                playerStats = stats,
                // Seed the setup screen with the recommended opponent strength
                opponentElo = EloConfiguration.getRecommendedOpponentElo(stats.currentElo),
                gameMode = settingsRepository.gameMode,
                animationsEnabled = settingsRepository.animationsEnabled
            )
        }
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    fun setOpponentElo(elo: Int) {
        _uiState.value = _uiState.value.copy(
            opponentElo = elo.coerceIn(RaiEngine.MIN_ELO, RaiEngine.MAX_ELO)
        )
    }

    fun setPlayerColor(color: PlayerColor) {
        _uiState.value = _uiState.value.copy(playerColor = color)
    }

    fun setGameMode(mode: GameMode) {
        settingsRepository.gameMode = mode
        _uiState.value = _uiState.value.copy(gameMode = mode)
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        settingsRepository.animationsEnabled = enabled
        _uiState.value = _uiState.value.copy(animationsEnabled = enabled)
    }

    fun startGame(randomColor: Boolean = false) {
        val state = _uiState.value
        val color = if (randomColor) {
            if (Random.nextBoolean()) PlayerColor.WHITE else PlayerColor.BLACK
        } else {
            state.playerColor
        }

        // Fresh Board per game: a stale AI search from a previous game can
        // never touch the live board even if it is still running
        board = Board()
        moveList = MoveList()
        // Release the previous game's engines (may hold a WebView) and pick
        // the engine for this opponent strength (RaiEngine weak band /
        // Stockfish above)
        if (analysisEngine !== engine) analysisEngine?.close()
        engine?.close()
        val newEngine = EngineFactory.create(getApplication<Application>(), state.opponentElo)
        engine = newEngine
        analysisEngine = if (
            state.gameMode == GameMode.TRAINING &&
            !EngineFactory.usesStockfish(state.opponentElo)
        ) {
            EngineFactory.createAnalyzer(getApplication<Application>())
        } else {
            newEngine
        }
        gameRecorded = false
        gameId++
        gameUndoCount = 0
        gameHintCount = 0
        currentAnalysis = null

        _uiState.value = state.copy(
            phase = GamePhase.PLAYING,
            playerColor = color,
            squares = boardSnapshot(),
            selectedSquare = null,
            legalTargets = emptySet(),
            lastMove = null,
            lastMoveByOpponent = false,
            engineLabel = newEngine.activeEngineLabel,
            moveHistorySan = emptyList(),
            isPlayerTurn = color == PlayerColor.WHITE,
            isAiThinking = false,
            isPlayerInCheck = false,
            ending = null,
            eloDelta = null,
            undoCount = 0,
            canUndo = false,
            moveSeq = 0,
            canHint = false,
            hintLevel = 0,
            hintText = null,
            hintHighlights = emptySet(),
            hintCount = 0,
            coachWarning = false,
            isDeepHintRunning = false,
            lastMoveRating = null,
            winPercent = null
        )

        if (color == PlayerColor.BLACK) {
            makeAiMove()
        } else {
            // Playing White: seed the coach/hint baseline for move one
            refreshBaseline()
        }
    }

    /**
     * Progressive hint (Training only): each tap reveals the next rung of
     * [HintAdvisor]'s ladder for the current position. Costs accuracy like
     * an undo — assistance shouldn't be free, or the incentive design of
     * Training mode collapses. No engine call: reuses the baseline analysis
     * the coach already computed for this position.
     */
    fun requestHint() {
        val state = _uiState.value
        if (state.gameMode != GameMode.TRAINING ||
            state.phase != GamePhase.PLAYING ||
            !state.canHint ||
            !state.isPlayerTurn ||
            state.isAiThinking ||
            state.hintLevel >= HintAdvisor.MAX_LEVEL
        ) {
            return
        }
        // Defense in depth beyond canHint: never hint from an analysis of a
        // different position than the one on screen
        val cached = currentAnalysis?.takeIf { it.fen == board.fen } ?: return
        val nextLevel = state.hintLevel + 1
        if (nextLevel == HintAdvisor.DEEP_LEVEL) {
            requestDeepHint(state, cached)
            return
        }
        val hint = HintAdvisor.hint(nextLevel, cached.analysis, cached.fen) ?: return
        gameHintCount++
        _uiState.value = state.copy(
            hintLevel = nextLevel,
            hintText = hint.text,
            hintHighlights = hint.highlights,
            hintCount = gameHintCount
        )
    }

    /**
     * Rung 3: a fresh, much deeper search (not the cached ~200ms baseline),
     * shown when it lands. The deeper analysis also replaces the baseline,
     * so the move played afterwards is graded against the better eval.
     */
    private fun requestDeepHint(state: GameUiState, cached: CachedAnalysis) {
        val coach = analysisEngine ?: return
        val currentGameId = gameId
        val positionFen = cached.fen
        // Level advances now (disables the button while searching), but the
        // hint is only charged when it actually delivers content — matching
        // rungs 1–2's no-charge-without-content behavior
        _uiState.value = state.copy(
            hintLevel = HintAdvisor.DEEP_LEVEL,
            hintText = "Thinking deeper…",
            isDeepHintRunning = true,
            // Undo rides out the search too — greyed, not silently dead
            canUndo = false
        )
        viewModelScope.launch {
            try {
                val deep = withContext(Dispatchers.Default) {
                    engineLock.withLock {
                        coach.analyze(
                            Board().apply { loadFromFen(positionFen) },
                            DEEP_HINT_ANALYZE_MS
                        )
                    }
                }
                if (gameId != currentGameId ||
                    _uiState.value.phase != GamePhase.PLAYING ||
                    board.fen != positionFen
                ) {
                    return@launch
                }
                val hint = deep?.let { HintAdvisor.hint(HintAdvisor.DEEP_LEVEL, it, positionFen) }
                if (deep == null || hint == null) {
                    // Refund: step back to rung 2 so the tap can be retried
                    _uiState.value = _uiState.value.copy(
                        hintLevel = HintAdvisor.DEEP_LEVEL - 1,
                        hintText = "No deeper line found — try again."
                    )
                    return@launch
                }
                currentAnalysis = CachedAnalysis(positionFen, deep)
                gameHintCount++
                _uiState.value = _uiState.value.copy(
                    hintText = hint.text,
                    hintHighlights = hint.highlights,
                    hintCount = gameHintCount,
                    winPercent = WinProbability.percent(deep.effectiveCp())
                )
            } finally {
                val current = _uiState.value
                _uiState.value = current.copy(
                    isDeepHintRunning = false,
                    canUndo = canUndo(
                        mode = current.gameMode,
                        isPlaying = current.phase == GamePhase.PLAYING,
                        isPlayerTurn = current.isPlayerTurn,
                        isAiThinking = current.isAiThinking,
                        moveCount = moveList.size
                    )
                )
            }
        }
    }

    /**
     * Analyze the current position in the background (Training only) so a
     * hint is instant when asked for and the coach can measure the player's
     * next move against an honest baseline.
     */
    private fun refreshBaseline() {
        val state = _uiState.value
        if (state.gameMode != GameMode.TRAINING || state.phase != GamePhase.PLAYING) return
        val coach = analysisEngine ?: return
        val currentGameId = gameId
        val positionFen = board.fen
        viewModelScope.launch {
            val analysis = withContext(Dispatchers.Default) {
                engineLock.withLock {
                    val analysisBoard = Board().apply { loadFromFen(positionFen) }
                    coach.analyze(analysisBoard, COACH_ANALYZE_MS)
                }
            }
            if (gameId != currentGameId || _uiState.value.phase != GamePhase.PLAYING) return@launch
            // Discard if the board moved on while we analyzed — the newer
            // refreshBaseline for the newer position will supply the cache
            if (analysis == null || board.fen != positionFen) return@launch
            currentAnalysis = CachedAnalysis(positionFen, analysis)
            _uiState.value = _uiState.value.copy(
                canHint = _uiState.value.isPlayerTurn,
                // Baseline positions are player-to-move, so effectiveCp is
                // already the player's perspective
                winPercent = WinProbability.percent(analysis.effectiveCp())
            )
        }
    }

    fun onSquareTapped(index: Int) {
        val state = _uiState.value
        if (state.phase != GamePhase.PLAYING || !state.isPlayerTurn || state.isAiThinking) return
        // Moving mid-deep-hint would waste the requested search and queue
        // the AI's reply behind it on the engine lock — hold input briefly
        if (state.isDeepHintRunning) return

        val selected = state.selectedSquare
        if (selected != null && index in state.legalTargets) {
            playPlayerMove(selected, index)
            return
        }

        // Select (or re-select) one of the player's own pieces
        val piece = board.getPiece(Square.squareAt(index))
        val playerSide = if (state.playerColor == PlayerColor.WHITE) Side.WHITE else Side.BLACK
        if (piece != Piece.NONE && piece.pieceSide == playerSide) {
            val targets = legalMovesFrom(index)
            _uiState.value = state.copy(selectedSquare = index, legalTargets = targets)
        } else {
            _uiState.value = state.copy(selectedSquare = null, legalTargets = emptySet())
        }
    }

    fun resign() {
        val state = _uiState.value
        if (state.phase != GamePhase.PLAYING) return
        finishGame(GameEnding.RESIGNED, GameResult.LOSS)
    }

    /**
     * Training-mode takeback: reverts the player's last move and the AI's
     * reply, and captures the pre-mistake position as practice material —
     * an undo means the player spotted the problem only after moving.
     */
    fun undoMove() {
        val state = _uiState.value
        // Ride out an in-flight deep hint (≤1.5s): undoing under it would
        // leave input blocked on a stale search holding the engine lock
        if (state.isDeepHintRunning) return
        if (!canUndo(
                mode = state.gameMode,
                isPlaying = state.phase == GamePhase.PLAYING,
                isPlayerTurn = state.isPlayerTurn,
                isAiThinking = state.isAiThinking,
                moveCount = moveList.size
            )
        ) return

        // board and moveList are always mutated together (see applyMove),
        // so the canUndo moveCount>=2 gate guarantees >=2 plies of history
        board.undoMove() // AI's reply
        board.undoMove() // player's mistaken move
        // Rebuild rather than removeLast(): MoveList caches its SAN encoding
        val remaining = moveList.toList().dropLast(2)
        moveList = MoveList().apply { remaining.forEach { add(it) } }

        gameUndoCount++
        repository.incrementLifetimeUndos()
        // Process-lifetime write, not viewModelScope: leaving the screen
        // right after an undo must not cancel the write and drop the
        // observation. board.fen must stay an eagerly-evaluated argument —
        // snapshotting it inside a deferred lambda would race further play
        // on the live board.
        practiceRepository.recordMistakePosition(
            fen = board.fen, // the position as it stood before the mistake
            sourceMoveNumber = remaining.size / 2 + 1
        )

        _uiState.value = state.copy(
            squares = boardSnapshot(),
            selectedSquare = null,
            legalTargets = emptySet(),
            lastMove = remaining.lastOrNull()?.let { LastMove(it.from.ordinal, it.to.ordinal) },
            lastMoveByOpponent = remaining.isNotEmpty() &&
                isOpponentMove(remaining.size - 1, state.playerColor),
            moveHistorySan = sanHistory(),
            isPlayerInCheck = isPlayerInCheck(),
            undoCount = gameUndoCount,
            canUndo = canUndo(
                mode = state.gameMode,
                isPlaying = true,
                isPlayerTurn = true,
                isAiThinking = false,
                moveCount = remaining.size
            ),
            // The rolled-back position needs a fresh baseline before hints
            // or coaching resume
            canHint = false,
            hintLevel = 0,
            hintText = null,
            hintHighlights = emptySet(),
            coachWarning = false,
            lastMoveRating = null,
            winPercent = null
            // moveSeq deliberately not incremented: undo never animates
        )
        currentAnalysis = null
        refreshBaseline()
    }

    fun backToSetup() {
        _uiState.value = _uiState.value.copy(
            phase = GamePhase.SETUP,
            playerStats = repository.getStats()
        )
    }

    override fun onCleared() {
        if (analysisEngine !== engine) analysisEngine?.close()
        engine?.close()
        super.onCleared()
    }

    private fun playPlayerMove(fromIndex: Int, toIndex: Int) {
        val move = findLegalMove(fromIndex, toIndex) ?: return
        val fenBeforeMove = board.fen
        applyMove(move)
        if (!checkGameOver()) {
            makeAiMove(
                playerMoveBaselineFen = fenBeforeMove,
                playerMoveLan = move.toString().lowercase()
            )
        }
    }

    /**
     * @param playerMoveBaselineFen the position the player just moved from,
     *   for move grading — null when there is no player move to grade
     *   (the AI's opening move as White)
     * @param playerMoveLan the move the player just played, for the BEST
     *   classification when it matches the engine's own choice
     */
    private fun makeAiMove(
        playerMoveBaselineFen: String? = null,
        playerMoveLan: String? = null
    ) {
        val currentEngine = engine ?: return
        val coach = analysisEngine
        val currentGameId = gameId
        // Snapshot the position so the background search never touches the
        // live board (resign/new-game can mutate it mid-search otherwise)
        val positionFen = board.fen
        val coaching = _uiState.value.gameMode == GameMode.TRAINING
        // Eval of the position before the player's last move, if the coach
        // had time to compute one for exactly that position — the FEN check
        // means a stale analysis of some earlier position can never grade
        // this move
        val baseline = playerMoveBaselineFen?.let { fen ->
            currentAnalysis?.takeIf { it.fen == fen }?.analysis
        }
        currentAnalysis = null
        _uiState.value = _uiState.value.copy(
            isAiThinking = true,
            isPlayerTurn = false,
            canHint = false
        )

        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            var playerMoveLoss = 0
            var playerMoveRating: MoveClassification? = null
            val move = withContext(Dispatchers.Default) {
                // ChessEngine.selectMove is single-caller only (see its KDoc):
                // never launch overlapping AI-move coroutines, or the engine's
                // shared UCI queue would be raced. The isAiThinking gate on
                // player input plus engineLock keep this to one in-flight
                // engine call at a time.
                engineLock.withLock {
                    val searchBoard = Board().apply { loadFromFen(positionFen) }
                    // baseline is null when the player outraced the ~200ms
                    // background analysis (move 1, or right after an undo):
                    // that move silently skips grading — an accepted gap,
                    // preferable to blocking input on the coach
                    if (coaching && baseline != null && coach != null) {
                        val afterMove = coach.analyze(searchBoard, COACH_ANALYZE_MS)
                        if (afterMove != null) {
                            playerMoveLoss = MoveClassifier.lossBetween(baseline, afterMove)
                            playerMoveRating = MoveClassifier.classify(
                                playerMoveLoss,
                                playedEngineBest = playerMoveLan != null &&
                                    playerMoveLan == baseline.bestMoveLan
                            )
                        }
                    }
                    currentEngine.selectMove(searchBoard)
                }
            }
            // Pause so instant replies still read as a "thinking" opponent,
            // as a floor on total time rather than an added delay
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < MIN_AI_MOVE_DELAY_MS) delay(MIN_AI_MOVE_DELAY_MS - elapsed)
            // Bail out if the game ended or a new game started while searching
            if (_uiState.value.phase != GamePhase.PLAYING || gameId != currentGameId) return@launch
            if (move != null) {
                applyMove(move)
            }
            if (!checkGameOver()) {
                val current = _uiState.value
                _uiState.value = current.copy(
                    isAiThinking = false,
                    isPlayerTurn = true,
                    // Reflect a mid-game Stockfish->RaiEngine fallback in the label
                    engineLabel = currentEngine.activeEngineLabel,
                    // Re-enabled once the background baseline lands
                    canHint = false,
                    // Passive nudge, not an interrupt: the AI has already
                    // replied, and the existing Training undo (which records
                    // the practice position) is the recovery path
                    coachWarning = coaching &&
                        playerMoveLoss >= MoveClassifier.BLUNDER_THRESHOLD_CP,
                    lastMoveRating = playerMoveRating,
                    canUndo = canUndo(
                        mode = current.gameMode,
                        isPlaying = true,
                        isPlayerTurn = true,
                        isAiThinking = false,
                        moveCount = moveList.size
                    )
                )
                // Baseline for the player's new position (hints + next
                // blunder check) catches up in the background — turn
                // handoff must never wait on the coach
                if (coaching) refreshBaseline()
            }
        }
    }

    private fun applyMove(move: Move) {
        // Invariant: every move is doMove'd on the live board so its
        // internal history stack stays valid for undoMove(). Do not switch
        // AI move application to a FEN reload.
        board.doMove(move)
        moveList.add(move)
        val state = _uiState.value
        _uiState.value = state.copy(
            squares = boardSnapshot(),
            selectedSquare = null,
            legalTargets = emptySet(),
            lastMove = LastMove(move.from.ordinal, move.to.ordinal),
            lastMoveByOpponent = isOpponentMove(moveList.size - 1, state.playerColor),
            moveHistorySan = sanHistory(),
            isPlayerInCheck = isPlayerInCheck(),
            moveSeq = state.moveSeq + 1,
            canUndo = false, // re-enabled when the turn returns to the player
            // Any move invalidates the current hint, warning, and grade
            canHint = false,
            hintLevel = 0,
            hintText = null,
            hintHighlights = emptySet(),
            coachWarning = false,
            lastMoveRating = null
        )
    }

    /** Returns true when the game ended (and records the result). */
    private fun checkGameOver(): Boolean {
        val state = _uiState.value
        val playerSide = if (state.playerColor == PlayerColor.WHITE) Side.WHITE else Side.BLACK
        return when {
            board.isMated -> {
                // Side to move has been mated
                if (board.sideToMove == playerSide) {
                    finishGame(GameEnding.CHECKMATE_LOSS, GameResult.LOSS)
                } else {
                    finishGame(GameEnding.CHECKMATE_WIN, GameResult.WIN)
                }
                true
            }
            board.isDraw -> {
                finishGame(GameEnding.DRAW, GameResult.DRAW)
                true
            }
            else -> false
        }
    }

    private fun finishGame(ending: GameEnding, result: GameResult) {
        if (gameRecorded) return
        gameRecorded = true
        val state = _uiState.value
        // Training-mode assistance docks the accuracy input; Rated stays
        // neutral. Hints count like undos: each rung revealed is help the
        // player asked for.
        val accuracy = if (state.gameMode == GameMode.TRAINING) {
            UndoPenalty.accuracyAfterUndos(gameUndoCount + gameHintCount)
        } else {
            UndoPenalty.NEUTRAL_ACCURACY
        }
        val (stats, delta) = repository.recordResult(result, state.opponentElo, accuracy)
        persistFinishedGame(state, result, eloAfter = stats.currentElo, eloDelta = delta)
        _uiState.value = state.copy(
            phase = GamePhase.GAME_OVER,
            playerStats = stats,
            isAiThinking = false,
            isPlayerTurn = false,
            ending = ending,
            eloDelta = delta,
            canUndo = false
        )
    }

    /**
     * Persist the finished game and queue its background analysis
     * (TECHNICAL_PLAN.md Phase 1/3). Fire-and-forget on an app-lifetime
     * scope inside the coordinator, so leaving this screen can't lose the
     * game or cancel its analysis. Games with no moves (an instant resign)
     * have nothing to store or analyze and are skipped.
     */
    private fun persistFinishedGame(
        state: GameUiState,
        result: GameResult,
        eloAfter: Int,
        eloDelta: Int
    ) {
        val sanMoves = sanHistory()
        if (sanMoves.isEmpty()) return
        val now = System.currentTimeMillis()
        AnalysisCoordinator.saveAndAnalyze(
            getApplication(),
            CompletedGame(
                pgn = PgnBuilder.build(
                    sanMoves = sanMoves,
                    result = result,
                    playerColor = state.playerColor,
                    opponentElo = state.opponentElo,
                    datePlayed = now
                ),
                movesLan = moveList.map { it.toString().lowercase() },
                result = result,
                playerColor = state.playerColor,
                opponentElo = state.opponentElo,
                // Relies on recordResult's delta being the exact signed
                // change it applied (newElo - oldElo); revisit if that
                // contract ever changes
                playerEloBefore = eloAfter - eloDelta,
                playerEloAfter = eloAfter,
                gameMode = state.gameMode,
                undoCount = gameUndoCount,
                datePlayed = now
            )
        )
    }

    private fun boardSnapshot(): List<Char?> = (0 until 64).map { index ->
        val piece = board.getPiece(Square.squareAt(index))
        if (piece == Piece.NONE) null else piece.fenChar()
    }

    private fun legalMovesFrom(index: Int): Set<Int> {
        val from = Square.squareAt(index)
        return MoveGenerator.generateLegalMoves(board)
            .filter { it.from == from }
            .map { it.to.ordinal }
            .toSet()
    }

    private fun findLegalMove(fromIndex: Int, toIndex: Int): Move? {
        val from = Square.squareAt(fromIndex)
        val to = Square.squareAt(toIndex)
        val matches = MoveGenerator.generateLegalMoves(board)
            .filter { it.from == from && it.to == to }
        if (matches.isEmpty()) return null
        // Multiple matches means promotion; auto-queen for the MVP
        return matches.firstOrNull { it.promotion.pieceType == PieceType.QUEEN }
            ?: matches.first()
    }

    /**
     * Whether the move at [moveIndex] (0 = White's first) was played by the AI
     * opponent, from move parity: White moves the even indices, and the AI is
     * whichever side the player is not.
     */
    private fun isOpponentMove(moveIndex: Int, playerColor: PlayerColor): Boolean {
        val whiteMoved = moveIndex % 2 == 0
        val playerIsWhite = playerColor == PlayerColor.WHITE
        return whiteMoved != playerIsWhite
    }

    private fun isPlayerInCheck(): Boolean {
        val playerSide =
            if (_uiState.value.playerColor == PlayerColor.WHITE) Side.WHITE else Side.BLACK
        return board.sideToMove == playerSide && board.isKingAttacked
    }

    // O(moves) per call since toSanArray re-encodes the full list; fine at
    // one call per ply for normal game lengths, don't reuse in hot paths
    private fun sanHistory(): List<String> = try {
        moveList.toSanArray().toList()
    } catch (e: MoveConversionException) {
        // Fall back to coordinate notation if SAN conversion fails
        moveList.map { it.toString() }
    }

    private fun Piece.fenChar(): Char? {
        val type = pieceType ?: return null
        val char = when (type) {
            PieceType.PAWN -> 'p'
            PieceType.KNIGHT -> 'n'
            PieceType.BISHOP -> 'b'
            PieceType.ROOK -> 'r'
            PieceType.QUEEN -> 'q'
            PieceType.KING -> 'k'
            else -> return null
        }
        return if (pieceSide == Side.WHITE) char.uppercaseChar() else char
    }

    companion object {
        private const val MIN_AI_MOVE_DELAY_MS = 350L

        // Coach/hint search budget per position. Two run per full move in
        // Training: grading the player's move runs during the AI-thinking
        // pause (fully hidden when the opponent's own search dominates; at
        // the fastest Stockfish bands it adds up to ~200ms to the reply —
        // an accepted cost of grading every move), and the new-position
        // baseline runs after turn handoff. Neither blocks board input.
        private const val COACH_ANALYZE_MS = 200L

        // The third hint tap's search budget: long enough for a genuinely
        // deeper verdict, short enough to feel like a considered answer
        // rather than a stall.
        private const val DEEP_HINT_ANALYZE_MS = 1500L
    }
}
