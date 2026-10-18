package dev.pawelduraj.memstash.ui

import android.app.Application
import android.icu.text.Collator
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pawelduraj.memstash.MemStashApp
import dev.pawelduraj.memstash.R
import dev.pawelduraj.memstash.data.Library
import dev.pawelduraj.memstash.data.LibraryEvents
import dev.pawelduraj.memstash.data.MemeImporter
import dev.pawelduraj.memstash.data.SharedImage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface ImportState {
    data object Loading : ImportState
    data class Blocked(val message: String, val needsFolder: Boolean = false) : ImportState
    data class Ready(val images: List<SharedImage>) : ImportState
}

sealed interface ImportEvent {
    data class Done(val message: String) : ImportEvent
    data class Failed(val message: String) : ImportEvent
}

class ImportViewModel(app: Application) : AndroidViewModel(app) {
    private val store = (app as MemStashApp).settingsStore
    private val importer = MemeImporter(app)
    private val library: Library? = Library.open(app, store.settings.value.rootUri)
    private val collator: Collator = Collator.getInstance().freeze()

    val rootName: String = library?.name.orEmpty()

    var state by mutableStateOf<ImportState>(ImportState.Loading)
        private set

    val names = mutableStateListOf<String>()

    var target by mutableStateOf("")
        private set

    var folders by mutableStateOf(listOf(""))
        private set

    /** Images copied so far; null when not importing. */
    var progress by mutableStateOf<Int?>(null)
        private set

    private val events = Channel<ImportEvent>(Channel.BUFFERED)
    val uiEvents = events.receiveAsFlow()

    private var started = false
    private var imported = 0

    fun start(uris: List<Uri>, initialTarget: String?) {
        if (started) return
        started = true
        viewModelScope.launch {
            state = attempt("Reading shared images failed", { ImportState.Blocked(string(R.string.import_read_failed)) }) {
                load(uris, initialTarget)
            }
        }
    }

    private suspend fun load(uris: List<Uri>, initialTarget: String?): ImportState {
        val library = library ?: return ImportState.Blocked(string(R.string.import_no_folder), needsFolder = true)
        folders = attempt("Reading the meme folder failed", {
            return ImportState.Blocked(string(R.string.error_reading_folder), needsFolder = true)
        }) {
            library.folderPaths(collator)
        }
        target = (initialTarget ?: store.lastImportFolder).takeIf { it in folders }.orEmpty()
        val images = uris.mapNotNull { importer.describe(it) }
        if (images.isEmpty()) return ImportState.Blocked(string(R.string.import_unsupported))
        names.clear()
        names.addAll(images.map { it.suggestedName })
        return ImportState.Ready(images)
    }

    fun setName(index: Int, value: String) {
        names[index] = value
    }

    fun chooseTarget(path: String) {
        target = path
        if (path !in folders) folders = Library.sortFolderPaths(folders + path, collator)
    }

    fun destinationLabel(): String = breadcrumb(target, rootName)

    fun importNow() {
        val images = (state as? ImportState.Ready)?.images ?: return
        val library = library ?: return
        if (progress != null) return
        progress = imported
        viewModelScope.launch {
            attempt("Copying images failed", {
                progress = null
                LibraryEvents.notifyChanged()
                events.send(ImportEvent.Failed(string(R.string.error_import)))
            }) {
                val label = destinationLabel()
                // After a failure, a retry continues with the images that weren't copied yet.
                for (i in imported until images.size) {
                    val image = images[i]
                    val name = names[i].ifBlank { image.suggestedName }
                    library.importImage(image.uri, target, MemeImporter.memeName(name), image.extension)
                    imported = i + 1
                    progress = imported
                }
                store.lastImportFolder = target
                LibraryEvents.notifyChanged()
                val count = images.size
                events.send(ImportEvent.Done(getApplication<Application>().resources.getQuantityString(R.plurals.added_memes, count, count, label)))
            }
        }
    }

    private fun string(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)
}
