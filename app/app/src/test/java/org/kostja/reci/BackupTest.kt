package org.kostja.reci

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Backup
import org.kostja.reci.data.Order
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Progress
import org.kostja.reci.data.ProgressDb
import org.kostja.reci.data.Repo
import org.kostja.reci.data.State
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

// Loads tests/progress-sample.json, the file web/tests/backup.test.mjs loads in the web version,
// so a progress file saved by either version opens in the other.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupTest {
    private val sample = File("../../tests/progress-sample.json").readText()
    private val json = JSONObject(sample)

    private fun expected(): Map<String, Progress> {
        val src = json.getJSONObject("progress")
        return src.keys().asSequence().associateWith { key ->
            val p = src.getJSONObject(key)
            Progress(
                State.entries[p.getInt("state")], p.getBoolean("fav"), p.getInt("seen"), p.getLong("last"),
                p.getLong("due"), p.getInt("step"), p.getInt("phase"), p.getInt("streak"), p.getInt("showAt"),
            )
        }
    }

    @Test
    fun loadsEveryWordAndSetting() {
        assertEquals(json.getJSONObject("progress").length(), Backup.import(sample))
        assertEquals(expected(), Repo.allProgress())
        assertEquals(expected(), ProgressDb(RuntimeEnvironment.getApplication()).loadAll())
        assertEquals(true, Prefs.latinFirst)
        assertEquals(false, Prefs.showBothScripts)
        assertEquals(2, Prefs.cardMode)
        assertEquals(30, Prefs.activeSize)
        assertEquals(123, Prefs.cardCount)
        assertEquals(false, Prefs.reviewKnown)
        assertEquals(0, Prefs.accentMode)
        assertEquals(5, Prefs.theme)
        assertEquals(48, Prefs.wordSize)
        assertEquals(Order.RANDOM, Prefs.order)
        assertEquals(true, Prefs.hintDone)
        assertEquals(setOf("L2", "T:food", "X:note"), Prefs.categories)
    }

    @Test
    fun savedFileHasTheSameFields() {
        Backup.import(sample)
        val out = JSONObject(Backup.export())
        assertEquals("reci", out.getString("app"))
        assertEquals(json.getInt("format"), out.getInt("format"))
        assertEquals(json.getJSONObject("progress").toMap(), out.getJSONObject("progress").toMap())
        val prefs = json.getJSONObject("prefs")
        val saved = out.getJSONObject("prefs")
        for (k in prefs.keys()) {
            when (k) {
                "currentKey", "todayDate", "todayCount", "shuffleSeed" -> {}
                "categories" -> assertEquals(k, prefs.getJSONArray(k).toSet(), saved.getJSONArray(k).toSet())
                else -> assertEquals(k, prefs.get(k), saved.get(k))
            }
        }
    }

    @Test
    fun refusesAnotherKindOfFile() {
        Backup.import(sample)
        assertThrows(Exception::class.java) { Backup.import("{\"hello\": 1}") }
        assertThrows(Exception::class.java) { Backup.import("not json") }
        assertEquals(expected(), Repo.allProgress())
    }

    private fun JSONObject.toMap(): Map<String, Any> =
        keys().asSequence().associateWith { k -> get(k).let { if (it is JSONObject) it.toMap() else it } }

    private fun org.json.JSONArray.toSet() = (0 until length()).map { getString(it) }.toSet()
}
