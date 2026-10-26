package org.kostja.reci

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.State
import org.kostja.reci.ui.MainActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// UI tests of the card screen on a device or emulator: ./gradlew connectedDebugAndroidTest.
// The rules themselves are covered by LearningScenariosTest; these check what the user sees.
@RunWith(AndroidJUnit4::class)
class CardScreenTest {
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
    fun close() = activity.close()

    private fun current() = Repo.current()!!.key

    private fun known() = Repo.words.count { Repo.progress(it).state == State.KNOWN }

    @Test
    fun opensOnTheMostFrequentWordOfTheDefaultLevels() {
        assertEquals(Repo.words.first { it.level == 3 || it.level == 4 }.key, current())
    }

    @Test
    fun longPressOnKnowShowsToastMovesOnAndCanBeUndone() {
        val first = current()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Знаю").performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Слово отмечено выученным").assertIsDisplayed()
        assertNotEquals(first, current())
        assertEquals(1, known())
        compose.mainClock.advanceTimeBy(3000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Слово отмечено выученным").assertDoesNotExist()

        compose.onNodeWithContentDescription("Предыдущее слово").performClick()
        compose.waitForIdle()
        assertEquals(first, current())
        assertEquals(State.KNOWN, Repo.answerOf(Repo.current()!!))
        compose.onNodeWithText("Не знаю").performClick()
        compose.waitForIdle()
        assertEquals(0, known())
    }

    @Test
    fun anAnswerCanBeChangedWithTaps() {
        val w = Repo.current()!!
        compose.onNodeWithText("Знаю").performClick()
        compose.waitForIdle()
        assertEquals(State.KNOWN, Repo.answerOf(w))
        compose.onNodeWithText("Не знаю").performClick()
        compose.waitForIdle()
        assertEquals(State.DONT_KNOW, Repo.answerOf(w))
        assertEquals(Repo.INTRO, Repo.progress(w).phase)
    }

    @Test
    fun theLongPressHintComesOnceAndStaysClosed() {
        compose.onNodeWithText("Знаю").performClick()
        compose.onNodeWithText("Удерживайте", substring = true).assertIsDisplayed()
        compose.onNodeWithText("✕").performClick()
        compose.onNodeWithText("Удерживайте", substring = true).assertDoesNotExist()
        assertTrue(Prefs.hintDone)
    }

    @Test
    fun articleCardsAskWhetherToShowThemAgain() {
        repeat(6) {
            if (!Repo.isArticle(Repo.current()!!)) {
                compose.onNodeWithText("Дальше").performClick()
                compose.waitForIdle()
            }
        }
        assertTrue("no article in the first cards", Repo.isArticle(Repo.current()!!))
        compose.onNodeWithText("Перечитать позже").assertIsDisplayed()
        compose.onNodeWithText("Не показывать").performClick()
        compose.waitForIdle()
        assertEquals(1, Repo.hiddenArticles(Repo.current()!!.pos))
    }

    @Test
    fun settingsOfferToShowHiddenArticlesAgain() {
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Снова показывать разборы").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Снова показывать заметки").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Скрытых нет").assertCountEquals(2)
    }
}
