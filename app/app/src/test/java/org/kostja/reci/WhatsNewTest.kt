package org.kostja.reci

import android.os.Looper
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.WhatsNew
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

// Which news the first start after an update shows: ./gradlew testDebugUnitTest.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WhatsNewTest {
    private val json = """[
        {"version": "2.0", "items": [{"text": "future"}]},
        {"version": "1.10", "items": [{"text": "ten"}, {"text": "ten, web only", "only": ["web"]}]},
        {"version": "1.8", "items": [{"text": "eight, iPhone only", "only": ["ios"]}]},
        {"version": "1.7", "items": [{"text": "seven"}, {"text": "seven, Android", "only": ["android", "web"]}]}
    ]"""
    private val entries = WhatsNew.parse(json, "android")

    private fun versions(seen: String?, version: String, fresh: Boolean = false) =
        WhatsNew.pick(entries, version, seen, fresh).map { it.version }

    @Before
    fun clean() {
        Prefs.init(RuntimeEnvironment.getApplication())
        Prefs.whatsNewSeen = null
        WhatsNew.close()
    }

    @Test
    fun onlyThePlatformsItemsAreKept() {
        assertEquals(listOf("2.0", "1.10", "1.7"), entries.map { it.version })
        assertEquals(listOf("ten"), entries[1].items)
        assertEquals(listOf("seven", "seven, Android"), entries[2].items)
    }

    @Test
    fun anUpdateShowsEveryVersionSinceTheLastSeenNewestFirst() {
        assertEquals(listOf("1.7"), versions("1.6", "1.7"))
        assertEquals(listOf("1.10", "1.7"), versions("1.6", "1.10"))
        assertEquals(listOf("1.10"), versions("1.7", "1.10"))
    }

    @Test
    fun theSameVersionAnOlderOneOrAVersionWithoutNewsShowNothing() {
        assertEquals(emptyList<String>(), versions("1.7", "1.7"))
        assertEquals(emptyList<String>(), versions("1.10", "1.7"))
        assertEquals(emptyList<String>(), versions("1.7", "1.8"))
    }

    @Test
    fun aFreshInstallShowsNothingButAnUpdateFromBeforeTheWindowDoes() {
        assertEquals(emptyList<String>(), versions(null, "1.10", fresh = true))
        assertEquals(listOf("1.10", "1.7"), versions(null, "1.10"))
    }

    @Test
    fun theNewsAreShownOnce() {
        Prefs.whatsNewSeen = "1.6"
        WhatsNew.check(json, "1.7", fresh = false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("1.7"), WhatsNew.pending.value.map { it.version })
        assertEquals("1.7", Prefs.whatsNewSeen)
        WhatsNew.close()
        WhatsNew.check(json, "1.7", fresh = false)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(WhatsNew.pending.value.isEmpty())
    }

    @Test
    fun theSharedFileIsWellFormed() {
        val all = JSONArray(File("src/main/assets/whatsnew.json").readText())
        val versions = (0 until all.length()).map { all.getJSONObject(it).getString("version") }
        assertEquals("newest first, each once", versions.distinct().sortedWith { a, b -> WhatsNew.compare(b, a) }, versions)
        for (v in versions) assertTrue(v, Regex("""\d+(\.\d+)+""").matches(v) && WhatsNew.compare(v, WhatsNew.BEFORE) > 0)
        for (i in 0 until all.length()) {
            val items = all.getJSONObject(i).getJSONArray("items")
            assertTrue(versions[i], items.length() > 0)
            for (j in 0 until items.length()) {
                val item = items.getJSONObject(j)
                assertTrue(item.getString("text").isNotBlank())
                val only = item.optJSONArray("only") ?: continue
                for (k in 0 until only.length()) assertTrue(only.getString(k), only.getString(k) in setOf("android", "ios", "web"))
            }
        }
    }
}
