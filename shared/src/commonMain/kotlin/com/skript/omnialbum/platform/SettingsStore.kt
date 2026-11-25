package com.skript.omnialbum.platform

expect object SettingsStore {
    fun getBool(key: String, default: Boolean): Boolean
    fun setBool(key: String, value: Boolean)
    fun getString(key: String, default: String): String
    fun setString(key: String, value: String)
}
