package dev.pawelduraj.memstash.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SharedImage(val uri: Uri, val suggestedName: String, val extension: String)

class MemeImporter(context: Context) {
    private val resolver = context.contentResolver

    /**
     * Returns null when [uri] is not a supported image. The format comes from the content when it can be read, and
     * the extension is always the format's usual one, e.g. "jpg" also for "photo.jpeg".
     */
    suspend fun describe(uri: Uri): SharedImage? = withContext(Dispatchers.IO) {
        val displayName = resolver.displayName(uri) ?: uri.lastPathSegment.orEmpty()
        val current = displayName.substringAfterLast('.', "")
        val head = readHead(uri)
        // Every supported format has a recognisable start, so readable content in another format (e.g. a video
        // shared as a GIF) isn't a meme, whatever its name or type says. Those decide only when it can't be read.
        val mime = if (head != null) {
            ImageTypes.sniffMime(head) ?: return@withContext null
        } else {
            ImageTypes.canonicalMime(runCatching { resolver.getType(uri) }.getOrNull())
                ?: ImageTypes.mimeForExtension(current)
                ?: return@withContext null
        }
        SharedImage(uri, ImageTypes.baseName(displayName).substringAfterLast('/'), ImageTypes.extensionFor(mime) ?: current)
    }

    /** False when the image can't be opened, e.g. a clip whose app no longer provides it. */
    suspend fun canRead(uri: Uri): Boolean = withContext(Dispatchers.IO) { readHead(uri) != null }

    private fun readHead(uri: Uri): ByteArray? = runCatching {
        resolver.openInputStream(uri)?.use { ImageTypes.readHead(it::read) }
    }.getOrNull()

    companion object {
        /** Characters that memory cards (FAT) and Windows don't allow in names. */
        val UNSAFE_CHARS = "[\\\\/:*?\"<>|\\p{Cntrl}]".toRegex()
        private val SPACES = "\\s+".toRegex()
        private const val MAX_NAME_LENGTH = 120

        /** Memory cards and Windows also drop dots and spaces at the end of a name, so they're removed here. */
        fun safeName(name: String): String =
            name.replace(UNSAFE_CHARS, "_")
                .replace(SPACES, " ")
                .trim()
                .trimStart('.')
                .take(MAX_NAME_LENGTH)
                // The cut mustn't split an emoji, or the name can't be saved.
                .let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
                .trimEnd('.', ' ')
                .ifEmpty { "mem" }

        /** A file name (without extension) for a meme; an image extension typed with the name is dropped. */
        fun memeName(name: String): String {
            val trimmed = name.trim()
            return safeName(if (ImageTypes.isImage(trimmed)) trimmed.substringBeforeLast('.') else trimmed)
        }

        /** [taken] must hold lower-case file names, because shared storage ignores case. */
        fun uniqueName(base: String, extension: String, taken: Set<String>): String {
            var name = "$base.$extension"
            var n = 2
            while (name.lowercase() in taken) name = "$base-${n++}.$extension"
            return name
        }
    }
}
