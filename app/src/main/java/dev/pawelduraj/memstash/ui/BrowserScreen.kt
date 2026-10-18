package dev.pawelduraj.memstash.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.pawelduraj.memstash.R
import dev.pawelduraj.memstash.data.FolderInfo
import dev.pawelduraj.memstash.data.GridMode
import dev.pawelduraj.memstash.data.Meme
import dev.pawelduraj.memstash.data.SendMode
import dev.pawelduraj.memstash.data.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun BrowserScreen(
    vm: MainViewModel,
    settings: Settings,
    rootName: String,
    onOpenSettings: () -> Unit,
    onImportImages: () -> Unit,
    onPasteImages: () -> Unit,
    onClose: () -> Unit,
) {
    val memes by vm.memes.collectAsStateWithLifecycle()
    val folderLevel by vm.folderLevel.collectAsStateWithLifecycle()
    val foldersOpen by vm.foldersOpen.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val indexing by vm.indexing.collectAsStateWithLifecycle()
    val indexSize by vm.indexSize.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val openSignal by vm.openSignal.collectAsStateWithLifecycle()
    val clipboardImage by vm.clipboardImage.collectAsStateWithLifecycle()
    val clipboardHasImage by vm.clipboardHasImage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val searchFocus = remember { FocusRequester() }
    var preview by remember { mutableStateOf<Meme?>(null) }
    var folderOptions by remember { mutableStateOf<FolderInfo?>(null) }
    var adding by remember { mutableStateOf(false) }
    val searching = query.isNotBlank()
    val firstLoad = indexing && indexSize == 0
    val focusManager = LocalFocusManager.current
    // Together with the keyboard the folder panel would leave no room for memes, so it waits until the keyboard is gone.
    // Derived, so the keyboard's animation doesn't recompose the whole screen on every frame.
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val keyboardShown by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val foldersShown = foldersOpen && !keyboardShown
    // Search keeps focus after the keyboard is closed, and any dialog that closes would bring the keyboard back.
    LaunchedEffect(keyboardShown) { if (!keyboardShown) focusManager.clearFocus() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullHeight = constraints.maxHeight.toFloat()
        val offset = remember { Animatable(fullHeight) }
        val dismiss: () -> Unit = {
            scope.launch {
                keyboard?.hide()
                offset.animateTo(fullHeight, tween(180))
                onClose()
                // When the app only moves to the background, the panel is back in place on return.
                delay(500)
                offset.snapTo(0f)
            }
        }
        val currentDismiss by rememberUpdatedState(dismiss)

        LaunchedEffect(openSignal) {
            preview = null
            folderOptions = null
            adding = false
            offset.snapTo(fullHeight)
            offset.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
            if (settings.focusSearchOnOpen && openSignal > 0) {
                searchFocus.requestFocus()
                keyboard?.show()
            }
        }

        BackHandler {
            when {
                query.isNotEmpty() -> vm.setQuery("")
                foldersShown && !folderLevel?.folder?.path.isNullOrEmpty() -> vm.folderUp()
                foldersShown -> vm.showFolders(false)
                else -> dismiss()
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - (offset.value / fullHeight).coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { currentDismiss() } }
        )

        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = maxOf(maxHeight * 0.11f, statusBar + 24.dp))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .offset { IntOffset(0, offset.value.roundToInt()) }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.background)
                // Taps on empty parts of the panel would otherwise reach the dimmed area behind it and close the app.
                .pointerInput(Unit) { detectTapGestures {} }
                .imePadding()
        ) {
            val dragState = rememberDraggableState { delta ->
                scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) }
            }
            Column(
                Modifier.fillMaxWidth().draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        if (offset.value > fullHeight * 0.18f || velocity > 1800f) dismiss() else offset.animateTo(0f)
                    },
                )
            ) {
                Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(40.dp, 5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                }
                Row(
                    Modifier.fillMaxWidth().height(44.dp).padding(start = 18.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        listOfNotNull(
                            (filter as? MemeFilter.Folder)?.takeIf { !searching }?.let { folderLabel(it, rootName) },
                            countLabel(memes.size, query, firstLoad),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val square = settings.gridMode == GridMode.SQUARE
                    IconButton(onClick = {
                        vm.updateSettings { it.copy(gridMode = if (square) GridMode.FULL else GridMode.SQUARE) }
                    }) {
                        Icon(
                            if (square) AppIcons.GridFull else AppIcons.GridSquare,
                            contentDescription = stringResource(if (square) R.string.show_full else R.string.show_square),
                        )
                    }
                    IconButton(onClick = { adding = true }) {
                        Icon(AppIcons.AddPhoto, contentDescription = stringResource(R.string.add_memes))
                    }
                    IconButton(onClick = onOpenSettings) { Icon(AppIcons.Sliders, contentDescription = stringResource(R.string.settings)) }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val currentError = error
                when {
                    currentError != null && memes.isEmpty() -> CenterMessage(currentError, stringResource(R.string.settings), onOpenSettings)
                    memes.isEmpty() && !firstLoad && !searching && filter != MemeFilter.Recent ->
                        CenterMessage(stringResource(R.string.no_memes), stringResource(R.string.add_memes), onAction = { adding = true })
                    memes.isEmpty() -> CenterMessage(
                        text = when {
                            firstLoad -> stringResource(R.string.loading_memes)
                            searching -> if (indexing) stringResource(R.string.searching) else stringResource(R.string.no_results, query)
                            else -> stringResource(R.string.recent_empty)
                        },
                        loading = firstLoad || (indexing && searching),
                    )
                    else -> MemeGrid(
                        memes = memes,
                        settings = settings,
                        stateKey = listOf(filter, searching, settings.gridMode, openSignal),
                        scrollToBottomOn = if (searching) memes else null,
                        uriOf = vm::memeUri,
                        onMeme = vm::send,
                        onPreview = { preview = it },
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, Color.Transparent)))
                )
                // Checking for changes is quick in a folder on the phone; a bar only for slow ones, such as cloud folders.
                val slowRefresh by produceState(false, indexing) {
                    value = false
                    if (indexing) {
                        delay(600)
                        value = true
                    }
                }
                if (slowRefresh && memes.isNotEmpty()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 18.dp))
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .padding(top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (clipboardImage != null && !searching) {
                    ClipboardOffer(onAdd = onPasteImages, onDismiss = vm::dismissClipboardImage)
                }
                if (!searching) {
                    val level = folderLevel
                    AnimatedVisibility(visible = level != null && foldersShown) {
                        level?.let {
                            FolderPanel(
                                level = it,
                                rootName = rootName,
                                selected = filter,
                                maxHeight = this@BoxWithConstraints.maxHeight * 0.4f,
                                onUp = vm::folderUp,
                                onOpen = vm::openFolder,
                                onOptions = { folderOptions = it },
                                onSelect = vm::setFilter,
                            )
                        }
                    }
                    ViewButtons(
                        filter = filter,
                        hasFolders = level != null,
                        foldersOpen = foldersShown,
                        onFolders = {
                            if (keyboardShown) focusManager.clearFocus()
                            vm.showFolders(!foldersShown)
                        },
                        onSelect = vm::setFilter,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SendModeToggle(settings.sendMode) { mode ->
                        vm.updateSettings { it.copy(sendMode = mode) }
                        val label = if (mode == SendMode.GALLERY) R.string.toast_mode_gallery else R.string.toast_mode_clipboard
                        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                    }
                    SearchField(
                        query = query,
                        onChange = vm::setQuery,
                        focus = searchFocus,
                        onSearch = { keyboard?.hide() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (adding) {
        AddSheet(
            rootName = rootName,
            clipboardHasImage = clipboardHasImage,
            onGallery = onImportImages,
            onClipboard = onPasteImages,
            onDismiss = { adding = false },
        )
    }

    folderOptions?.let { folder ->
        FolderSheet(
            folder = folder,
            rootName = rootName,
            checkContents = { vm.otherFilesIn(folder.path) },
            onRename = { vm.renameFolder(folder.path, it) },
            onDelete = { vm.deleteFolder(folder.path) },
            onShowInFiles = { vm.showInFiles(folder.path) },
            onDismiss = { folderOptions = null },
        )
    }

    preview?.let { meme ->
        MemeSheet(
            meme = meme,
            uri = vm.memeUri(meme),
            lastSent = vm.lastSent(meme),
            sendMode = settings.sendMode,
            rootName = rootName,
            loadFolders = vm::libraryFolders,
            onSend = { mode ->
                preview = null
                vm.send(meme, mode)
            },
            onRename = { vm.rename(meme, it) },
            onMove = { vm.move(meme, it) },
            onDelete = { vm.delete(meme) },
            onDismiss = { preview = null },
        )
    }
}

@Composable
private fun folderLabel(folder: MemeFilter.Folder, rootName: String): String {
    val name = folderName(folder.path, rootName)
    return if (folder.onlyHere) stringResource(R.string.folder_only_this, name) else name
}

@Composable
private fun countLabel(count: Int, query: String, loading: Boolean): String = when {
    loading -> stringResource(R.string.loading)
    query.isNotBlank() -> pluralStringResource(R.plurals.memes_for_query, count, count, query)
    else -> pluralStringResource(R.plurals.memes_count, count, count)
}

@Composable
private fun MemeGrid(
    memes: List<Meme>,
    settings: Settings,
    stateKey: Any,
    scrollToBottomOn: Any?,
    uriOf: (Meme) -> Uri,
    onMeme: (Meme) -> Unit,
    onPreview: (Meme) -> Unit,
) {
    val padding = PaddingValues(8.dp)
    if (settings.gridMode == GridMode.SQUARE) {
        val state = remember(stateKey) { LazyGridState() }
        LaunchedEffect(scrollToBottomOn) { if (scrollToBottomOn != null) state.scrollToItem(0) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(settings.columns),
            state = state,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
        ) {
            // Not the path: some providers allow two files with the same name, and a repeated key crashes the grid.
            items(memes, key = { it.id }) { meme ->
                MemeTile(uriOf(meme), meme, 1f, { onMeme(meme) }, { onPreview(meme) })
            }
        }
    } else {
        val state = remember(stateKey) { LazyStaggeredGridState() }
        LaunchedEffect(scrollToBottomOn) { if (scrollToBottomOn != null) state.scrollToItem(0) }
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(settings.columns),
            state = state,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalItemSpacing = 6.dp,
        ) {
            items(memes, key = { it.id }) { meme ->
                MemeTile(uriOf(meme), meme, meme.displayRatio, { onMeme(meme) }, { onPreview(meme) })
            }
        }
    }
}

@Composable
private fun MemeTile(uri: Uri, meme: Meme, ratio: Float, onClick: () -> Unit, onLongClick: () -> Unit) {
    AsyncImage(
        model = rememberMemeImage(uri, meme),
        contentDescription = meme.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClickLabel = stringResource(R.string.send),
                onLongClickLabel = stringResource(R.string.meme_options),
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    )
}

@Composable
private fun ViewButtons(
    filter: MemeFilter,
    hasFolders: Boolean,
    foldersOpen: Boolean,
    onFolders: () -> Unit,
    onSelect: (MemeFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (hasFolders) {
            Chip(
                label = stringResource(R.string.folders),
                icon = AppIcons.Folders,
                selected = filter is MemeFilter.Folder,
                trailing = if (foldersOpen) AppIcons.ChevronDown else AppIcons.ChevronUp,
                clickLabel = stringResource(if (foldersOpen) R.string.hide_folders else R.string.show_folders),
                modifier = Modifier.weight(1f),
                onClick = onFolders,
            )
        }
        Chip(stringResource(R.string.chip_all), AppIcons.GridSquare, filter == MemeFilter.All) { onSelect(MemeFilter.All) }
        Chip(stringResource(R.string.chip_recent), AppIcons.Clock, filter == MemeFilter.Recent) { onSelect(MemeFilter.Recent) }
    }
}

/** The folders inside one folder, all visible at once; a folder with subfolders opens to show them. */
@Composable
private fun FolderPanel(
    level: FolderLevel,
    rootName: String,
    selected: MemeFilter,
    maxHeight: Dp,
    onUp: () -> Unit,
    onOpen: (FolderInfo) -> Unit,
    onOptions: (FolderInfo) -> Unit,
    onSelect: (MemeFilter) -> Unit,
) {
    val path = level.folder.path
    Column(
        Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val shape = RoundedCornerShape(22.dp)
        // Inside a folder the whole path is one button that goes a folder up, so it's easy to hit.
        Row(
            if (path.isEmpty()) Modifier.heightIn(min = 44.dp).padding(horizontal = 10.dp)
            else Modifier
                .heightIn(min = 44.dp)
                .clip(shape)
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable(onClickLabel = stringResource(R.string.folder_up), role = Role.Button, onClick = onUp)
                .padding(start = 8.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (path.isNotEmpty()) {
                Icon(AppIcons.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
            Text(
                breadcrumb(path, rootName),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                // A long path keeps its end, the folder that's open.
                overflow = TextOverflow.StartEllipsis,
            )
        }
        FlowRow(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val all = if (path.isEmpty()) MemeFilter.All else MemeFilter.Folder(path)
            Chip(withCount(stringResource(R.string.chip_all), level.folder.count), AppIcons.GridSquare, selected == all) { onSelect(all) }
            if (level.folder.direct > 0) {
                val own = MemeFilter.Folder(path, onlyHere = true)
                Chip(withCount(folderName(path, rootName), level.folder.direct), AppIcons.Folders, selected == own) { onSelect(own) }
            }
            level.subfolders.forEach { folder ->
                Chip(
                    label = withCount(folder.name, folder.count),
                    icon = null,
                    selected = selected == MemeFilter.Folder(folder.path),
                    trailing = if (folder.hasSubfolders) AppIcons.ChevronRight else null,
                    onLongClick = { onOptions(folder) },
                    longClickLabel = stringResource(R.string.folder_options),
                ) { onOpen(folder) }
            }
        }
    }
}

private fun withCount(label: String, count: Int) = "$label ($count)"

@Composable
private fun Chip(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    trailing: ImageVector? = null,
    clickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onTertiary else colors.onSurface
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) colors.tertiary else colors.surfaceContainerHighest)
            .then(
                when {
                    clickLabel != null -> Modifier.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
                    onLongClick != null -> Modifier
                        .semantics { this.selected = selected }
                        .combinedClickable(role = Role.Tab, onLongClickLabel = longClickLabel, onLongClick = onLongClick, onClick = onClick)
                    else -> Modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick)
                }
            )
            .padding(start = if (icon != null) 10.dp else 14.dp, end = if (trailing != null) 8.dp else if (icon != null) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(
            label,
            color = content,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) Icon(trailing, contentDescription = null, tint = content.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SendModeToggle(mode: SendMode, onChange: (SendMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(colors.surfaceContainerHighest)
            .padding(4.dp)
            .selectableGroup()
    ) {
        SendMode.entries.forEach { m ->
            val selected = m == mode
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.tertiary else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange(m) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (m == SendMode.GALLERY) AppIcons.Photo else AppIcons.Clipboard,
                    contentDescription = stringResource(if (m == SendMode.GALLERY) R.string.mode_gallery else R.string.mode_clipboard),
                    tint = if (selected) colors.onTertiary else colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, focus: FocusRequester, onSearch: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface, fontSize = 17.sp)
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(colors.onSurface),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier.height(52.dp).focusRequester(focus),
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(26.dp))
                    .background(colors.surfaceContainerHighest)
                    .padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Search, contentDescription = null, tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(stringResource(R.string.search_hint), style = style.copy(color = colors.onSurfaceVariant))
                    field()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onChange("") }, modifier = Modifier.size(44.dp)) {
                        Icon(AppIcons.Close, contentDescription = stringResource(R.string.clear), modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun ClipboardOffer(onAdd: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainerHighest)
            .clickable(onClick = onAdd)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Paste, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.clipboard_offer),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colors.tertiary)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(stringResource(R.string.clipboard_add), color = colors.onTertiary, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onDismiss) {
            Icon(AppIcons.Close, contentDescription = stringResource(R.string.dismiss), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CenterMessage(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null, loading: Boolean = false) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
        }
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
