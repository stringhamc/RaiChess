package com.raichess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raichess.data.analysis.AnalysisCoordinator
import com.raichess.domain.usecase.CoachAdvisor
import com.raichess.domain.usecase.DrillSelector
import com.raichess.ui.coach.CoachScreen
import com.raichess.ui.coach.CoachViewModel
import com.raichess.ui.game.GamePhase
import com.raichess.ui.game.GameScreen
import com.raichess.ui.game.GameViewModel
import com.raichess.ui.games.GamesScreen
import com.raichess.ui.games.GamesViewModel
import com.raichess.ui.home.HomeScreen
import com.raichess.ui.home.PlaySetupScreen
import com.raichess.ui.practice.PracticeScreen
import com.raichess.ui.practice.PracticeViewModel
import com.raichess.ui.review.ReviewScreen
import com.raichess.ui.review.ReviewViewModel
import com.raichess.ui.settings.SettingsScreen
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
    var showGames by rememberSaveable { mutableStateOf(false) }
    var showCoach by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showPlaySetup by rememberSaveable { mutableStateOf(false) }
    // Set when the coach's action opens practice: switch to the lesson
    // queue on entry instead of the default mixed queue
    var openLessonOnPractice by rememberSaveable { mutableStateOf(false) }
    // -1 = no game open; rememberSaveable needs a non-null primitive
    var reviewGameId by rememberSaveable { mutableStateOf(-1L) }

    if (reviewGameId >= 0) {
        val reviewViewModel: ReviewViewModel = viewModel()
        val reviewState by reviewViewModel.uiState.collectAsState()
        // Reload on every entry and id change: the Activity-scoped
        // ViewModel would otherwise keep showing a previously loaded game
        LaunchedEffect(reviewGameId) { reviewViewModel.load(reviewGameId) }
        ReviewScreen(
            state = reviewState,
            onPrevious = reviewViewModel::previous,
            onNext = reviewViewModel::next,
            onBack = { reviewGameId = -1L }
        )
        return
    }

    if (showGames) {
        val gamesViewModel: GamesViewModel = viewModel()
        val gamesState by gamesViewModel.uiState.collectAsState()
        LaunchedEffect(Unit) { gamesViewModel.refresh() }
        GamesScreen(
            state = gamesState,
            onOpenGame = { reviewGameId = it },
            onBack = { showGames = false }
        )
        return
    }

    if (showPractice) {
        val practiceViewModel: PracticeViewModel = viewModel()
        val practiceState by practiceViewModel.uiState.collectAsState()
        LaunchedEffect(openLessonOnPractice) {
            if (openLessonOnPractice) {
                practiceViewModel.setSource(DrillSelector.Source.LESSON)
                openLessonOnPractice = false
            }
        }
        PracticeScreen(
            state = practiceState,
            onSquareTapped = practiceViewModel::onSquareTapped,
            onSourceChanged = practiceViewModel::setSource,
            onNext = practiceViewModel::nextDrill,
            onBack = { showPractice = false }
        )
        return
    }

    if (showCoach) {
        val coachViewModel: CoachViewModel = viewModel()
        val coachState by coachViewModel.uiState.collectAsState()
        // Recompute on every entry: games and drills since the last visit
        // change the advice
        LaunchedEffect(Unit) { coachViewModel.refresh() }
        CoachScreen(
            state = coachState,
            onAction = { action ->
                showCoach = false
                when (action) {
                    // Same one-tap philosophy as the Play tile: the coach's
                    // "play a game" starts one, it doesn't open a form
                    CoachAdvisor.Action.PLAY_GAME -> viewModel.startGame(false)
                    CoachAdvisor.Action.START_LESSON -> {
                        openLessonOnPractice = true
                        showPractice = true
                    }
                    CoachAdvisor.Action.REVIEW_GAMES -> showGames = true
                }
            },
            onBack = { showCoach = false }
        )
        return
    }

    if (showSettings) {
        SettingsScreen(
            stats = state.playerStats,
            animationsEnabled = state.animationsEnabled,
            onAnimationsChanged = viewModel::setAnimationsEnabled,
            onBack = { showSettings = false }
        )
        return
    }

    when (state.phase) {
        GamePhase.SETUP -> if (showPlaySetup) {
            PlaySetupScreen(
                stats = state.playerStats,
                opponentElo = state.opponentElo,
                playerColor = state.playerColor,
                gameMode = state.gameMode,
                onOpponentEloChanged = viewModel::setOpponentElo,
                onPlayerColorChanged = viewModel::setPlayerColor,
                onGameModeChanged = viewModel::setGameMode,
                onStartGame = viewModel::startGame,
                onBack = { showPlaySetup = false }
            )
        } else {
            val coachViewModel: CoachViewModel = viewModel()
            val coachState by coachViewModel.uiState.collectAsState()
            // Keep the Coach tile's one-liner current with the latest games
            LaunchedEffect(Unit) { coachViewModel.refresh() }
            HomeScreen(
                stats = state.playerStats,
                coachLine = coachState.headline.takeIf { !coachState.loading },
                opponentElo = state.opponentElo,
                gameMode = state.gameMode,
                // Straight into the game with the current setup; the tile's
                // corner button is the path to the setup screen
                onPlay = { viewModel.startGame(false) },
                onCustomizeGame = { showPlaySetup = true },
                onTrain = { showPractice = true },
                onCoach = { showCoach = true },
                onReview = { showGames = true },
                onSettings = { showSettings = true }
            )
        }

        GamePhase.PLAYING, GamePhase.GAME_OVER -> GameScreen(
            state = state,
            onSquareTapped = viewModel::onSquareTapped,
            onUndo = viewModel::undoMove,
            onHint = viewModel::requestHint,
            onWhyTapped = viewModel::toggleMoveWhy,
            onResign = viewModel::resign,
            onNewGame = viewModel::backToSetup
        )
    }
}
