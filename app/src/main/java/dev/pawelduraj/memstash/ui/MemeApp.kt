package dev.pawelduraj.memstash.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pawelduraj.memstash.R

@Composable
fun MemeApp(vm: MainViewModel, onClose: () -> Unit, onRequestTile: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val settingsOpen by vm.settingsOpen.collectAsStateWithLifecycle()

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && vm.setRoot(uri)) {
            vm.showSettings(false)
            if (!vm.tileOffered) {
                vm.markTileOffered()
                onRequestTile()
            }
        }
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(), vm::importPicked)

    // Android lets apps read the clipboard only while their window has focus.
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { if (focused) vm.checkClipboard() }

    val current = library
    when {
        current == null -> InfoScreen(
            icon = AppIcons.Folder,
            title = stringResource(R.string.choose_folder_title),
            text = stringResource(R.string.choose_folder_text),
            action = stringResource(R.string.choose_folder_action),
            onAction = { pickFolder.launch(null) },
        )
        settingsOpen -> SettingsScreen(
            vm = vm,
            library = current,
            settings = settings,
            onPickFolder = { pickFolder.launch(current.pickerLocation) },
            onRequestTile = onRequestTile,
            onBack = { vm.showSettings(false) },
        )
        else -> BrowserScreen(
            vm = vm,
            settings = settings,
            rootName = current.name,
            onOpenSettings = { vm.showSettings(true) },
            onImportImages = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onPasteImages = vm::pasteFromClipboard,
            onClose = onClose,
        )
    }
}

@Composable
private fun InfoScreen(icon: ImageVector, title: String, text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAction) { Text(action) }
    }
}
