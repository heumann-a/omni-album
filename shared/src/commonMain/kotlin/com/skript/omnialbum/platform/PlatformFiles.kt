package com.skript.omnialbum.platform

expect fun fileExists(path: String): Boolean

expect fun readFileBytes(path: String): ByteArray

expect fun writeFileBytes(path: String, data: ByteArray)

expect fun createParentDirectories(path: String)
