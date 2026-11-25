package com.skript.omnialbum.platform

import android.content.Context

actual object SettingsStore {
    private val prefs get() =
        appContext.getSharedPreferences("omnialbum_settings", Context.MODE_PRIVATE)

    actual fun getBool(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    actual fun setBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    actual fun getString(key: String, default: String): String = prefs.getString(key, default) ?: default
    actual fun setString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
}
