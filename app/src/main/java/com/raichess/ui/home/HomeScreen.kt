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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.raichess.data.engine.RaiEngine
import com.raichess.domain.model.EloStats
import com.raichess.domain.model.PlayerColor
import kotlin.math.roundToInt

/**
 * Home / new-game setup screen: player rating summary, opponent
 * strength selection, color choice, and start button.
 */
@Composable
fun HomeScreen(
    stats: EloStats?,
    opponentElo: Int,
    playerColor: PlayerColor,
    onOpponentEloChanged: (Int) -> Unit,
    onPlayerColorChanged: (PlayerColor) -> Unit,
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
            Text(
                text = "${stats.wins}W · ${stats.draws}D · ${stats.losses}L",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

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
            ColorChoiceButton("White ♔", playerColor == PlayerColor.WHITE) {
                onPlayerColorChanged(PlayerColor.WHITE)
            }
            ColorChoiceButton("Black ♚", playerColor == PlayerColor.BLACK) {
                onPlayerColorChanged(PlayerColor.BLACK)
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

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
private fun ColorChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}
