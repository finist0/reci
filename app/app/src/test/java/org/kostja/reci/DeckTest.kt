package org.kostja.reci

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Decks
import org.kostja.reci.data.I18n
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.State
import org.kostja.reci.data.WordJson
import org.kostja.reci.data.plural
import org.kostja.reci.data.t
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeckTest {
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

    @Test
    fun aRussianPhoneStartsWithSerbianForRussians() {
        assertEquals("sr-ru", Prefs.deck)
        assertEquals("Настройки", t("Настройки"))
    }

    @Test
    fun theRussianDeckSpeaksSerbianAndKeepsItsOwnProgress() {
        waitIo()
        val sr = Repo.words.first { it.key == "кућа" }
        Repo.answer(sr, State.KNOWN, learned = true)
        waitIo()

        switchTo("ru-sr")
        assertEquals("Подешавања", t("Настройки"))
        val ru = Repo.words.first { it.key == "дом" }
        assertTrue("Russian examples carry the headword", ru.examples.all { it.text.contains("[") })
        assertTrue(ru.examples.all { it.tr.isNotBlank() })
        assertEquals(State.NEW, Repo.progress(ru).state)
        assertTrue("Serbian cards are not in the Russian deck", Repo.word("кућа") == null)

        switchTo("sr-ru")
        assertEquals(State.KNOWN, Repo.progress(Repo.word("кућа")!!).state)
    }

    @Test
    fun aCardSurvivesItsSnapshotInEachDeck() {
        switchTo("ru-sr")
        val w = Repo.words.first { it.related.isNotEmpty() }
        val back = WordJson.parse(WordJson.write(w))
        assertNotNull(back)
        assertEquals(w, back)
        switchTo("sr-ru")
    }

    @Test
    fun serbianInterfaceFollowsTheScriptSetting() {
        I18n.langOverride = "sr"
        try {
            Prefs.latinFirst = true
            assertEquals("Podešavanja", t("Настройки"))
            assertEquals("kartica", plural(5, "карточку", "карточки", "карточек"))
        } finally {
            Prefs.latinFirst = false
            I18n.langOverride = null
        }
    }

    @Test
    fun englishHasTwoPluralForms() {
        I18n.langOverride = "en"
        try {
            assertEquals("card", plural(1, "карточку", "карточки", "карточек"))
            assertEquals("cards", plural(3, "карточку", "карточки", "карточек"))
            assertEquals("Learn 5 cards", t("Учить %d %s", 5, plural(5, "карточку", "карточки", "карточек")))
        } finally {
            I18n.langOverride = null
        }
    }
}
