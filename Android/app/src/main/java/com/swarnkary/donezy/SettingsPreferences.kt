package com.swarnkary.donezy

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("hobby_log_prefs", Context.MODE_PRIVATE)

    private val _soundEnabled   = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled

    private val _vibrateEnabled = MutableStateFlow(prefs.getBoolean(KEY_VIBRATE, true))
    val vibrateEnabled: StateFlow<Boolean> = _vibrateEnabled

    private val _streakRescueEnabled = MutableStateFlow(prefs.getBoolean(KEY_STREAK_RESCUE, true))
    val streakRescueEnabled: StateFlow<Boolean> = _streakRescueEnabled

    fun setSound(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
        _soundEnabled.value = enabled
    }

    fun setVibrate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATE, enabled).apply()
        _vibrateEnabled.value = enabled
    }

    fun setStreakRescue(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STREAK_RESCUE, enabled).apply()
        _streakRescueEnabled.value = enabled
    }

    private companion object {
        const val KEY_SOUND          = "notif_sound"
        const val KEY_VIBRATE        = "notif_vibrate"
        const val KEY_STREAK_RESCUE  = "streak_rescue"
    }
}
