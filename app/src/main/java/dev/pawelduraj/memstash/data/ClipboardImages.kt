package dev.pawelduraj.memstash.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri

class ClipboardImages(context: Context) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)

    /**
     * When the clipboard holds an image copied in another app, the time it was copied. Reads only the clip's
     * description, which doesn't trigger Android's clipboard access notice. Works only while the window has focus.
     */
    fun pendingImage(): Long? {
        val description = clipboard.primaryClipDescription ?: return null
        if (!description.hasMimeType("image/*") || description.isOwnClip) return null
        return description.timestamp
    }

    /** The images on the clipboard. Reading them makes Android show its clipboard access notice. */
    fun images(): List<Uri> {
        val clip = clipboard.primaryClip ?: return emptyList()
        if (clip.description.isOwnClip) return emptyList()
        return clip.uris().filter { it.scheme == ContentResolver.SCHEME_CONTENT }
    }

    private val ClipDescription.isOwnClip: Boolean get() = extras?.getBoolean(Sender.CLIP_MARKER) == true
}

fun ClipData.uris(): List<Uri> = (0 until itemCount).mapNotNull { getItemAt(it).uri }
