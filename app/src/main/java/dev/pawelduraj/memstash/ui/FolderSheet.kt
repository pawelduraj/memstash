package dev.pawelduraj.memstash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pawelduraj.memstash.R
import dev.pawelduraj.memstash.data.FolderInfo
import dev.pawelduraj.memstash.data.FolderTree
import dev.pawelduraj.memstash.data.MemeImporter

private enum class FolderDialog { Rename, Delete }

private const val SHOWN_OTHER_FILES = 3

/** Actions for a folder below the meme folder, opened by holding it in the folder panel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSheet(
    folder: FolderInfo,
    rootName: String,
    checkContents: suspend () -> List<String>?,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onShowInFiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    var dialog by remember { mutableStateOf<FolderDialog?>(null) }
    val location = breadcrumb(FolderTree.parent(folder.path), rootName)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(folder.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    "$location · ${pluralStringResource(R.plurals.memes_count, folder.count, folder.count)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetAction(AppIcons.Pencil, stringResource(R.string.rename)) { dialog = FolderDialog.Rename }
                SheetAction(AppIcons.Trash, stringResource(R.string.delete), MaterialTheme.colorScheme.error) { dialog = FolderDialog.Delete }
            }
        }
    }

    when (dialog) {
        FolderDialog.Rename -> RenameDialog(
            initial = folder.name,
            extension = "",
            hint = null,
            onConfirm = {
                dialog = null
                if (MemeImporter.safeName(it) != folder.name) onRename(it)
                onDismiss()
            },
            onDismiss = { dialog = null },
        )
        FolderDialog.Delete -> {
            // Whatever MemStash doesn't show would go too, so the user sees what it is before deciding.
            var others by remember { mutableStateOf<List<String>?>(null) }
            LaunchedEffect(Unit) {
                others = checkContents()
                if (others == null) dialog = null
            }
            others?.let { list ->
                val delete = {
                    dialog = null
                    onDelete()
                    onDismiss()
                }
                if (list.isEmpty()) {
                    ConfirmDialog(
                        title = stringResource(R.string.delete_folder_title),
                        text = pluralStringResource(R.plurals.delete_folder_text, folder.count, folder.name, folder.count),
                        confirmLabel = stringResource(R.string.delete),
                        destructive = true,
                        onConfirm = delete,
                        onDismiss = { dialog = null },
                    )
                } else {
                    val shown = list.take(SHOWN_OTHER_FILES).joinToString(", ")
                    val more = list.size - SHOWN_OTHER_FILES
                    val names = if (more > 0) "$shown ${pluralStringResource(R.plurals.other_files_more, more, more)}" else shown
                    AlertDialog(
                        onDismissRequest = { dialog = null },
                        title = { Text(stringResource(R.string.delete_folder_other_title)) },
                        text = { Text(stringResource(R.string.delete_folder_other_text, folder.name, names)) },
                        confirmButton = {
                            TextButton(onClick = delete) { Text(stringResource(R.string.delete_everything), color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            Row {
                                TextButton(onClick = {
                                    dialog = null
                                    onShowInFiles()
                                    onDismiss()
                                }) { Text(stringResource(R.string.show_in_files)) }
                                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) }
                            }
                        },
                    )
                }
            }
        }
        null -> Unit
    }
}
