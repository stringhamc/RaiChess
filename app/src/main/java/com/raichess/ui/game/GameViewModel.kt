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
import com.raichess.data.engine.ChessEngine
import com.raichess.data.engine.EngineFactory
import com.raichess.data.engine.RaiEngine
import com.raichess.data.repository.PlayerProfileRepository
import com.raichess.data.repository.PracticeRepository
import com.raichess.data.repository.SettingsRepository
import com.raichess.domain.model.EloConfiguration
import com.raichess.domain.model.EloStats
import com.raichess.domain.model.GameMode
import com.raichess.domain.model.GameResult
import com.raichess.domain.model.PlayerColor
import com.raichess.domain.model.UndoPenalty
import com.raichess.domain.model.canUndo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private var gameRecorded = false
    private var gameId = 0
    private var gameUndoCount = 0

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
        // Release the previous game's engine (may hold a WebView) and pick the
        // engine for this opponent strength (RaiEngine weak band / Stockfish above)
        engine?.close()
        val newEngine = EngineFactory.create(getApplication<Application>(), state.opponentElo)
        engine = newEngine
        gameRecorded = false
        gameId++
        gameUndoCount = 0

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
            moveSeq = 0
        )

        if (color == PlayerColor.BLACK) {
            makeAiMove()
        }
    }

    fun onSquareTapped(index: Int) {
        val state = _uiState.value
        if (state.phase != GamePhase.PLAYING || !state.isPlayerTurn || state.isAiThinking) return

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
        practiceRepository.addMistakePosition(
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
            )
            // moveSeq deliberately not incremented: undo never animates
        )
    }

    fun backToSetup() {
        _uiState.value = _uiState.value.copy(
            phase = GamePhase.SETUP,
            playerStats = repository.getStats()
        )
    }

    override fun onCleared() {
        engine?.close()
        super.onCleared()
    }

    private fun playPlayerMove(fromIndex: Int, toIndex: Int) {
        val move = findLegalMove(fromIndex, toIndex) ?: return
        applyMove(move)
        if (!checkGameOver()) {
            makeAiMove()
        }
    }

    private fun makeAiMove() {
        val currentEngine = engine ?: return
        val currentGameId = gameId
        // Snapshot the position so the background search never touches the
        // live board (resign/new-game can mutate it mid-search otherwise)
        val positionFen = board.fen
        _uiState.value = _uiState.value.copy(isAiThinking = true, isPlayerTurn = false)

        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val move = withContext(Dispatchers.Default) {
                val searchBoard = Board().apply { loadFromFen(positionFen) }
                currentEngine.selectMove(searchBoard)
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
                    canUndo = canUndo(
                        mode = current.gameMode,
                        isPlaying = true,
                        isPlayerTurn = true,
                        isAiThinking = false,
                        moveCount = moveList.size
                    )
                )
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
            canUndo = false // re-enabled when the turn returns to the player
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
        // Training-mode undos dock the accuracy input; Rated stays neutral
        val accuracy = if (state.gameMode == GameMode.TRAINING) {
            UndoPenalty.accuracyAfterUndos(gameUndoCount)
        } else {
            UndoPenalty.NEUTRAL_ACCURACY
        }
        val (stats, delta) = repository.recordResult(result, state.opponentElo, accuracy)
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
    }
}
