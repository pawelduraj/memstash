package dev.pawelduraj.memstash.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Hands memes over unchanged, under a name whose extension matches the content. */
class Sender(private val context: Context) {
    private val resolver = context.contentResolver
    private val images = Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private val ownCopies = "${Media.RELATIVE_PATH} = ? AND ${Media.OWNER_PACKAGE_NAME} = ?"
    private val ownCopiesArgs = arrayOf(COPY_DIR, context.packageName)

    /**
     * The copy is always named "memstash" and replaces the previous one, so the meme's own name doesn't reach the
     * people it's sent to. Returns false when Android gave it another name because a copy left by an earlier
     * installation holds that one; MemStash isn't allowed to delete it.
     */
    suspend fun toGallery(source: Uri, extension: String, mime: String): Boolean = withContext(Dispatchers.IO) {
        deleteGalleryCopies(olderThanMillis = 0)
        val name = "$COPY_NAME.$extension"
        val values = ContentValues().apply {
            put(Media.DISPLAY_NAME, name)
            put(Media.MIME_TYPE, mime)
            put(Media.RELATIVE_PATH, COPY_DIR)
            put(Media.IS_PENDING, 1)
        }
        val target = resolver.insert(images, values) ?: throw IOException("Can't create the gallery copy")
        try {
            (resolver.openOutputStream(target) ?: throw IOException("Can't write the gallery copy")).use { resolver.copy(source, it) }
            resolver.update(target, ContentValues().apply { put(Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(target, null, null) }
            throw e
        }
        resolver.displayName(target)?.let { it == name } ?: true
    }

    /** Returns how many copies were deleted; failures are ignored, the next run tries again. */
    suspend fun deleteGalleryCopies(olderThanMillis: Long): Int = withContext(Dispatchers.IO) {
        runCatching { expiredCopies(olderThanMillis) }.getOrDefault(emptyList())
            .count { uri -> runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false) }
    }

    /** When the oldest copy still in the gallery was made, or null when there's none. */
    suspend fun oldestCopyTime(): Long? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.query(images, arrayOf(Media.DATE_ADDED), ownCopies, ownCopiesArgs, "${Media.DATE_ADDED} ASC")?.use { c ->
                if (c.moveToFirst()) c.getLong(0) * 1000 else null
            }
        }.getOrNull()
    }

    /** The clip is named like the gallery copy, for the same reason. */
    suspend fun toClipboard(source: Uri, extension: String) {
        val clip = withContext(Dispatchers.IO) {
            val clips = File(context.cacheDir, "clip")
            clips.listFiles()?.sortedByDescending { it.lastModified() }?.drop(KEEP_CLIPS - 1)?.forEach { it.deleteRecursively() }
            // Each clip in its own folder, so a new one doesn't overwrite a clip that may still be being pasted.
            val dir = File(clips, System.nanoTime().toString()).apply { mkdirs() }
            // FileProvider derives the MIME type from the extension, and keyboards need it to recognise an image.
            val file = File(dir, "$COPY_NAME.$extension")
            file.outputStream().use { resolver.copy(source, it) }
            val uri = FileProvider.getUriForFile(context, fileAuthority(context), file)
            ClipData.newUri(resolver, CLIP_LABEL, uri).apply {
                description.extras = PersistableBundle().apply { putBoolean(CLIP_MARKER, true) }
            }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    private fun expiredCopies(olderThanMillis: Long): List<Uri> {
        val cutoffSeconds = (System.currentTimeMillis() - olderThanMillis) / 1000
        val uris = mutableListOf<Uri>()
        resolver.query(
            images,
            arrayOf(Media._ID),
            "$ownCopies AND ${Media.DATE_ADDED} <= ?",
            ownCopiesArgs + cutoffSeconds.toString(),
            null,
        )?.use { c ->
            while (c.moveToNext()) uris += ContentUris.withAppendedId(images, c.getLong(0))
        }
        return uris
    }

    companion object {
        const val COPY_DIR = "Pictures/MemStash/"
        const val CLIP_MARKER = "dev.pawelduraj.memstash.OWN_CLIP"

        private const val COPY_NAME = "memstash"
        private const val CLIP_LABEL = "MemStash"
        private const val KEEP_CLIPS = 5

        fun fileAuthority(context: Context): String = "${context.packageName}.files"
    }
}
