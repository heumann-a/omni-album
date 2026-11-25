package com.skript.omnialbum

import android.app.Activity
import android.app.Application
import com.skript.omnialbum.platform.appContext
import com.skript.omnialbum.providers.registerAllProviders

class OmniAlbumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        registerAllProviders()
    }

    companion object {
        var currentActivity: Activity? = null
    }
}
