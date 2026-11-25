package com.skript.omnialbum.platform

expect fun shareCsvFile(fileName: String, content: String)

expect fun pickCsvFile(onResult: (content: String?) -> Unit)
