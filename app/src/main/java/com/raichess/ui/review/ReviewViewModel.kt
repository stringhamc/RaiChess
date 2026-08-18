package com.raichess.ui.review

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raichess.data.analysis.AnalysisCoordinator
import com.raichess.data.database.AnalysisState
import com.raichess.data.database.PositionEntity
import com.raichess.data.repository.GameRepository
import com.raichess.domain.model.EvalPerspective
import com.raichess.domain.model.GameResult
import com.raichess.domain.model.LanFormat
import com.raichess.domain.model.PlayerColor
import com.raichess.domain.model.ThemeTag
import com.raichess.domain.usecase.HintAdvisor
import com.raichess.domain.usecase.MistakeNarrator
import com.raichess.ui.game.LastMove
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One player mistake from the reviewed game, ready to render. */
data class ReviewMistakeUi(
    /** Full-move number the mistake happened on. */
    val moveNumber: Int,
    /** "Blunder — hung a piece". */
    val classificationLabel: String,
    /** What was played, what was best, and why it mattered. */
    val detail: String,
    /** Board before the mistake, FEN chars a1=0..h8=63. */
    val squares: List<Char?>,
    /** The mistake move's squares (rendered as a gray arrow ending in ✕). */
    val playedMove: LastMove?,
    /** The engine's best move (rendered as a white arrow ending in ○). */
    val bestMove: LastMove?,
    /** The engine's best move's squares (square tint under the arrow). */
    val bestHighlights: Set<Int>
)

data class ReviewUiState(
    val loading: Boolean = true,
    /** Nothing stored yet — invite the player to play first. */
    val noGames: Boolean = false,
    /** Latest game exists but background analysis hasn't landed. */
    val analysisPending: Boolean = false,
    /** "Won vs 1250 · Accuracy 84%". */
    val headline: String = "",
    /** Analyzed game had no graded mistakes — celebrate it. */
    val cleanGame: Boolean = false,
    val mistakes: List<ReviewMistakeUi> = emptyList(),
    val index: Int = 0,
    val playerIsWhite: Boolean = true
)

/**
 * Post-game review (the coaching loop's missing surface): replays each
 * graded mistake from the most recent analyzed game on the board, with
 * the engine's best move and the tagged *why*. Reads only stored
 * analysis — no engine runs here.
 */
