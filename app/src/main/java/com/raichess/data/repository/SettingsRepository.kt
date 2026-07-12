package com.raichess.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.raichess.domain.model.GameMode

/**
 * UI preferences. Kept separate from PlayerProfileRepository because the
 * profile data is destined for the Room migration while these remain
 * simple preferences.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Move animations are on by default; players can turn them off. */
    var animationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANIMATIONS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ANIMATIONS, value).apply()
        }

    /** Remembers the player's last-selected game mode across launches. */
    var gameMode: GameMode
        get() = when (prefs.getString(KEY_GAME_MODE, null)) {
            GameMode.TRAINING.name -> GameMode.TRAINING
            else -> GameMode.RATED
        }
        set(value) {
            prefs.edit().putString(KEY_GAME_MODE, value.name).apply()
        }

    companion object {
        private const val PREFS_NAME = "raichess_settings"
        private const val KEY_ANIMATIONS = "animations_enabled"
        private const val KEY_GAME_MODE = "game_mode"
    }
}
