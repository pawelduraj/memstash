package dev.pawelduraj.memstash.ui

import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.pawelduraj.memstash.R
import dev.pawelduraj.memstash.data.ImageTypes
import dev.pawelduraj.memstash.data.Meme
import dev.pawelduraj.memstash.data.MemeImporter
import dev.pawelduraj.memstash.data.SendMode

private enum class MemeDialog { Rename, Move, Delete }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemeSheet(
    meme: Meme,
    uri: Uri,
    lastSent: Long?,
    sendMode: SendMode,
    rootName: String,
    loadFolders: suspend () -> List<String>?,
    onSend: (SendMode) -> Unit,
    onRename: (String) -> Unit,
    onMove: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val image = rememberMemeImage(uri, meme, animated = true)
    // Leaves room for the actions below the image, also in landscape.
    val windowHeight = LocalWindowInfo.current.containerSize.height
    // The window size can be unknown (0) for the first frame of the sheet; the image must not collapse then.
    val maxImageHeight = if (windowHeight > 0) with(LocalDensity.current) { (windowHeight * 0.45f).toDp() }.coerceAtMost(360.dp) else 360.dp
    var dialog by remember { mutableStateOf<MemeDialog?>(null) }
    val title = meme.title
    val folder = breadcrumb(meme.folderPath, rootName)
    val format = ImageTypes.formatName(meme.mime)
    val sent = lastSent?.let {
        val now = System.currentTimeMillis()
        if (now - it < DateUtils.MINUTE_IN_MILLIS) stringResource(R.string.sent_just_now)
        else stringResource(R.string.sent_ago, DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS))
    }.orEmpty()

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
            AsyncImage(
                model = image,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxImageHeight)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(Modifier.padding(horizontal = 4.dp)) {
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    buildAnnotatedString {
                        append(title)
                        if (meme.extension.isNotEmpty()) withStyle(SpanStyle(color = muted)) { append(".${meme.extension}") }
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                // A format the extension doesn't name is only marked; the file and its copies stay as they are named.
                val error = MaterialTheme.colorScheme.error
                Text(
                    buildAnnotatedString {
                        append("$folder · ")
                        if (meme.extensionFits) append(format) else withStyle(SpanStyle(color = error)) { append(format) }
                        append(sent)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetAction(AppIcons.Pencil, stringResource(R.string.rename)) { dialog = MemeDialog.Rename }
                SheetAction(AppIcons.MoveFolder, stringResource(R.string.move)) { dialog = MemeDialog.Move }
                SheetAction(AppIcons.Trash, stringResource(R.string.delete), MaterialTheme.colorScheme.error) { dialog = MemeDialog.Delete }
            }
            // The mode a tap in the grid uses is highlighted; the other copies that way once, the setting stays.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WideButton(AppIcons.Photo, stringResource(R.string.to_gallery), highlighted = sendMode == SendMode.GALLERY) {
                    onSend(SendMode.GALLERY)
                }
                WideButton(AppIcons.Clipboard, stringResource(R.string.to_clipboard), highlighted = sendMode == SendMode.CLIPBOARD) {
                    onSend(SendMode.CLIPBOARD)
                }
            }
        }
    }

    when (dialog) {
        MemeDialog.Rename -> RenameDialog(
            initial = title,
            extension = meme.matchingExtension,
            hint = stringResource(R.string.name_hint),
            onConfirm = {
                dialog = null
                // Saving the same name still renames a file whose extension doesn't match its content.
                if (MemeImporter.memeName(it) != meme.title || meme.matchingName != meme.name) {
                    onRename(it)
                    onDismiss()
                }
            },
            onDismiss = { dialog = null },
        )
        MemeDialog.Move -> {
            var folders by remember { mutableStateOf<List<String>?>(null) }
            LaunchedEffect(Unit) {
                folders = loadFolders()
                if (folders == null) dialog = null
            }
            folders?.let { list ->
                FolderChoiceDialog(
                    title = stringResource(R.string.move_to_folder),
                    rootName = rootName,
                    folders = list,
                    selected = meme.folderPath,
                    onPick = {
                        dialog = null
                        if (it != meme.folderPath) {
                            onMove(it)
                            onDismiss()
                        }
                    },
                    onDismiss = { dialog = null },
                )
            }
        }
        MemeDialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.delete_title),
            text = stringResource(R.string.delete_text, title),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                dialog = null
                onDelete()
                onDismiss()
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/** One of two main buttons side by side at the bottom of a sheet. */
@Composable
fun RowScope.WideButton(icon: ImageVector, label: String, highlighted: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Filled grey is how an enabled button that isn't highlighted looks, so a disabled one is only outlined.
    val colors = ButtonDefaults.buttonColors(
        containerColor = if (highlighted) scheme.tertiary else scheme.surfaceContainerHighest,
        contentColor = if (highlighted) scheme.onTertiary else scheme.onSurface,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = scheme.onSurface.copy(alpha = 0.38f),
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = colors,
        border = if (enabled) null else BorderStroke(1.dp, scheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun RowScope.SheetAction(icon: ImageVector, label: String, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = color),
        contentPadding = PaddingValues(4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun RenameDialog(initial: String, extension: String, hint: String?, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (value.text.isNotBlank()) onConfirm(value.text) }),
                suffix = { if (extension.isNotEmpty()) Text(".$extension") },
                supportingText = hint?.let { { Text(it) } },
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text) }, enabled = value.text.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
