package dev.pawelduraj.memstash.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MemeImporterTest {

    @Test
    fun safeNameKeepsWhatTheUserTyped() {
        assertEquals("Drake says no", MemeImporter.safeName("  Drake   says no "))
        assertEquals("łoś na łące", MemeImporter.safeName("łoś na łące"))
    }

    @Test
    fun safeNameRemovesCharactersFileSystemsReject() {
        assertEquals("a_b_c_d", MemeImporter.safeName("a/b\\c:d"))
        assertEquals("hidden", MemeImporter.safeName("..hidden"))
        assertEquals("mem", MemeImporter.safeName("   "))
        assertEquals(120, MemeImporter.safeName("x".repeat(300)).length)
        assertEquals("abc", MemeImporter.safeName("abc. . "))
        assertEquals("mem", MemeImporter.safeName("..."))
        assertEquals("x".repeat(119), MemeImporter.safeName("x".repeat(119) + "😂😂"))
    }

    @Test
    fun memeNameDropsATypedImageExtension() {
        assertEquals("cat", MemeImporter.memeName("cat.jpg"))
        assertEquals("cat", MemeImporter.memeName(" Cat.PNG ".lowercase()))
        assertEquals("a.b", MemeImporter.memeName("a.b.webp"))
        assertEquals("notes.txt", MemeImporter.memeName("notes.txt"))
        assertEquals("a_b", MemeImporter.memeName("a/b.jpg"))
        assertEquals("x".repeat(120), MemeImporter.memeName("x".repeat(200) + ".jpg"))
        assertEquals("mem", MemeImporter.memeName(".jpg"))
    }

    @Test
    fun uniqueNameNumbersTakenNamesIgnoringCase() {
        assertEquals("stonks.jpg", MemeImporter.uniqueName("stonks", "jpg", emptySet()))
        val taken = setOf("stonks.jpg", "stonks-2.jpg")
        assertEquals("stonks-3.jpg", MemeImporter.uniqueName("stonks", "jpg", taken))
        assertEquals("Stonks-3.jpg", MemeImporter.uniqueName("Stonks", "jpg", taken))
        assertEquals("stonks.png", MemeImporter.uniqueName("stonks", "png", taken))
    }
}
