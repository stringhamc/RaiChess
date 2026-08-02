package com.raichess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
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
        enableEdgeToEdge()
        // Finish any post-game analysis a previous process didn't complete
        // (fire-and-forget; no-op when nothing is pending)
        AnalysisCoordinator.analyzePendingGames(applicationContext)
        setContent {
            RaiChessTheme {
                // The Surface paints the whole window (incl. behind the
                // system bars) black; content itself stays inside the safe
                // drawing insets so nothing hides under bars or the gesture
                // nav pill
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        RaiChessApp()
                    }
                }
            }
        }
    }
}

/**
 * The app's destinations. Exactly one is active — replacing the previous
 * pile of independent boolean flags whose priority depended on statement
 * order. An active game (GamePhase != SETUP) overlays whatever screen is
 * set; [Screen] is where the player returns when it ends.
 */
sealed interface Screen {
    data object Home : Screen
    data object PlaySetup : Screen
    data class Practice(val openLesson: Boolean = false) : Screen
    data object Coach : Screen
    data object Games : Screen
    data class Review(val gameId: Long) : Screen
    data object Settings : Screen

    companion object {
        /** The just-finished game (still saving/analyzing) in [Review]. */
        const val LATEST_GAME = -1L

        /** Bundle codec for rememberSaveable. */
        val Saver = listSaver<Screen, Any>(
            save = { screen ->
                when (screen) {
                    Home -> listOf("home")
                    PlaySetup -> listOf("play")
                    is Practice -> listOf("practice", screen.openLesson)
                    Coach -> listOf("coach")
                    Games -> listOf("games")
                    is Review -> listOf("review", screen.gameId)
                    Settings -> listOf("settings")
                }
            },
            restore = { saved ->
                when (saved.firstOrNull()) {
                    "play" -> PlaySetup
                    "practice" -> Practice(saved.getOrNull(1) as? Boolean ?: false)
                    "coach" -> Coach
                    "games" -> Games
                    "review" -> Review(saved.getOrNull(1) as? Long ?: LATEST_GAME)
                    "settings" -> Settings
                    else -> Home
                }
            }
        )
    }
}

