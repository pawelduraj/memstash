package dev.pawelduraj.memstash.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.io.OutputStream

fun ContentResolver.displayName(uri: Uri): String? = runCatching {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()

fun ContentResolver.copy(source: Uri, out: OutputStream) {
    (openInputStream(source) ?: throw IOException("Can't read $source")).use { it.copyTo(out) }
}
