package org.kostja.reci.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private lateinit var sp: SharedPreferences

    // Bumped on every change so that Compose readers recompose.
    val version = mutableIntStateOf(0)

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    }

    private fun changed() {
        version.intValue++
    }

    private fun bool(key: String, def: Boolean): Boolean {
        version.intValue
        return sp.getBoolean(key, def)
    }

    private fun int(key: String, def: Int): Int {
        version.intValue
        return sp.getInt(key, def)
    }

    private fun long(key: String, def: Long): Long {
        version.intValue
        return sp.getLong(key, def)
    }

    private fun setBool(key: String, v: Boolean) {
        sp.edit { putBoolean(key, v) }
        changed()
    }

    private fun setInt(key: String, v: Int) {
        sp.edit { putInt(key, v) }
        changed()
    }

    private fun setLong(key: String, v: Long) {
        sp.edit { putLong(key, v) }
        changed()
    }

    var latinFirst: Boolean
        get() = bool("latin_first", false)
        set(v) = setBool("latin_first", v)

    var showBothScripts: Boolean
        get() = bool("show_both", true)
        set(v) = setBool("show_both", v)

    var lockEnabled: Boolean
        get() = bool("lock_enabled", false)
        set(v) = setBool("lock_enabled", v)

    var skipDuringCalls: Boolean
        get() = bool("skip_calls", true)
        set(v) = setBool("skip_calls", v)

    var newWordEachTime: Boolean
        get() = bool("new_each_time", false)
        set(v) = setBool("new_each_time", v)

    var pausedUntil: Long
        get() = long("paused_until", 0)
        set(v) = setLong("paused_until", v)

    var autoSpeak: Boolean
        get() = bool("auto_speak", false)
        set(v) = setBool("auto_speak", v)

    // 0: learning (word hidden only while it is being recalled), 1: translation only,
    // 2: word only (translation hidden until tapped).
    var cardMode: Int
        get() = int("card_mode", if (sp.getBoolean("slide_mode", false)) 2 else 0)
        set(v) = setInt("card_mode", v)

    // How many words are learned at the same time.
    var activeSize: Int
        get() = int("active_size", 20)
        set(v) = setInt("active_size", v)

    // Cards shown so far; decides when an article or a check comes up.
    val cardCount: Int
        get() = sp.getInt("card_count", 0)

    fun bumpCardCount() = sp.edit { putInt("card_count", cardCount + 1) }

    var useMarks: Boolean
        get() = bool("use_marks", true)
        set(v) = setBool("use_marks", v)

    var reviewNotification: Boolean
        get() = bool("review_notification", false)
        set(v) = setBool("review_notification", v)

    // The long-press hint is shown on the 1st, 10th and 30th «Знаю» given to a new word, until
    // it is closed or the long press is used once.
    var hintDone: Boolean
        get() = bool("hint_done", false)
        set(v) = setBool("hint_done", v)

    fun hintAfterNewKnown(): Boolean {
        if (hintDone) return false
        val n = sp.getInt("new_known_count", 0) + 1
        sp.edit { putInt("new_known_count", n) }
        if (n >= 30) hintDone = true
        return n == 1 || n == 10 || n == 30
    }

    var reviewKnown: Boolean
        get() = bool("review_known", true)
        set(v) = setBool("review_known", v)

    // The current card, drawn at once on a cold start while the dictionary loads.
    val snapshot: String?
        get() = sp.getString("snapshot", null)

    // 0: full accentuation (ку̏ћа), 1: stressed syllable only (ку́ћа), 2: none.
    var accentMode: Int
        get() = int("accent_mode", 1)
        set(v) = setInt("accent_mode", v)

    var theme: Int
        get() = int("theme", 2)
        set(v) = setInt("theme", v)

    var wordSize: Int
        get() = int("word_size", 42)
        set(v) = setInt("word_size", v)

    var order: Order
        get() = Order.entries.getOrElse(int("order", 0)) { Order.SEQ }
        set(v) = setInt("order", v.ordinal)

    val shuffleSeed: Long
        get() = long("shuffle_seed", 1)

    val position: Int
        get() = int("position", 0)

    val currentKey: String?
        get() {
            version.intValue
            return sp.getString("current_key", null)
        }

    // One write and one recomposition per step; the snapshot always matches the key.
    fun setCurrent(position: Int, key: String?, snapshot: String?, seed: Long? = null) {
        sp.edit {
            putInt("position", position)
            putString("current_key", key)
            putString("snapshot", snapshot)
            if (seed != null) putLong("shuffle_seed", seed)
        }
        changed()
    }

    // Level ids "L1".."L5", topic ids "T:<code>", my-list ids "M:<name>".
    var categories: Set<String>
        get() {
            version.intValue
            val stored = sp.getStringSet("categories", null)
            // The phrase, comparison and note sections arrived later; switch them on once.
            if (stored != null && !sp.getBoolean("sections_added", false)) {
                val withSections = stored + Labels.sections.keys
                sp.edit { putStringSet("categories", withSections).putBoolean("sections_added", true) }
                return withSections
            }
            return stored?.toSet() ?: (setOf("L3", "L4") + Labels.sections.keys)
        }
        set(v) {
            // A choice made in this version already knows about the sections.
            sp.edit { putStringSet("categories", v).putBoolean("sections_added", true) }
            changed()
        }

    val todayCount: Int
        get() = int("today_count", 0)

    // The answer to the card on screen with the progress it was given on, as JSON; "" when none.
    var pendingAnswer: String
        get() = sp.getString("pending_answer", "") ?: ""
        set(v) = sp.edit { putString("pending_answer", v) }

    // The showing counted last, "<key>#<card number>": the lock card and the app show the same
    // card, and a showing must count once.
    val lastShown: String
        get() = sp.getString("last_shown", "") ?: ""

    fun countShown(today: Int, showing: String) {
        val n = if (sp.getInt("today_date", 0) == today) sp.getInt("today_count", 0) + 1 else 1
        sp.edit {
            putInt("today_date", today)
            putInt("today_count", n)
            putString("last_shown", showing)
        }
        changed()
    }

    // Settings that mean the same in the web version, under its names. Lock screen settings
    // stay on the device: they depend on permissions granted there.
    private val portable = listOf(
        "latinFirst" to "latin_first", "showBothScripts" to "show_both", "autoSpeak" to "auto_speak",
        "useMarks" to "use_marks", "reviewKnown" to "review_known", "hintDone" to "hint_done",
        "cardMode" to "card_mode", "activeSize" to "active_size", "cardCount" to "card_count",
        "accentMode" to "accent_mode", "theme" to "theme", "wordSize" to "word_size", "order" to "order",
        "newKnownCount" to "new_known_count", "shuffleSeed" to "shuffle_seed",
    )

    fun exportJson(): JSONObject {
        val o = JSONObject()
        for ((name, key) in portable) {
            when (val v = sp.all[key]) {
                is Boolean, is Int, is Long -> o.put(name, v)
            }
        }
        o.put("categories", JSONArray(categories.sorted()))
        return o
    }

    fun importJson(o: JSONObject) {
        sp.edit {
            for ((name, key) in portable) {
                when (val v = o.opt(name)) {
                    is Boolean -> putBoolean(key, v)
                    is Number -> if (key == "shuffle_seed") putLong(key, v.toLong()) else putInt(key, v.toInt())
                }
            }
            o.optJSONArray("categories")?.let { a ->
                putStringSet("categories", (0 until a.length()).map { a.getString(it) }.toSet())
                putBoolean("sections_added", true)
            }
        }
        changed()
    }

    fun primary(cyr: String) = if (latinFirst) Translit.toLatin(cyr) else cyr

    fun headword(w: Word): String {
        // A note's title is Russian; transliterating it would garble it.
        if (w.pos == "note") return w.cyr
        val shown = when {
            w.acc.isEmpty() || accentMode == 2 -> w.cyr
            accentMode == 1 -> Accents.placeOnly(w.acc)
            else -> w.acc
        }
        return primary(shown)
    }

    fun secondary(cyr: String): String? {
        if (!showBothScripts) return null
        return if (latinFirst) cyr else Translit.toLatin(cyr)
    }
}
