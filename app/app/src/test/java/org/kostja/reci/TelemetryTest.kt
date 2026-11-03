package org.kostja.reci

import android.content.Context
import android.os.Looper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Backup
import org.kostja.reci.data.Env
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Telemetry
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import java.io.File
import java.util.Calendar

// The daily statistics and the message queue of Telemetry, with the server faked. The fields are
// checked against tests/telemetry.json, the list web/tests/telemetry.test.mjs checks too.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TelemetryTest {
    private val app = RuntimeEnvironment.getApplication()
    private val fields = JSONObject(File("../../tests/telemetry.json").readText())
    private val posts = mutableListOf<Pair<String, JSONObject>>()
    private var reply: (String) -> Int = { 204 }
    private var clock = Calendar.getInstance().apply { set(2026, 10, 2, 12, 0, 0) }.timeInMillis

    @Before
    fun fresh() {
        app.getSharedPreferences("telemetry", Context.MODE_PRIVATE).edit().clear().commit()
        Telemetry.init(app)
        Telemetry.markNoticeShown()
        Telemetry.post = { url, body ->
            synchronized(posts) { posts.add(url to JSONObject(body)) }
            reply(url)
        }
        Telemetry.now = { clock }
    }

    // A list from tests/telemetry.json, e.g. names("ping", "required").
    private fun names(vararg path: String): List<String> {
        var o: Any = fields
        for (k in path) o = (o as JSONObject).get(k)
        val a = o as org.json.JSONArray
        return (0 until a.length()).map { a.getString(it) }
    }

    // Runs the background work and the results it posts back to the main thread.
    private fun <T> await(start: ((T) -> Unit) -> Unit): T {
        var result: T? = null
        var got = false
        start { result = it; got = true }
        for (i in 0 until 500) {
            shadowOf(Looper.getMainLooper()).idle()
            @Suppress("UNCHECKED_CAST")
            if (got) return result as T
            Thread.sleep(10)
        }
        error("no answer from the background")
    }

    private fun ping(learned: Int = 0): Boolean = await { done -> Telemetry.sendPing({ learned }, done) }

    private fun pingSkipped() {
        val before = posts.size
        Telemetry.sendPing({ 0 })
        for (i in 0 until 20) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertEquals(before, posts.size)
    }

    private fun feedback(kind: String, text: String, card: JSONObject? = null, mail: String = "", env: JSONObject? = null): Boolean =
        await { done -> Telemetry.sendFeedback(kind, text, card, mail, env, done) }

    private fun flush(): Int = await { done -> Telemetry.flush(done) }

    @Test
    fun aNewInstallHasTwoDifferentIdsAndStatisticsOn() {
        assertTrue(Regex("[0-9a-f-]{36}").matches(Telemetry.id))
        assertNotEquals(Telemetry.id, Telemetry.feedbackId)
        assertTrue(Telemetry.enabled)
    }

    @Test
    fun thePingCarriesOnlyTheFieldsTheServerAccepts() {
        Telemetry.count("cards")
        Telemetry.count("answers", "know")
        Telemetry.count("opened", "search")
        Prefs.lockEnabled = true
        assertTrue(ping(7))
        val (url, p) = posts.single()
        assertTrue(url.endsWith("/ping"))
        val allowed = names("ping", "required") + names("ping", "optional")
        assertEquals(emptyList<String>(), p.keys().asSequence().filter { it !in allowed }.toList())
        for (k in names("ping", "required")) assertTrue(k, p.getString(k).isNotEmpty())
        val answers = names("answers")
        assertTrue(p.getJSONObject("answers").keys().asSequence().all { it in answers })
        assertEquals("2026-11-02", p.getString("day"))
        assertEquals("android", p.getString("platform"))
        assertEquals("overlay", p.getString("lock"))
        assertTrue(p.getString("lock") in names("lock"))
        assertEquals(listOf(1, 1, 1, 7), listOf(p.getInt("cards"), p.getJSONObject("answers").getInt("know"),
            p.getJSONObject("opened").getInt("search"), p.getInt("learned")))
    }

    @Test
    fun onePingADayAndTheSumsStartAgainAfterIt() {
        Telemetry.count("cards")
        assertTrue(ping())
        assertEquals(0, Telemetry.counts().length())
        Telemetry.count("cards")
        clock += 2 * HOUR
        pingSkipped()
        clock += 24 * HOUR
        assertTrue(ping())
        assertEquals("2026-11-03", posts[1].second.getString("day"))
        assertEquals(1, posts[1].second.getInt("cards"))
    }

    @Test
    fun nothingIsSentOrCollectedWhileStatisticsAreOff() {
        Telemetry.enabled = false
        Telemetry.count("cards")
        pingSkipped()
        Telemetry.enabled = true
        assertTrue(ping())
        assertFalse(posts.single().second.has("cards"))
    }

    @Test
    fun nothingIsSentBeforeTheNoticeWasShown() {
        app.getSharedPreferences("telemetry", Context.MODE_PRIVATE).edit().remove("notice_shown").commit()
        pingSkipped()
        Telemetry.markNoticeShown()
        assertTrue(ping())
    }

    @Test
    fun aFailedPingKeepsTheSumsAndWaitsAnHour() {
        reply = { -1 }
        Telemetry.count("cards")
        assertFalse(ping())
        assertEquals(1, Telemetry.counts().getInt("cards"))
        reply = { 204 }
        clock += HOUR / 2
        pingSkipped()
        clock += HOUR / 2
        assertTrue(ping())
        assertEquals(1, posts[1].second.getInt("cards"))
    }

    @Test
    fun aRejectedPingIsNotSentAgainThatDay() {
        reply = { 400 }
        Telemetry.count("cards")
        assertFalse(ping())
        assertEquals("2026-11-02", Telemetry.sentDay)
        assertEquals(0, Telemetry.counts().length())
        assertEquals(null, Telemetry.last)
    }

    @Test
    fun theLastPacketIsKeptForTheSettings() {
        assertTrue(ping())
        assertEquals(Telemetry.id, Telemetry.last!!.getString("id"))
    }

    @Test
    fun aMessageGoesOutWithItsOwnIdAndOnlyTheAcceptedFields() {
        val card = JSONObject().put("key", "бити").put("word", "бити").put("problem", "stress").put("latin", false).put("stress", 1)
        assertTrue(feedback("card", "", card, "me@example.org"))
        val (url, m) = posts.single()
        assertTrue(url.endsWith("/feedback"))
        val allowed = names("feedback", "required") + names("feedback", "optional")
        assertEquals(emptyList<String>(), m.keys().asSequence().filter { it !in allowed }.toList())
        val cardFields = names("feedback", "card")
        assertTrue(m.getJSONObject("card").keys().asSequence().all { it in cardFields })
        assertEquals(Telemetry.feedbackId, m.getString("id"))
        assertNotEquals(Telemetry.id, m.getString("id"))
        assertFalse(m.has("text"))
        assertEquals("me@example.org", Telemetry.email)
        assertEquals(0, Telemetry.waiting)
    }

    @Test
    fun aMessageTellsWhereTheAppRunsWithinTheServerLimits() {
        Prefs.latinFirst = true
        assertTrue(feedback("app", "Карточка не помещается", env = Env.of(app)))
        val env = posts.single().second.getJSONObject("env")
        val rules = fields.getJSONObject("feedback").getJSONObject("env")
        val name = Regex(rules.getString("name"))
        assertTrue(env.length() <= rules.getInt("maxEntries"))
        for (k in env.keys()) {
            assertTrue(k, name.matches(k))
            when (val v = env.get(k)) {
                is Boolean, is Number -> {}
                is String -> assertTrue(k, v.length <= rules.getInt("maxString") && v.none { it < ' ' })
                else -> error("$k is $v")
            }
        }
        for (k in listOf("device", "screen", "font", "theme", "mode", "dict", "lock", "overlay")) assertTrue(k, env.has(k))
        assertEquals(true, env.getBoolean("latin"))
        assertEquals("built-in", env.getString("dict"))
    }

    @Test
    fun aCrashReportGoesOutWithoutTheRememberedEmail() {
        Telemetry.email = "me@example.org"
        assertTrue(feedback("crash", "java.lang.IllegalStateException: boom", env = Env.of(app)))
        val m = posts.single().second
        assertEquals("crash", m.getString("kind"))
        assertFalse(m.has("email"))
        assertEquals("me@example.org", Telemetry.email)
        assertTrue(m.getString("kind") in names("feedback", "kinds"))
    }

    @Test
    fun withoutNetworkAMessageWaitsAndGoesOutLater() {
        reply = { -1 }
        assertFalse(feedback("app", "Привет"))
        assertEquals(1, Telemetry.waiting)
        reply = { 204 }
        assertEquals(0, flush())
        assertEquals(2, posts.size)
    }

    @Test
    fun aMessageOverTheLimitWaitsARejectedOneIsDropped() {
        reply = { 429 }
        feedback("app", "один")
        assertEquals(1, Telemetry.waiting)
        reply = { 400 }
        assertEquals(0, flush())
    }

    @Test
    fun messagesGoOutInOrder() {
        reply = { -1 }
        feedback("app", "первое")
        feedback("app", "второе")
        posts.clear()
        reply = { 204 }
        flush()
        assertEquals(listOf("первое", "второе"), posts.map { it.second.getString("text") })
    }

    @Test
    fun messagesAreSentWithStatisticsOff() {
        Telemetry.enabled = false
        assertTrue(feedback("app", "Привет"))
    }

    @Test
    fun theEmailCheckMatchesTheServer() {
        for (ok in listOf("a@b.cd", "me.name+tag@mail.example.org")) assertTrue(ok, Telemetry.isEmail(ok))
        for (bad in listOf("", "a@b", "a b@c.de", "a\u00a0b@c.de", "@c.de", "a@.de", "<a>@c.de")) assertFalse(bad, Telemetry.isEmail(bad))
    }

    @Test
    fun controlCharactersAreDroppedFromAMessage() {
        feedback("app", "a\u000cb\nc\u0000")
        assertEquals("ab\nc", posts.single().second.getString("text"))
    }

    @Test
    fun countsMadeWhileAPingIsOnItsWayAreKept() {
        Telemetry.count("cards")
        reply = {
            Telemetry.count("cards")
            Telemetry.count("answers", "know")
            204
        }
        assertTrue(ping())
        assertEquals(1, posts.single().second.getInt("cards"))
        assertEquals(1, Telemetry.counts().getInt("cards"))
        assertEquals(1, Telemetry.counts().getJSONObject("answers").getInt("know"))
    }

    @Test
    fun anOddSystemVersionDoesNotGetThePingRefused() {
        ShadowBuild.setVersionRelease("12, beta*")
        assertTrue(ping())
        assertEquals("Android 12 beta", posts.single().second.getString("os"))
    }

    @Test
    fun theProgressFileCarriesNoStatisticsIds() {
        val file = Backup.export()
        assertFalse(file.contains(Telemetry.id))
        assertFalse(file.contains(Telemetry.feedbackId))
    }

    companion object {
        const val HOUR = 60 * 60 * 1000L
    }
}
