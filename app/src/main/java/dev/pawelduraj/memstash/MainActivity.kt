package dev.pawelduraj.memstash

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.pawelduraj.memstash.data.SendMode
import dev.pawelduraj.memstash.ui.MainViewModel
import dev.pawelduraj.memstash.ui.MemeApp
import dev.pawelduraj.memstash.ui.MemeTheme
import dev.pawelduraj.memstash.ui.UiEvent

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) viewModel.onFreshOpen()

        setContent {
            MemeTheme {
                LaunchedEffect(Unit) {
                    viewModel.uiEvents.collect { event ->
                        when (event) {
                            is UiEvent.Sent -> {
                                when {
                                    event.oldCopyLeft -> toast(getString(R.string.toast_sent_gallery_old_copy), long = true)
                                    event.mode == SendMode.GALLERY -> toast(getString(R.string.toast_sent_gallery))
                                    // Android 13+ shows its own confirmation for clipboard copies.
                                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> toast(getString(R.string.toast_sent_clipboard))
                                }
                                returnToPreviousApp()
                            }
                            is UiEvent.Message -> toast(event.text)
                            is UiEvent.Import -> ImportActivity.importImages(this@MainActivity, event.uris, event.target)
                        }
                    }
                }
                Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
                    MemeApp(vm = viewModel, onClose = ::returnToPreviousApp, onRequestTile = ::requestTile)
                }
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        // Memes may have been added, renamed or deleted in other apps in the meantime.
        viewModel.refreshIndex(force = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.onFreshOpen()
    }

    private fun returnToPreviousApp() {
        if (viewModel.settings.value.closeCompletely) finishAndRemoveTask() else moveTaskToBack(true)
    }

    private fun requestTile() {
        val label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, getString(R.string.tile_how, label), Toast.LENGTH_LONG).show()
            return
        }
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, MemeTileService::class.java),
            label,
            Icon.createWithResource(this, R.drawable.ic_tile),
            mainExecutor,
        ) { result ->
            when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> toast(getString(R.string.tile_added, label))
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> toast(getString(R.string.tile_already_added))
            }
        }
    }

    private fun toast(text: String, long: Boolean = false) =
        Toast.makeText(this, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}
