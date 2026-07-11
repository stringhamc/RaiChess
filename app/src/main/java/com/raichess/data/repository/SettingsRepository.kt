package com.raichess.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * UI preferences. Kept separate from PlayerProfileRepository because the
 * profile data is destined for the Room migration while these remain
 * simple preferences.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Move animations are an explicit opt-in: the brand default is
     * instant transitions (see BRANDING.md).
     */
    var animationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANIMATIONS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ANIMATIONS, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "raichess_settings"
        private const val KEY_ANIMATIONS = "animations_enabled"
    }
}
