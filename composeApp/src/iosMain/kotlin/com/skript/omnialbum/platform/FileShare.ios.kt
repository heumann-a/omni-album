@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.skript.omnialbum.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.NSURL
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeCommaSeparatedText
import platform.UniformTypeIdentifiers.UTTypeText
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
private fun topViewController(): UIViewController? {
    val app = UIApplication.sharedApplication
    val window = app.windows()?.firstOrNull() as? platform.UIKit.UIWindow ?: return null
    var top = window.rootViewController
    while (top?.presentedViewController != null) {
        top = top.presentedViewController
    }
    return top
}

actual fun shareCsvFile(fileName: String, content: String) {
    val vc = topViewController() ?: return
    val fileURL = platform.Foundation.NSTemporaryDirectory() + fileName
    (content as NSString).writeToFile(fileURL, atomically = true, encoding = platform.Foundation.NSUTF8StringEncoding, error = null)
    val activity = UIActivityViewController(
        activityItems = listOf(NSURL.fileURLWithPath(fileURL)),
        applicationActivities = null
    )
    vc.presentViewController(activity, animated = true, completion = null)
}

private var pendingPickCallback: ((String?) -> Unit)? = null

@OptIn(ExperimentalForeignApi::class)
private class DocumentPickerDelegate : NSObject(), platform.UIKit.UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        val callback = pendingPickCallback
        pendingPickCallback = null
        if (url == null || callback == null) return
        val content = runCatching { NSString.stringWithContentsOfURL(url, encoding = platform.Foundation.NSUTF8StringEncoding, error = null) }.getOrNull()
        callback(content)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        val callback = pendingPickCallback
        pendingPickCallback = null
        callback?.invoke(null)
    }
}

private val pickerDelegate = DocumentPickerDelegate()

actual fun pickCsvFile(onResult: (content: String?) -> Unit) {
    val vc = topViewController() ?: run { onResult(null); return }
    pendingPickCallback = onResult
    val picker = UIDocumentPickerViewController(
        documentTypes = listOf(UTTypeCommaSeparatedText.identifier, UTTypeText.identifier),
        inMode = platform.UIKit.UIDocumentPickerMode.UIDocumentPickerModeImport
    )
    picker.delegate = pickerDelegate
    vc.presentViewController(picker, animated = true, completion = null)
}
