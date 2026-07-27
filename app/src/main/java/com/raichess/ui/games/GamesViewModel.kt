package com.raichess.ui.games

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raichess.data.analysis.AnalysisCoordinator
import com.raichess.data.database.AnalysisState
import com.raichess.data.repository.GameRepository
import com.raichess.domain.model.GameResult
import com.raichess.domain.model.PlayerColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One game in the history list, ready to render. */
data class GameRow(
    val id: Long,
    /** "Jul 26, 15:42". */
    val dateText: String,
    /** "Won vs 1100 · Training". */
    val summary: String,
    /** "Accuracy 78%", or the analysis state while not DONE. */
    val detailText: String
)

data class GamesUiState(
    val loading: Boolean = true,
    val rows: List<GameRow> = emptyList()
)

/**
 * The stored game history, newest first — every game is browsable and any
 * analyzed one reviewable (tapping a row opens ReviewScreen for it).
 */
class GamesViewModel(application: Application) : AndroidViewModel(application) {

    private val gameRepository = GameRepository(application)

    private val _uiState = MutableStateFlow(GamesUiState())
    val uiState: StateFlow<GamesUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        // Opening the history is a natural moment to finish any analysis a
        // killed process left behind
        AnalysisCoordinator.analyzePendingGames(getApplication())
        viewModelScope.launch {
            try {
                val rows = gameRepository.recentGames(MAX_GAMES).map { game ->
                    val color = runCatching { PlayerColor.valueOf(game.playerColor) }
                        .getOrDefault(PlayerColor.WHITE)
                    val resultWord = when (GameResult.fromPgnResult(game.result, color)) {
                        GameResult.WIN -> "Won"
                        GameResult.LOSS -> "Lost"
                        GameResult.DRAW -> "Drew"
                    }
                    GameRow(
                        id = game.id,
                        dateText = DATE_FORMAT.format(Date(game.datePlayed)),
                        summary = "$resultWord vs ${game.opponentElo}" +
                            if (game.gameMode == "TRAINING") " · Training" else "",
                        detailText = when (game.analysisState) {
                            AnalysisState.DONE ->
                                game.accuracy?.let { "Accuracy ${it.roundToInt()}%" }
                                    ?: "Analyzed"
                            AnalysisState.FAILED -> "Analysis failed"
                            else -> "Analyzing…"
                        }
                    )
                }
                _uiState.value = GamesUiState(loading = false, rows = rows)
            } catch (e: Exception) {
                Log.w(TAG, "failed to load game history", e)
                _uiState.value = GamesUiState(loading = false, rows = emptyList())
            }
        }
    }

    companion object {
        private const val TAG = "GamesViewModel"
        private const val MAX_GAMES = 50
        private val DATE_FORMAT = SimpleDateFormat("MMM d, HH:mm", Locale.US)
    }
}
