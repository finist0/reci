package org.kostja.reci

import android.content.Context
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Deck
import org.kostja.reci.data.Decks
import org.kostja.reci.data.I18n
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.State
import org.kostja.reci.data.WordJson
import org.kostja.reci.data.plural
import org.kostja.reci.data.t
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale
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
    fun theRussianDeckKeepsItsOwnProgress() {
        waitIo()
        val sr = Repo.words.first { it.key == "кућа" }
        Repo.answer(sr, State.KNOWN, learned = true)
        waitIo()

        switchTo("ru-sr")
        assertEquals("the interface is not the deck's", "Настройки", t("Настройки"))
        val ru = Repo.words.first { it.key == "дом" }
        assertTrue("Russian examples carry the headword", ru.examples.all { it.text.contains("[") })
        assertTrue(ru.examples.all { it.tr.isNotBlank() })
        assertEquals(State.NEW, Repo.progress(ru).state)
        assertTrue("Serbian cards are not in the Russian deck", Repo.word("кућа") == null)

        switchTo("sr-ru")
        assertEquals(State.KNOWN, Repo.progress(Repo.word("кућа")!!).state)
    }

    // What the settings do on a tap of the language learned.
    private fun learn(target: String) {
        Repo.switchDeck(Decks.pick(target, Decks.current.native).id)
        waitIo()
        assertEquals(target, Decks.current.target)
    }

    @Test
    fun theLanguageLearnedSwitchesTheDeckAndEachKeepsItsProgress() {
        waitIo()
        assertEquals(listOf("sr", "ru"), Decks.targets)
        assertEquals(listOf("ru"), Decks.natives("sr"))
        assertEquals(listOf("sr"), Decks.natives("ru"))
        val sr = Repo.words.first { it.key == "кућа" }
        Repo.answer(sr, State.KNOWN, learned = true)
        waitIo()

        learn("ru")
        assertEquals("ru-sr", Decks.current.id)
        val ru = Repo.words.first { it.key == "дом" }
        assertEquals(State.NEW, Repo.progress(ru).state)
        Repo.answer(ru, State.DONT_KNOW)
        waitIo()

        learn("sr")
        assertEquals("sr-ru", Decks.current.id)
        assertEquals(State.KNOWN, Repo.progress(Repo.word("кућа")!!).state)
        assertTrue("Russian cards are not in the Serbian deck", Repo.word("хотеть") == null)
        learn("ru")
        assertEquals(State.DONT_KNOW, Repo.progress(Repo.word("дом")!!).state)
        learn("sr")
    }

    @Test
    fun theTranslationsFollowTheLanguageLearnedAndStayWhereTheyCan() {
        val srRu = Decks.all.first { it.id == "sr-ru" }
        val ruSr = Decks.all.first { it.id == "ru-sr" }
        val srEn = Deck("sr-en", "sr", "en", "words.json", "")
        Decks.allOverride = listOf(srRu, ruSr, srEn)
        try {
            assertEquals(listOf("sr", "ru"), Decks.targets)
            assertEquals(listOf("ru", "en"), Decks.natives("sr"))
            assertEquals(listOf("sr"), Decks.natives("ru"))
            assertEquals("English translations stay", srEn, Decks.pick("sr", "en"))
            assertEquals(srRu, Decks.pick("sr", "ru"))
            assertEquals("no Serbian deck translates into Serbian", srRu, Decks.pick("sr", "sr"))
            assertEquals("no Russian deck translates into English", ruSr, Decks.pick("ru", "en"))

            assertEquals("Сербский", Decks.name("sr"))
            I18n.langOverride = "en"
            assertEquals("English", Decks.name("en"))
            I18n.langOverride = "sr"
            assertEquals("Руски", Decks.name("ru"))
        } finally {
            Decks.allOverride = null
            I18n.langOverride = null
        }
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

    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("prefs", Context.MODE_PRIVATE)

    // A start with no interface language stored, on a phone set to the locale; `card` is the
    // key of the place in the cards an earlier user has.
    private fun start(locale: String, stored: String? = null, card: String? = null): String {
        val e = prefs.edit()
        prefs.all.keys.filter { it.endsWith("current_key") }.forEach { e.remove(it) }
        if (stored == null) e.remove("ui_lang") else e.putString("ui_lang", stored)
        if (card != null) e.putString(card, "кућа")
        e.commit()
        Locale.setDefault(Locale.forLanguageTag(locale))
        return Prefs.uiLang
    }

    @Test
    fun aFirstStartTakesTheInterfaceLanguageFromThePhoneAndKeepsIt() {
        waitIo()
        val was = Locale.getDefault()
        try {
            assertEquals("ru", start("ru-RU"))
            assertEquals("sr", start("sr-RS"))
            assertEquals("sr", start("hr-HR"))
            assertEquals("en", start("de-DE"))
            assertEquals("Settings", t("Настройки"))

            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            assertEquals("the stored language stays", "en", Prefs.uiLang)
            switchTo("ru-sr")
            assertEquals("en", Prefs.uiLang)
            switchTo("sr-ru")
            assertEquals("Settings", t("Настройки"))

            assertEquals("an empty value of an early build counts as none", "sr", start("sr-RS", stored = ""))
            assertEquals("an earlier user keeps the interface of the deck", "ru", start("en-US", card = "current_key"))
            assertEquals("ru", start("sr-RS", stored = "ru"))
            prefs.edit().putString("deck", "ru-sr").commit()
            assertEquals("sr", start("en-US", card = "ru-sr.current_key"))
            prefs.edit().putString("deck", "sr-ru").commit()
        } finally {
            Locale.setDefault(was)
            Prefs.uiLang = "ru"
        }
    }

    @Test
    fun theInterfaceLanguageIsChosenApartFromTheDeck() {
        assertEquals("ru", Prefs.uiLang)
        Prefs.uiLang = "en"
        assertEquals("Settings", t("Настройки"))
        assertEquals("cards", plural(5, "карточку", "карточки", "карточек"))
        // The cards stay in the language of the deck: the search of translations asks for Russian.
        assertEquals("word in Russian", Decks.meaningHint)

        switchTo("ru-sr")
        assertEquals("the choice is not the deck's", "Settings", t("Настройки"))
        assertEquals("word in Serbian", Decks.meaningHint)

        switchTo("sr-ru")
        Prefs.uiLang = "sr"
        I18n.langOverride = "en"
        try {
            assertEquals("Settings", t("Настройки"))
        } finally {
            I18n.langOverride = null
        }
        assertEquals("Подешавања", t("Настройки"))
        assertTrue("Serbian on screen needs the script setting", Decks.hasSerbian)
        Prefs.uiLang = "ru"
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
