package com.skript.omnialbum.platform

import platform.Foundation.NSUserDefaults

actual object SettingsStore {
    private val defaults get() = NSUserDefaults.standardUserDefaults

    actual fun getBool(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) != null) defaults.boolForKey(key) else default

    actual fun setBool(key: String, value: Boolean) { defaults.setBool(value, key) }

    actual fun getString(key: String, default: String): String =
        defaults.stringForKey(key) ?: default

    actual fun setString(key: String, value: String) { defaults.setObject(value, key) }
}