@Composable
fun RaiChessApp(viewModel: GameViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var screen: Screen by rememberSaveable(stateSaver = Screen.Saver) {
        mutableStateOf(Screen.Home)
    }
    var showAbandonConfirm by remember { mutableStateOf(false) }

    // System back mirrors the on-screen Back everywhere instead of killing
    // the Activity; mid-game it asks first, since leaving is a resignation
    BackHandler(enabled = state.phase != GamePhase.SETUP || screen != Screen.Home) {
        when {
            state.phase == GamePhase.PLAYING -> showAbandonConfirm = true
            state.phase == GamePhase.GAME_OVER -> viewModel.backToSetup()
            screen is Screen.Review -> screen = Screen.Games
            else -> screen = Screen.Home
        }
    }

    // An active game overlays whatever destination is set
    if (state.phase != GamePhase.SETUP) {
        GameScreen(
            state = state,
            onSquareTapped = viewModel::onSquareTapped,
            onUndo = viewModel::undoMove,
            onHint = viewModel::requestHint,
            onWhyTapped = viewModel::toggleMoveWhy,
            onResign = viewModel::resign,
            onNewGame = {
                screen = Screen.Home
                viewModel.backToSetup()
            },
            onPlayAgain = { viewModel.startGame(false) },
            onReviewGame = {
                screen = Screen.Review(Screen.LATEST_GAME)
                viewModel.backToSetup()
            }
        )
        if (showAbandonConfirm && state.phase == GamePhase.PLAYING) {
            AlertDialog(
                onDismissRequest = { showAbandonConfirm = false },
                title = { Text("Leave the game?") },
                text = { Text("Leaving counts as a resignation.") },
                confirmButton = {
                    TextButton(onClick = {
                        showAbandonConfirm = false
                        viewModel.resign()
                    }) { Text("Resign") }
                },
                dismissButton = {
                    TextButton(onClick = { showAbandonConfirm = false }) {
                        Text("Keep playing")
                    }
                }
            )
        }
        return
    }

    when (val current = screen) {
        Screen.Home -> {
            val coachViewModel: CoachViewModel = viewModel()
            val coachState by coachViewModel.uiState.collectAsState()
            // Keep the Coach tile's one-liner current with the latest games
            LaunchedEffect(Unit) { coachViewModel.refresh() }
            HomeScreen(
                stats = state.playerStats,
                coachLine = coachState.headline.takeIf { !coachState.loading },
                opponentElo = state.opponentElo,
                gameMode = state.gameMode,
                trainingStatus = coachState.trainingStatus,
                dailySolved = coachState.dailySolved,
                dailyGoal = coachState.dailyGoal,
                // Straight into the game with the current setup; the tile's
                // corner button is the path to the setup screen
                onPlay = { viewModel.startGame(false) },
                onCustomizeGame = { screen = Screen.PlaySetup },
                onTrain = { screen = Screen.Practice() },
                onCoach = { screen = Screen.Coach },
                onReview = { screen = Screen.Games },
                onSettings = { screen = Screen.Settings }
            )
        }

        Screen.PlaySetup -> PlaySetupScreen(
            stats = state.playerStats,
            opponentElo = state.opponentElo,
            playerColor = state.playerColor,
            gameMode = state.gameMode,
            onOpponentEloChanged = viewModel::setOpponentElo,
            onPlayerColorChanged = viewModel::setPlayerColor,
            onGameModeChanged = viewModel::setGameMode,
            // Land on home when the game ends, not back on this form
            onStartGame = { random ->
                screen = Screen.Home
                viewModel.startGame(random)
            },
            onBack = { screen = Screen.Home }
        )

        is Screen.Practice -> {
            val practiceViewModel: PracticeViewModel = viewModel()
            val practiceState by practiceViewModel.uiState.collectAsState()
            // Coach deep-link: open on the lesson queue instead of Mixed.
            // Keyed on the screen value; setSource self-guards re-fires.
            LaunchedEffect(current) {
                if (current.openLesson) {
                    practiceViewModel.setSource(DrillSelector.Source.LESSON)
                }
            }
            PracticeScreen(
                state = practiceState,
                onSquareTapped = practiceViewModel::onSquareTapped,
                onSourceChanged = practiceViewModel::setSource,
                onNext = practiceViewModel::nextDrill,
                onBack = { screen = Screen.Home }
            )
        }

        Screen.Coach -> {
            val coachViewModel: CoachViewModel = viewModel()
            val coachState by coachViewModel.uiState.collectAsState()
            // Recompute on every entry: games and drills since the last
            // visit change the advice
            LaunchedEffect(Unit) { coachViewModel.refresh() }
            CoachScreen(
                state = coachState,
                onAction = { action ->
                    when (action) {
                        // Same one-tap philosophy as the Play tile: the
                        // coach's "play a game" starts one, not a form
                        CoachAdvisor.Action.PLAY_GAME -> {
                            screen = Screen.Home
                            viewModel.startGame(false)
                        }
                        CoachAdvisor.Action.START_LESSON ->
                            screen = Screen.Practice(openLesson = true)
                        CoachAdvisor.Action.REVIEW_GAMES ->
                            screen = Screen.Review(Screen.LATEST_GAME)
                    }
                },
                onBack = { screen = Screen.Home }
            )
        }

        Screen.Games -> {
            val gamesViewModel: GamesViewModel = viewModel()
            val gamesState by gamesViewModel.uiState.collectAsState()
            LaunchedEffect(Unit) { gamesViewModel.refresh() }
            GamesScreen(
                state = gamesState,
                onOpenGame = { screen = Screen.Review(it) },
                onBack = { screen = Screen.Home }
            )
        }

        is Screen.Review -> {
            val reviewViewModel: ReviewViewModel = viewModel()
            val reviewState by reviewViewModel.uiState.collectAsState()
            // Reload on every entry and id change: the Activity-scoped
            // ViewModel would otherwise keep showing a previously loaded
            // game. LATEST_GAME loads the newest game, polling while its
            // analysis lands.
            LaunchedEffect(current) {
                reviewViewModel.load(current.gameId.takeIf { it >= 0 })
            }
            ReviewScreen(
                state = reviewState,
                onPrevious = reviewViewModel::previous,
                onNext = reviewViewModel::next,
                onBack = { screen = Screen.Games }
            )
        }

        Screen.Settings -> SettingsScreen(
            stats = state.playerStats,
            animationsEnabled = state.animationsEnabled,
            onAnimationsChanged = viewModel::setAnimationsEnabled,
            onBack = { screen = Screen.Home }
        )
    }
}
