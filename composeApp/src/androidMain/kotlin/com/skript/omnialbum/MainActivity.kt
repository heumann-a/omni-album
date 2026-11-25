package com.skript.omnialbum

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.skript.omnialbum.platform.handleCsvPickResult
import com.skript.omnialbum.platform.registerCsvPicker

class MainActivity : ComponentActivity() {

    private val appState by lazy { AppStateHolder.state }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OmniAlbumApplication.currentActivity = this
        setContent {
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                handleCsvPickResult(uri)
            }
            LaunchedEffect(Unit) { registerCsvPicker(picker) }
            App(appState)
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "omnialbum") {
            appState.handleDeepLink(uri.toString())
        }
    }
}

object AppStateHolder {
    val state: AppState by lazy { AppState() }
}
