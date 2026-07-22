package com.raichess.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raichess.BuildConfig
import com.raichess.data.diagnostics.EngineDiagnostics
import com.raichess.data.engine.RaiEngine
import com.raichess.domain.model.EloStats
import com.raichess.domain.model.GameMode
import com.raichess.domain.model.PlayerColor
import com.raichess.ui.components.RaiLogo
import com.raichess.ui.theme.ChessColors
import kotlin.math.roundToInt

/**
 * Home / new-game setup screen: brand lockup, player rating summary, opponent
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
    onStartGame: (randomColor: Boolean) -> Unit,
    onPractice: () -> Unit = {},
    onReview: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Brand lockup
        RaiLogo(size = 74.dp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "RAICHESS",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "来チェス",
            style = MaterialTheme.typography.bodyMedium,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Rating hero
        if (stats != null) {
            Text(
                text = "${stats.currentElo}",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            val confidence =
                if (stats.confidenceInterval > 0) "  ±${stats.confidenceInterval}" else ""
            SectionLabel(
                text = "Your rating$confidence",
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            RecordRow(
                wins = stats.wins,
                draws = stats.draws,
                losses = stats.losses
            )
            // Positive progress framing: personal best and streak, not a
            // running tally of mistakes (undos stay tracked internally)
            if (stats.gamesPlayed > 0) {
                val progressBits = buildList {
                    add("Peak ${stats.peakElo}")
                    if (stats.winStreak >= 2) add("${stats.winStreak}-game win streak")
                }
                Text(
                    text = progressBits.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Mode
        Section(label = "Mode") {
            SegmentedControl(
                options = listOf("Rated", "Training"),
                selectedIndex = if (gameMode == GameMode.TRAINING) 1 else 0,
                onSelect = { onGameModeChanged(if (it == 1) GameMode.TRAINING else GameMode.RATED) }
            )
            Text(
                text = if (gameMode == GameMode.TRAINING) {
                    "Undo allowed — undos are tracked and reduce ELO gains"
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

        Spacer(modifier = Modifier.height(26.dp))

        // Animation toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                SectionLabel(text = "Move animation")
                Text(
                    text = "150 ms slide · on by default",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Switch(
                checked = animationsEnabled,
                onCheckedChange = onAnimationsChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ChessColors.ControlActive,
                    checkedTrackColor = ChessColors.ControlTrackActive,
                    uncheckedThumbColor = ChessColors.ControlThumbInactive,
                    uncheckedTrackColor = ChessColors.ControlTrackInactive
                )
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
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onPractice,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Practice")
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onReview,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Review Last Game")
        }

        Spacer(modifier = Modifier.height(20.dp))
        // Visible build marker so it's unambiguous which APK is installed.
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.secondary
        )
        EngineLogRow()
    }
}

/**
 * Debug affordance: opens the persisted engine event log (see
 * EngineDiagnostics) so "why did my game fall back to RaiEngine?" is
 * answerable from the device, without logcat.
 */
@Composable
private fun EngineLogRow() {
    var showLog by remember { mutableStateOf(false) }
    TextButton(onClick = { showLog = true }) {
        Text(
            text = "Engine log",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
    if (showLog) {
        val context = LocalContext.current
        // Newest first — the event being debugged is usually the last one
        val entries = remember { EngineDiagnostics.entries(context).reversed() }
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("Engine log") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (entries.isEmpty()) {
                        Text(
                            "No engine events recorded yet.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    entries.forEach { entry ->
                        Text(
                            text = entry,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLog = false }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = {
                    EngineDiagnostics.clear(context)
                    showLog = false
                }) { Text("Clear") }
            }
        )
    }
}

/** A labelled section: an uppercase eyebrow above its content. */
@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(text = label, modifier = Modifier.padding(bottom = 11.dp))
        content()
    }
}

/** Uppercase, wide-tracked eyebrow label. */
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
        letterSpacing = 2.sp,
        modifier = modifier
    )
}

@Composable
private fun TickLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary
    )
}

/** Two-or-more option segmented control; the selected cell is filled. */
@Composable
private fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .border(1.dp, ChessColors.SquareBorder.copy(alpha = 0.35f), shape)
    ) {
        options.forEachIndexed { index, label ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(ChessColors.SquareBorder.copy(alpha = 0.35f))
                )
            }
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (selected) ChessColors.ControlActive else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) Color.Black else MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

/** Framed Won / Drew / Lost record. */
@Composable
private fun RecordRow(
    wins: Int,
    draws: Int,
    losses: Int,
    modifier: Modifier = Modifier
) {
    val line = ChessColors.SquareBorder.copy(alpha = 0.35f)
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .border(1.dp, line, shape)
    ) {
        RecordCell(label = "Won", value = wins, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(line)
        )
        RecordCell(label = "Drew", value = draws, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(line)
        )
        RecordCell(label = "Lost", value = losses, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RecordCell(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$value",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}
