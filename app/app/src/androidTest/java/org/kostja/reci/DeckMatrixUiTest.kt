package org.kostja.reci

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Decks
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.Word
import org.kostja.reci.ui.MainActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// A deck and an interface language picked from the settings menus, then seen on the card.
@RunWith(AndroidJUnit4::class)
class DeckMatrixUiTest {
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
        FakeServer.install(closeNotice = true)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().clear().putString("ui_lang", "ru").putString("deck", "sr-ru").commit()
        waitIo()
        Repo.resetAll()
        waitIo()
        Repo.resetPosition()
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { Repo.current() != null }
    }

    @After
    fun close() {
        Prefs.uiLang = "ru"
        Repo.switchDeck("sr-ru")
        compose.waitUntil(10_000) { Decks.current.id == "sr-ru" && Repo.isLoaded }
        activity.close()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun exists(text: String) = runCatching { compose.onNodeWithText(text).assertExists() }.isSuccess

    private fun deck(id: String) = compose.waitUntil(10_000) { Decks.current.id == id && Repo.isLoaded }

    // The card on screen: a Russian word of the deck with its English translation.
    private fun checkCard(): Word {
        compose.waitUntil(10_000) { Repo.current() != null }
        val w = Repo.current()
        assertNotNull(w)
        assertTrue("${w!!.key} is not a card of ru-en", Repo.word(w.key) === w)
        assertTrue(Prefs.headword(w), exists(Prefs.headword(w)))
        assertFalse("${w.cyr}: a Russian card has no Serbian-only letters", Regex("[ђјљњћџ]").containsMatchIn(w.cyr))
        if (!Repo.isArticle(w)) {
            val m = Prefs.native(w.meanings.first())
            assertTrue("$m is not English", Regex("[A-Za-z]").containsMatchIn(m))
            assertTrue(m, exists(m))
        }
        return w
    }

    @Test
    fun russianWithEnglishInASerbianInterface() {
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.waitForIdle()
        tap("target")
        tap("target:ru")
        deck("ru-sr")
        compose.onNodeWithTag("native").assert(hasText("Сербский"))
        tap("native")
        compose.onNodeWithTag("native:sr").assertExists()
        compose.onNodeWithTag("native:ru").assertDoesNotExist()
        tap("native:en")
        deck("ru-en")
        compose.onNodeWithTag("native").assert(hasText("Английский"))

        tap("uilang")
        tap("uilang:sr")
        assertEquals("sr", Prefs.uiLang)
        assertEquals("the interface does not change the deck", "ru-en", Decks.current.id)
        compose.onNodeWithText("Подешавања").assertExists()
        compose.onNodeWithTag("target").assert(hasText("Учим")).assert(hasText("Руски"))
        compose.onNodeWithTag("native").assert(hasText("Превод на")).assert(hasText("Енглески"))
        compose.onNodeWithTag("uilang").assert(hasText("Језик интерфејса")).assert(hasText("Српски"))
        assertFalse(exists("Настройки"))

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        val first = checkCard()
        assertTrue(exists("Знам"))
        assertFalse(exists("Знаю"))
        compose.onNodeWithText("Даље").performClick()
        compose.waitForIdle()
        compose.waitUntil(10_000) { Repo.current()?.key != first.key }
        checkCard()
    }
}
