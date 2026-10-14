package com.ratulhossen.tiffinbox.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemePalette {
    BLACK_RED,
    CLASSIC_GREEN
}

private const val PREFS_NAME = "tiffinbox_prefs"
private const val KEY_PALETTE = "theme_palette"

/**
 * User-selectable color theme, persisted via plain SharedPreferences (no
 * new Gradle dependency, unlike DataStore). [current] is Compose-observable
 * so switching it in SettingsScreen recomposes the whole app's theme
 * immediately. [init] must be called once, early (MainActivity.onCreate),
 * before any composable reads [current].
 */
object ThemePreferences {

    var current by mutableStateOf(ThemePalette.BLACK_RED)
        private set

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_PALETTE, null)
        current = saved?.let { runCatching { ThemePalette.valueOf(it) }.getOrNull() } ?: ThemePalette.BLACK_RED
    }

    fun setPalette(context: Context, palette: ThemePalette) {
        current = palette
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PALETTE, palette.name)
            .apply()
    }
}
