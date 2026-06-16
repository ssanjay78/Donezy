package com.swarnkary.donezy

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val key: String, val label: String) {
    System("system", "System"),
    Light("light",   "Light"),
    Dark("dark",     "Dark");

    companion object {
        fun from(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: System
    }
}

class ThemePreference(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("hobby_log_prefs", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(ThemeMode.from(prefs.getString(KEY_THEME, null)))
    val mode: StateFlow<ThemeMode> = _mode

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.key).apply()
        _mode.value = mode
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
    }
}
