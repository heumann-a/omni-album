@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.skript.omnialbum.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.stringByDeletingLastPathComponent
import platform.Foundation.writeToFile
import platform.posix.memcpy

private fun NSData.toByteArrayCompat(): ByteArray =
    ByteArray(length.toInt()).apply { usePinned { memcpy(it.addressOf(0), bytes, length) } }

private fun ByteArray.toNSDataCompat(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSDataCompat), length = this@toNSDataCompat.size.toULong())
}

actual fun fileExists(path: String): Boolean =
    NSFileManager.defaultManager.fileExistsAtPath(path)

actual fun readFileBytes(path: String): ByteArray =
    NSData.dataWithContentsOfFile(path)?.toByteArrayCompat() ?: ByteArray(0)

actual fun writeFileBytes(path: String, data: ByteArray) {
    data.toNSDataCompat().writeToFile(path, atomically = true)
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
