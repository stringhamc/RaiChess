package com.raichess.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.raichess.data.engine.RaiEngine
import com.raichess.domain.model.EloStats
import com.raichess.domain.model.GameMode
import com.raichess.domain.model.PlayerColor
import kotlin.math.roundToInt

/**
 * Home / new-game setup screen: player rating summary, opponent
 * strength selection, game mode, color choice, and start button.
 */
@Composable
fun HomeScreen(
    stats: EloStats?,
    opponentElo: Int,
    playerColor: PlayerColor,
    gameMode: GameMode,
    animationsEnabled: Boolean,
    onOpponentEloChanged: (Int) -> Unit,
    onPlayerColorChanged: (PlayerColor) -> Unit,
    onGameModeChanged: (GameMode) -> Unit,
    onAnimationsChanged: (Boolean) -> Unit,
    onStartGame: (randomColor: Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "RAICHESS",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "来Chess",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (stats != null) {
            Text(
                text = "${stats.currentElo}",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            val confidence =
                if (stats.confidenceInterval > 0) " ±${stats.confidenceInterval}" else ""
            Text(
                text = "Your ELO$confidence",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            val undoSuffix = if (stats.totalUndos > 0) " · ${stats.totalUndos} undos" else ""
            Text(
                text = "${stats.wins}W · ${stats.draws}D · ${stats.losses}L$undoSuffix",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Mode",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceButton("Rated", gameMode == GameMode.RATED) {
                onGameModeChanged(GameMode.RATED)
            }
            ChoiceButton("Training", gameMode == GameMode.TRAINING) {
                onGameModeChanged(GameMode.TRAINING)
            }
        }
        Text(
            text = if (gameMode == GameMode.TRAINING) {
                "Undo allowed — undos are tracked and reduce ELO gains"
            } else {
                "No takebacks — full ELO stakes"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Opponent: $opponentElo ELO",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Slider(
            value = opponentElo.toFloat(),
            onValueChange = { onOpponentEloChanged((it / 50f).roundToInt() * 50) },
            valueRange = RaiEngine.MIN_ELO.toFloat()..RaiEngine.MAX_ELO.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0xFF333333)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Play as",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceButton("White ♔", playerColor == PlayerColor.WHITE) {
                onPlayerColorChanged(PlayerColor.WHITE)
            }
            ChoiceButton("Black ♚", playerColor == PlayerColor.BLACK) {
                onPlayerColorChanged(PlayerColor.BLACK)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Move animation",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "150 ms slide · off by default",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Switch(
                checked = animationsEnabled,
                onCheckedChange = onAnimationsChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF555555),
                    uncheckedThumbColor = Color(0xFF888888),
                    uncheckedTrackColor = Color(0xFF222222)
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onStartGame(false) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Game")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onStartGame(true) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Random Color")
        }
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}