class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val gameRepository = GameRepository(application)

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState

    /** Bumped by every load(); in-flight polls from older loads bail out. */
    private var loadSeq = 0

    init {
        load()
    }

    /** Explicit id → that game; null → the most recent game, any state. */
    private suspend fun fetch(gameId: Long?) = if (gameId != null) {
        gameRepository.getGame(gameId)
    } else {
        gameRepository.recentGames(1).firstOrNull()
    }

    /**
     * Load a specific game's review, or the most recent game when [gameId]
     * is null (the game-over "Review this game" path — that game may still
     * be saving or analyzing, so pending states poll until the analysis
     * lands instead of demanding a manual back-and-retry). Public and
     * re-callable: the screen reloads on every entry (the Activity-scoped
     * ViewModel used to load once in init and then showed a stale game
     * forever after new games were played).
     */
    fun load(gameId: Long? = null) {
        _uiState.value = ReviewUiState()
        val seq = ++loadSeq
        viewModelScope.launch {
            try {
                var game = fetch(gameId)
                if (game == null || game.analysisState == AnalysisState.PENDING) {
                    // Nudge the background analyzer: if this game's analysis
                    // was interrupted (app killed mid-sweep), this is what
                    // finishes it so the next visit has the full review
                    AnalysisCoordinator.analyzePendingGames(getApplication())
                    val anyGames = game != null || gameRepository.recentGames(1).isNotEmpty()
                    _uiState.value = ReviewUiState(
                        loading = false,
                        // In latest-game mode the row may simply not be
                        // saved yet — treat "no games" as pending too and
                        // let the poll below find it
                        noGames = gameId != null && !anyGames,
                        analysisPending = gameId == null || anyGames
                    )
                    // Analysis takes ~15s of engine time; poll rather than
                    // sending the player away to come back manually
                    var attempts = 0
                    while (attempts < MAX_PENDING_POLLS &&
                        (game == null || game.analysisState == AnalysisState.PENDING)
                    ) {
                        delay(PENDING_POLL_MS)
                        // A newer load() owns the screen now — stop quietly
                        if (seq != loadSeq) return@launch
                        game = fetch(gameId)
                        attempts++
                    }
                    if (seq != loadSeq) return@launch
                }
                if (game == null || game.analysisState != AnalysisState.DONE) {
                    val anyGames = game != null || gameRepository.recentGames(1).isNotEmpty()
                    _uiState.value = ReviewUiState(
                        loading = false,
                        noGames = !anyGames,
                        analysisPending = anyGames
                    )
                    return@launch
                }
                val playerColor = runCatching { PlayerColor.valueOf(game.playerColor) }
                    .getOrDefault(PlayerColor.WHITE)
                val result = GameResult.fromPgnResult(game.result, playerColor)
                val resultWord = when (result) {
                    GameResult.WIN -> "Won"
                    GameResult.LOSS -> "Lost"
                    GameResult.DRAW -> "Drew"
                }
                val accuracy = game.accuracy
                    ?.let { " · Accuracy ${it.roundToInt()}%" } ?: ""
                val rows = gameRepository.positionsForGame(game.id)
                val mistakes = rows
                    .filter { it.isPlayerMove && it.classification in GRADED }
                    .map { toUi(it, rows, playerColor == PlayerColor.WHITE) }
                _uiState.value = ReviewUiState(
                    loading = false,
                    headline = "$resultWord vs ${game.opponentElo}$accuracy",
                    cleanGame = mistakes.isEmpty(),
                    mistakes = mistakes,
                    playerIsWhite = playerColor == PlayerColor.WHITE
                )
            } catch (e: Exception) {
                Log.w(TAG, "failed to load review", e)
                _uiState.value = ReviewUiState(loading = false, noGames = true)
            }
        }
    }

    fun next() {
        val state = _uiState.value
        if (state.index < state.mistakes.size - 1) {
            _uiState.value = state.copy(index = state.index + 1)
        }
    }

    fun previous() {
        val state = _uiState.value
        if (state.index > 0) _uiState.value = state.copy(index = state.index - 1)
    }

    /**
     * One mistake row, narrated as the chess that went wrong rather than
     * the centipawn bill: [allRows] supplies the next ply's stored analysis
     * (same join the drill coach uses) — its best move is the opponent's
     * punishing reply, its eval the post-move standing. No schema change.
     */
    private fun toUi(
        row: PositionEntity,
        allRows: List<PositionEntity>,
        playerIsWhite: Boolean
    ): ReviewMistakeUi {
        val best = row.bestMove?.let { moveSquares(it) }
        val kind = if (row.classification == "BLUNDER") "Blunder" else "Mistake"
        val next = allRows.firstOrNull { it.ply == row.ply + 1 }
        // Stored evals are White-perspective; the narrator wants the player's
        val evalBefore = EvalPerspective.toPlayer(row.evaluationCp, playerIsWhite)
        val evalAfter = EvalPerspective.afterMove(
            evalBeforePlayerCp = evalBefore,
            nextEvalWhiteCp = next?.evaluationCp,
            playerIsWhite = playerIsWhite,
            lossCp = row.centipawnLoss
        )
        val themes = ThemeTag.fromCsv(row.themes)
        val why = MistakeNarrator.narrate(
            fenBefore = row.fen,
            moveLan = row.movePlayed,
            bestLan = row.bestMove,
            replyLan = next?.bestMove,
            themes = themes,
            evalBeforeCp = evalBefore,
            evalAfterCp = evalAfter
        )
        return ReviewMistakeUi(
            moveNumber = row.ply / 2 + 1,
            classificationLabel =
                "$kind — ${MistakeNarrator.label(themes, evalBefore, evalAfter)}",
            detail = "You played ${LanFormat.arrow(row.movePlayed)}. $why",
            squares = HintAdvisor.parseFenBoard(row.fen) ?: List(64) { null },
            playedMove = moveSquares(row.movePlayed),
            bestMove = best,
            bestHighlights = best?.let { setOf(it.from, it.to) } ?: emptySet()
        )
    }

    private fun moveSquares(lan: String): LastMove? {
        val from = HintAdvisor.squareOrdinal(lan.take(2)) ?: return null
        val to = HintAdvisor.squareOrdinal(lan.drop(2).take(2)) ?: return null
        return LastMove(from, to)
    }

    companion object {
        private const val TAG = "ReviewViewModel"

        /**
         * Deliberately narrower than the live coach's "Why?" (which also
         * covers INACCURACY): the review screen focuses on the misses
         * worth replaying, not every half-pawn slip.
         */
        private val GRADED = setOf("MISTAKE", "BLUNDER")

        // ~15s of engine time per analysis; 12 × 2.5s comfortably covers it
        private const val MAX_PENDING_POLLS = 12
        private const val PENDING_POLL_MS = 2500L
    }
}
