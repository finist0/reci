package org.kostja.reci

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Intro
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.WhatsNew
import org.kostja.reci.lock.LockService
import org.kostja.reci.ui.MainActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The first start screens: the language pair, the lock screen offer and the steps it shows in
// the settings: ./gradlew connectedDebugAndroidTest.
@RunWith(AndroidJUnit4::class)
class IntroUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var activity: ActivityScenario<MainActivity>? = null
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext

    private fun waitIo() {
        val done = CountDownLatch(1)
        Repo.afterLoad { done.countDown() }
        assertTrue("dictionary did not load", done.await(60, TimeUnit.SECONDS))
    }

    // Earlier test classes may have left news open in this process.
    private fun start(intro: Intro.State, lockEnabled: Boolean = false, news: Boolean = false, crash: Boolean = false) {
        FakeServer.install(closeNotice = true)
        WhatsNew.close()
        Intro.leftScreen()
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().clear()
            .putString("ui_lang", "ru").putString("deck", "sr-ru").putBoolean("lock_enabled", lockEnabled).commit()
        Prefs.intro = intro
        if (news) {
            Prefs.whatsNewSeen = "9.8"
            WhatsNew.check("""[{"version": "9.9", "items": [{"text": "Новое в 9.9"}]}]""", "9.9", fresh = false)
        }
        if (crash) File(ctx.filesDir, "crash.txt").writeText("java.lang.IllegalStateException: boom")
        Intro.showing.value = Intro.Show.NONE
        waitIo()
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    @After
    fun close() {
        activity?.close()
        File(ctx.filesDir, "crash.txt").delete()
        Intro.showing.value = Intro.Show.NONE
        Prefs.lockEnabled = false
        LockService.sync(ctx)
        if (Prefs.deck != "sr-ru") {
            Repo.switchDeck("sr-ru")
            Thread.sleep(500)
            waitIo()
        }
    }

    private fun shell(cmd: String) = inst.uiAutomation.executeShellCommand(cmd).close()

    private fun overlay(allowed: Boolean) = shell("appops set ${ctx.packageName} SYSTEM_ALERT_WINDOW ${if (allowed) "allow" else "ignore"}")

    private fun click(text: String) {
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun has(tag: String) = runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).assertExists() }.isSuccess

    private fun hasText(text: String) = runCatching { compose.onNodeWithText(text, substring = true).assertExists() }.isSuccess

    // Away and back, as from the system settings: the screen reads the permissions again.
    private fun comeBack() {
        activity!!.moveToState(Lifecycle.State.CREATED)
        activity!!.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
    }

    @Test
    fun aNewUserChoosesTheLanguagesThenIsOfferedTheCardAndPutsItOff() {
        start(Intro.begin(fresh = true))
        assertTrue(has("pair"))
        assertFalse(has("lockOffer"))
        tap("target:ru")
        assertTrue("Russian is translated into Serbian", has("native:sr"))
        tap("target:sr")
        tap("native:en")
        click("Начать")
        compose.waitUntil(30_000) { Prefs.deck == "sr-en" && Repo.current() != null }
        assertTrue(Prefs.intro.pairAsked)
        assertFalse(has("pair"))
        assertTrue(has("lockOffer"))

        val now = System.currentTimeMillis()
        click("Позже")
        assertFalse(has("lockOffer"))
        assertEquals(1, Prefs.intro.offerLeft)
        assertTrue(Prefs.intro.offerAt in now + Intro.LATER - 60_000..now + Intro.LATER + 60_000)
        activity!!.recreate()
        compose.waitForIdle()
        assertFalse(has("pair") || has("lockOffer"))
    }

    @Test
    fun startKeepsTheGuess() {
        start(Intro.begin(fresh = true))
        click("Начать")
        assertFalse(has("pair"))
        assertTrue(has("lockOffer"))
        assertEquals("sr-ru", Prefs.deck)
    }

    @Test
    fun yesShowsTheStepsAndTheSettingsLightThemUpOneByOne() {
        if (Build.VERSION.SDK_INT >= 33) inst.uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.POST_NOTIFICATIONS)
        overlay(false)
        start(Intro.begin(fresh = false))
        assertFalse("an update is not asked for the languages", has("pair"))
        click("Да, настроить")
        assertTrue(hasText("Два шага в настройках"))
        click("Открыть настройки")
        assertFalse(has("lockOffer"))
        assertEquals(0, Prefs.intro.offerLeft)
        assertTrue(has("lockGuide"))
        assertTrue(has("highlighted:Карточка при включении экрана"))

        click("Карточка при включении экрана")
        assertTrue(Prefs.lockEnabled)
        assertFalse(has("highlighted:Карточка при включении экрана"))
        assertTrue(has("highlighted:Поверх других окон"))

        overlay(true)
        comeBack()
        assertFalse(has("highlighted:Поверх других окон"))
        assertTrue(hasText("Готово!"))
    }

    @Test
    fun aTurnedScreenKeepsTheSettingsWithTheSteps() {
        start(Intro.begin(fresh = false))
        click("Да, настроить")
        click("Открыть настройки")
        activity!!.recreate()
        compose.waitForIdle()
        assertTrue(has("lockGuide"))
        assertTrue(has("highlighted:Карточка при включении экрана"))
        activity!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertFalse(has("lockGuide"))
        compose.onNodeWithText("Знаю").assertExists()
    }

    @Test
    fun aCrashReportPutsTheOfferOffToTheNextStart() {
        start(Intro.begin(fresh = false), crash = true)
        assertFalse(has("lockOffer"))
        click("Не отправлять")
        activity!!.recreate()
        compose.waitForIdle()
        assertFalse("not on the start that showed the report", has("lockOffer"))
        comeBack()
        assertTrue(has("lockOffer"))
    }

    @Test
    fun backPutsTheOfferOffLikeLater() {
        start(Intro.begin(fresh = false))
        assertTrue(has("lockOffer"))
        activity!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertFalse(has("lockOffer"))
        assertEquals(0, Prefs.intro.offerLeft)
    }

    @Test
    fun anUpdateWhoseCardWorksIsNotOfferedIt() {
        overlay(true)
        start(Intro.begin(fresh = false), lockEnabled = true)
        assertFalse(has("lockOffer"))
        assertEquals(0, Prefs.intro.offerLeft)
    }

    @Test
    fun theNewsOfAnUpdatePutTheOfferOffToTheNextStart() {
        start(Intro.begin(fresh = false), news = true)
        assertTrue(has("whatsNew"))
        assertFalse(has("lockOffer"))
        click("Понятно")
        activity!!.recreate()
        compose.waitForIdle()
        assertFalse("not on the start that showed the news", has("lockOffer"))
        assertEquals(1, Prefs.intro.offerLeft)
        comeBack()
        assertTrue(has("lockOffer"))
    }
}
