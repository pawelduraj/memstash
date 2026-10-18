package dev.pawelduraj.memstash.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SendMode { GALLERY, CLIPBOARD }

enum class SortOrder { NAME, NEWEST }

enum class GridMode { SQUARE, FULL }

/** When the gallery copy is deleted, if the next one hasn't replaced it yet: some minutes after copying, or when MemStash opens. */
enum class CopyCleanup(val minutes: Int = 0) {
    AFTER_1_MIN(1),
    AFTER_2_MIN(2),
    AFTER_3_MIN(3),
    AFTER_5_MIN(5),
    AFTER_10_MIN(10),
    ON_OPEN;

    val lifetimeMillis: Long? get() = if (minutes > 0) minutes * 60_000L else null
}

data class Settings(
    val rootUri: String? = null,
    val sendMode: SendMode = SendMode.GALLERY,
    val columns: Int = 3,
    val sortOrder: SortOrder = SortOrder.NAME,
    val focusSearchOnOpen: Boolean = false,
    val gridMode: GridMode = GridMode.SQUARE,
    val copyCleanup: CopyCleanup = CopyCleanup.AFTER_3_MIN,
    val closeCompletely: Boolean = true,
) {
    companion object {
        val COLUMN_RANGE = 2..6
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val usagePrefs = context.getSharedPreferences("usage", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    /** Read on first use: the import screen and the cleanup worker don't need the history. */
    private val _usage by lazy { MutableStateFlow(readUsage()) }
    /** Meme path to the last time it was sent. */
    val usage: StateFlow<Map<String, Long>> by lazy { _usage.asStateFlow() }

    var tileOffered: Boolean
        get() = prefs.getBoolean(KEY_TILE_OFFERED, false)
        set(value) = prefs.edit { putBoolean(KEY_TILE_OFFERED, value) }

    var lastImportFolder: String
        get() = prefs.getString(KEY_LAST_IMPORT_FOLDER, null).orEmpty()
        set(value) = prefs.edit { putString(KEY_LAST_IMPORT_FOLDER, value) }

    /** The meme folder the copy history belongs to. */
    var historyFolder: String?
        get() = prefs.getString(KEY_HISTORY_FOLDER, null)
        set(value) = prefs.edit { putString(KEY_HISTORY_FOLDER, value) }

    /** Copy time of the clipboard image the user last added or dismissed. */
    var handledClip: Long
        get() = prefs.getLong(KEY_HANDLED_CLIP, -1L)
        set(value) = prefs.edit { putLong(KEY_HANDLED_CLIP, value) }

    /** The last clip checked for an image that can be added; reading a clip makes Android show a notice. */
    var checkedClip: Long
        get() = prefs.getLong(KEY_CHECKED_CLIP, -1L)
        set(value) = prefs.edit { putLong(KEY_CHECKED_CLIP, value) }

    var checkedClipUsable: Boolean
        get() = prefs.getBoolean(KEY_CHECKED_CLIP_USABLE, false)
        set(value) = prefs.edit { putBoolean(KEY_CHECKED_CLIP_USABLE, value) }

    fun update(transform: (Settings) -> Settings) {
        val new = transform(_settings.value)
        prefs.edit {
            putString(KEY_ROOT_URI, new.rootUri)
            putString(KEY_MODE, new.sendMode.name)
            putInt(KEY_COLUMNS, new.columns)
            putString(KEY_SORT, new.sortOrder.name)
            putBoolean(KEY_FOCUS, new.focusSearchOnOpen)
            putString(KEY_GRID_MODE, new.gridMode.name)
            putString(KEY_COPY_CLEANUP, new.copyCleanup.name)
            putBoolean(KEY_CLOSE_COMPLETELY, new.closeCompletely)
        }
        _settings.value = new
    }

    @Synchronized
    fun recordUsage(path: String) {
        val usage = _usage.value + (path to System.currentTimeMillis())
        val stale = usage.entries.sortedByDescending { it.value }.drop(MAX_USAGE_ENTRIES).map { it.key }
        usagePrefs.edit {
            putLong(path, usage.getValue(path))
            stale.forEach { remove(it) }
        }
        _usage.value = usage - stale.toSet()
    }

    @Synchronized
    fun moveUsage(from: String, to: String) {
        val time = _usage.value[from] ?: return
        usagePrefs.edit {
            remove(from)
            putLong(to, time)
        }
        _usage.value = _usage.value - from + (to to time)
    }

    /** Moves the history of the memes in folder [from] and its subfolders to folder [to]. */
    @Synchronized
    fun moveFolderUsage(from: String, to: String) {
        val moved = _usage.value.filterKeys { it.startsWith("$from/") }
        val renamed = moved.mapKeys { to + it.key.removePrefix(from) }
        usagePrefs.edit {
            moved.keys.forEach { remove(it) }
            renamed.forEach { (path, time) -> putLong(path, time) }
        }
        _usage.value = _usage.value - moved.keys + renamed
    }

    @Synchronized
    fun forgetFolderUsage(path: String) {
        val gone = _usage.value.keys.filter { it.startsWith("$path/") }.toSet()
        usagePrefs.edit { gone.forEach { remove(it) } }
        _usage.value = _usage.value - gone
    }

    @Synchronized
    fun clearUsage() {
        usagePrefs.edit { clear() }
        _usage.value = emptyMap()
    }

    @Synchronized
    fun forgetUsage(path: String) {
        usagePrefs.edit { remove(path) }
        _usage.value = _usage.value - path
    }

    private fun readUsage(): Map<String, Long> =
        usagePrefs.all.mapNotNull { (key, value) -> (value as? Long)?.let { key to it } }.toMap()

    private fun read(): Settings {
        val defaults = Settings()
        return Settings(
            rootUri = prefs.getString(KEY_ROOT_URI, defaults.rootUri),
            sendMode = enumOrDefault(prefs.getString(KEY_MODE, null), defaults.sendMode),
            columns = prefs.getInt(KEY_COLUMNS, defaults.columns).coerceIn(Settings.COLUMN_RANGE),
            sortOrder = enumOrDefault(prefs.getString(KEY_SORT, null), defaults.sortOrder),
            focusSearchOnOpen = prefs.getBoolean(KEY_FOCUS, defaults.focusSearchOnOpen),
            gridMode = enumOrDefault(prefs.getString(KEY_GRID_MODE, null), defaults.gridMode),
            copyCleanup = enumOrDefault(prefs.getString(KEY_COPY_CLEANUP, null), defaults.copyCleanup),
            closeCompletely = prefs.getBoolean(KEY_CLOSE_COMPLETELY, defaults.closeCompletely),
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val KEY_ROOT_URI = "root_uri"
        const val KEY_MODE = "send_mode"
        const val KEY_COLUMNS = "columns"
        const val KEY_SORT = "sort_order"
        const val KEY_FOCUS = "focus_search_on_open"
        const val KEY_GRID_MODE = "grid_mode"
        const val KEY_COPY_CLEANUP = "copy_cleanup"
        const val KEY_TILE_OFFERED = "tile_offered"
        const val KEY_CLOSE_COMPLETELY = "close_completely"
        const val KEY_LAST_IMPORT_FOLDER = "last_import_folder"
        const val KEY_HANDLED_CLIP = "handled_clip"
        const val KEY_CHECKED_CLIP = "checked_clip"
        const val KEY_CHECKED_CLIP_USABLE = "checked_clip_usable"
        const val KEY_HISTORY_FOLDER = "history_folder"
        const val MAX_USAGE_ENTRIES = 300
    }
}
