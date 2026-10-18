package dev.pawelduraj.memstash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pawelduraj.memstash.R

/** The ways to add memes. Most of them start in other apps, so they are only described here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(rootName: String, clipboardHasImage: Boolean, onGallery: () -> Unit, onClipboard: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.add_memes), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp))
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AddWay(AppIcons.Share, stringResource(R.string.add_way_share))
                AddWay(AppIcons.Paste, stringResource(R.string.add_way_clipboard))
                AddWay(AppIcons.Folders, stringResource(R.string.add_way_files, rootName))
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WideButton(AppIcons.Photo, stringResource(R.string.from_gallery), highlighted = true) {
                    onDismiss()
                    onGallery()
                }
                WideButton(AppIcons.Clipboard, stringResource(R.string.from_clipboard), highlighted = false, enabled = clipboardHasImage) {
                    onDismiss()
                    onClipboard()
                }
            }
        }
    }
}

@Composable
private fun AddWay(icon: ImageVector, text: String) {
    Row {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp).size(20.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}
