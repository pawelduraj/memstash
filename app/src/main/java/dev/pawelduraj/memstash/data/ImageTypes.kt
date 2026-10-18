package dev.pawelduraj.memstash.data

object ImageTypes {
    const val HEAD_SIZE = 16

    private val MIMES = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "avif" to "image/avif",
        "bmp" to "image/bmp",
    )

    private val ALIASES = mapOf(
        "image/jpg" to "jpg",
        "image/pjpeg" to "jpg",
        "image/heic-sequence" to "heic",
        "image/heif-sequence" to "heif",
        "image/x-ms-bmp" to "bmp",
        "image/x-bmp" to "bmp",
    )

    private val FORMAT_NAMES = mapOf(
        "image/jpeg" to "JPEG",
        "image/png" to "PNG",
        "image/gif" to "GIF",
        "image/webp" to "WebP",
        "image/heic" to "HEIC",
        "image/heif" to "HEIF",
        "image/avif" to "AVIF",
        "image/bmp" to "BMP",
    )

    /** HEIF brands that mean HEVC inside, i.e. HEIC; "mif1" and "msf1" are plain HEIF, whatever the codec. */
    private val HEVC_BRANDS = listOf("heic", "heix", "hevc", "hevx", "heim", "heis")
    private val HEIF_BRANDS = listOf("mif1", "msf1")

    /** A letter and up to four more letters or digits, e.g. "jpg", "jfif" or "php", but not "5" in "v1.5". */
    private val EXTENSION_LIKE = "[A-Za-z][A-Za-z0-9]{1,4}".toRegex()

    fun isImage(name: String): Boolean = extension(name) in MIMES

    fun mimeOf(name: String): String = MIMES[extension(name)] ?: "image/jpeg"

    fun mimeForExtension(extension: String): String? = MIMES[extension.lowercase()]

    fun extensionFor(mime: String?): String? {
        val type = mime?.lowercase() ?: return null
        return ALIASES[type] ?: MIMES.entries.firstOrNull { it.value == type }?.key
    }

    /** The extension for [mime]: [current] when it already names that format (e.g. "jpeg" or "JPG"), otherwise the usual one. */
    fun extensionFor(mime: String, current: String): String {
        val currentMime = MIMES[current.lowercase()]
        return if (currentMime != null && family(currentMime) == family(mime)) current else extensionFor(mime) ?: current
    }

    /** [name] without its extension, when it ends with something that looks like one. */
    fun baseName(name: String): String {
        val extension = name.substringAfterLast('.', "")
        return if (extension.matches(EXTENSION_LIKE) && name.length > extension.length + 1) name.substringBeforeLast('.') else name
    }

    fun formatName(mime: String): String = FORMAT_NAMES[mime] ?: mime.substringAfter('/').uppercase()

    /** The file's first [HEAD_SIZE] bytes, fewer if it's shorter; [read] works like InputStream.read. */
    fun readHead(read: (buffer: ByteArray, offset: Int, size: Int) -> Int): ByteArray {
        val head = ByteArray(HEAD_SIZE)
        var filled = 0
        while (filled < head.size) {
            val n = read(head, filled, head.size - filled)
            if (n <= 0) break
            filled += n
        }
        return head.copyOf(filled)
    }

    fun sniffMime(head: ByteArray): String? = sniff(head)?.let(::mimeForExtension)

    /** [mime] as named here, e.g. "image/jpeg" for "image/pjpeg"; null when it isn't a supported image. */
    fun canonicalMime(mime: String?): String? = extensionFor(mime)?.let(::mimeForExtension)

    /** Recognises the format by the file's first bytes; returns its usual extension. */
    fun sniff(head: ByteArray): String? {
        fun bytes(offset: Int, vararg values: Int) =
            head.size >= offset + values.size && values.indices.all { head[offset + it] == values[it].toByte() }
        fun text(offset: Int, value: String) = bytes(offset, *value.map { it.code }.toIntArray())
        return when {
            bytes(0, 0xFF, 0xD8, 0xFF) -> "jpg"
            bytes(0, 0x89) && text(1, "PNG") -> "png"
            text(0, "GIF8") -> "gif"
            text(0, "RIFF") && text(8, "WEBP") -> "webp"
            text(4, "ftyp") && (text(8, "avif") || text(8, "avis")) -> "avif"
            text(4, "ftyp") && HEVC_BRANDS.any { text(8, it) } -> "heic"
            text(4, "ftyp") && HEIF_BRANDS.any { text(8, it) } -> "heif"
            text(0, "BM") -> "bmp"
            else -> null
        }
    }

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()

    /** HEIC is HEIF with HEVC inside; both extensions fit either. */
    private fun family(mime: String) = if (mime == "image/heif") "image/heic" else mime
}
