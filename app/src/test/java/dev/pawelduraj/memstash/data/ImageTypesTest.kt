package dev.pawelduraj.memstash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageTypesTest {

    @Test
    fun extensionForMimeTypes() {
        assertEquals("jpg", ImageTypes.extensionFor("image/jpeg"))
        assertEquals("jpg", ImageTypes.extensionFor("image/jpg"))
        assertEquals("webp", ImageTypes.extensionFor("IMAGE/WEBP"))
        assertEquals("heic", ImageTypes.extensionFor("image/heic"))
        assertEquals("heif", ImageTypes.extensionFor("image/heif"))
        assertEquals("bmp", ImageTypes.extensionFor("image/x-ms-bmp"))
        assertNull(ImageTypes.extensionFor("image/svg+xml"))
        assertNull(ImageTypes.extensionFor(null))
    }

    @Test
    fun matchingExtensionIsKeptOtherwiseReplaced() {
        assertEquals("jpeg", ImageTypes.extensionFor("image/jpeg", "jpeg"))
        assertEquals("JPG", ImageTypes.extensionFor("image/jpeg", "JPG"))
        assertEquals("heif", ImageTypes.extensionFor("image/heic", "heif"))
        assertEquals("webp", ImageTypes.extensionFor("image/webp", "jpg"))
        assertEquals("png", ImageTypes.extensionFor("image/png", "php"))
        assertEquals("gif", ImageTypes.extensionFor("image/gif", ""))
    }

    @Test
    fun baseNameDropsWhatLooksLikeAnExtension() {
        assertEquals("cat", ImageTypes.baseName("cat.jpg"))
        assertEquals("cat", ImageTypes.baseName("cat.jfif"))
        assertEquals("download", ImageTypes.baseName("download.php"))
        assertEquals("IMG_1234", ImageTypes.baseName("IMG_1234"))
        assertEquals("v1.5", ImageTypes.baseName("v1.5"))
        assertEquals("Mr. Bean", ImageTypes.baseName("Mr. Bean"))
        assertEquals("a.b", ImageTypes.baseName("a.b.png"))
    }

    @Test
    fun sniffRecognisesFormatsByTheirFirstBytes() {
        fun head(vararg bytes: Int) = ByteArray(bytes.size) { bytes[it].toByte() }
        fun head(text: String) = text.toByteArray(Charsets.ISO_8859_1)
        assertEquals("jpg", ImageTypes.sniff(head(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals("png", ImageTypes.sniff(head(0x89, 'P'.code, 'N'.code, 'G'.code, 0x0D, 0x0A)))
        assertEquals("gif", ImageTypes.sniff(head("GIF89a")))
        assertEquals("webp", ImageTypes.sniff(head("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ")))
        assertEquals("heic", ImageTypes.sniff(head("\u0000\u0000\u0000\u0018ftypheic")))
        assertEquals("heif", ImageTypes.sniff(head("\u0000\u0000\u0000\u0018ftypmif1")))
        assertEquals("avif", ImageTypes.sniff(head("\u0000\u0000\u0000\u001Cftypavif")))
        assertNull(ImageTypes.sniff(head("<svg xmlns")))
        assertNull(ImageTypes.sniff(ByteArray(0)))
    }

    @Test
    fun copiesGoOutAsTheFileIsNamed() {
        val heif = Meme("1", "cat.heif", "image/heic", 0L, "")
        assertTrue(heif.extensionFits)
        assertEquals("heif", heif.copyExtension)
        assertEquals("image/heif", heif.copyMime)
        val webp = Meme("2", "dog.JPG", "image/webp", 0L, "")
        assertFalse(webp.extensionFits)
        assertEquals("jpg", webp.copyExtension)
        assertEquals("image/jpeg", webp.copyMime)
    }
}
