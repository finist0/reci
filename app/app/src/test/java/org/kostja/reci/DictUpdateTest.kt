package org.kostja.reci

import android.content.Context
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.DictUpdate
import org.kostja.reci.data.Grammar
import org.kostja.reci.data.Repo
import org.kostja.reci.data.WordJson
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.Calendar
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Card fixes without a new release (DictUpdate): the shared cases of tests/dict-patches.json, which
// data/scripts/test_build_patches.py and the iPhone tests run too, and the updater against a fake
// site: what is fetched when, what is refused, and that the cards never wait for the network.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DictUpdateTest {
    private val app = RuntimeEnvironment.getApplication()
    private val fixture = JSONObject(File("../../tests/dict-patches.json").readText())
    private val site = HashMap<String, ByteArray>()
    private val gets = CopyOnWriteArrayList<String>()
    private val getThreads = CopyOnWriteArrayList<Thread>()
    private var clock = Calendar.getInstance().apply { set(2026, 10, 2, 9, 0, 0) }.timeInMillis
    private lateinit var base: String

    private fun waitIo() {
        val done = CountDownLatch(1)
        Repo.afterLoad { done.countDown() }
        assertTrue("dictionary did not load", done.await(60, TimeUnit.SECONDS))
    }

    @Before
    fun fresh() {
        app.getSharedPreferences("dict", Context.MODE_PRIVATE).edit().clear().commit()
        File(app.filesDir, "dict/patch.json").delete()
        DictUpdate.get = { url ->
            gets += url.substringAfterLast('/')
            getThreads += Thread.currentThread()
            site[url.substringAfterLast('/')]
        }
        DictUpdate.now = { clock }
        waitIo()
        Repo.swap(Repo.builtIn())
        shadowOf(Looper.getMainLooper()).idle()
        base = DictUpdate.baseId()
    }

    @After
    fun backToBuiltIn() {
        Repo.swap(Repo.builtIn())
        Grammar.swap(Grammar.parseAll(null))
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    // Puts a patch for this build on the fake site, as build_patches.py would.
    private fun publish(words: List<String>, removed: List<String> = emptyList(), grammar: String? = null, forBase: String = base): ByteArray {
        val p = JSONObject().put("format", 1).put("base", forBase)
            .put("words", JSONArray(words.map { JSONObject(it) })).put("removed", JSONArray(removed))
            .put("grammar", grammar?.let { JSONArray(it) } ?: JSONObject.NULL)
        val data = p.toString().toByteArray()
        site["patch-$forBase.json"] = data
        site["manifest.json"] = JSONObject().put("format", 1).put("patches", JSONObject()
            .put(base, JSONObject().put("file", "patch-$forBase.json").put("sha256", sha(data)).put("size", data.size))).toString().toByteArray()
        return data
    }

    private fun check(): Boolean {
        var result: Boolean? = null
        DictUpdate.check { result = it }
        for (i in 0 until 500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (result != null) return result!!
            Thread.sleep(10)
        }
        return false
    }

    // A check that decided not to look at all comes back without a callback.
    private fun checkSkipped() {
        val before = gets.size
        DictUpdate.check()
        for (i in 0 until 20) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertEquals(before, gets.size)
    }

    private fun first() = Repo.words.first { it.level > 0 }

    private fun fixed(meaning: String) = WordJson.write(first().copy(meanings = listOf(meaning)))

    @Test
    fun theSharedCases() {
        val baseWords = fixture.getJSONArray("base").let { a -> List(a.length()) { WordJson.parse(a.getJSONObject(it).toString())!! } }
        val cases = fixture.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val p = DictUpdate.parse(JSONObject(c.getJSONObject("patch").toString()).put("base", "b").toString().toByteArray(), "b")!!
            val words = DictUpdate.apply(baseWords, p)
            val keys = c.getJSONArray("keys")
            assertEquals(c.getString("name"), List(keys.length()) { keys.getString(it) }, words.map { it.key })
            val changed = c.getJSONObject("changed")
            for (k in changed.keys()) {
                val m = changed.getJSONArray(k)
                assertEquals(List(m.length()) { m.getString(it) }, words.first { it.key == k }.meanings)
            }
            val grammar = p.grammar ?: Grammar.parseAll(fixture.getJSONArray("grammar").toString())
            assertEquals(c.getString("grammar"), grammar[0].blocks[0].text)
        }
    }

    @Test
    fun theBaseIdIsTheOneTheBuildGives() {
        val b = fixture.getJSONObject("baseId")
        assertEquals(b.getString("id"), DictUpdate.baseIdOf(b.getString("words").byteInputStream(), b.getString("grammar").byteInputStream()))
    }

    @Test
    fun aPatchFixesACardWhileTheAppRuns() {
        val key = first().key
        publish(listOf(fixed("исправленный перевод")))
        assertTrue(check())
        assertEquals(listOf("исправленный перевод"), Repo.word(key)!!.meanings)
        assertNotNull(DictUpdate.stored())
        assertEquals(Repo.builtIn().size, Repo.words.size)
    }

    @Test
    fun theKeptPatchIsAppliedOnTheNextLoad() {
        val key = first().key
        publish(listOf(fixed("из файла")))
        check()
        val words = DictUpdate.stored()!!.let { DictUpdate.apply(Repo.builtIn(), it) }
        assertEquals(listOf("из файла"), words.first { it.key == key }.meanings)
    }

    @Test
    fun aDayAfterACheckOnlyTheManifestIsAskedAndAnUnchangedPatchIsNotFetched() {
        publish(listOf(fixed("раз")))
        check()
        assertEquals(listOf("manifest.json", "patch-$base.json"), gets.toList())
        checkSkipped()
        clock += 24 * HOUR
        check()
        assertEquals(listOf("manifest.json", "patch-$base.json", "manifest.json"), gets.toList())
    }

    @Test
    fun aDifferentHashIsRefused() {
        val key = first().key
        val meanings = first().meanings
        publish(listOf(fixed("подменено")))
        site["patch-$base.json"] = site["patch-$base.json"]!! + " ".toByteArray()
        assertFalse(check())
        assertEquals(meanings, Repo.word(key)!!.meanings)
        assertNull(DictUpdate.stored())
    }

    @Test
    fun aPatchForAnotherDictionaryIsRefused() {
        publish(listOf(fixed("чужой")), forBase = "0".repeat(32))
        assertFalse(check())
        assertNull(DictUpdate.stored())
    }

    @Test
    fun brokenJsonWithTheRightHashIsRefused() {
        publish(listOf(fixed("x")))
        val broken = "{\"format\":1,\"base\":\"$base\",\"words\":[{\"key\":".toByteArray()
        site["patch-$base.json"] = broken
        site["manifest.json"] = JSONObject().put("patches", JSONObject().put(base, JSONObject()
            .put("file", "patch-$base.json").put("sha256", sha(broken)))).toString().toByteArray()
        assertFalse(check())
        assertNull(DictUpdate.stored())
    }

    @Test
    fun noNetworkKeepsTheCardsAndTriesAgainAnHourLater() {
        val meanings = first().meanings
        assertFalse(check())
        assertEquals(meanings, first().meanings)
        publish(listOf(fixed("позже")))
        clock += HOUR / 2
        checkSkipped()
        clock += HOUR / 2
        assertTrue(check())
    }

    @Test
    fun noPatchForThisDictionaryChangesNothing() {
        site["manifest.json"] = """{"format":1,"patches":{}}""".toByteArray()
        assertFalse(check())
        assertEquals(listOf("manifest.json"), gets.toList())
        assertNull(DictUpdate.stored())
    }

    @Test
    fun aPatchTakenBackReturnsTheBuiltInCards() {
        val key = first().key
        val meanings = first().meanings
        publish(listOf(fixed("временно")))
        check()
        site["manifest.json"] = """{"format":1,"patches":{}}""".toByteArray()
        clock += 24 * HOUR
        assertTrue(check())
        assertEquals(meanings, Repo.word(key)!!.meanings)
        assertNull(DictUpdate.stored())
    }

    @Test
    fun aRemovedCardOnScreenGivesWayToTheNextOne() {
        Repo.resetPosition()
        Repo.step(1)
        val shown = Repo.current()!!.key
        publish(emptyList(), removed = listOf(shown))
        check()
        assertNull(Repo.word(shown))
        assertNull(Repo.current())
        Repo.step(1)
        assertNotEquals(shown, Repo.current()!!.key)
    }

    @Test
    fun aPatchedGrammarReplacesTheReference() {
        val g = JSONArray().put(JSONObject().put("id", "test").put("group", "Г").put("title", "Новая тема")
            .put("blocks", JSONArray().put(JSONObject().put("type", "p").put("text", "текст")))).toString()
        publish(emptyList(), grammar = g)
        check()
        assertEquals("Новая тема", Grammar.topic("test")!!.title)
    }

    @Test
    fun aGrammarTheAppCannotReadRefusesThePatch() {
        val key = first().key
        val meanings = first().meanings
        publish(listOf(fixed("с чужой грамматикой")), grammar = """[{"id":"test","heading":"Новый формат"}]""")
        assertFalse(check())
        assertEquals(meanings, Repo.word(key)!!.meanings)
        assertNull(DictUpdate.stored())
    }

    @Test
    fun aKeptPatchThatDidNotReachTheDiskIsFetchedAgain() {
        publish(listOf(fixed("снова")))
        check()
        File(app.filesDir, "dict/patch.json").writeBytes(ByteArray(0))
        clock += 24 * HOUR
        assertTrue(check())
        assertEquals(listOf("manifest.json", "patch-$base.json", "manifest.json", "patch-$base.json"), gets.toList())
        assertNotNull(DictUpdate.stored())
    }

    @Test
    fun aLookThatThrowsIsTriedAgainAnHourLater() {
        DictUpdate.get = { error("network stack failed") }
        assertFalse(check())
        publish(listOf(fixed("после сбоя")))
        DictUpdate.get = { url -> site[url.substringAfterLast('/')] }
        clock += HOUR
        assertTrue(check())
    }

    @Test
    fun aPatchKeptForAnotherInstallIsNotApplied() {
        publish(listOf(fixed("старая сборка")))
        check()
        assertNotNull(DictUpdate.stored())
        shadowOf(app.packageManager).getInternalMutablePackageInfo(app.packageName).lastUpdateTime += 1000
        assertNull(DictUpdate.stored())
    }

    @Test
    fun aTooLargePatchIsRefused() {
        publish(listOf(fixed("x")))
        val big = ByteArray(17 * 1024 * 1024) { ' '.code.toByte() }
        site["patch-$base.json"] = big
        site["manifest.json"] = JSONObject().put("patches", JSONObject().put(base, JSONObject()
            .put("file", "patch-$base.json").put("sha256", sha(big)))).toString().toByteArray()
        assertFalse(check())
    }

    @Test
    fun aFileNameFromTheManifestCannotLeaveTheSite() {
        site["manifest.json"] = JSONObject().put("patches", JSONObject().put(base, JSONObject()
            .put("file", "../../evil.json").put("sha256", "0"))).toString().toByteArray()
        assertFalse(check())
        assertEquals(listOf("manifest.json"), gets.toList())
    }

    @Test
    fun aSlowNetworkHoldsUpNothing() {
        val release = CountDownLatch(1)
        publish(listOf(fixed("медленно")))
        DictUpdate.get = { url ->
            getThreads += Thread.currentThread()
            release.await(30, TimeUnit.SECONDS)
            site[url.substringAfterLast('/')]
        }
        val started = System.nanoTime()
        DictUpdate.check()
        assertTrue("check() waited for the network", System.nanoTime() - started < 200_000_000)
        Repo.resetPosition()
        Repo.step(1)
        assertNotNull("no card while the network hangs", Repo.current())
        release.countDown()
        for (i in 0 until 500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (first().meanings == listOf("медленно")) break
            Thread.sleep(10)
        }
        assertEquals(listOf("медленно"), first().meanings)
        assertTrue("the network was used on the main thread", getThreads.none { it == Looper.getMainLooper().thread })
    }

    companion object {
        const val HOUR = 60 * 60 * 1000L
    }
}
