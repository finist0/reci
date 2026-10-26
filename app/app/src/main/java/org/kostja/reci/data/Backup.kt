package org.kostja.reci.data

import org.json.JSONObject
import java.time.Instant

// Progress and settings as a file, in the format of the web version.
object Backup {
    fun export(): String {
        val progress = JSONObject()
        for ((key, p) in Repo.allProgress().toSortedMap()) {
            progress.put(
                key,
                JSONObject()
                    .put("state", p.state.ordinal)
                    .put("fav", p.fav)
                    .put("seen", p.seen)
                    .put("last", p.last)
                    .put("due", p.due)
                    .put("step", p.step)
                    .put("phase", p.phase)
                    .put("streak", p.streak)
                    .put("showAt", p.showAt),
            )
        }
        return JSONObject()
            .put("app", "reci")
            .put("format", 1)
            .put("exported", Instant.now().toString())
            .put("prefs", Prefs.exportJson())
            .put("progress", progress)
            .toString()
    }

    // Replaces the progress and returns the number of words in the file.
    fun import(text: String): Int {
        val o = JSONObject(text)
        require(o.optString("app") == "reci") { "not a progress file" }
        val src = o.getJSONObject("progress")
        val all = HashMap<String, Progress>()
        for (key in src.keys()) {
            val p = src.getJSONObject(key)
            all[key] = Progress(
                state = State.entries.getOrElse(p.optInt("state")) { State.NEW },
                fav = p.optBoolean("fav"),
                seen = p.optInt("seen"),
                last = p.optLong("last"),
                due = p.optLong("due"),
                step = p.optInt("step"),
                phase = p.optInt("phase"),
                streak = p.optInt("streak"),
                showAt = p.optInt("showAt"),
            )
        }
        o.optJSONObject("prefs")?.let { Prefs.importJson(it) }
        Repo.replaceProgress(all)
        return all.size
    }
}
