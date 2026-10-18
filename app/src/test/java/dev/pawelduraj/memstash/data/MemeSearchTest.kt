package dev.pawelduraj.memstash.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MemeSearchTest {
    private fun meme(name: String, folder: String = "") = Meme(name, name, "image/jpeg", 0L, folder)

    private val memes = listOf(
        meme("Żółw_się_śmieje.jpg"),
        meme("cat-in-a-basket.png", "animals"),
        meme("CatDog.webp"),
        meme("drake says no.jpg", "reactions"),
        meme("Łoś na łące.gif"),
    )

    private fun names(query: String, source: List<Meme> = memes, usage: Map<String, Long> = emptyMap()) =
        MemeSearch.search(source, query, usage).map { it.name }

    @Test
    fun ignoresCaseAndPolishDiacritics() {
        assertEquals(listOf("Żółw_się_śmieje.jpg"), names("zolw"))
        assertEquals(listOf("Łoś na łące.gif"), names("los"))
        assertEquals(listOf("cat-in-a-basket.png"), names("BASK"))
    }

    @Test
    fun allWordsMustMatch() {
        assertEquals(listOf("drake says no.jpg"), names("drake no"))
        assertEquals(emptyList<String>(), names("drake cat"))
    }

    @Test
    fun matchesFolderNames() {
        assertEquals(listOf("drake says no.jpg"), names("reactions"))
    }

    @Test
    fun toleratesOneTypoInShorterWords() {
        assertEquals(listOf("drake says no.jpg"), names("drk"))
        assertEquals(listOf("drake says no.jpg"), names("drkae"))
        assertEquals(listOf("cat-in-a-basket.png"), names("baskrt"))
        assertEquals(emptyList<String>(), names("darek"))
    }

    @Test
    fun toleratesTwoTyposFromSixLetters() {
        assertEquals(listOf("Żółw_się_śmieje.jpg"), names("smiaje"))
        assertEquals(listOf("cat-in-a-basket.png"), names("bsakte"))
        assertEquals(listOf("Żółw_się_śmieje.jpg"), names("smjeie"))
    }

    @Test
    fun exactWordRanksAbovePrefix() {
        assertEquals(listOf("cat-in-a-basket.png", "CatDog.webp"), names("cat"))
    }

    @Test
    fun recentlySentWinsTies() {
        val twins = listOf(meme("cat a.jpg"), meme("cat b.jpg"))
        assertEquals(listOf("cat a.jpg", "cat b.jpg"), names("cat", twins))
        assertEquals(listOf("cat b.jpg", "cat a.jpg"), names("cat", twins, mapOf("cat b.jpg" to 100L)))
    }

    @Test
    fun matchesTheFileType() {
        assertEquals(listOf("Łoś na łące.gif"), names("gif"))
    }

    @Test
    fun gluedQueryMatchesSeparatedName() {
        assertEquals(listOf("drake says no.jpg"), names("drakesays"))
    }
}
