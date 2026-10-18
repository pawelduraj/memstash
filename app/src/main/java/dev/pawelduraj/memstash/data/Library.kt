package dev.pawelduraj.memstash.data

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.system.Os
import android.system.OsConstants
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileDescriptor
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The meme folder picked in the system folder picker, accessed through its documents provider.
 * Folders inside it are identified by their path relative to it, "" being the meme folder itself.
 */
class Library private constructor(context: Context, private val treeUri: Uri) {
    private val resolver = context.contentResolver
    private val rootId: String = DocumentsContract.getTreeDocumentId(treeUri)

    val key: String = treeUri.toString()

    val name: String by lazy {
        if (isOnDevice) rootId.substringAfter(':').substringAfterLast('/')
        else resolver.displayName(documentUri(rootId)) ?: rootId
    }

    val isCopyFolder: Boolean
        get() = isOnDevice && (isCopyFolderId(rootId) || rootId.startsWith("$COPY_FOLDER_ID/", ignoreCase = true))

    val pickerLocation: Uri get() = documentUri(rootId)

    /** False for cloud folders, such as Google Drive, which galleries don't see anyway. */
    val isOnDevice: Boolean get() = treeUri.authority == EXTERNAL_STORAGE

    private class Entry(val id: String, val name: String, val mime: String, val modified: Long) {
        val isFolder: Boolean get() = mime == Document.MIME_TYPE_DIR
    }

    fun documentUri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    /** True when the gallery copies' folder is inside the meme folder, as with Pictures on the phone's storage. */
    val holdsCopyFolder: Boolean get() = isOnDevice && COPY_FOLDER_ID.startsWith("$rootId/", ignoreCase = true)

    fun location(context: Context): String {
        val volume = volume(context) ?: return name
        val path = rootId.substringAfter(':')
        val storage = volume.getDescription(context)
        return if (path.isEmpty()) storage else "$storage/$path"
    }

    /** The meme folder as a path, for the media scanner; null when it isn't on this phone's storage. */
    fun directory(context: Context): File? = volume(context)?.directory?.let { File(it, rootId.substringAfter(':')) }

    suspend fun isHiddenFromGallery(): Boolean = withContext(Dispatchers.IO) {
        runCatching { children(rootId, includeHidden = true).any { it.name == NO_MEDIA } }.getOrDefault(false)
    }

    /** Adds or removes the .nomedia file in the meme folder, which hides it and its subfolders from galleries. */
    suspend fun setHiddenFromGallery(hidden: Boolean) = withContext(Dispatchers.IO) {
        val noMedia = children(rootId, includeHidden = true).firstOrNull { it.name == NO_MEDIA }
        if (hidden && noMedia == null) createFile(rootId, NO_MEDIA) {}
        if (!hidden && noMedia != null && !DocumentsContract.deleteDocument(resolver, documentUri(noMedia.id))) {
            throw IOException("Can't delete $NO_MEDIA")
        }
    }

    /** Lists every meme below the meme folder; [onFolder] gets the memes found so far after each folder that has some. */
    suspend fun scan(previous: List<Meme>, onFolder: suspend (List<Meme>) -> Unit = {}): List<Meme> = withContext(Dispatchers.IO) {
        val known = previous.associateBy { it.id }
        // A provider can list one file in several folders; it's still one meme.
        val result = LinkedHashMap<String, Meme>()
        walk { folderId, path ->
            ensureActive()
            val entries = readableChildren(folderId)
            val images = entries.filter { !it.isFolder && ImageTypes.isImage(it.name) }
            images.forEach { file ->
                result.getOrPut(file.id) {
                    val old = known[file.id]
                    if (old != null && old.ratio > 0f && old.modified == file.modified && old.name == file.name && old.folderPath == path) old
                    else probe(file.id).let { Meme(file.id, file.name, it.mime ?: ImageTypes.mimeOf(file.name), file.modified, path, it.ratio) }
                }
            }
            if (images.isNotEmpty()) onFolder(result.values.toList())
            entries
        }
        result.values.toList()
    }

    suspend fun folderPaths(order: Comparator<in String>): List<String> = withContext(Dispatchers.IO) {
        val paths = mutableListOf<String>()
        walk { folderId, path ->
            paths += path
            readableChildren(folderId, refresh = false)
        }
        // Some providers allow two folders with the same name; they share one path.
        sortFolderPaths(paths.distinct(), order)
    }

    suspend fun importImage(source: Uri, folderPath: String, base: String, extension: String): String = withContext(Dispatchers.IO) {
        val folderId = folderId(folderPath, create = true)
        val fileName = MemeImporter.uniqueName(base, extension, takenNames(folderId))
        copyInto(source, folderId, fileName)
        fileName
    }

