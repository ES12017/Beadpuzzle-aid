package com.beadpixel.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val mode = SettingsRepository.themeMode(this)
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                SettingsRepository.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                SettingsRepository.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
