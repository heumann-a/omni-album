package com.skript.omnialbum.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual fun createDriver(): SqlDriver =
    NativeSqliteDriver(OmniAlbumDatabase.Schema, "omnialbum.db")
