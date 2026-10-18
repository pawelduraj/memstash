package dev.pawelduraj.memstash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pawelduraj.memstash.R
import dev.pawelduraj.memstash.data.FolderTree
import dev.pawelduraj.memstash.data.MemeImporter

@Composable
fun FolderChoiceDialog(
    title: String,
    rootName: String,
    folders: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    // Where a new folder goes: the chosen folder at first, or any folder whose "new folder" button was tapped.
    var parent by remember { mutableStateOf(selected) }
    val parentLabel = folderName(parent, rootName)
    val nameFocus = remember { FocusRequester() }
    val createFolder = {
        if (newName.isNotBlank()) {
            val path = FolderTree.child(parent, MemeImporter.safeName(newName))
            // Shared storage ignores case, so "Cats" typed next to "cats" is the existing folder.
            onPick(folders.firstOrNull { it.equals(path, ignoreCase = true) } ?: path)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                // The list gives way first, so the new folder field stays visible above the keyboard.
                LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 360.dp)) {
                    items(folders, key = { it }) { path ->
                        val depth = FolderTree.segments(path).size
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(path) }.padding(start = (depth * 16).dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                folderName(path, rootName),
                                color = if (path == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                style = if (path == selected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                parent = path
                                nameFocus.requestFocus()
                            }) {
                                Icon(
                                    AppIcons.NewFolder,
                                    contentDescription = stringResource(R.string.new_folder_here),
                                    tint = if (path == parent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.new_folder_in, parentLabel)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { createFolder() }),
                        modifier = Modifier.weight(1f).focusRequester(nameFocus),
                    )
                    TextButton(onClick = createFolder, enabled = newName.isNotBlank()) { Text(stringResource(R.string.create)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

fun folderName(path: String, rootName: String): String = FolderTree.name(path).ifEmpty { rootName }

fun breadcrumb(path: String, rootName: String): String = (listOf(rootName) + FolderTree.segments(path)).joinToString(" › ")
