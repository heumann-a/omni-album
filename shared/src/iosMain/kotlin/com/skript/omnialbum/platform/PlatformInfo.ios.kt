package com.skript.omnialbum.platform

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice

actual fun platformCacheDir(): String =
    NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String

actual fun platformDeviceId(): String =
    UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "ios-device"
