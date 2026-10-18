package dev.pawelduraj.memstash.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Collator
import java.util.Locale

class LibraryTest {

    @Test
    fun foldersAreSortedAsATree() {
        val polish = Collator.getInstance(Locale.forLanguageTag("pl"))
        val paths = listOf("reactions/black", "", "zebras", "cats/black", "reactions", "cats", "a b", "Łosie")
        assertEquals(
            listOf("", "a b", "cats", "cats/black", "Łosie", "reactions", "reactions/black", "zebras"),
            Library.sortFolderPaths(paths, polish),
        )
    }

    @Test
    fun memesFollowARenamedFolder() {
        val renamed = Library.RenamedFolder("cats", "kittens", "primary:Memes/cats", "primary:Memes/kittens")
        val inside = Meme("primary:Memes/cats/black/a.jpg", "a.jpg", "image/jpeg", 1, "cats/black")
        val moved = renamed.rebase(inside)
        assertEquals("primary:Memes/kittens/black/a.jpg", moved.id)
        assertEquals("kittens/black", moved.folderPath)
        assertEquals(inside.imageKey, moved.imageKey)
        val outside = Meme("primary:Memes/catsup/b.jpg", "b.jpg", "image/jpeg", 1, "catsup")
        assertEquals(outside, renamed.rebase(outside))
        // Cloud providers keep the ids of the files in a renamed folder.
        val cloud = Library.RenamedFolder("cats", "kittens", "doc=1", "doc=1")
        assertEquals(Meme("doc=7", "c.jpg", "image/jpeg", 1, "kittens"), cloud.rebase(Meme("doc=7", "c.jpg", "image/jpeg", 1, "cats")))
    }
}
