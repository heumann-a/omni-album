package com.skript.omnialbum.platform

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

lateinit var appContext: Context

actual fun platformCacheDir(): String = appContext.cacheDir.absolutePath

@SuppressLint("HardwareIds")
actual fun platformDeviceId(): String =
    Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) ?: "android-device"
