package dev.pawelduraj.memstash.data

/** A folder below the meme folder ("" is the meme folder itself) that holds memes, directly or in its subfolders. */
data class FolderInfo(
    val path: String,
    /** Memes in this folder and all its subfolders. */
    val count: Int,
    /** Memes lying directly in this folder. */
    val direct: Int,
    val hasSubfolders: Boolean,
    /** When a meme from here was last copied; 0 if never. */
    val lastSent: Long,
) {
    val name: String get() = FolderTree.name(path)
}

object FolderTree {
    /** Every folder with memes in it or below it, by path. Folders without memes anywhere below are left out. */
    fun build(memes: List<Meme>, usage: Map<String, Long>): Map<String, FolderInfo> {
        val count = HashMap<String, Int>()
        val direct = HashMap<String, Int>()
        val lastSent = HashMap<String, Long>()
        val withSubfolders = HashSet<String>()
        for (meme in memes) {
            val sent = usage[meme.path] ?: 0L
            direct.merge(meme.folderPath, 1, Int::plus)
            var path = meme.folderPath
            while (true) {
                count.merge(path, 1, Int::plus)
                lastSent.merge(path, sent, ::maxOf)
                if (path.isEmpty()) break
                path = parent(path)
                withSubfolders += path
            }
        }
        return count.mapValues { (path, n) -> FolderInfo(path, n, direct[path] ?: 0, path in withSubfolders, lastSent[path] ?: 0L) }
    }

    fun parent(path: String): String = path.substringBeforeLast('/', "")

    fun name(path: String): String = path.substringAfterLast('/')

    fun child(parent: String, name: String): String = if (parent.isEmpty()) name else "$parent/$name"

    fun segments(path: String): List<String> = if (path.isEmpty()) emptyList() else path.split('/')

    /** True when a meme in [folderPath] is in the folder at [path], directly or in a subfolder. */
    fun contains(path: String, folderPath: String): Boolean =
        path.isEmpty() || folderPath == path || folderPath.startsWith("$path/")
}
