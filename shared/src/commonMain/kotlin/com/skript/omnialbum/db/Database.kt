package com.skript.omnialbum.db

import app.cash.sqldelight.db.SqlDriver

expect fun createDriver(): SqlDriver

object DatabaseHolder {
    val database: OmniAlbumDatabase by lazy { OmniAlbumDatabase(createDriver()) }
}
