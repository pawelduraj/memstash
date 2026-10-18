package dev.pawelduraj.memstash.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class IndexCache(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "index.tsv"))

    suspend fun load(key: String): List<Meme> = withContext(Dispatchers.IO) {
        runCatching {
            file.openRead().bufferedReader().useLines { lines ->
                val iterator = lines.iterator()
                if (!iterator.hasNext() || iterator.next() != key) return@useLines emptyList()
                iterator.asSequence().mapNotNull { line ->
                    val f = line.split('\t')
                    if (f.size < 6) null else Meme(f[0], f[1], f[2], f[3].toLongOrNull() ?: 0L, f[4], f[5].toFloatOrNull() ?: 0f)
                }.toList()
            }
        }.getOrDefault(emptyList())
    }

    fun clear() = file.delete()

    suspend fun save(key: String, memes: List<Meme>) = withContext(Dispatchers.IO) {
        val out = runCatching { file.startWrite() }.getOrNull() ?: return@withContext
        try {
            val writer = out.bufferedWriter()
            writer.appendLine(key)
            memes.filter { m -> listOf(m.id, m.name, m.folderPath).none { '\t' in it || '\n' in it || '\r' in it } }.forEach {
                writer.appendLine("${it.id}\t${it.name}\t${it.mime}\t${it.modified}\t${it.folderPath}\t${it.ratio}")
            }
            writer.flush()
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }
}
