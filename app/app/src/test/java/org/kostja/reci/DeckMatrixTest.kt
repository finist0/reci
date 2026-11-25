package org.kostja.reci

import android.content.Context
import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Decks
import org.kostja.reci.data.Labels
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.State
import org.kostja.reci.data.Word
import org.kostja.reci.data.t
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Every deck the app carries learned in every interface language: the same cards, the same steps.
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35])
class DeckMatrixTest(private val id: String) {

    companion object {
        // Parameters are listed outside the Robolectric sandbox, where org.json is only a stub.
        @JvmStatic
        @Parameters(name = "{0}")
        fun decks(): List<Array<Any>> =
            Regex("\"id\":\\s*\"([^\"]*)\"").findAll(File("src/main/assets/decks.json").readText()).map { arrayOf<Any>(it.groupValues[1]) }.toList()

        private val settings = mapOf("ru" to "Настройки", "sr" to "Подешавања", "en" to "Settings")
        private val beginner = mapOf("ru" to "A1 · Начальный", "sr" to "A1 · Почетни", "en" to "A1 · Beginner")
        private val cyrillic = Regex("\\p{IsCyrillic}")
        private val latin = Regex("\\p{IsLatin}")
    }

    private val start = 1_767_225_600_000L // 2026-01-01
    private var clock = start

    private fun waitIo() {
        val done = CountDownLatch(1)
        Repo.afterLoad { done.countDown() }
        assertTrue("dictionary did not load", done.await(60, TimeUnit.SECONDS))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun switchTo(deck: String) {
        Repo.switchDeck(deck)
        waitIo()
        assertEquals(deck, Decks.current.id)
    }

    @After
    fun back() {
        Repo.now = System::currentTimeMillis
        Prefs.uiLang = "ru"
        switchTo("sr-ru")
    }

    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("prefs", Context.MODE_PRIVATE)

    // A fresh start of the deck in the interface language, with the order of the cards fixed.
    private fun fresh(lang: String, categories: Set<String>) {
        prefs.edit().clear().putString("deck", id).putString("ui_lang", lang).commit()
        clock = start
        Repo.resetAll()
        waitIo()
        Repo.restart()
        Repo.setCategories(categories)
        Prefs.setCurrent(0, null, null, 7L)
    }

    // What the card screen does after a step: the card on screen is counted as shown.
    private fun next(): Word {
        clock += 1000
        Repo.step(1)
        val w = Repo.current()
        assertNotNull("no card after ${Prefs.cardCount - 1}", w)
        Repo.markShown(w!!)
        return w
    }

    private fun checkLanguages(w: Word) {
        val deck = Decks.current
        assertTrue("${w.key}: not a card of $id", Repo.word(w.key) === w)
        if (Repo.isArticle(w)) return
        assertTrue("${w.key}: headword ${w.cyr}", cyrillic.containsMatchIn(w.cyr))
        val script = if (deck.native == "en") latin else cyrillic
        assertTrue("${w.key}: meanings ${w.meanings}", w.meanings.isNotEmpty() && w.meanings.all { script.containsMatchIn(it) })
        for (e in w.examples) {
            assertTrue("${w.key}: example ${e.text}", cyrillic.containsMatchIn(e.text))
            assertTrue("${w.key}: translation ${e.tr}", script.containsMatchIn(e.tr))
        }
    }

    // Know, don't know and «already known» in turn, articles hidden; the trace of cards and phases.
    private fun session(lang: String): List<String> {
        fresh(lang, setOf("L1") + Labels.sections.keys)
        assertEquals(settings[lang], t("Настройки"))
        assertEquals(beginner[lang], Labels.levels[1])
        val trace = ArrayList<String>()
        for (i in 0 until 40) {
            val w = next()
            checkLanguages(w)
            assertTrue("${w.key}: level ${w.level}", w.level == 1 || w.level == 0)
            val before = Repo.progress(w)
            when {
                Repo.isArticle(w) -> {
                    Repo.answer(w, State.KNOWN)
                    assertEquals("${w.key} hidden", Repo.LEARNED, Repo.progress(w).phase)
                }
                i % 3 == 0 -> {
                    Repo.answer(w, State.KNOWN)
                    if (before.phase == Repo.INTRO) assertEquals("${w.key} known", Repo.RECALL, Repo.progress(w).phase)
                }
                i % 3 == 1 -> {
                    Repo.answer(w, State.DONT_KNOW)
                    assertEquals("${w.key} not known", State.DONT_KNOW, Repo.progress(w).state)
                }
                else -> {
                    Repo.answer(w, State.KNOWN, learned = true)
                    assertEquals("${w.key} learned", Repo.LEARNED, Repo.progress(w).phase)
                }
            }
            val p = Repo.progress(w)
            trace += "${w.key} ${p.phase} ${p.state}"
        }
        val counts = Repo.counts()
        assertTrue("A1 in $id", counts.getValue("L1")[0] >= 100)
        assertTrue("known A1 words", counts.getValue("L1")[1] > 0)
        assertTrue("known words", counts.getValue("M:known")[0] > 0)
        assertTrue("words not known", counts.getValue("M:dontknow")[0] > 0)
        for (l in 1..5) assertTrue("level $l in $id", (counts["L$l"]?.get(0) ?: 0) > 0)

        // Another level brings only its own words.
        fresh(lang, setOf("L2"))
        for (i in 0 until 10) {
            val w = next()
            checkLanguages(w)
            assertEquals("${w.key}: level", 2, w.level)
            trace += w.key
        }
        return trace
    }

    @Test
    fun theDeckIsLearnedTheSameInEachInterfaceLanguage() {
        Repo.now = { clock }
        switchTo(id)
        assertEquals(Decks.all.map { it.id }, decks().map { it[0] })
        val ru = session("ru")
        assertEquals("cards and answers in Serbian", ru, session("sr"))
        assertEquals("cards and answers in English", ru, session("en"))
    }
}
