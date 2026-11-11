package org.kostja.reci

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.WhatsNew
import org.kostja.reci.ui.MainActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The window with the news of an update on the main screen: ./gradlew connectedDebugAndroidTest.
@RunWith(AndroidJUnit4::class)
class WhatsNewUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var activity: ActivityScenario<MainActivity>? = null
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private val two = """[
        {"version": "9.9", "items": [{"text": "Новое в 9.9"}, {"text": "Только для iPhone", "only": ["ios"]}]},
        {"version": "9.8", "items": [{"text": "Новое в 9.8"}]}
    ]"""

    @Before
    fun start() {
        FakeServer.install(closeNotice = true)
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val done = CountDownLatch(1)
        Repo.afterLoad { done.countDown() }
        assertTrue("dictionary did not load", done.await(60, TimeUnit.SECONDS))
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { Repo.current() != null }
        WhatsNew.close()
    }

    @After
    fun close() {
        WhatsNew.close()
        activity?.close()
    }

    private fun update(json: String, version: String, fresh: Boolean = false) {
        WhatsNew.check(json, version, fresh)
        compose.waitForIdle()
    }

    @Test
    fun theNewsShowOnceAndStayWhenTheScreenTurns() {
        Prefs.whatsNewSeen = "9.8"
        update(two, "9.9")
        compose.onNodeWithText("Что нового в версии 9.9").assertExists()
        compose.onNodeWithText("Новое в 9.9").assertExists()
        compose.onNodeWithText("Только для iPhone").assertDoesNotExist()
        compose.onNodeWithText("Новое в 9.8").assertDoesNotExist()
        activity!!.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("whatsNew").assertExists()
        compose.onNodeWithText("Понятно").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("whatsNew").assertDoesNotExist()
        assertEquals("9.9", Prefs.whatsNewSeen)
        update(two, "9.9")
        compose.onNodeWithTag("whatsNew").assertDoesNotExist()
    }

    @Test
    fun skippedVersionsShowUnderTheirNumbers() {
        Prefs.whatsNewSeen = "1.6"
        update(two, "9.9")
        compose.onNodeWithText("Что нового").assertExists()
        compose.onNodeWithText("Версия 9.9").assertExists()
        compose.onNodeWithText("Версия 9.8").assertExists()
        compose.onNodeWithText("Новое в 9.8").assertExists()
    }

    @Test
    fun aFreshInstallIsNotToldAboutChanges() {
        update(two, "9.9", fresh = true)
        compose.onNodeWithTag("whatsNew").assertDoesNotExist()
        assertEquals("9.9", Prefs.whatsNewSeen)
    }
}