    suspend fun rename(meme: Meme, newBase: String): Meme = withContext(Dispatchers.IO) {
        val extension = meme.matchingExtension
        val folderId = folderId(meme.folderPath, create = false)
        val fileName = MemeImporter.uniqueName(newBase, extension, takenNames(folderId) - meme.name.lowercase())
        if (fileName == meme.name) return@withContext meme
        val renamed = renameKeepingCase(documentUri(meme.id), meme.name, fileName, tempSuffix = ".$extension")
        meme.copy(id = DocumentsContract.getDocumentId(renamed), name = resolver.displayName(renamed) ?: fileName)
    }

    suspend fun move(meme: Meme, folderPath: String): Meme = withContext(Dispatchers.IO) {
        if (folderPath == meme.folderPath) return@withContext meme
        val fromId = folderId(meme.folderPath, create = false)
        val toId = folderId(folderPath, create = true)
        // A clashing file is renamed before the move, so the new name must be free in both folders.
        val taken = takenNames(toId) + (takenNames(fromId) - meme.name.lowercase())
        val fileName = MemeImporter.uniqueName(meme.title, meme.name.substringAfterLast('.', ""), taken)
        var uri = documentUri(meme.id)
        if (fileName != meme.name) uri = renameDocument(uri, fileName)
        val moved = try {
            runCatching { DocumentsContract.moveDocument(resolver, uri, documentUri(fromId), documentUri(toId)) }.getOrNull()
                ?: copyThenDelete(uri, toId, fileName)
        } catch (e: Exception) {
            if (fileName != meme.name) runCatching { renameDocument(uri, meme.name) }
            throw e
        }
        meme.copy(id = DocumentsContract.getDocumentId(moved), name = resolver.displayName(moved) ?: fileName, folderPath = folderPath)
    }

    /** Renames the folder at [path] (never the meme folder itself); null when [newName] is its name already. */
    suspend fun renameFolder(path: String, newName: String): RenamedFolder? = withContext(Dispatchers.IO) {
        require(path.isNotEmpty())
        val parent = FolderTree.parent(path)
        val name = FolderTree.name(path)
        if (newName == name) return@withContext null
        val id = folderId(path, create = false)
        if (children(folderId(parent, create = false)).any { it.id != id && it.name.equals(newName, ignoreCase = true) }) {
            throw NameTakenException()
        }
        val renamed = renameKeepingCase(documentUri(id), name, newName, tempSuffix = "")
        RenamedFolder(path, FolderTree.child(parent, resolver.displayName(renamed) ?: newName), id, DocumentsContract.getDocumentId(renamed))
    }

    class RenamedFolder(private val oldPath: String, val path: String, private val oldId: String, private val newId: String) {
        /** The meme as it is after the rename, without reading the folder again. */
        fun rebase(meme: Meme): Meme {
            if (!FolderTree.contains(oldPath, meme.folderPath)) return meme
            // Phone storage builds document ids from paths, so the files inside get new ones; cloud providers keep theirs.
            val id = if (meme.id.startsWith("$oldId/")) newId + meme.id.removePrefix(oldId) else meme.id
            return meme.copy(id = id, folderPath = rebase(meme.folderPath))
        }

        fun rebase(folderPath: String): String =
            if (FolderTree.contains(oldPath, folderPath)) path + folderPath.removePrefix(oldPath) else folderPath
    }

    /**
     * Everything below the folder at [path] that MemStash doesn't show as a meme, by its path inside that folder:
     * other files, hidden files and whatever lies in hidden folders.
     */
    suspend fun otherFiles(path: String): List<String> = withContext(Dispatchers.IO) {
        val result = mutableListOf<String>()
        walk(folderId(path, create = false)) { folderId, inside ->
            ensureActive()
            val hiddenFolder = FolderTree.segments(inside).any { it.startsWith(".") }
            children(folderId, includeHidden = true).also { entries ->
                entries.filter { !it.isFolder && (hiddenFolder || it.name.startsWith(".") || !ImageTypes.isImage(it.name)) }
                    .forEach { result += FolderTree.child(inside, it.name) }
            }
        }
        result
    }

    /** Deletes the folder at [path] (never the meme folder itself) with everything in it. */
    suspend fun deleteFolder(path: String) = withContext(Dispatchers.IO) {
        require(path.isNotEmpty())
        if (!DocumentsContract.deleteDocument(resolver, documentUri(folderId(path, create = false)))) throw IOException("Can't delete $path")
    }

    /** The folder at [path] as a document, for opening it in a file manager. */
    suspend fun folderDocument(path: String): Uri = withContext(Dispatchers.IO) {
        DocumentsContract.buildDocumentUri(treeUri.authority, folderId(path, create = false))
    }

