package com.skript.omnialbum.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.skript.omnialbum.platform.appContext

actual fun createDriver(): SqlDriver =
    AndroidSqliteDriver(OmniAlbumDatabase.Schema, appContext, "omnialbum.db")
