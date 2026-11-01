package org.kostja.reci

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.DictUpdate
import org.kostja.reci.data.Repo
import org.kostja.reci.data.WordJson
import org.kostja.reci.ui.MainActivity
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// A card fix published on the site reaches the card on screen without restarting the app:
// ./gradlew connectedDebugAndroidTest.
@RunWith(AndroidJUnit4::class)
class DictUpdateUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private var activity: ActivityScenario<MainActivity>? = null

    private fun waitIo() {
        val done = CountDownLatch(1)
        Repo.afterLoad { done.countDown() }
        assertTrue("dictionary did not load", done.await(60, TimeUnit.SECONDS))
    }

    @Before
    fun freshStart() {
        FakeServer.install(closeNotice = true)
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences("dict", Context.MODE_PRIVATE).edit().clear().commit()
        File(ctx.filesDir, "dict/patch.json").delete()
        waitIo()
        Repo.resetAll()
        waitIo()
        Repo.resetPosition()
    }

    @After
    fun close() {
        activity?.close()
        ctx.getSharedPreferences("dict", Context.MODE_PRIVATE).edit().clear().commit()
        File(ctx.filesDir, "dict/patch.json").delete()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { Repo.swap(Repo.builtIn()) }
    }

    @Test
    fun aFixPublishedOnTheSiteShowsOnTheCardOnScreen() {
        val first = Repo.builtIn().first { it.level == 3 || it.level == 4 }
        val base = DictUpdate.baseId()
        val patch = JSONObject().put("format", 1).put("base", base)
            .put("words", JSONArray().put(JSONObject(WordJson.write(first.copy(meanings = listOf("исправлено на сайте"))))))
            .put("removed", JSONArray()).put("grammar", JSONObject.NULL).toString().toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(patch).joinToString("") { "%02x".format(it) }
        val manifest = JSONObject().put("format", 1).put("patches", JSONObject().put(base, JSONObject()
            .put("file", "patch-$base.json").put("sha256", sha))).toString().toByteArray()
        DictUpdate.get = { url -> mapOf("manifest.json" to manifest, "patch-$base.json" to patch)[url.substringAfterLast('/')] }
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { Repo.current()?.key == first.key }
        compose.waitUntil(20_000) {
            runCatching { compose.onNodeWithText("исправлено на сайте").assertExists() }.isSuccess
        }
    }
}