    class NameTakenException : IOException("Name taken")

    suspend fun exists(meme: Meme): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.query(documentUri(meme.id), arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } == true
        }.getOrDefault(false)
    }

    suspend fun delete(meme: Meme) = withContext(Dispatchers.IO) {
        if (!DocumentsContract.deleteDocument(resolver, documentUri(meme.id))) throw IOException("Can't delete ${meme.name}")
    }

    private inline fun walk(startId: String = rootId, visit: (folderId: String, path: String) -> List<Entry>) {
        // Some providers can show one folder in several places; each is visited once, so a loop can't hang the walk.
        val visited = hashSetOf(startId)
        val queue = ArrayDeque(listOf(startId to ""))
        while (queue.isNotEmpty()) {
            val (folderId, path) = queue.removeFirst()
            visit(folderId, path).filter { it.isFolder && visited.add(it.id) }.forEach {
                queue += it.id to FolderTree.child(path, it.name)
            }
        }
    }

    private fun children(folderId: String, refresh: Boolean = false, includeHidden: Boolean = false): List<Entry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)
        // Google Drive keeps an old listing until it syncs on its own, which can take minutes; this asks it to sync
        // now, as pulling down in the Files app does, so changes made elsewhere show up on the next scan.
        if (refresh && !isOnDevice) runCatching { resolver.refresh(documentUri(folderId), null, null) }
        val deadline = SystemClock.elapsedRealtime() + LOADING_TIMEOUT_MILLIS
        while (true) {
            val cursor = resolver.query(uri, COLUMNS, null, null, null) ?: throw FileNotFoundException("Can't open $folderId")
            cursor.use { c ->
                val left = deadline - SystemClock.elapsedRealtime()
                // Cloud providers such as Google Drive first return what they have cached, marked as loading, and
                // report a change once the folder is fetched.
                if (!c.extras.getBoolean(DocumentsContract.EXTRA_LOADING) || left <= 0) return entries(c, includeHidden)
                c.awaitChange(minOf(left, LOADING_RECHECK_MILLIS))
            }
        }
    }

    private fun entries(c: Cursor, includeHidden: Boolean): List<Entry> {
        val result = mutableListOf<Entry>()
        while (c.moveToNext()) {
            val id = c.getString(0)
            val name = c.getString(1).orEmpty()
            if (name.isNotEmpty() && (includeHidden || !name.startsWith(".")) && !isCopyFolderId(id)) {
                result += Entry(id, name, c.getString(2).orEmpty(), c.getLong(3))
            }
        }
        return result
    }

    /** A subfolder that can't be read is skipped; the meme folder itself has to be readable. */
    private fun readableChildren(folderId: String, refresh: Boolean = true): List<Entry> =
        if (folderId == rootId) children(folderId, refresh)
        else runCatching { children(folderId, refresh) }.onFailure { Log.w(TAG, "Skipping a folder that can't be read", it) }.getOrDefault(emptyList())

    private fun volume(context: Context): StorageVolume? {
        if (!isOnDevice) return null
        val volumeId = rootId.substringBefore(':')
        return context.getSystemService(StorageManager::class.java).storageVolumes.firstOrNull {
            if (volumeId == PRIMARY_VOLUME) it.isPrimary else it.uuid.equals(volumeId, ignoreCase = true)
        }
    }

    private fun takenNames(folderId: String): Set<String> = children(folderId).mapTo(HashSet()) { it.name.lowercase() }

    /**
     * Document id of a folder below the meme folder; with [create], missing folders on the way are created. Looked
     * up anew each time, because folders can be renamed or deleted in other apps. A meme's own folder isn't
     * created, so one deleted in another app doesn't come back empty.
     */
    private fun folderId(path: String, create: Boolean): String {
        if (path.isEmpty()) return rootId
        val parentId = folderId(FolderTree.parent(path), create)
        val name = FolderTree.name(path)
        // Phone storage ignores case, but cloud providers can hold "Cats" and "cats" side by side.
        val folders = children(parentId).filter { it.isFolder }
        val existing = (folders.firstOrNull { it.name == name } ?: folders.firstOrNull { it.name.equals(name, ignoreCase = true) })?.id
        return when {
            existing != null -> existing
            !create -> throw FileNotFoundException("No folder $path")
            else -> DocumentsContract.createDocument(resolver, documentUri(parentId), Document.MIME_TYPE_DIR, name)
                ?.let(DocumentsContract::getDocumentId)
                ?: throw IOException("Can't create folder $name")
        }
    }

    private fun copyInto(source: Uri, folderId: String, fileName: String): Uri =
        createFile(folderId, fileName) { resolver.copy(source, it) }

    /** Creates [fileName] in a folder; nothing is left behind if writing fails. */
    private fun createFile(folderId: String, fileName: String, write: (OutputStream) -> Unit): Uri {
        // The provider derives the extension from the MIME type, so the type must match the name.
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ImageTypes.extension(fileName)) ?: "application/octet-stream"
        val target = DocumentsContract.createDocument(resolver, documentUri(folderId), mime, fileName)
            ?: throw IOException("Can't create $fileName")
        try {
            (resolver.openOutputStream(target, "w") ?: throw IOException("Can't write $fileName")).use(write)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            throw e
        }
        return target
    }

    private fun copyThenDelete(source: Uri, folderId: String, fileName: String): Uri {
        val copy = copyInto(source, folderId, fileName)
        if (!runCatching { DocumentsContract.deleteDocument(resolver, source) }.getOrDefault(false)) {
            runCatching { DocumentsContract.deleteDocument(resolver, copy) }
            throw IOException("Can't remove $fileName from its old folder")
        }
        return copy
    }

    private fun renameDocument(uri: Uri, name: String): Uri =
        DocumentsContract.renameDocument(resolver, uri, name) ?: throw IOException("Can't rename to $name")

    /** Shared storage ignores case, so a rename that only changes case goes through a temporary name. */
    private fun renameKeepingCase(uri: Uri, from: String, to: String, tempSuffix: String): Uri {
        if (!to.equals(from, ignoreCase = true)) return renameDocument(uri, to)
        val temp = renameDocument(uri, ".rename-${System.nanoTime()}$tempSuffix")
        return try {
            renameDocument(temp, to)
        } catch (e: Exception) {
            runCatching { renameDocument(temp, from) }
            throw e
        }
    }

    private class Probe(val mime: String?, val ratio: Float)

    /**
     * The format from the file's first bytes (the extension may be wrong) and its width divided by height. A file
     * that can't be read gets ratio 0, not read yet, so the next scan tries again.
     */
    private fun probe(id: String): Probe {
        val pfd = runCatching { resolver.openFileDescriptor(documentUri(id), "r") }.getOrNull() ?: return Probe(null, 0f)
        return pfd.use {
            val fd = pfd.fileDescriptor
            val head = runCatching { ImageTypes.readHead { buffer, offset, size -> Os.read(fd, buffer, offset, size) } }
                .getOrElse { return Probe(null, 0f) }
            // The format is known by now; a size that can't be read leaves the meme square.
            Probe(ImageTypes.sniffMime(head), runCatching { ratio(fd) }.getOrDefault(1f))
        }
    }

    private fun ratio(fd: FileDescriptor): Float {
        Os.lseek(fd, 0, OsConstants.SEEK_SET)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFileDescriptor(fd, null, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return 1f
        val ratio = options.outWidth.toFloat() / options.outHeight
        val orientation = runCatching {
            Os.lseek(fd, 0, OsConstants.SEEK_SET)
            ExifInterface(fd).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return if (orientation in SIDEWAYS) 1f / ratio else ratio
    }

    companion object {
        private const val TAG = "MemStash"
        private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
        private const val NO_MEDIA = ".nomedia"
        private const val PRIMARY_VOLUME = "primary"
        private const val LOADING_TIMEOUT_MILLIS = 30_000L
        private const val LOADING_RECHECK_MILLIS = 2_000L
        private val COPY_FOLDER_ID = "$PRIMARY_VOLUME:" + Sender.COPY_DIR.trimEnd('/')
        private val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
        )
        private val SIDEWAYS = setOf(
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE,
        )

        /** Returns null when no folder was picked or Android no longer grants access to it. */
        fun open(context: Context, treeUri: String?): Library? {
            val uri = treeUri?.toUri() ?: return null
            val granted = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
            return if (granted) Library(context, uri) else null
        }

        fun sortFolderPaths(paths: Collection<String>, order: Comparator<in String>): List<String> =
            paths.sortedWith { a, b ->
                val x = FolderTree.segments(a)
                val y = FolderTree.segments(b)
                x.zip(y).firstNotNullOfOrNull { (p, q) -> order.compare(p, q).takeIf { it != 0 } } ?: x.size.compareTo(y.size)
            }

        private fun isCopyFolderId(id: String) = id.equals(COPY_FOLDER_ID, ignoreCase = true)
    }
}

/** Waits until the provider reports a change to this cursor's data, at most [timeoutMillis]. */
private fun Cursor.awaitChange(timeoutMillis: Long) {
    val changed = CountDownLatch(1)
    val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) = changed.countDown()
    }
    registerContentObserver(observer)
    try {
        changed.await(timeoutMillis, TimeUnit.MILLISECONDS)
    } finally {
        unregisterContentObserver(observer)
    }
}
