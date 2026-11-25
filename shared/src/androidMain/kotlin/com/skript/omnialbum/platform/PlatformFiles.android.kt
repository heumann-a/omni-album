package com.skript.omnialbum.platform

import java.io.File

actual fun fileExists(path: String): Boolean = File(path).exists()

actual fun readFileBytes(path: String): ByteArray = File(path).readBytes()

actual fun writeFileBytes(path: String, data: ByteArray) {
    File(path).writeBytes(data)
}

actual fun createParentDirectories(path: String) {
    File(path).parentFile?.mkdirs()
}
