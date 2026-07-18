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
import com.raichess.data.repository.GameRepository
import com.raichess.data.repository.PlayerProfileRepository
import com.raichess.data.repository.PracticeRepository
import com.raichess.data.repository.PuzzleRepository
import com.raichess.domain.usecase.DrillSelector
import com.raichess.domain.usecase.GameAnalyzer
import com.raichess.domain.usecase.PuzzleDrill
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
    /** Squares to flash on reveal (the expected move). */
    val revealHighlights: Set<Int> = emptySet(),
    val solvedCount: Int = 0,
    val attemptedCount: Int = 0
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

    private var queue: List<DrillSelector.Drill> = emptyList()
    private var queueIndex = 0
    private var loadJob: Job? = null
    private var activePuzzle: PuzzleDrill? = null
    private var activeMistake: DrillSelector.MistakeDrill? = null
    private var board = Board()

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
            val weaknesses = try {
                gameRepository.weaknessProfile().weaknesses.map { it.theme }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "weakness profile unavailable", e)
                emptyList()
            }
            // A newer loadQueue cancelled this job while it was suspended
            // above: bail before touching queue state
            ensureActive()
            // Off the main thread: the sort is trivial for the seed set but
            // the fetch script can grow the asset to thousands of puzzles
            queue = withContext(Dispatchers.Default) {
                DrillSelector.buildQueue(
                    source = source,
                    mistakes = mistakes,
                    puzzles = puzzles,
                    progressById = progress,
                    playerElo = profileRepository.getStats().currentElo,
                    weaknesses = weaknesses,
                    nowMs = System.currentTimeMillis()
                )
            }
            queueIndex = 0
            _uiState.value = _uiState.value.copy(loading = false, queueEmpty = queue.isEmpty())
            if (queue.isNotEmpty()) startDrill(queue[0])
        }
    }

    fun nextDrill() {
        if (queue.isEmpty()) return
        queueIndex = (queueIndex + 1) % queue.size
        startDrill(queue[queueIndex])
    }

    private fun startDrill(drill: DrillSelector.Drill) {
        activePuzzle = null
        activeMistake = null
        val puzzle = drill.puzzle
        if (puzzle != null) {
            val running = PuzzleDrill(puzzle)
            if (running.isFinished) { // corrupt data: skip it
                queue = queue.filterNot { it === drill }
                if (queue.isEmpty()) {
                    _uiState.value = _uiState.value.copy(queueEmpty = true)
                } else {
                    queueIndex %= queue.size
                    startDrill(queue[queueIndex])
                }
                return
            }
            activePuzzle = running
            board = running.boardCopy()
            _uiState.value = _uiState.value.copy(
                squares = boardSnapshot(),
                playerIsWhite = running.solverSide == Side.WHITE,
                phase = DrillPhase.SOLVING,
                prompt = promptFor(running.solverSide),
                sourceLabel = "Puzzle · ${puzzle.rating}",
                selectedSquare = null,
                legalTargets = emptySet(),
                revealHighlights = emptySet()
            )
        } else {
            val mistake = drill.mistake ?: return
            activeMistake = mistake
            board = Board().apply { loadFromFen(mistake.fen) }
            _uiState.value = _uiState.value.copy(
                squares = boardSnapshot(),
                playerIsWhite = board.sideToMove == Side.WHITE,
                phase = DrillPhase.SOLVING,
                prompt = promptFor(board.sideToMove),
                sourceLabel = "From your games",
                selectedSquare = null,
                legalTargets = emptySet(),
                revealHighlights = emptySet()
            )
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
                _uiState.value = _uiState.value.copy(
                    squares = boardSnapshot(),
                    selectedSquare = null,
                    legalTargets = emptySet(),
                    prompt = "Keep going — ${sideName(drill.solverSide)} to move"
                )
            }
            PuzzleDrill.Outcome.Solved -> {
                board = drill.boardCopy()
                finishDrill(solved = true, revealLan = null)
            }
            is PuzzleDrill.Outcome.Wrong -> {
                finishDrill(solved = false, revealLan = outcome.expectedLan)
            }
        }
    }

    private fun handleMistakeMove(mistake: DrillSelector.MistakeDrill, move: Move) {
        val played = move.toString().lowercase()
        if (played == mistake.bestMoveLan.lowercase()) {
            board.doMove(move)
            finishDrill(solved = true, revealLan = null)
        } else {
            finishDrill(solved = false, revealLan = mistake.bestMoveLan)
        }
    }

    private fun finishDrill(solved: Boolean, revealLan: String?) {
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
        val reveal = revealLan
            ?.let { GameAnalyzer.lanToLegalMove(board, it) }
            ?.let { setOf(it.from.ordinal, it.to.ordinal) }
            ?: emptySet()
        _uiState.value = state.copy(
            squares = boardSnapshot(),
            phase = if (solved) DrillPhase.SOLVED else DrillPhase.FAILED,
            prompt = if (solved) {
                "Solved!"
            } else {
                "Not quite — best was ${revealLan?.let { formatLan(it) }}."
            },
            selectedSquare = null,
            legalTargets = emptySet(),
            revealHighlights = reveal,
            solvedCount = state.solvedCount + if (solved) 1 else 0,
            attemptedCount = state.attemptedCount + 1
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

    private fun promptFor(side: Side) = "Find the best move for ${sideName(side)}"

    private fun sideName(side: Side) = if (side == Side.WHITE) "White" else "Black"

    private fun formatLan(lan: String) =
        if (lan.length >= 4) "${lan.substring(0, 2)} → ${lan.substring(2, 4)}" else lan

    companion object {
        private const val TAG = "PracticeViewModel"
    }
}
