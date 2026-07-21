package com.raichess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raichess.data.analysis.AnalysisCoordinator
import com.raichess.ui.game.GamePhase
import com.raichess.ui.game.GameScreen
import com.raichess.ui.game.GameViewModel
import com.raichess.ui.home.HomeScreen
import com.raichess.ui.practice.PracticeScreen
import com.raichess.ui.practice.PracticeViewModel
import com.raichess.ui.review.ReviewScreen
import com.raichess.ui.review.ReviewViewModel
import com.raichess.ui.theme.RaiChessTheme

/**
 * Main Activity for RaiChess (来Chess)
 * Entry point for the application
 *
 * @version 1.0.0-alpha
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Finish any post-game analysis a previous process didn't complete
        // (fire-and-forget; no-op when nothing is pending)
        AnalysisCoordinator.analyzePendingGames(applicationContext)
        setContent {
            RaiChessTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RaiChessApp()
                }
            }
        }
    }
}

@Composable
fun RaiChessApp(viewModel: GameViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showPractice by rememberSaveable { mutableStateOf(false) }
    var showReview by rememberSaveable { mutableStateOf(false) }

    if (showReview) {
        val reviewViewModel: ReviewViewModel = viewModel()
        val reviewState by reviewViewModel.uiState.collectAsState()
        ReviewScreen(
            state = reviewState,
            onPrevious = reviewViewModel::previous,
            onNext = reviewViewModel::next,
            onBack = { showReview = false }
        )
        return
    }

    if (showPractice) {
        val practiceViewModel: PracticeViewModel = viewModel()
        val practiceState by practiceViewModel.uiState.collectAsState()
        PracticeScreen(
            state = practiceState,
            onSquareTapped = practiceViewModel::onSquareTapped,
            onSourceChanged = practiceViewModel::setSource,
            onNext = practiceViewModel::nextDrill,
            onBack = { showPractice = false }
        )
        return
    }

    when (state.phase) {
        GamePhase.SETUP -> HomeScreen(
            stats = state.playerStats,
            opponentElo = state.opponentElo,
            playerColor = state.playerColor,
            gameMode = state.gameMode,
            animationsEnabled = state.animationsEnabled,
            onOpponentEloChanged = viewModel::setOpponentElo,
            onPlayerColorChanged = viewModel::setPlayerColor,
            onGameModeChanged = viewModel::setGameMode,
            onAnimationsChanged = viewModel::setAnimationsEnabled,
            onStartGame = viewModel::startGame,
            onPractice = { showPractice = true },
            onReview = { showReview = true }
        )

        GamePhase.PLAYING, GamePhase.GAME_OVER -> GameScreen(
            state = state,
            onSquareTapped = viewModel::onSquareTapped,
            onUndo = viewModel::undoMove,
            onHint = viewModel::requestHint,
            onResign = viewModel::resign,
            onNewGame = viewModel::backToSetup
        )
    }
}
