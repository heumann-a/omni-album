package com.skript.omnialbum.platform

import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.toByteArray
import platform.Foundation.toNSData
import platform.Foundation.writeToFile
import platform.Foundation.NSString
import platform.Foundation.stringByDeletingLastPathComponent

actual fun fileExists(path: String): Boolean =
    NSFileManager.defaultManager.fileExistsAtPath(path)

actual fun readFileBytes(path: String): ByteArray =
    NSData.dataWithContentsOfFile(path)?.toByteArray() ?: ByteArray(0)

actual fun writeFileBytes(path: String, data: ByteArray) {
    data.toNSData().writeToFile(path, atomically = true)
}

actual fun createParentDirectories(path: String) {
    val parent = (path as NSString).stringByDeletingLastPathComponent
    NSFileManager.defaultManager.createDirectoryAtPath(
        parent,
        withIntermediateDirectories = true,
        attributes = null,
        error = null
    )
}
