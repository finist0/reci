package org.kostja.reci

import android.content.Context
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Accents
import org.kostja.reci.data.Order
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.Speaker
import org.kostja.reci.data.State
import org.kostja.reci.data.Translit
import org.kostja.reci.ui.MainActivity
import org.kostja.reci.ui.Palettes
import java.text.Collator
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Every setting of the app, changed on the settings screen and checked on the card.
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var activity: ActivityScenario<MainActivity>

    private fun waitIo() {
        val done = CountDownLatch(1)
        Repo.afterLoad { done.countDown() }
        assertTrue("dictionary did not load", done.await(60, TimeUnit.SECONDS))
    }

    @Before
    fun freshStart() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().clear().commit()
        waitIo()
        Repo.resetAll()
        waitIo()
        Repo.resetPosition()
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { Repo.current() != null }
    }

    @After
    fun close() {
        Speaker.say = Speaker::speak
        activity.close()
    }

    private fun word() = Repo.current()!!

    // Through the click action, so a row needs no scrolling into view, not even sideways.
    private fun click(text: String, substring: Boolean = false) {
        compose.onNodeWithText(text, substring = substring).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun exists(text: String) = runCatching { compose.onNodeWithText(text).assertExists() }.isSuccess

    private fun next() {
        compose.onNodeWithText("Дальше").performClick()
        compose.waitForIdle()
    }

    private fun back() {
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
    }

    // Opens the settings, taps the given rows in turn and goes back to the card.
    private fun settings(vararg taps: String) {
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.waitForIdle()
        for (t in taps) click(t)
        back()
    }

    @Test
    fun whatToLearnOneLevelInAlphabeticalOrder() {
        compose.onNodeWithContentDescription("Настройки").performClick()
        click("Что учим")
        for (t in listOf("Средний", "Выше среднего", "Выражения", "Похожие слова", "Заметки", "Начальный")) {
            click(t, substring = true)
        }
        click("По алфавиту")
        click("Учить", substring = true)
        assertEquals(setOf("L1"), Prefs.categories)
        assertEquals(Order.ALPHA, Prefs.order)
        compose.onNodeWithText("A1 · Начальный · По алфавиту").assertExists()
        back()
        val coll = Collator.getInstance(Locale.forLanguageTag("sr"))
        val first = Repo.words.filter { it.level == 1 && it.pos != "phrase" }.minWith { a, b -> coll.compare(a.cyr, b.cyr) }
        assertEquals(first.key, word().key)
    }

    @Test
    fun cardModesApplyToTheCardOnScreen() {
        settings("Перевод")
        assertTrue(exists("Показать слово"))
        assertFalse(exists(Prefs.headword(word())))
        click("Показать слово")
        assertTrue(exists(Prefs.headword(word())))

        settings("Слово")
        assertTrue(exists(Prefs.headword(word())))
        click("Показать перевод")
        assertFalse(exists("Показать перевод"))

        settings("Обучение")
        assertTrue(exists(Prefs.headword(word())))
        assertFalse(exists("Показать перевод"))
    }

    @Test
    fun wordsAtATimeLimitWhichWordsComeUp() {
        settings("10")
        val seen = HashSet<String>()
        repeat(30) {
            if (!Repo.isArticle(word())) seen += word().key
            next()
        }
        assertEquals(10, seen.size)
    }

    @Test
    fun knowButtonsCanBeHidden() {
        assertTrue(exists("Знаю"))
        settings("Кнопки «Не знаю / Знаю»")
        assertFalse(exists("Знаю"))
        settings("Кнопки «Не знаю / Знаю»")
        assertTrue(exists("Знаю"))
    }

    @Test
    fun checkLearnedWordsCanBeTurnedOff() {
        settings("Проверять выученные слова")
        assertFalse(Prefs.reviewKnown)
    }

    @Test
    fun aDarkThemeRecoloursTheScreen() {
        val dark = Palettes.indexOfFirst { it.name == "Ноћ" }
        settings("Ноћ")
        assertEquals(dark, Prefs.theme)
        val image = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(Palettes[dark].bg, image[2, image.height / 2])
    }

    @Test
    fun alphabetAndTheSecondScriptLine() {
        val w = word()
        assertTrue(exists(Prefs.headword(w)))
        assertTrue(exists(Translit.toLatin(w.cyr)))

        settings("Latinica")
        val latin = Prefs.headword(w)
        assertFalse(latin, Regex("[\u0400-\u04FF]").containsMatchIn(latin))
        assertTrue(exists(latin))
        assertTrue(exists(w.cyr))

        settings("Подпись другим алфавитом")
        assertFalse(exists(w.cyr))
    }

    @Test
    fun stressMarksInAllThreeModes() {
        repeat(20) { if (word().acc.isEmpty()) next() }
        val w = word()
        assertTrue("no word with stress marks in the first cards", w.acc.isNotEmpty())
        settings("ку̏ћа")
        assertTrue(exists(w.acc))
        settings("ку́ћа")
        assertTrue(exists(Accents.placeOnly(w.acc)))
        settings("кућа")
        compose.onNodeWithText(w.cyr).assertExists()
        assertFalse(exists(w.acc))
    }

    @Test
    fun fontSizeReachesTheHeadword() {
        val w = word()
        val before = compose.onNodeWithText(Prefs.headword(w)).fetchSemanticsNode().size.height
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(72f) }
        compose.onNodeWithText("Размер шрифта: 72").assertExists()
        back()
        val after = compose.onNodeWithText(Prefs.headword(w)).fetchSemanticsNode().size.height
        assertTrue("headword grew from $before to $after", after > before * 1.4)
    }

    @Test
    fun sayTheWordAtOnceSkipsAWordToRecall() {
        val spoken = ArrayList<String>()
        Speaker.say = { spoken += it }
        next()
        assertEquals(emptyList<String>(), spoken)

        settings("Произносить слово сразу")
        spoken.clear()
        do next() while (Repo.isArticle(word()))
        assertEquals(listOf(word().cyr), spoken)

        settings("Перевод")
        spoken.clear()
        next()
        assertEquals(emptyList<String>(), spoken)
    }

    @Test
    fun hiddenArticlesAreCountedAndShownAgain() {
        repeat(6) { if (!Repo.isArticle(word())) next() }
        val pos = word().pos
        click("Не показывать")
        val row = if (pos == "compare") "Снова показывать разборы" else "Снова показывать заметки"
        compose.onNodeWithContentDescription("Настройки").performClick()
        click(row)
        click("Отмена")
        compose.onNodeWithText("Скрыто: 1").performScrollTo().assertExists()
        click(row)
        click("Показывать")
        assertEquals(0, Repo.hiddenArticles(pos))
        assertFalse(exists("Скрыто: 1"))
    }

    @Test
    fun resetClearsTheProgress() {
        compose.onNodeWithText("Знаю").performClick()
        compose.waitForIdle()
        assertTrue(Repo.words.any { Repo.progress(it).phase > 0 })
        compose.onNodeWithContentDescription("Настройки").performClick()
        click("Сбросить прогресс")
        click("Сбросить")
        waitIo()
        assertTrue(Repo.words.none { Repo.progress(it).state != State.NEW || Repo.progress(it).phase > Repo.INTRO })
    }
}
