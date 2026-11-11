package org.kostja.reci.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray

// What changed in each version, from assets/whatsnew.json (shared with the iPhone app and the
// web), shown once on the first start after an update.
object WhatsNew {
    // The last version without this window: an update from it has no version seen yet.
    const val BEFORE = "1.6"

    class Entry(val version: String, val items: List<String>)

    // The entries for the main screen, newest first, until closed.
    val pending = mutableStateOf<List<Entry>>(emptyList())
    private var checked = false

    fun parse(json: String, platform: String): List<Entry> {
        val all = JSONArray(json)
        return (0 until all.length()).map { all.getJSONObject(it) }.map { e ->
            val items = e.getJSONArray("items")
            Entry(
                e.getString("version"),
                (0 until items.length()).map { items.getJSONObject(it) }.filter { item ->
                    val only = item.optJSONArray("only") ?: return@filter true
                    (0 until only.length()).any { only.getString(it) == platform }
                }.map { it.getString("text") },
            )
        }.filter { it.items.isNotEmpty() }
    }

    fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    // The entries after the version seen last, up to the running one. A fresh install has
    // nothing to compare with, so it is told nothing.
    fun pick(entries: List<Entry>, version: String, seen: String?, fresh: Boolean): List<Entry> {
        val from = seen ?: if (fresh) version else BEFORE
        return entries.filter { compare(it.version, from) > 0 && compare(it.version, version) <= 0 }
            .sortedWith { a, b -> compare(b.version, a.version) }
    }

    // Once per process: a turned screen makes a new activity, and the window must stay.
    fun checkOnce(ctx: Context) {
        if (checked) return
        checked = true
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val json = ctx.assets.open("whatsnew.json").bufferedReader().use { it.readText() }
        check(json, info.versionName ?: return, fresh = info.firstInstallTime == info.lastUpdateTime)
    }

    fun check(json: String, version: String, fresh: Boolean) {
        val entries = pick(parse(json, "android"), version, Prefs.whatsNewSeen, fresh)
        Prefs.whatsNewSeen = version
        if (entries.isNotEmpty()) Handler(Looper.getMainLooper()).post { pending.value = entries }
    }

    fun close() {
        pending.value = emptyList()
    }
}
