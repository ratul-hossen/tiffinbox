package com.ratulhossen.tiffinbox.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * User-selectable color themes (Settings -> Theme). Enum names are what's
 * persisted, so never rename an existing entry - only add new ones.
 */
enum class ThemePalette {
    BLACK_RED,
    SUNSET_ORANGE,
    OCEAN_BLUE,
    CLASSIC_GREEN,
    /** Material You: colors taken from the phone's wallpaper (Android 12+, falls back to BLACK_RED below that). */
    DYNAMIC
}

/** Light / dark appearance, independent of the color palette. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

private const val PREFS_NAME = "tiffinbox_prefs"
private const val KEY_PALETTE = "theme_palette"
private const val KEY_MODE = "theme_mode"

/**
 * Persisted via plain SharedPreferences (no new Gradle dependency, unlike
 * DataStore). [current]/[mode] are Compose-observable, so switching them in
 * SettingsScreen recomposes the whole app's theme immediately. [init] must
 * be called once, early (MainActivity.onCreate), before any composable
 * reads them.
 */
object ThemePreferences {

    var current by mutableStateOf(ThemePalette.BLACK_RED)
        private set

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        current = prefs.getString(KEY_PALETTE, null)
            ?.let { runCatching { ThemePalette.valueOf(it) }.getOrNull() }
            ?: ThemePalette.BLACK_RED
        mode = prefs.getString(KEY_MODE, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    fun setPalette(context: Context, palette: ThemePalette) {
        current = palette
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PALETTE, palette.name)
            .apply()
    }

    fun setMode(context: Context, newMode: ThemeMode) {
        mode = newMode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, newMode.name)
            .apply()
    }
}
