package com.raichess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.raichess.ui.game.GamePhase
import com.raichess.ui.game.GameScreen
import com.raichess.ui.game.GameViewModel
import com.raichess.ui.home.HomeScreen
import com.raichess.ui.theme.RaiChessTheme

/**
 * Main Activity for RaiChess (来Chess)
 * Entry point for the application
 *
 * @version 1.0.0-alpha
 */
class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RaiChessTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RaiChessApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun RaiChessApp(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()

    when (state.phase) {
        GamePhase.SETUP -> HomeScreen(
            stats = state.playerStats,
            opponentElo = state.opponentElo,
            playerColor = state.playerColor,
            onOpponentEloChanged = viewModel::setOpponentElo,
            onPlayerColorChanged = viewModel::setPlayerColor,
            onStartGame = viewModel::startGame
        )

        GamePhase.PLAYING, GamePhase.GAME_OVER -> GameScreen(
            state = state,
            onSquareTapped = viewModel::onSquareTapped,
            onResign = viewModel::resign,
            onNewGame = viewModel::backToSetup
        )
    }
}
