package dev.pawelduraj.memstash.ui

import android.app.Application
import android.content.Intent
import android.icu.text.Collator
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pawelduraj.memstash.MemStashApp
import dev.pawelduraj.memstash.R
import dev.pawelduraj.memstash.data.ClipboardImages
import dev.pawelduraj.memstash.data.CopyCleanup
import dev.pawelduraj.memstash.data.CopyCleanupWorker
import dev.pawelduraj.memstash.data.FolderInfo
import dev.pawelduraj.memstash.data.FolderTree
import dev.pawelduraj.memstash.data.IndexCache
import dev.pawelduraj.memstash.data.Library
import dev.pawelduraj.memstash.data.LibraryEvents
import dev.pawelduraj.memstash.data.Meme
import dev.pawelduraj.memstash.data.MemeImporter
import dev.pawelduraj.memstash.data.MemeSearch
import dev.pawelduraj.memstash.data.SendMode
import dev.pawelduraj.memstash.data.Sender
import dev.pawelduraj.memstash.data.Settings
import dev.pawelduraj.memstash.data.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiEvent {
    /** [oldCopyLeft]: a gallery copy left by an earlier installation, which only the user can delete, is still there. */
    data class Sent(val mode: SendMode, val oldCopyLeft: Boolean = false) : UiEvent
    data class Message(val text: String) : UiEvent
    data class Import(val uris: List<Uri>, val target: String) : UiEvent
}

sealed interface MemeFilter {
    data object Recent : MemeFilter
    data object All : MemeFilter
    /** Memes in the folder at [path] and its subfolders; with [onlyHere], only those lying directly in it. */
    data class Folder(val path: String, val onlyHere: Boolean = false) : MemeFilter {
        fun matches(meme: Meme): Boolean = if (onlyHere) meme.folderPath == path else FolderTree.contains(path, meme.folderPath)
    }
}

