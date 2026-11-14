package org.kostja.reci

import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Decks
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.State
import org.kostja.reci.data.WordJson
import org.kostja.reci.ui.searchMatch
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The decks with English translations as the app carries them; the first cards of each are
// translated in any case.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EnglishDeckTest {
    private fun waitIo() {
        val done = CountDownLatch(1)
        Repo.afterLoad { done.countDown() }
        assertTrue("dictionary did not load", done.await(60, TimeUnit.SECONDS))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun switchTo(id: String) {
        Repo.switchDeck(id)
        waitIo()
        assertEquals(id, Decks.current.id)
    }

    @After
    fun back() {
        Prefs.latinFirst = false
        switchTo("sr-ru")
    }

    @Test
    fun theSettingsOfferEnglishTranslationsOfBothLanguages() {
        assertEquals(listOf("sr", "ru"), Decks.targets)
        assertEquals(listOf("ru", "en"), Decks.natives("sr"))
        assertEquals(listOf("sr", "en"), Decks.natives("ru"))
        assertEquals("sr-en", Decks.pick("sr", "en").id)
        assertEquals("ru-en", Decks.pick("ru", "en").id)
        switchTo("sr-en")
        assertEquals("the translations stay English", "ru-en", Decks.pick("ru", Decks.current.native).id)
    }

    @Test
    fun serbianWithEnglishSharesTheProgressOfSerbianWithRussian() {
        waitIo()
        val biti = Repo.word("бити")!!
        Repo.answer(biti, State.KNOWN, learned = true)
        waitIo()
        switchTo("sr-en")
        val en = Repo.word("бити")!!
        assertEquals(State.KNOWN, Repo.progress(en).state)
        assertTrue(en.meanings.contains("to be"))
        assertEquals("impf.", en.gram)
        assertTrue(en.examples.all { it.text.contains("[") && it.tr.isNotBlank() })
        assertEquals(en, WordJson.parse(WordJson.write(en)))

        Prefs.latinFirst = true
        assertTrue("Serbian in an English tip", Prefs.native(en.tips.first()).contains("jesam/jesi/jeste"))
        assertTrue(Repo.words.filter { searchMatch(it, "to be", byMeaning = true) }.any { it.key == "бити" })
        assertTrue(Repo.words.filter { searchMatch(it, "biti", byMeaning = false) }.any { it.key == "бити" })
    }

    @Test
    fun russianWithEnglishReadsItsCards() {
        switchTo("ru-en")
        val ya = Repo.word("я")!!
        assertEquals(listOf("I"), ya.meanings)
        assertTrue(ya.examples.all { it.tr.isNotBlank() })
        Prefs.latinFirst = true
        assertEquals("nothing Serbian to transliterate", ya.note, Prefs.native(ya.note))
        assertTrue(Repo.words.filter { searchMatch(it, "i", byMeaning = true) }.any { it.key == "я" })
        switchTo("ru-sr")
        assertEquals(listOf("ја"), Repo.word("я")!!.meanings)
    }
}
