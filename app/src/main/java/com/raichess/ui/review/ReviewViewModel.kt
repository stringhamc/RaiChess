package com.raichess.ui.review

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raichess.data.database.AnalysisState
import com.raichess.data.database.PositionEntity
import com.raichess.data.repository.GameRepository
import com.raichess.domain.model.GameResult
import com.raichess.domain.model.LanFormat
import com.raichess.domain.model.PlayerColor
import com.raichess.domain.model.ThemeTag
import com.raichess.domain.usecase.HintAdvisor
import com.raichess.ui.game.LastMove
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One player mistake from the reviewed game, ready to render. */
data class ReviewMistakeUi(
    /** Full-move number the mistake happened on. */
    val moveNumber: Int,
    /** "Blunder — lost 3.2 pawns". */
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

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val recent = gameRepository.recentGames(20)
                val game = recent.firstOrNull { it.analysisState == AnalysisState.DONE }
                if (game == null) {
                    _uiState.value = ReviewUiState(
                        loading = false,
                        noGames = recent.isEmpty(),
                        analysisPending = recent.isNotEmpty()
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
                val mistakes = gameRepository.positionsForGame(game.id)
                    .filter { it.isPlayerMove && it.classification in GRADED }
                    .map { toUi(it) }
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

    private fun toUi(row: PositionEntity): ReviewMistakeUi {
        val kind = if (row.classification == "BLUNDER") "Blunder" else "Mistake"
        // Integer tenths keep the decimal locale-proof
        val tenths = (row.centipawnLoss ?: 0) / 10
        val why = ThemeTag.explain(ThemeTag.fromCsv(row.themes))
            ?.let { " Your move $it." } ?: ""
        val bestText = row.bestMove?.let { "; best was ${LanFormat.arrow(it)}" } ?: ""
        return ReviewMistakeUi(
            moveNumber = row.ply / 2 + 1,
            classificationLabel = "$kind — lost ${tenths / 10}.${tenths % 10} pawns",
            detail = "You played ${LanFormat.arrow(row.movePlayed)}$bestText.$why",
            squares = HintAdvisor.parseFenBoard(row.fen) ?: List(64) { null },
            playedMove = moveSquares(row.movePlayed),
            bestMove = row.bestMove?.let { moveSquares(it) },
            bestHighlights = row.bestMove?.let { lan ->
                moveSquares(lan)?.let { setOf(it.from, it.to) }
            } ?: emptySet()
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
    }
}
