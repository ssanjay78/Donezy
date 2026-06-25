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

    // Opt-in "guaranteed delivery" mode: runs a persistent foreground service (with a permanent
    // minimized notification) so aggressive OEMs (Samsung, Xiaomi, …) can't kill the process on
    // swipe-away and drop the scheduled alarms. Off by default — most users don't need the
    // permanent notification, and it's only reliable workaround on hostile OEMs.
    private val _keepAliveEnabled = MutableStateFlow(prefs.getBoolean(KEY_KEEP_ALIVE, false))
    val keepAliveEnabled: StateFlow<Boolean> = _keepAliveEnabled

    private val _customSoundUri = MutableStateFlow(prefs.getString(KEY_CUSTOM_SOUND, null))
    val customSoundUri: StateFlow<String?> = _customSoundUri

    private val _playbackDurationSeconds = MutableStateFlow(prefs.getInt(KEY_PLAYBACK_DURATION, 3)) // default 3 seconds
    val playbackDurationSeconds: StateFlow<Int> = _playbackDurationSeconds

    val onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

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

    fun setKeepAlive(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_ALIVE, enabled).apply()
        _keepAliveEnabled.value = enabled
    }

    fun setCustomSound(uri: String?) {
        prefs.edit().putString(KEY_CUSTOM_SOUND, uri).apply()
        _customSoundUri.value = uri
    }

    fun setPlaybackDuration(seconds: Int) {
        prefs.edit().putInt(KEY_PLAYBACK_DURATION, seconds).apply()
        _playbackDurationSeconds.value = seconds
    }

    fun setOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }

    private companion object {
        const val KEY_SOUND                = "notif_sound"
        const val KEY_VIBRATE              = "notif_vibrate"
        const val KEY_STREAK_RESCUE        = "streak_rescue"
        const val KEY_KEEP_ALIVE           = "keep_alive_service"
        const val KEY_CUSTOM_SOUND         = "custom_sound_uri"
        const val KEY_PLAYBACK_DURATION    = "playback_duration_seconds"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}
