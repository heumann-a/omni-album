package com.skript.omnialbum.platform

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.skript.omnialbum.OmniAlbumApplication
import java.io.File

private var csvPicker: ActivityResultLauncher<Array<String>>? = null
private var pendingPickCallback: ((String?) -> Unit)? = null

fun registerCsvPicker(launcher: ActivityResultLauncher<Array<String>>) {
    csvPicker = launcher
}

actual fun shareCsvFile(fileName: String, content: String) {
    val activity = OmniAlbumApplication.currentActivity ?: return
    val file = File(activity.cacheDir, fileName)
    file.writeText(content)
    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    activity.startActivity(Intent.createChooser(intent, "Export albums"))
}

actual fun pickCsvFile(onResult: (content: String?) -> Unit) {
    pendingPickCallback = onResult
    csvPicker?.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) ?: onResult(null)
}

fun handleCsvPickResult(uri: Uri?) {
    val callback = pendingPickCallback
    pendingPickCallback = null
    if (uri == null || callback == null) return
    val activity = OmniAlbumApplication.currentActivity ?: return
    val content = runCatching {
        activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()
    callback(content)
}
