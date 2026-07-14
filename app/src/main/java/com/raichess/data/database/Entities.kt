package com.raichess.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.raichess.domain.model.PracticeCategory
import com.raichess.domain.model.PracticePosition

/**
 * A finished game. The PGN is the canonical record; [movesLan] duplicates
 * the moves in raw LAN so analysis (and future re-analysis at higher depth)
 * can replay the game without a PGN parser.
 *
 * The row is written immediately at game over with [analysisState] PENDING;
 * the background analysis pass later fills [accuracy] and flips the state.
 * PENDING rows surviving an app restart are picked up by the next analysis
 * sweep — nothing is lost if analysis is interrupted.
 */
@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pgn: String,
    /** Space-separated lowercase LAN plies, in play order. */
    val movesLan: String,
    val datePlayed: Long,
    /** PGN result tag: "1-0", "0-1", "1/2-1/2". */
    val result: String,
    /** [com.raichess.domain.model.PlayerColor] name. */
    val playerColor: String,
    val opponentElo: Int,
    val playerEloBefore: Int,
    val playerEloAfter: Int,
    /** [com.raichess.domain.model.GameMode] name. */
    val gameMode: String,
    /** Training-mode undos used in this game. */
    val undoCount: Int,
    /** Player accuracy 0–100 from post-game analysis, null until analyzed. */
    val accuracy: Double?,
    /** One of [AnalysisState]'s constants. */
    val analysisState: String,
    /** Reserved for the opening-recognition phase. */
    val openingName: String? = null,
    val createdAt: Long
)

/** Values for [GameEntity.analysisState]. */
object AnalysisState {
    const val PENDING = "PENDING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
}

/**
 * One analyzed ply of a stored game: the position as it stood before the
 * move, the engine's verdict, and — for the player's own moves — how much
 * the move cost. These rows are the immutable observations that the
 * weakness profile and practice selection are derived from; interpretation
 * (themes, profiles) can always be recomputed from them.
 */
@Entity(
    tableName = "positions",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("gameId")]
)
data class PositionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    /** 0-based ply index; White played the even plies. */
    val ply: Int,
    /** FEN before the move was played. */
    val fen: String,
    /** Eval of [fen] in centipawns from White's perspective, mate-capped. */
    val evaluationCp: Int,
    /** Engine's best move in LAN, null for terminal positions. */
    val bestMove: String?,
    /** The move actually played, LAN. */
    val movePlayed: String,
    val isPlayerMove: Boolean,
    /** Centipawns the move threw away; null for the AI's moves. */
    val centipawnLoss: Int?,
    /** [com.raichess.domain.model.MoveClassification] name; null for AI moves. */
    val classification: String?,
    /** Comma-separated theme tags (hanging_piece, missed_fork, ...); filled by the tagging phase. */
    val themes: String = "",
    /** Search depth the verdict came from. */
    val analysisDepth: Int,
    /** Version of the analysis pipeline that wrote this row, for re-analysis. */
    val analyzerVersion: Int
)

/**
 * Room row for a practice position — field-for-field the domain
 * [PracticePosition], which SharedPreferences previously persisted as JSON
 * (see PracticeRepository's legacy import).
 */
@Entity(tableName = "practice_positions")
data class PracticePositionEntity(
    @PrimaryKey val id: String,
    val sourceGameId: Long?,
    val sourceMoveNumber: Int,
    val fen: String,
    /** [PracticeCategory] name. */
    val category: String,
    val difficulty: Int,
    val timesPracticed: Int,
    val successRate: Double,
    val lastPracticed: Long?,
    val createdAt: Long
)

fun PracticePositionEntity.toDomain() = PracticePosition(
    id = id,
    sourceGameId = sourceGameId,
    sourceMoveNumber = sourceMoveNumber,
    fen = fen,
    category = runCatching { PracticeCategory.valueOf(category) }
        .getOrDefault(PracticeCategory.MISTAKE_CORRECTION),
    difficulty = difficulty,
    timesPracticed = timesPracticed,
    successRate = successRate,
    lastPracticed = lastPracticed,
    createdAt = createdAt
)

fun PracticePosition.toEntity() = PracticePositionEntity(
    id = id,
    sourceGameId = sourceGameId,
    sourceMoveNumber = sourceMoveNumber,
    fen = fen,
    category = category.name,
    difficulty = difficulty,
    timesPracticed = timesPracticed,
    successRate = successRate,
    lastPracticed = lastPracticed,
    createdAt = createdAt
)
