package dev.pawelduraj.memstash.data

data class Meme(
    val id: String,
    val name: String,
    val mime: String,
    val modified: Long,
    val folderPath: String,
    /** Width divided by height, or 0 when not read yet. */
    val ratio: Float = 0f,
    /** The image's key in the image cache; kept when the file is renamed or moved, so its picture doesn't load again. */
    val imageKey: String = id,
) {
    val path: String get() = FolderTree.child(folderPath, name)

    /** The name without the file extension, as shown to the user. */
    val title: String get() = name.substringBeforeLast('.')

    /** The file's own extension, as it is written. */
    val extension: String get() = name.substringAfterLast('.', "")

    /** An extension that matches the content: the file's own when it does, otherwise the format's usual one. */
    val matchingExtension: String get() = ImageTypes.extensionFor(mime, extension)

    val matchingName: String get() = "$title.$matchingExtension"

    /** False when the extension names another format; that is only shown, the file stays as it is. */
    val extensionFits: Boolean get() = matchingExtension == extension

    /** Copies go out as the file is named, in lowercase like the rest of the copy's name. */
    val copyExtension: String get() = ImageTypes.extension(name).ifEmpty { matchingExtension }

    /** The type the copy's extension names; the gallery renames a copy whose name and type disagree. */
    val copyMime: String get() = ImageTypes.mimeForExtension(copyExtension) ?: mime

    val nameWords: List<String> by lazy { MemeSearch.words(title) }
    val nameJoined: String by lazy { nameWords.joinToString("") }
    val folderWords: List<String> by lazy { MemeSearch.words(folderPath) }

    val displayRatio: Float get() = if (ratio > 0f) ratio.coerceIn(0.4f, 2.5f) else 1f
}
