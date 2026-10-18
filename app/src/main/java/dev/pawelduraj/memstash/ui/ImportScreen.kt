package dev.pawelduraj.memstash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.pawelduraj.memstash.R
import dev.pawelduraj.memstash.data.SharedImage

@Composable
fun ImportScreen(vm: ImportViewModel, onCancel: () -> Unit, onOpenApp: () -> Unit) {
    var choosingTarget by rememberSaveable { mutableStateOf(false) }
    val state = vm.state
    val images = (state as? ImportState.Ready)?.images

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(AppIcons.Close, contentDescription = stringResource(R.string.cancel)) }
            Text(
                when {
                    images == null -> stringResource(R.string.app_name)
                    images.size == 1 -> stringResource(R.string.add_meme)
                    else -> stringResource(R.string.add_memes_count, images.size)
                },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when (state) {
            ImportState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is ImportState.Blocked -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                if (state.needsFolder) {
                    FilledTonalButton(onClick = onOpenApp) { Text(stringResource(R.string.open_app)) }
                } else {
                    FilledTonalButton(onClick = onCancel) { Text(stringResource(R.string.close)) }
                }
            }
            is ImportState.Ready -> ReadyContent(vm, state.images, onChooseTarget = { choosingTarget = true })
        }
    }

    if (choosingTarget) {
        FolderChoiceDialog(
            title = stringResource(R.string.save_in_folder),
            rootName = vm.rootName,
            folders = vm.folders,
            selected = vm.target,
            onPick = {
                vm.chooseTarget(it)
                choosingTarget = false
            },
            onDismiss = { choosingTarget = false },
        )
    }
}

@Composable
private fun ColumnScope.ReadyContent(vm: ImportViewModel, images: List<SharedImage>, onChooseTarget: () -> Unit) {
    val busy = vm.progress != null
    val single = images.size == 1

    Box(Modifier.weight(1f).fillMaxWidth()) {
        if (single) {
            AsyncImage(
                model = images[0].uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(16.dp),
            )
        } else {
            ImageList(images, vm.names, enabled = !busy, onName = vm::setName)
        }
    }

    if (single) NameField(vm.names[0], images[0].extension, enabled = !busy, onDone = vm::importNow) { vm.setName(0, it) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onChooseTarget)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.save_in), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(vm.destinationLabel(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(stringResource(R.string.change), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }

    val total = images.size
    Button(
        onClick = vm::importNow,
        enabled = !busy && (!single || vm.names[0].isNotBlank()),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Text(
            when {
                busy -> stringResource(R.string.copying, vm.progress ?: 0, total)
                single -> stringResource(R.string.add)
                else -> pluralStringResource(R.plurals.add_n_memes, total, total)
            }
        )
    }
}

@Composable
private fun NameField(name: String, extension: String, enabled: Boolean, onDone: () -> Unit, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    var value by remember { mutableStateOf(TextFieldValue(name, TextRange(0, name.length))) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            onChange(it.text)
        },
        enabled = enabled,
        label = { Text(stringResource(R.string.meme_name)) },
        supportingText = { Text(stringResource(R.string.name_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (value.text.isNotBlank()) onDone() }),
        suffix = { Text(".$extension") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
    )
}

@Composable
private fun ImageList(images: List<SharedImage>, names: List<String>, enabled: Boolean, onName: (Int, String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(images, key = { _, image -> image.uri.toString() }) { i, image ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = image.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(12.dp))
                TextField(
                    value = names[i],
                    onValueChange = { onName(i, it) },
                    enabled = enabled,
                    placeholder = { Text(image.suggestedName) },
                    suffix = { Text(".${image.extension}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
