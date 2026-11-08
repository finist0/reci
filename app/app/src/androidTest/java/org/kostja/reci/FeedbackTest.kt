package org.kostja.reci

import android.content.Context
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Repo
import org.kostja.reci.data.Telemetry
import org.kostja.reci.ui.MainActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The statistics notice and switch, reporting a card and writing to the developer, on a device
// with the server faked: ./gradlew connectedDebugAndroidTest.
@RunWith(AndroidJUnit4::class)
class FeedbackTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var activity: ActivityScenario<MainActivity>? = null
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private fun waitIo() {
        val done = CountDownLatch(1)
        Repo.afterLoad { done.countDown() }
        assertTrue("dictionary did not load", done.await(60, TimeUnit.SECONDS))
    }

    @Before
    fun freshStart() {
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().clear().commit()
        waitIo()
        Repo.resetAll()
        waitIo()
        Repo.resetPosition()
    }

    @After
    fun close() {
        activity?.close()
    }

    private fun launch(closeNotice: Boolean = true, route: String? = null) {
        FakeServer.install(closeNotice)
        val intent = Intent(ctx, MainActivity::class.java)
        if (route != null) intent.putExtra(MainActivity.EXTRA_ROUTE, route)
        activity = ActivityScenario.launch(intent)
        // A route opens a word screen over the card, which then never picks a card.
        if (route == null) compose.waitUntil(10_000) { Repo.current() != null } else compose.waitForIdle()
    }

    private fun click(text: String, substring: Boolean = false) {
        compose.onNodeWithText(text, substring = substring).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun exists(text: String, substring: Boolean = false) =
        runCatching { compose.onNodeWithText(text, substring = substring).assertExists() }.isSuccess

    private fun waitPosts(kind: String, n: Int = 1) {
        compose.waitUntil(10_000) { FakeServer.of(kind).size >= n }
        assertEquals(n, FakeServer.of(kind).size)
    }

    private fun report() {
        compose.onAllNodesWithContentDescription("Сообщить об ошибке")[0].performClick()
        compose.waitForIdle()
    }

    @Test
    fun theFirstStartTellsAboutStatisticsAndWhereToTurnThemOff() {
        launch(closeNotice = false)
        compose.onNodeWithTag("statsNotice").assertExists()
        assertTrue(exists("Настройки → «Анонимная статистика»", substring = true))
        waitPosts("ping")
        assertEquals(Telemetry.id, FakeServer.of("ping")[0].getString("id"))
        assertEquals("android", FakeServer.of("ping")[0].getString("platform"))
        click("✕")
        compose.onNodeWithTag("statsNotice").assertDoesNotExist()
        activity!!.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("statsNotice").assertDoesNotExist()
    }

    @Test
    fun withStatisticsOffNothingIsSent() {
        FakeServer.install(closeNotice = true)
        Telemetry.enabled = false
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { Repo.current() != null }
        click("Дальше")
        Thread.sleep(500)
        assertEquals(emptyList<Any>(), FakeServer.posts.toList())
    }

    @Test
    fun theLastCrashIsOfferedOnceAndSentOnYes() {
        File(ctx.filesDir, "crash.txt").writeText("java.lang.IllegalStateException: boom\n\tat org.kostja.reci.X")
        launch()
        assertTrue(exists("Приложение закрылось с ошибкой"))
        click("Отправить")
        waitPosts("feedback")
        val m = FakeServer.of("feedback")[0]
        assertEquals("crash", m.getString("kind"))
        assertTrue(m.getString("text").startsWith("java.lang.IllegalStateException: boom"))
        assertTrue(m.has("env"))
        assertFalse(File(ctx.filesDir, "crash.txt").exists())
        activity?.close()
        launch()
        assertFalse(exists("Приложение закрылось с ошибкой"))
    }

    @Test
    fun aDeclinedCrashReportIsDroppedUnsent() {
        File(ctx.filesDir, "crash.txt").writeText("java.lang.IllegalStateException: boom")
        launch()
        click("Не отправлять")
        Thread.sleep(500)
        assertEquals(emptyList<Any>(), FakeServer.of("feedback"))
        assertFalse(File(ctx.filesDir, "crash.txt").exists())
    }

    @Test
    fun aCardIsReportedFromTheCardAndTheEmailIsRemembered() {
        launch()
        val key = Repo.current()!!.key
        report()
        assertTrue(exists("Что не так с карточкой?"))
        compose.onNodeWithText("Отправить").assertIsNotEnabled()
        click("Ударение")
        compose.onNodeWithTag("comment").performTextInput("ударение на втором слоге")
        compose.onNodeWithTag("email").performTextInput("me@example.org")
        click("Отправить")
        waitPosts("feedback")
        val m = FakeServer.of("feedback")[0]
        assertEquals("card", m.getString("kind"))
        assertEquals(key, m.getJSONObject("card").getString("key"))
        assertEquals("stress", m.getJSONObject("card").getString("problem"))
        assertEquals("ударение на втором слоге", m.getString("text"))
        assertEquals("me@example.org", m.getString("email"))
        assertNotEquals(Telemetry.id, m.getString("id"))
        assertTrue(m.getJSONObject("env").getString("device").isNotBlank())
        report()
        compose.onNodeWithTag("email").assert(hasText("me@example.org"))
    }

    @Test
    fun aMistypedEmailIsCaughtBeforeSending() {
        launch()
        report()
        click("Перевод")
        compose.onNodeWithTag("email").performTextInput("me@example")
        click("Отправить")
        assertTrue(exists("Проверьте адрес почты"))
        Thread.sleep(300)
        assertEquals(0, FakeServer.of("feedback").size)
    }

    @Test
    fun anArticleOffersTheKindsOfErrorsThatFitIt() {
        Repo.setCategories(setOf("X:compare"))
        launch()
        assertTrue(Repo.current()!!.key.startsWith("cmp:"))
        report()
        assertTrue(exists("Ошибка в тексте"))
        assertFalse(exists("Ударение"))
    }

    @Test
    fun aWordOpenedFromSearchOrAListIsReportedFromItsScreen() {
        launch(route = "grammar:w:кућа")
        report()
        click("Пример")
        click("Отправить")
        waitPosts("feedback")
        assertEquals("кућа", FakeServer.of("feedback")[0].getJSONObject("card").getString("word"))
    }

    @Test
    fun theFlagOnTheLockCardOpensTheFormInTheApp() {
        launch(route = "report:кућа")
        assertTrue(exists("Что не так с карточкой?"))
        click("Отмена")
        // The flag on the lock card tapped again for the same word.
        ctx.startActivity(
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "report:кућа")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        compose.waitUntil(10_000) { exists("Что не так с карточкой?") }
        click("Отмена")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitUntil(10_000) { Repo.current() != null }
    }

    @Test
    fun withoutNetworkAMessageWaitsAndGoesOutLater() {
        launch()
        FakeServer.status = -1
        report()
        click("Другое")
        click("Отправить")
        compose.waitUntil(10_000) { FakeServer.of("feedback").isNotEmpty() }
        assertEquals(1, Telemetry.waiting)
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.waitForIdle()
        assertTrue(exists("Ждут отправки: 1"))
        FakeServer.status = 204
        activity!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        activity!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitUntil(10_000) { Telemetry.waiting == 0 }
    }

    @Test
    fun writingToTheDeveloperFromTheSettings() {
        launch()
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.waitForIdle()
        click("Написать разработчику")
        click("Отправить")
        assertTrue(exists("Напишите сообщение"))
        compose.onNodeWithTag("message").performTextInput("Добавьте глаголы движения")
        click("Отправить")
        waitPosts("feedback")
        val m = FakeServer.of("feedback")[0]
        assertEquals("app", m.getString("kind"))
        assertEquals("Добавьте глаголы движения", m.getString("text"))
        assertFalse(m.has("card"))
        assertFalse(m.has("email"))
    }

    @Test
    fun theStatisticsSwitchAndWhatIsSent() {
        launch()
        waitPosts("ping")
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.waitForIdle()
        assertTrue(exists("Последний раз:", substring = true))
        click("Что отправляется")
        compose.onNodeWithTag("packet").assert(hasText(Telemetry.id, substring = true))
        click("Закрыть")
        click("Анонимная статистика")
        assertFalse(Telemetry.enabled)
        click("Анонимная статистика")
        assertTrue(Telemetry.enabled)
    }

    @Test
    fun whatIsSentShowsBothIdsAndForgetsThemOnRequest() {
        launch()
        waitPosts("ping")
        val (id, feedbackId) = Telemetry.id to Telemetry.feedbackId
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.waitForIdle()
        click("Что отправляется")
        compose.onNodeWithTag("installId").assert(hasText(id))
        compose.onNodeWithTag("feedbackId").assert(hasText(feedbackId))
        click("Удалить мои данные с сервера")
        click("Отмена")
        assertTrue(FakeServer.of("forget").isEmpty())
        click("Удалить мои данные с сервера")
        click("Удалить")
        compose.waitUntil(5000) { FakeServer.of("forget").isNotEmpty() && Telemetry.id != id }
        val sent = FakeServer.of("forget").single()
        assertEquals(id, sent.getString("id"))
        assertEquals(feedbackId, sent.getString("feedbackId"))
        compose.onNodeWithTag("installId").assert(hasText(Telemetry.id))
        assertNotEquals(feedbackId, Telemetry.feedbackId)
    }

    @Test
    fun statisticsCountShownCardsAndAnswers() {
        launch()
        waitPosts("ping")
        click("Знаю")
        click("Знаю")
        click("Дальше")
        click("Не знаю")
        compose.onNodeWithContentDescription("Поиск").performClick()
        compose.waitForIdle()
        val c = Telemetry.counts()
        assertEquals(1, c.getJSONObject("answers").getInt("know"))
        assertEquals(1, c.getJSONObject("answers").getInt("dontknow"))
        assertTrue(c.getInt("cards") >= 1)
        assertEquals(1, c.getJSONObject("opened").getInt("search"))
    }

    // Catches the activity a tap would start, without starting it.
    private fun catchStart(tap: () -> Unit): Intent {
        var caught: Intent? = null
        val inst = InstrumentationRegistry.getInstrumentation()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult {
                caught = intent
                return Instrumentation.ActivityResult(0, null)
            }
        }
        inst.addMonitor(monitor)
        try {
            tap()
            compose.waitUntil(5_000) { caught != null }
        } finally {
            inst.removeMonitor(monitor)
        }
        return caught!!
    }

    @Test
    fun helpTheAuthorSharesRatesAndStars() {
        launch()
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.waitForIdle()
        click("Помочь автору")
        val chooser = catchStart { click("Поделиться с другом") }
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val text = (chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))!!.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(text.contains("play.google.com/store/apps/details?id=org.kostja.reci"))
        assertTrue(text.contains("finist0.github.io/reci/web/"))
        val rate = catchStart { click("Оценить приложение") }
        assertTrue(rate.dataString!!.endsWith("details?id=org.kostja.reci"))
        assertEquals("https://github.com/finist0/reci", catchStart { click("Поставить звезду на GitHub") }.dataString)
        val opened = Telemetry.counts().getJSONObject("opened")
        assertEquals(listOf(1, 1, 1), listOf("share", "rate", "star").map { opened.getInt(it) })
    }
}
