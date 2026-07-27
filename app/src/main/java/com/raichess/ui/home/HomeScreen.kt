package com.raichess.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raichess.BuildConfig
import com.raichess.domain.model.EloStats
import com.raichess.domain.model.GameMode
import com.raichess.ui.components.RaiLogo
import com.raichess.ui.components.SectionLabel
import com.raichess.ui.theme.ChessColors

/**
 * Home launcher: brand lockup, rating hero, and a quad of destination
 * tiles (Play / Train / Coach / Review) with a Settings row beneath.
 * The Play tile starts a game immediately with the current setup (shown
 * in its subtitle); its corner "custom" button opens PlaySetupScreen.
 * Toggles and diagnostics live in SettingsScreen — home stays minimal
 * on purpose.
 */
@Composable
fun HomeScreen(
    stats: EloStats?,
    /** Coach tile subtitle — the coach's current headline, when loaded. */
    coachLine: String?,
    /** Current setup, shown on the Play tile so one-tap play is no surprise. */
    opponentElo: Int,
    gameMode: GameMode,
    onPlay: () -> Unit,
    onCustomizeGame: () -> Unit,
    onTrain: () -> Unit,
    onCoach: () -> Unit,
    onReview: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Brand lockup
        RaiLogo(size = 64.dp)
        Spacer(modifier = Modifier.height(12.dp))
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

        Spacer(modifier = Modifier.height(28.dp))

        // Rating hero — compact; the full record lives in Settings
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

        Spacer(modifier = Modifier.height(28.dp))

        // Destination quad
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeTile(
                glyph = "♟",
                title = "Play",
                subtitle = "vs $opponentElo · " +
                    if (gameMode == GameMode.TRAINING) "Training" else "Rated",
                onClick = onPlay,
                cornerLabel = "CUSTOM",
                onCornerClick = onCustomizeGame,
                modifier = Modifier.weight(1f)
            )
            HomeTile(
                glyph = "♞",
                title = "Train",
                subtitle = "Puzzles & drills",
                onClick = onTrain,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeTile(
                glyph = "来",
                title = "Coach",
                subtitle = coachLine ?: "Your plan & focus",
                onClick = onCoach,
                modifier = Modifier.weight(1f)
            )
            HomeTile(
                glyph = "♜",
                title = "Review",
                subtitle = "Browse past games",
                onClick = onReview,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Settings row tile
        SettingsTile(onClick = onSettings)

        Spacer(modifier = Modifier.height(20.dp))
        // Visible build marker so it's unambiguous which APK is installed.
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

/**
 * One square destination tile: big glyph, title, short subtitle. The
 * optional corner label is a small secondary action (its own tap target,
 * nested inside the tile's) — used by Play for "customize this game".
 */
@Composable
private fun HomeTile(
    glyph: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerLabel: String? = null,
    onCornerClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .border(1.dp, ChessColors.SquareBorder.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = glyph,
                fontSize = 30.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            if (cornerLabel != null && onCornerClick != null) {
                Text(
                    text = cornerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCornerClick)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** Full-width bottom row tile for settings. */
@Composable
private fun SettingsTile(onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, ChessColors.SquareBorder.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Stats · animations · engine log",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = "⚙",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
