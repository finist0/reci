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
    fun aSerbianPhoneStartsWithRussianAndKeepsTheChoice() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val sp = ctx.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
        val was = java.util.Locale.getDefault()
        try {
            sp.edit().remove("deck").remove("current_key").commit()
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("sr-RS"))
            assertEquals("ru-sr", Prefs.deck)
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ru-RU"))
            assertEquals("ru-sr", Prefs.deck)

            sp.edit().remove("deck").putString("current_key", "кућа").commit()
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("sr-RS"))
            assertEquals("an earlier user stays", "sr-ru", Prefs.deck)
        } finally {
            java.util.Locale.setDefault(was)
        }
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
    fun theInterfaceLanguageIsChosenApartFromTheDeck() {
        assertEquals("", Prefs.uiLang)
        Prefs.uiLang = "en"
        assertEquals("Settings", t("Настройки"))
        assertEquals("cards", plural(5, "карточку", "карточки", "карточек"))
        // The cards stay in the language of the deck: the search of translations asks for Russian.
        assertEquals("word in Russian", Decks.meaningHint)

        switchTo("ru-sr")
        assertEquals("the choice is not the deck's", "Settings", t("Настройки"))
        assertEquals("word in Serbian", Decks.meaningHint)

        Prefs.uiLang = ""
        assertEquals("Подешавања", t("Настройки"))
        switchTo("sr-ru")
        assertEquals("Настройки", t("Настройки"))

        Prefs.uiLang = "sr"
        I18n.langOverride = "en"
        try {
            assertEquals("Settings", t("Настройки"))
        } finally {
            I18n.langOverride = null
        }
        assertEquals("Подешавања", t("Настройки"))
        assertTrue("Serbian on screen needs the script setting", Decks.hasSerbian)
        Prefs.uiLang = ""
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
