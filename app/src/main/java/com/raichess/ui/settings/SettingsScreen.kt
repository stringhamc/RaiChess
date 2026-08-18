package com.raichess.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raichess.BuildConfig
import com.raichess.data.diagnostics.EngineDiagnostics
import com.raichess.domain.model.CoachPersonality
import com.raichess.domain.model.EloStats
import com.raichess.ui.components.RaiScreen
import com.raichess.ui.components.RecordRow
import com.raichess.ui.components.Section
import com.raichess.ui.components.SectionLabel
import com.raichess.ui.theme.ChessColors

/**
 * Settings & stats: the full game record, preference toggles, and the
 * engine diagnostics log. Everything the launcher home deliberately
 * doesn't show lives here.
 */
@Composable
fun SettingsScreen(
    stats: EloStats?,
    animationsEnabled: Boolean,
    onAnimationsChanged: (Boolean) -> Unit,
    boardColorized: Boolean,
    onBoardColorizedChanged: (Boolean) -> Unit,
    coachPersonality: CoachPersonality,
    onCoachPersonalityChanged: (CoachPersonality) -> Unit,
    onBack: () -> Unit
) {
    RaiScreen(title = "Settings", onBack = onBack) {
        if (stats != null) {
            Section(label = "Record") {
                RecordRow(
                    wins = stats.wins,
                    draws = stats.draws,
                    losses = stats.losses
                )
                val bits = buildList {
                    add("${stats.gamesPlayed} games")
                    add("Peak ${stats.peakElo}")
                    if (stats.winStreak >= 2) add("${stats.winStreak}-game win streak")
                    if (stats.confidenceInterval > 0) add("±${stats.confidenceInterval} confidence")
                }
                Text(
                    text = bits.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Spacer(modifier = Modifier.height(26.dp))
        }

        // Animation toggle
        SettingSwitchRow(
            title = "Move animation",
            subtitle = "150 ms slide · on by default",
            checked = animationsEnabled,
            onCheckedChange = onAnimationsChanged
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Colorized board toggle — both palettes keep the same luminance
        // ladder, so turning color off never loses information
        SettingSwitchRow(
            title = "Colorized board",
            subtitle = "Colorblind-safe hues, distinct brightness · off = pure grayscale",
            checked = boardColorized,
            onCheckedChange = onBoardColorizedChanged
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Coach style — delivery only: every style teaches the same chess
        CoachStyleRows(
            selected = coachPersonality,
            onSelected = onCoachPersonalityChanged
        )

        Spacer(modifier = Modifier.height(26.dp))

        EngineLogRow()

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

/**
 * Rai's delivery style: one radio row per [CoachPersonality]. The chess
 * the coach teaches is identical in every style — only the voice changes —
 * so this sits with the other cosmetic preferences.
 */
@Composable
private fun CoachStyleRows(
    selected: CoachPersonality,
    onSelected: (CoachPersonality) -> Unit
) {
    SectionLabel(text = "Coach style")
    CoachPersonality.entries.forEach { personality ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelected(personality) }
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The row is the single click target (onClick = null keeps the
            // radio from doubling up the row's accessibility semantics)
            RadioButton(
                selected = personality == selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = ChessColors.ControlActive,
                    unselectedColor = ChessColors.ControlThumbInactive
                )
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = personality.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = personality.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

/** One labeled preference switch, grayscale chrome as everywhere else. */
@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SectionLabel(text = title)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ChessColors.ControlActive,
                checkedTrackColor = ChessColors.ControlTrackActive,
                uncheckedThumbColor = ChessColors.ControlThumbInactive,
                uncheckedTrackColor = ChessColors.ControlTrackInactive
            )
        )
    }
}

/**
 * Deliberately shipped in ALL builds, not gated behind BuildConfig.DEBUG:
 * the point is that a player in the field can self-diagnose "why did my
 * game fall back to RaiEngine?" from the device, without logcat. Opens
 * the persisted engine event log (see EngineDiagnostics).
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
        val clipboard = LocalClipboardManager.current
        // Stored oldest-first; displayed newest-first (the event being
        // debugged is usually the last one), copied chronologically
        val stored = remember { EngineDiagnostics.entries(context) }
        val entries = remember(stored) { stored.reversed() }
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
                    } else {
                        Text(
                            "Tap a line to copy it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    entries.forEach { entry ->
                        Text(
                            text = entry,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .clickable { clipboard.setText(AnnotatedString(entry)) }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLog = false }) { Text("Close") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(stored.joinToString("\n")))
                    }) { Text("Copy all") }
                    TextButton(onClick = {
                        EngineDiagnostics.clear(context)
                        showLog = false
                    }) { Text("Clear") }
                }
            }
        )
    }
}
