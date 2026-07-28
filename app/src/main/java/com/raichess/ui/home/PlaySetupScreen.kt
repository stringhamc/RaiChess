package com.raichess.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raichess.data.engine.RaiEngine
import com.raichess.domain.model.EloCalculator
import com.raichess.domain.model.EloStats
import com.raichess.domain.model.GameMode
import com.raichess.domain.model.PlayerColor
import com.raichess.ui.components.RaiScreen
import com.raichess.ui.components.Section
import com.raichess.ui.components.SegmentedControl
import com.raichess.ui.components.TickLabel
import com.raichess.ui.theme.ChessColors
import kotlin.math.roundToInt

/**
 * Game setup: mode, opponent strength, color, and start buttons. Split
 * out of the home screen when it became a launcher — this is what the
 * Play tile opens.
 */
@Composable
fun PlaySetupScreen(
    stats: EloStats?,
    opponentElo: Int,
    playerColor: PlayerColor,
    gameMode: GameMode,
    onOpponentEloChanged: (Int) -> Unit,
    onPlayerColorChanged: (PlayerColor) -> Unit,
    onGameModeChanged: (GameMode) -> Unit,
    onStartGame: (randomColor: Boolean) -> Unit,
    onBack: () -> Unit
) {
    RaiScreen(title = "Play", onBack = onBack) {
        if (stats != null) {
            Text(
                text = "You: ${stats.currentElo}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        // Mode
        Section(label = "Mode") {
            SegmentedControl(
                options = listOf("Rated", "Training"),
                selectedIndex = if (gameMode == GameMode.TRAINING) 1 else 0,
                onSelect = { onGameModeChanged(if (it == 1) GameMode.TRAINING else GameMode.RATED) }
            )
            Text(
                text = if (gameMode == GameMode.TRAINING) {
                    "Undo and hints allowed — each use shrinks ELO gains"
                } else {
                    "No takebacks — full ELO stakes"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Opponent strength
        Section(label = "Opponent") {
            Text(
                text = "$opponentElo ELO",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Slider(
                value = opponentElo.toFloat(),
                onValueChange = { onOpponentEloChanged((it / 50f).roundToInt() * 50) },
                valueRange = RaiEngine.MIN_ELO.toFloat()..RaiEngine.MAX_ELO.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = ChessColors.ControlActive,
                    activeTrackColor = ChessColors.ControlActive,
                    inactiveTrackColor = ChessColors.SliderInactiveTrack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TickLabel("${RaiEngine.MIN_ELO}")
                TickLabel("1600")
                TickLabel("${RaiEngine.MAX_ELO}")
            }
            // During placement the opponent auto-tracks the fast-moving
            // provisional rating after each game (manual picks still apply
            // to the next game only)
            if (stats != null && stats.gamesPlayed < EloCalculator.PROVISIONAL_GAMES) {
                Text(
                    text = "Calibrating (game ${stats.gamesPlayed + 1} of " +
                        "${EloCalculator.PROVISIONAL_GAMES}): the opponent adapts " +
                        "to your results",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Color
        Section(label = "Play as") {
            SegmentedControl(
                options = listOf("White ♔", "Black ♚"),
                selectedIndex = if (playerColor == PlayerColor.BLACK) 1 else 0,
                onSelect = {
                    onPlayerColorChanged(if (it == 1) PlayerColor.BLACK else PlayerColor.WHITE)
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onStartGame(false) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Game")
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = { onStartGame(true) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Random Color")
        }
    }
}
