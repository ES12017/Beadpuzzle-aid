package com.beadpixel.app

import android.content.Context

object SettingsRepository {
    private const val PREFS = "beadpixel_settings"

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    const val CODE_AUTO = 0
    const val CODE_ALWAYS = 1
    const val CODE_OFF = 2

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun themeMode(c: Context): Int = prefs(c).getInt("theme_mode", THEME_LIGHT)
    fun setThemeMode(c: Context, v: Int) { prefs(c).edit().putInt("theme_mode", v).apply() }

    fun codeDisplayMode(c: Context): Int = prefs(c).getInt("code_display", CODE_AUTO)
    fun setCodeDisplayMode(c: Context, v: Int) { prefs(c).edit().putInt("code_display", v).apply() }

    fun autoCollapseOnDraw(c: Context): Boolean = prefs(c).getBoolean("auto_collapse", true)
    fun setAutoCollapseOnDraw(c: Context, v: Boolean) { prefs(c).edit().putBoolean("auto_collapse", v).apply() }

    fun showGrid(c: Context): Boolean = prefs(c).getBoolean("show_grid", true)
    fun setShowGrid(c: Context, v: Boolean) { prefs(c).edit().putBoolean("show_grid", v).apply() }

    fun checkerBackground(c: Context): Boolean = prefs(c).getBoolean("checker", true)
    fun setCheckerBackground(c: Context, v: Boolean) { prefs(c).edit().putBoolean("checker", v).apply() }

    fun showZoomInfo(c: Context): Boolean = prefs(c).getBoolean("show_zoom_info", true)
    fun setShowZoomInfo(c: Context, v: Boolean) { prefs(c).edit().putBoolean("show_zoom_info", v).apply() }

    fun undoLimit(c: Context): Int = prefs(c).getInt("undo_limit", 60)
    fun setUndoLimit(c: Context, v: Int) { prefs(c).edit().putInt("undo_limit", v).apply() }

    fun majorGridThresholdDp(c: Context): Int = prefs(c).getInt("major_grid", 8)
    fun setMajorGridThresholdDp(c: Context, v: Int) { prefs(c).edit().putInt("major_grid", v).apply() }

    fun minorGridThresholdDp(c: Context): Int = prefs(c).getInt("minor_grid", 20)
    fun setMinorGridThresholdDp(c: Context, v: Int) { prefs(c).edit().putInt("minor_grid", v).apply() }

    fun doubleTapToFit(c: Context): Boolean = prefs(c).getBoolean("double_tap_fit", false)
    fun setDoubleTapToFit(c: Context, v: Boolean) { prefs(c).edit().putBoolean("double_tap_fit", v).apply() }

    fun exportToGallery(c: Context): Boolean = prefs(c).getBoolean("export_gallery", true)
    fun setExportToGallery(c: Context, v: Boolean) { prefs(c).edit().putBoolean("export_gallery", v).apply() }

    fun reset(c: Context) {
        prefs(c).edit().clear().apply()
    }

    fun disclaimerAccepted(c: Context): Boolean = prefs(c).getBoolean("disclaimer", false)
    fun setDisclaimerAccepted(c: Context, v: Boolean) { prefs(c).edit().putBoolean("disclaimer", v).apply() }
}
