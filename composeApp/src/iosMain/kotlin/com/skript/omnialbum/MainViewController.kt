package com.skript.omnialbum

import androidx.compose.ui.window.ComposeUIViewController
import com.skript.omnialbum.providers.registerAllProviders
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    registerAllProviders()
    return ComposeUIViewController { App() }
}