/** What the folder panel shows: a folder and its subfolders. */
data class FolderLevel(val folder: FolderInfo, val subfolders: List<FolderInfo>)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = (app as MemStashApp).settingsStore
    private val cache = IndexCache(app)
    private val sender = Sender(app)
    private val clipboard = ClipboardImages(app)
    private val importer = MemeImporter(app)
    private val collator: Collator = Collator.getInstance().freeze()

    val settings: StateFlow<Settings> = store.settings

    private val _library = MutableStateFlow(Library.open(app, settings.value.rootUri))
    val library: StateFlow<Library?> = _library.asStateFlow()

    private val index = MutableStateFlow<List<Meme>>(emptyList())
    val indexSize: StateFlow<Int> = index.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _indexing = MutableStateFlow(false)
    val indexing: StateFlow<Boolean> = _indexing.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow<MemeFilter>(MemeFilter.All)
    val filter: StateFlow<MemeFilter> = _filter.asStateFlow()

    private val usage = store.usage
    val hasHistory: StateFlow<Boolean> = usage.map { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val folderTree: StateFlow<Map<String, FolderInfo>> = combine(index, usage, FolderTree::build)
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val folderPath = MutableStateFlow("")

    /** The folder the folder panel is in; null when the meme folder has no subfolders with memes. */
    val folderLevel: StateFlow<FolderLevel?> = combine(folderTree, folderPath) { tree, wanted ->
        if (tree[""]?.hasSubfolders != true) return@combine null
        // A folder that was removed or lost its subfolders shows the nearest one above it that still has some.
        var path = wanted
        while (path.isNotEmpty() && tree[path]?.hasSubfolders != true) path = FolderTree.parent(path)
        val subfolders = tree.values.filter { it.path.isNotEmpty() && FolderTree.parent(it.path) == path }
            .sortedWith(compareByDescending<FolderInfo> { it.lastSent }.thenComparator { a, b -> collator.compare(a.name, b.name) })
        FolderLevel(tree.getValue(path), subfolders)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _foldersOpen = MutableStateFlow(false)
    val foldersOpen: StateFlow<Boolean> = _foldersOpen.asStateFlow()

    /** Sorted once per change of the list, so choosing what to show only filters. */
    private val sortedIndex = combine(index, settings.map { it.sortOrder }.distinctUntilChanged(), ::sorted)

    val memes: StateFlow<List<Meme>> = combine(_query, _filter, sortedIndex, usage) { q, f, idx, use ->
        if (q.isNotBlank()) MemeSearch.search(idx, q, use) else filtered(idx, f, use)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    private val _openSignal = MutableStateFlow(0)
    val openSignal: StateFlow<Int> = _openSignal.asStateFlow()

    private val _hiddenFromGallery = MutableStateFlow<Boolean?>(null)
    /** Whether the meme folder has a .nomedia file; null until known. */
    val hiddenFromGallery: StateFlow<Boolean?> = _hiddenFromGallery.asStateFlow()

    private val _clipboardImage = MutableStateFlow<Long?>(null)
    /** When the clipboard got the image copied in another app that MemStash offers to add; null when there's none. */
    val clipboardImage: StateFlow<Long?> = _clipboardImage.asStateFlow()

    private val _clipboardHasImage = MutableStateFlow(false)
    /** Whether the clipboard holds an image copied in another app, also one whose offer was dismissed. */
    val clipboardHasImage: StateFlow<Boolean> = _clipboardHasImage.asStateFlow()

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = events.receiveAsFlow()

    private var indexJob: Job? = null
    private var galleryJob: Job? = null
    private var sending = false
    private var checkingClip: Long? = null

    init {
        _library.value?.let { openLibrary(it, useCache = true) }
        viewModelScope.launch { LibraryEvents.changes.collect { refreshIndex(force = true) } }
    }

    val tileOffered: Boolean get() = store.tileOffered
    fun markTileOffered() { store.tileOffered = true }

    /** Takes the folder picked in the system folder picker. Returns false when it can't be used. */
    fun setRoot(treeUri: Uri): Boolean {
        val key = treeUri.toString()
        val previous = settings.value.rootUri
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(treeUri, ACCESS_FLAGS)
        } catch (_: SecurityException) {
            message(R.string.error_folder_access)
            return false
        }
        val library = Library.open(getApplication(), key)
        // The gallery copies must stay outside the meme folder, or hiding it from the gallery would hide them too.
        val problem = when {
            library == null -> R.string.error_folder_access
            library.isCopyFolder -> R.string.error_copy_folder
            library.holdsCopyFolder -> R.string.error_holds_copy_folder
            else -> null
        }
        if (library == null || problem != null) {
            if (key != previous) releaseAccess(treeUri)
            message(problem ?: R.string.error_folder_access)
            return false
        }
        if (previous != null && previous != key) releaseAccess(previous.toUri())
        // History is kept by paths inside the meme folder; in another folder they would point at other memes.
        if (store.historyFolder != key) {
            store.clearUsage()
            store.historyFolder = key
        }
        store.update { it.copy(rootUri = key) }
        clearFolderState()
        _library.value = library
        openLibrary(library, useCache = false)
        return true
    }

    /** Gives up access to the meme folder; the memes stay where they are. */
    fun disconnectFolder() {
        val library = _library.value ?: return
        val scan = indexJob
        scan?.cancel()
        releaseAccess(library.key.toUri())
        store.update { it.copy(rootUri = null) }
        // A cancelled scan may still be saving the list of memes, so it's removed once the scan has stopped.
        viewModelScope.launch {
            scan?.join()
            cache.clear()
        }
        clearFolderState()
        _error.value = null
        _foldersOpen.value = false
        _settingsOpen.value = false
        _library.value = null
    }

    private fun clearFolderState() {
        index.value = emptyList()
        _hiddenFromGallery.value = null
        _filter.value = MemeFilter.All
        folderPath.value = ""
    }

    private fun releaseAccess(uri: Uri) {
        runCatching { getApplication<Application>().contentResolver.releasePersistableUriPermission(uri, ACCESS_FLAGS) }
    }

    fun updateSettings(transform: (Settings) -> Settings) = store.update(transform)

    fun setQuery(value: String) { _query.value = value }

    /** Chooses what to show; the folder panel closes, as a choice is made. */
    fun setFilter(value: MemeFilter) {
        _filter.value = value
        _foldersOpen.value = false
    }

    /** Opens or closes the folder panel; it opens at the chosen folder. */
    fun showFolders(open: Boolean) {
        if (open) folderPath.value = currentFolderPath()
        _foldersOpen.value = open
    }

    /** Chooses a folder in the folder panel; a folder with subfolders opens to show them, the panel stays. */
    fun openFolder(folder: FolderInfo) {
        if (!folder.hasSubfolders) return setFilter(MemeFilter.Folder(folder.path))
        folderPath.value = folder.path
        _filter.value = MemeFilter.Folder(folder.path)
    }

    fun folderUp() {
        folderLevel.value?.let { folderPath.value = FolderTree.parent(it.folder.path) }
    }

    fun renameFolder(path: String, newName: String) {
        val library = _library.value ?: return
        viewModelScope.launch {
            attempt("Renaming a folder failed", {
                message(if (it is Library.NameTakenException) R.string.error_name_taken else R.string.error_rename_folder)
            }) {
                val renamed = library.renameFolder(path, MemeImporter.safeName(newName)) ?: return@attempt
                store.moveFolderUsage(path, renamed.path)
                // The memes keep showing while the scan that follows checks the folder.
                index.value = index.value.map(renamed::rebase)
                // What was shown from the folder stays shown under its new name.
                (_filter.value as? MemeFilter.Folder)?.let { _filter.value = it.copy(path = renamed.rebase(it.path)) }
                folderPath.value = renamed.rebase(folderPath.value)
                store.lastImportFolder = renamed.rebase(store.lastImportFolder)
                message(R.string.renamed)
            }
            refreshIndex(force = true)
        }
    }

    fun deleteFolder(path: String) {
        val library = _library.value ?: return
        viewModelScope.launch {
            attempt("Deleting a folder failed", { message(R.string.error_delete_folder) }) {
                library.deleteFolder(path)
                store.forgetFolderUsage(path)
                val parent = FolderTree.parent(path)
                if ((_filter.value as? MemeFilter.Folder)?.let { FolderTree.contains(path, it.path) } == true) {
                    _filter.value = if (parent.isEmpty()) MemeFilter.All else MemeFilter.Folder(parent)
                }
                if (FolderTree.contains(path, folderPath.value)) folderPath.value = parent
                message(R.string.deleted, FolderTree.name(path))
            }
            refreshIndex(force = true)
        }
    }

    /** Returns null, after telling the user, when the folder can't be read. */
    suspend fun otherFilesIn(path: String): List<String>? {
        val library = _library.value ?: return null
        return attempt("Reading a folder failed", { message(R.string.error_reading_folder); null }) { library.otherFiles(path) }
    }

    fun showInFiles(path: String) {
        val library = _library.value ?: return
        viewModelScope.launch {
            attempt("Opening a folder in a file manager failed", { message(R.string.error_open_files) }) {
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(library.folderDocument(path), DocumentsContract.Document.MIME_TYPE_DIR)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                getApplication<Application>().startActivity(intent)
            }
        }
    }

    fun showSettings(show: Boolean) { _settingsOpen.value = show }

    fun onFreshOpen() {
        viewModelScope.launch { cleanUpCopiesOnOpen() }
        _settingsOpen.value = false
        _query.value = ""
        _filter.value = MemeFilter.All
        folderPath.value = ""
        _foldersOpen.value = false
        _openSignal.value++
        refreshIndex(force = false)
    }

    /** Without [force], a scan already running isn't started again. */
    fun refreshIndex(force: Boolean = true) {
        val library = _library.value ?: return
        if (!force && indexJob?.isActive == true) return
        startIndexing(library, useCache = false)
    }

    private fun startIndexing(library: Library, useCache: Boolean) {
        indexJob?.cancel()
        val job = viewModelScope.launch {
            _indexing.value = true
            if (useCache && index.value.isEmpty()) index.value = cache.load(library.key)
            // On a first load memes show up folder by folder, which matters for slow cloud folders. Later scans
            // replace the list at once, so memes don't vanish and come back.
            val firstLoad = index.value.isEmpty()
            attempt("Reading the meme folder failed", {
                // Only the meme folder itself makes the scan fail; memes shown from before could no longer be sent.
                index.value = emptyList()
                _error.value = string(R.string.error_reading_folder)
            }) {
                val fresh = library.scan(previous = index.value) { found ->
                    // On the main thread, where a newer scan cancels this one, so a cancelled scan can't show its memes.
                    if (firstLoad) withContext(Dispatchers.Main) { index.value = found }
                }
                index.value = fresh
                resetMissingFolder(fresh)
                // Galleries see only folders on the phone.
                if (library.isOnDevice) _hiddenFromGallery.value = library.isHiddenFromGallery()
                _error.value = null
                cache.save(library.key, fresh)
            }
        }
        indexJob = job
        job.invokeOnCompletion { if (indexJob === job) _indexing.value = false }
    }

    /** Returns null, after telling the user, when the folders can't be read. */
    suspend fun libraryFolders(): List<String>? {
        val library = _library.value ?: return null
        return attempt("Listing folders failed", { message(R.string.error_reading_folder); null }) { library.folderPaths(collator) }
    }

    fun currentFolderPath(): String = (_filter.value as? MemeFilter.Folder)?.path.orEmpty()

    fun memeUri(meme: Meme): Uri = _library.value?.documentUri(meme.id) ?: Uri.EMPTY

    fun lastSent(meme: Meme): Long? = usage.value[meme.path]

    fun checkClipboard() {
        val image = clipboard.pendingImage()
        if (image == null || image == store.checkedClip) return showClipboardImage(image, store.checkedClipUsable)
        if (image == checkingClip) return
        checkingClip = image
        // Android tells only that an image was copied. Whether it can still be read and is a supported format needs
        // its content, and reading that makes Android show a notice, so each clip is checked once.
        val uris = clipboard.images()
        viewModelScope.launch {
            val usable = uris.any { importer.canRead(it) && importer.describe(it) != null }
            store.checkedClip = image
            store.checkedClipUsable = usable
            checkingClip = null
            showClipboardImage(image, usable)
        }
    }

    private fun showClipboardImage(image: Long?, usable: Boolean) {
        _clipboardHasImage.value = image != null && usable
        _clipboardImage.value = image?.takeIf { usable && it > store.handledClip }
    }

    fun dismissClipboardImage() {
        clipboard.pendingImage()?.let { store.handledClip = it }
        _clipboardImage.value = null
    }

    fun pasteFromClipboard() {
        val uris = clipboard.images()
        val image = clipboard.pendingImage()
        dismissClipboardImage()
        if (uris.isEmpty()) return message(R.string.no_clipboard_image)
        viewModelScope.launch {
            // The image was readable when the clip was checked, but the app that copied it may have removed it since.
            val readable = uris.filter { importer.canRead(it) }
            if (readable.isEmpty()) {
                image?.let {
                    store.checkedClip = it
                    store.checkedClipUsable = false
                }
                _clipboardHasImage.value = false
                return@launch message(R.string.clipboard_image_unreadable)
            }
            val images = readable.filter { importer.describe(it) != null }
            if (images.isEmpty()) message(R.string.clipboard_image_unsupported)
            else events.send(UiEvent.Import(images, currentFolderPath()))
        }
    }

    fun importPicked(uris: List<Uri>) {
        if (uris.isNotEmpty()) events.trySend(UiEvent.Import(uris, currentFolderPath()))
    }

    fun send(meme: Meme, mode: SendMode = settings.value.sendMode) {
        if (sending) return
        sending = true
        val uri = memeUri(meme)
        viewModelScope.launch {
            try {
                attempt("Copying a meme failed", {
                    message(failureMessage(meme, R.string.error_send))
                    refreshIndex(force = true)
                }) {
                    val oldCopyLeft = when (mode) {
                        SendMode.GALLERY -> {
                            val named = sender.toGallery(uri, meme.copyExtension, meme.copyMime)
                            settings.value.copyCleanup.lifetimeMillis?.let { CopyCleanupWorker.scheduleAfterCopy(getApplication(), it) }
                            !named
                        }
                        SendMode.CLIPBOARD -> {
                            sender.toClipboard(uri, meme.copyExtension)
                            false
                        }
                    }
                    store.recordUsage(meme.path)
                    _query.value = ""
                    events.send(UiEvent.Sent(mode, oldCopyLeft))
                }
            } finally {
                sending = false
            }
        }
    }

    fun setCopyCleanup(cleanup: CopyCleanup) {
        store.update { it.copy(copyCleanup = cleanup) }
        val lifetime = cleanup.lifetimeMillis
        if (lifetime == null) {
            CopyCleanupWorker.cancel(getApplication())
            return
        }
        // Copies already in the gallery follow the new time too.
        viewModelScope.launch {
            sender.deleteGalleryCopies(olderThanMillis = lifetime)
            CopyCleanupWorker.reschedule(getApplication(), lifetime)
        }
    }

    fun deleteCopiesNow() {
        viewModelScope.launch {
            val count = sender.deleteGalleryCopies(olderThanMillis = 0)
            CopyCleanupWorker.cancel(getApplication())
            message(R.string.copies_deleted, count)
        }
    }

    /** By the time MemStash opens again, the last meme has been sent; its copy goes now or once its time is up. */
    private suspend fun cleanUpCopiesOnOpen() {
        sender.deleteGalleryCopies(olderThanMillis = settings.value.copyCleanup.lifetimeMillis ?: 0L)
    }

    fun rename(meme: Meme, newName: String) = changeFile(meme, string(R.string.renamed), R.string.error_rename) { library ->
        val renamed = library.rename(meme, MemeImporter.memeName(newName))
        store.moveUsage(meme.path, renamed.path)
        updateIndex(meme, renamed)
    }

    fun move(meme: Meme, folder: String) = changeFile(meme, string(R.string.moved_to, breadcrumb(folder, rootName())), R.string.error_move) { library ->
        val moved = library.move(meme, folder)
        store.moveUsage(meme.path, moved.path)
        updateIndex(meme, moved)
    }

    fun delete(meme: Meme) = changeFile(meme, string(R.string.deleted, meme.title), R.string.error_delete) { library ->
        library.delete(meme)
        store.forgetUsage(meme.path)
        updateIndex(meme, null)
    }

    /**
     * Shows a change at once, without waiting for the scan that follows; that scan then knows the meme under its
     * new name and doesn't read the file again.
     */
    private fun updateIndex(old: Meme, new: Meme?) {
        index.value = index.value.mapNotNull { if (it.id == old.id) new else it }
        resetMissingFolder(index.value)
    }

    fun setHiddenFromGallery(hidden: Boolean) {
        val library = _library.value ?: return
        // A second tap while the file is being created would make the provider add ".nomedia (1)".
        if (galleryJob?.isActive == true) return
        galleryJob = viewModelScope.launch {
            attempt("Changing the gallery visibility failed", { message(R.string.error_hide_from_gallery) }) {
                library.setHiddenFromGallery(hidden)
                _hiddenFromGallery.value = hidden
                rescanMedia(library)
            }
        }
    }

    /** Android applies a new or removed .nomedia file only when the folder is scanned again. */
    private fun rescanMedia(library: Library) {
        val directory = library.directory(getApplication()) ?: return
        MediaScannerConnection.scanFile(getApplication(), arrayOf(directory.path), null, null)
    }

    fun clearHistory() {
        store.clearUsage()
        message(R.string.history_cleared)
    }

    private fun rootName(): String = _library.value?.name.orEmpty()

    private fun changeFile(meme: Meme, done: String, @StringRes error: Int, action: suspend (Library) -> Unit) {
        val library = _library.value ?: return
        viewModelScope.launch {
            attempt("Changing a file failed", { message(failureMessage(meme, error)) }) {
                action(library)
                events.send(UiEvent.Message(done))
            }
            refreshIndex(force = true)
        }
    }

    private fun openLibrary(library: Library, useCache: Boolean) {
        _error.value = null
        startIndexing(library, useCache)
    }

    /** Tells a meme deleted outside the app apart from other failures. */
    private suspend fun failureMessage(meme: Meme, @StringRes otherwise: Int): Int =
        if (_library.value?.exists(meme) == false) R.string.error_meme_missing else otherwise

    private fun resetMissingFolder(memes: List<Meme>) {
        val folder = _filter.value as? MemeFilter.Folder ?: return
        if (memes.none(folder::matches)) _filter.value = MemeFilter.All
    }

    private fun message(@StringRes text: Int, vararg args: Any) {
        events.trySend(UiEvent.Message(string(text, *args)))
    }

    private fun string(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private fun filtered(index: List<Meme>, filter: MemeFilter, usage: Map<String, Long>): List<Meme> =
        when (filter) {
            MemeFilter.Recent -> index.filter { it.path in usage }.sortedByDescending { usage[it.path] }.take(RECENT_LIMIT)
            MemeFilter.All -> index
            is MemeFilter.Folder -> index.filter(filter::matches)
        }

    private fun sorted(memes: List<Meme>, order: SortOrder): List<Meme> = when (order) {
        SortOrder.NAME -> memes.sortedWith { a, b -> collator.compare(a.name, b.name) }
        SortOrder.NEWEST -> memes.sortedByDescending { it.modified }
    }

    private companion object {
        const val ACCESS_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        const val RECENT_LIMIT = 60
    }
}
