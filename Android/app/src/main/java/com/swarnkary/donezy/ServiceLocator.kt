package com.swarnkary.donezy

import android.content.Context

/**
 * Single source of truth for app-wide singletons. Plain object — Hilt/Dagger would be
 * overkill for the size of this app, and the previous code created multiple
 * `HobbyRepository` instances which silently broke UI updates from background work.
 */
object ServiceLocator {
    @Volatile private var repository: HobbyRepository? = null
    @Volatile private var themePreference: ThemePreference? = null
    @Volatile private var settingsPreferences: SettingsPreferences? = null

    fun repository(context: Context): HobbyRepository =
        repository ?: synchronized(this) {
            repository ?: HobbyRepository(AppDatabase.get(context)).also { repository = it }
        }

    fun themePreference(context: Context): ThemePreference =
        themePreference ?: synchronized(this) {
            themePreference ?: ThemePreference(context.applicationContext).also { themePreference = it }
        }

    fun settingsPreferences(context: Context): SettingsPreferences =
        settingsPreferences ?: synchronized(this) {
            settingsPreferences ?: SettingsPreferences(context.applicationContext)
                .also { settingsPreferences = it }
        }
}
