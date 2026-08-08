package com.raichess.ui.coach

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raichess.data.repository.DailyRepository
import com.raichess.data.repository.GameRepository
import com.raichess.data.repository.LessonRepository
import com.raichess.data.repository.PlayerProfileRepository
import com.raichess.domain.model.GameResult
import com.raichess.domain.model.PlayerColor
import com.raichess.domain.model.TrainingLoad
import com.raichess.domain.model.TrainingStatus
import com.raichess.domain.usecase.CoachAdvisor
import com.raichess.domain.usecase.Curriculum
import com.raichess.domain.usecase.LessonPlanner
import com.raichess.domain.usecase.WeaknessProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One lesson-plan row on the coach screen. */
data class CoachPlanRow(
    val id: String,
    val title: String,
    val description: String,
    /** "3 of 8" */
    val progressText: String,
    val done: Boolean,
    /** The unit the next lesson session opens. */
    val active: Boolean
)

/** One curriculum step on the coach screen's ladder. */
data class CoachStepRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val doneUnits: Int,
    val totalUnits: Int,
    val done: Boolean,
    /** The step the plan currently draws its units from. */
    val active: Boolean
)

data class CoachUiState(
    val loading: Boolean = true,
    val headline: String = "",
    val detail: String = "",
    val focuses: List<String> = emptyList(),
    val action: CoachAdvisor.Action = CoachAdvisor.Action.PLAY_GAME,
    val actionLabel: String = "",
    val steps: List<CoachStepRow> = emptyList(),
    val planRows: List<CoachPlanRow> = emptyList(),
    /** The coach's read on recent training load (null = no history). */
    val trainingStatus: TrainingStatus? = null,
    /** Games finished + drills solved today, toward [dailyGoal]. */
    val dailySolved: Int = 0,
    val dailyGoal: Int = TrainingLoad.DAILY_GOAL
)

/**
 * Assembles the coach's view: advice (CoachAdvisor over stats + weakness
 * profile) and the lesson plan with progress. Recomputed on every
 * [refresh] — the underlying data changes after each game and drill.
 */
class CoachViewModel(application: Application) : AndroidViewModel(application) {

    private val gameRepository = GameRepository(application)
    private val profileRepository = PlayerProfileRepository(application)
    private val lessonRepository = LessonRepository(application)
    private val dailyRepository = DailyRepository(application)

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState

    // No refresh() in init: every screen that shows coach state refreshes
    // on entry (MainActivity LaunchedEffect), and the first entry is the
    // home screen itself — an init call would just compute everything twice
    private var refreshJob: Job? = null

    fun refresh() {
        // A newer refresh owns the state; racing loads would finish
        // last-write-wins otherwise
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val profile = try {
                gameRepository.weaknessProfile()
            } catch (e: Exception) {
                Log.w(TAG, "weakness profile unavailable", e)
                WeaknessProfile.EMPTY
            }
            val stats = profileRepository.getStats()
            val practiceRating = profileRepository.getPracticeRating()
            val solves = try {
                lessonRepository.getSolves()
            } catch (e: Exception) {
                Log.w(TAG, "lesson progress unavailable", e)
                emptyMap()
            }
            val plan = LessonPlanner.buildPlan(profile, practiceRating, solves)
            val activeStepId = Curriculum.activeStep(practiceRating, solves).id
            val trainingStatus = dailyRepository.trainingStatus()
            val lastGameWasLoss = try {
                gameRepository.recentGames(1).firstOrNull()?.let { game ->
                    val color = runCatching { PlayerColor.valueOf(game.playerColor) }
                        .getOrDefault(PlayerColor.WHITE)
                    GameResult.fromPgnResult(game.result, color) == GameResult.LOSS
                } ?: false
            } catch (e: Exception) {
                Log.w(TAG, "last game unavailable", e)
                false
            }
            val advice = CoachAdvisor.advise(
                stats, profile, plan, solves, lastGameWasLoss, trainingStatus
            )
            val active = LessonPlanner.activeLesson(plan, solves)

            _uiState.value = CoachUiState(
                loading = false,
                headline = advice.headline,
                detail = advice.detail,
                focuses = advice.focuses,
                action = advice.action,
                actionLabel = advice.actionLabel,
                steps = Curriculum.STEPS.map { step ->
                    val doneUnits = step.units.count {
                        (solves[it.id] ?: 0) >= it.targetSolves
                    }
                    CoachStepRow(
                        id = step.id,
                        title = step.title,
                        subtitle = step.subtitle,
                        doneUnits = doneUnits,
                        totalUnits = step.units.size,
                        done = doneUnits == step.units.size,
                        active = step.id == activeStepId
                    )
                },
                planRows = plan.map { lesson ->
                    val done = (solves[lesson.id] ?: 0).coerceAtMost(lesson.targetSolves)
                    CoachPlanRow(
                        id = lesson.id,
                        title = lesson.title,
                        description = lesson.description,
                        progressText = "$done of ${lesson.targetSolves}",
                        done = done >= lesson.targetSolves,
                        active = lesson.id == active?.id
                    )
                },
                trainingStatus = trainingStatus,
                dailySolved = dailyRepository.countToday()
            )
        }
    }

    companion object {
        private const val TAG = "CoachViewModel"
    }
}
