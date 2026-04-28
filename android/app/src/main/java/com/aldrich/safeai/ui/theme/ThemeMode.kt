package com.aldrich.safeai.ui.theme

import com.aldrich.safeai.R

enum class ThemeMode(
    val preferenceValue: String,
    val labelRes: Int,
) {
    SYSTEM("system", R.string.setup_theme_mode_system),
    LIGHT("light", R.string.setup_theme_mode_light),
    DARK("dark", R.string.setup_theme_mode_dark),
    ;

    companion object {
        fun fromPreferenceValue(rawValue: String?): ThemeMode {
            return entries.firstOrNull { it.preferenceValue == rawValue } ?: SYSTEM
        }
    }
}