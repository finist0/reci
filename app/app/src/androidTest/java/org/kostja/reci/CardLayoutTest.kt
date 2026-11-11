package org.kostja.reci

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Labels
import org.kostja.reci.data.Repo
import org.kostja.reci.ui.CardScreen
import org.kostja.reci.ui.CardText
import org.kostja.reci.ui.ReciTheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// How the card screen fits small and ordinary screens. The screen size and the font scale are
// set inside the test, so it checks the same on any emulator.
@RunWith(AndroidJUnit4::class)
class CardLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private var settings = false
    private var done = false
    private var reported = false
    private var fontScale by mutableFloatStateOf(1f)

    @Before
    fun freshStart() {
        FakeServer.install(closeNotice = true)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().clear().commit()
        waitIo()
        Repo.resetAll()
        waitIo()
        Repo.resetPosition()
    }

    private fun waitIo() {
        val done = CountDownLatch(1)
        Repo.afterLoad { done.countDown() }
        assertTrue("dictionary did not load", done.await(60, TimeUnit.SECONDS))
    }

    private fun show(width: Int, height: Int, scale: Float = 1f, lock: Boolean = false) {
        fontScale = scale
        compose.setContent {
            val conf = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = width
                screenHeightDp = height
                // A tablet's own value would scale the layout up for a tablet.
                smallestScreenWidthDp = minOf(width, height)
            }
            CompositionLocalProvider(
                LocalConfiguration provides conf,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                ReciTheme {
                    Box(Modifier.size(width.dp, height.dp)) {
                        CardScreen(
                            onNext = { Repo.step(1) },
                            onDone = if (lock) ({ done = true }) else null,
                            onSearch = {},
                            onSettings = { settings = true },
                            onCategories = {},
                            onGrammar = {},
                            onSchools = {},
                        )
                    }
                }
            }
        }
        compose.waitUntil(10_000) { Repo.current() != null }
    }

    private fun bounds(description: String) = compose.onNodeWithContentDescription(description).getBoundsInRoot()

    private fun sameRow(a: DpRect, b: DpRect) = a.top < b.bottom && b.top < a.bottom

    private val count = SemanticsMatcher("the known/total count") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { Regex("""\s*\d+/\d+""").matches(it.text) } == true
    }

    @Test
    fun theTopBarIconsStayOnASmallScreenWithALargeFont() {
        show(320, 568, scale = 1.3f)
        for (icon in listOf("Поиск", "Грамматика", "Настройки")) {
            compose.onNodeWithContentDescription(icon).assertIsDisplayed()
            assertTrue("$icon sticks out of the screen", bounds(icon).right <= 320.dp)
        }
        compose.onNodeWithContentDescription("Настройки").performClick()
        assertTrue(settings)
    }

    @Test
    fun theDeckChipDropsTheCountBeforeItCutsTheTitle() {
        show(340, 605, scale = 1.3f)
        compose.onNode(count, useUnmergedTree = true).assertIsNotDisplayed()
        val layout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(Labels.category("L3").substringBefore(' '), useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        assertFalse("the level is cut", layout.single().isLineEllipsized(0))
    }

    @Test
    fun aSmallScreenPutsTheAnswersAndTheArrowsInOneRow() {
        show(340, 605)
        compose.onNodeWithText("Дальше").assertDoesNotExist()
        val know = compose.onNodeWithText("Знаю").getBoundsInRoot()
        assertTrue(sameRow(know, bounds("Дальше")))
        assertTrue(sameRow(know, bounds("Предыдущее слово")))
        val first = Repo.current()!!.key
        compose.onNodeWithContentDescription("Дальше").performClick()
        compose.waitForIdle()
        assertNotEquals(first, Repo.current()!!.key)
    }

    @Test
    fun theLockCardOnASmallScreenKeepsBackAndDoneButNotForward() {
        show(340, 605, lock = true)
        compose.onNodeWithContentDescription("Следующее слово").assertDoesNotExist()
        compose.onNodeWithContentDescription("Предыдущее слово").assertIsDisplayed()
        assertTrue(sameRow(bounds("Готово"), compose.onNodeWithText("Знаю").getBoundsInRoot()))
        compose.onNodeWithContentDescription("Готово").performClick()
        assertTrue(done)
    }

    @Test
    fun articleButtonsTakeShortLabelsWhenTheFullOnesDoNotFit() {
        Repo.setCategories(setOf("X:compare"))
        show(340, 605)
        compose.waitUntil(10_000) { Repo.isArticle(Repo.current()!!) }
        compose.onNodeWithText("Позже").assertIsDisplayed()
        compose.onNodeWithText("Скрыть").assertIsDisplayed()
        compose.onNodeWithContentDescription("Перечитать позже").assertExists()
        compose.onNodeWithContentDescription("Не показывать").performClick()
        compose.waitForIdle()
        assertEquals(1, Repo.hiddenArticles(Repo.current()!!.pos))
    }

    @Test
    fun theOneRowLabelsKeepTheirSizeWithALargerFont() {
        show(340, 605)
        val label = compose.onNodeWithText("Знаю", useUnmergedTree = true).getBoundsInRoot()
        val stage = compose.onNodeWithText("Новое слово", useUnmergedTree = true).getBoundsInRoot()
        fontScale = 1.3f
        compose.waitForIdle()
        assertEquals(label.height.value, compose.onNodeWithText("Знаю", useUnmergedTree = true).getBoundsInRoot().height.value, 0.5f)
        assertTrue(
            "the card text did not grow",
            compose.onNodeWithText("Новое слово", useUnmergedTree = true).getBoundsInRoot().height > stage.height,
        )
    }

    @Test
    fun anOrdinaryScreenKeepsTwoRowsAndTheCount() {
        show(411, 823)
        compose.onNodeWithText("Дальше").assertIsDisplayed()
        compose.onNode(count, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Перечитать позже").assertDoesNotExist()
    }

    @Test
    fun theLockCardOnAnOrdinaryScreenKeepsBothArrows() {
        show(411, 823, lock = true)
        compose.onNodeWithText("Готово").assertIsDisplayed()
        compose.onNodeWithContentDescription("Следующее слово").assertIsDisplayed()
        compose.onNodeWithContentDescription("Предыдущее слово").assertIsDisplayed()
    }

    private fun showText(lines: Int) {
        compose.setContent {
            ReciTheme {
                Box(Modifier.size(300.dp, 500.dp)) {
                    CardText(
                        top = 16.dp,
                        onReport = { reported = true },
                        onSchools = {},
                        star = { Box(Modifier.align(Alignment.TopEnd).size(44.dp).semantics { contentDescription = "Звезда" }) },
                    ) {
                        repeat(lines) { Text("Строка $it") }
                    }
                }
            }
        }
    }

    @Test
    fun aShortCardKeepsTheFlagAndTheSchoolInTheBottomCorner() {
        showText(3)
        compose.onNodeWithContentDescription("Сообщить об ошибке").assertIsDisplayed()
        compose.onNodeWithContentDescription("Куда пойти учиться").assertIsDisplayed()
        val flag = bounds("Сообщить об ошибке")
        assertTrue("the flag is not at the bottom", flag.bottom > 480.dp)
        assertTrue("the flag is not on the right", flag.right > 280.dp)
    }

    @Test
    fun aLongCardShowsTheFlagOnlyAtTheEndAndScrollsTheStarAway() {
        showText(80)
        compose.onNodeWithContentDescription("Звезда").assertIsDisplayed()
        compose.onNodeWithContentDescription("Сообщить об ошибке").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("Куда пойти учиться").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("Сообщить об ошибке").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Звезда").assertIsNotDisplayed()
        compose.onNodeWithText("Строка 79").assertIsDisplayed()
        compose.onNodeWithContentDescription("Сообщить об ошибке").performClick()
        assertTrue(reported)
    }
}
