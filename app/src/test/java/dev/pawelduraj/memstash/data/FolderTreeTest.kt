package dev.pawelduraj.memstash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeTest {
    private fun meme(path: String) = Meme(
        id = path,
        name = path.substringAfterLast('/'),
        mime = "image/jpeg",
        modified = 0L,
        folderPath = path.substringBeforeLast('/', ""),
    )

    @Test
    fun countsMemesDirectlyAndBelow() {
        val memes = listOf("a.jpg", "basics/b.jpg", "basics/maps/c.jpg", "basics/maps/d.jpg", "ai/e.jpg").map(::meme)
        val tree = FolderTree.build(memes, mapOf("basics/maps/c.jpg" to 5L))

        assertEquals(setOf("", "basics", "basics/maps", "ai"), tree.keys)
        assertEquals(FolderInfo("", 5, 1, true, 5L), tree[""])
        assertEquals(FolderInfo("basics", 3, 1, true, 5L), tree["basics"])
        assertEquals(FolderInfo("basics/maps", 2, 2, false, 5L), tree["basics/maps"])
        assertEquals(FolderInfo("ai", 1, 1, false, 0L), tree["ai"])
    }

    @Test
    fun foldersOnTheWayCountEvenWithoutOwnMemes() {
        val tree = FolderTree.build(listOf(meme("a/b/c/x.jpg")), emptyMap())

        assertEquals(FolderInfo("a", 1, 0, true, 0L), tree["a"])
        assertEquals(FolderInfo("a/b", 1, 0, true, 0L), tree["a/b"])
        assertEquals("c", tree.getValue("a/b/c").name)
    }

    @Test
    fun containsMatchesWholeFolderNames() {
        assertTrue(FolderTree.contains("", "anything"))
        assertTrue(FolderTree.contains("games", "games"))
        assertTrue(FolderTree.contains("games", "games/rpg"))
        assertFalse(FolderTree.contains("games", "games-old"))
        assertFalse(FolderTree.contains("games/rpg", "games"))
    }
}
