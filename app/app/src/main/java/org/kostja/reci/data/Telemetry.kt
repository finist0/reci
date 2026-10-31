package org.kostja.reci.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

// Anonymous daily statistics and messages to the developer, sent to the project's server.
// Kept in a file of their own, out of the progress file and the Android backup, so that a
// restored or copied install never carries the same id.
object Telemetry {
    const val SERVER = "https://reci.finist.workers.dev"
    const val PRIVACY = "https://finist0.github.io/reci/web/privacy.html"
    private const val RETRY = 60 * 60 * 1000L
    // The spaces \s matches in JavaScript, where the server checks the address.
    private const val SPACE = "\\s\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff"
    private val EMAIL = Regex("^[^$SPACE@<>\"]{1,64}@[^$SPACE@<>\"]{1,128}\\.[^$SPACE@<>\"]{2,32}$")
    // Control characters the server refuses in a message; line breaks and tabs are kept.
    private val CONTROL = Regex("[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]")
    private val LOCALE = Regex("""^[\w-]{1,40}$""")
    private val CAT = Regex("""^[\w:.-]{1,32}$""")
    // Everything the server's check lets through in app and system names.
    private val TEXT_JUNK = Regex("""[^\p{L}\p{N}_ .:+()/-]""")

    private lateinit var sp: SharedPreferences
    private lateinit var appVersion: String

    // Answers an HTTP POST with its status, or -1 when there was no answer. Tests replace it.
    var post: (url: String, body: String) -> Int = ::httpPost
    var now: () -> Long = System::currentTimeMillis

    // Network calls run one at a time off the main thread; results come back on it.
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    // Bumped on every change so that Compose readers recompose.
    val version = mutableIntStateOf(0)

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("telemetry", Context.MODE_PRIVATE)
        appVersion = clean(ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName)
        sp.edit {
            if (!sp.contains("id")) putString("id", UUID.randomUUID().toString())
            if (!sp.contains("feedback_id")) putString("feedback_id", UUID.randomUUID().toString())
        }
    }

    private fun changed() {
        version.intValue++
    }

    private fun str(key: String): String {
        version.intValue
        return sp.getString(key, "") ?: ""
    }

    val id get() = str("id")
    val feedbackId get() = str("feedback_id")

    var enabled: Boolean
        get() {
            version.intValue
            return sp.getBoolean("enabled", true)
        }
        set(v) {
            // Sums collected while statistics were off are never sent.
            sp.edit {
                putBoolean("enabled", v)
                if (!v) remove("counts")
            }
            changed()
        }

    val noticeShown get() = sp.getBoolean("notice_shown", false)

    val noticeClosed: Boolean
        get() {
            version.intValue
            return sp.getBoolean("notice_closed", false)
        }

    fun markNoticeShown() {
        if (!noticeShown) sp.edit { putBoolean("notice_shown", true) }
    }

    fun closeNotice() {
        sp.edit { putBoolean("notice_closed", true) }
        changed()
    }

    val sentDay get() = str("sent_day")

    // The last ping sent, shown in the settings as it went out.
    val last: JSONObject?
        get() = str("last").takeIf { it.isNotEmpty() }?.let { JSONObject(it) }

    var email: String
        get() = str("email")
        set(v) = sp.edit { putString("email", v) }

    fun isEmail(s: String) = EMAIL.matches(s)

    // --- What the app does, summed since the last ping ---

    @Synchronized
    fun count(name: String, sub: String? = null) {
        if (!enabled) return
        val c = JSONObject(sp.getString("counts", "{}")!!)
        if (sub == null) {
            c.put(name, c.optInt(name) + 1)
        } else {
            val o = c.optJSONObject(name) ?: JSONObject()
            o.put(sub, o.optInt(sub) + 1)
            c.put(name, o)
        }
        sp.edit { putString("counts", c.toString()) }
    }

    fun counts() = JSONObject(sp.getString("counts", "{}")!!)

    // What was counted while a ping was on its way stays for the next one.
    private fun minus(now: JSONObject, sent: JSONObject): JSONObject {
        val out = JSONObject()
        for (k in now.keys()) {
            val v = now.get(k)
            if (v is JSONObject) {
                val o = JSONObject()
                for (sub in v.keys()) {
                    val n = v.getInt(sub) - (sent.optJSONObject(k)?.optInt(sub) ?: 0)
                    if (n > 0) o.put(sub, n)
                }
                if (o.length() > 0) out.put(k, o)
            } else {
                val n = now.getInt(k) - sent.optInt(k)
                if (n > 0) out.put(k, n)
            }
        }
        return out
    }

    // A vendor's odd version string would get every ping refused.
    private fun clean(s: String?) = s?.replace(TEXT_JUNK, "")?.trim()?.take(40)?.ifEmpty { null } ?: "unknown"

    val osName get() = clean("Android " + Build.VERSION.RELEASE)

    // --- The daily ping ---

    fun localDay(millis: Long = now()): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return "%04d-%02d-%02d".format(Locale.ROOT, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun ping(day: String, learned: Int, c: JSONObject = counts()): JSONObject {
        val locale = Locale.getDefault().toLanguageTag()
        val p = JSONObject()
            .put("id", id).put("day", day).put("platform", "android").put("app", appVersion)
            .put("os", osName).put("locale", if (LOCALE.matches(locale)) locale else "other")
            .put("cats", JSONArray(Prefs.categories.filter { CAT.matches(it) }.sorted().take(100)))
            .put("mode", Prefs.cardMode).put("theme", Prefs.theme).put("latin", Prefs.latinFirst)
            .put("lock", if (Prefs.lockEnabled) "overlay" else "off").put("learned", learned)
        for (k in listOf("cards", "answers", "opened")) if (c.has(k)) p.put(k, c.get(k))
        return p
    }

    private var sending = false

    // Sends today's ping once: when statistics are on, the notice about them has been shown and
    // nothing went out today. A failed attempt is retried an hour later at the earliest.
    // `learned` is called off the main thread, as counting needs the loaded dictionary.
    fun sendPing(learned: () -> Int, done: (Boolean) -> Unit = {}) {
        val t = now()
        val day = localDay(t)
        if (sending || !enabled || !noticeShown || sentDay == day || t - sp.getLong("tried_at", 0) < RETRY) return
        sending = true
        sp.edit { putLong("tried_at", t) }
        io.execute {
            val sentCounts = counts()
            val p = ping(day, learned(), sentCounts)
            val status = post("$SERVER/ping", p.toString())
            main.post {
                sending = false
                // A rejected ping would be rejected again: drop it with the sums, as if it was sent.
                if (status in 200..299 || status == 400 || status == 413) {
                    synchronized(this) {
                        sp.edit {
                            putString("sent_day", day)
                            putString("counts", minus(counts(), sentCounts).toString())
                            if (status in 200..299) putString("last", p.toString())
                        }
                    }
                    changed()
                }
                done(status in 200..299)
            }
        }
    }

    // --- Messages to the developer ---

    private fun queue() = JSONArray(sp.getString("queue", "[]")!!)

    val waiting: Int
        get() {
            version.intValue
            return queue().length()
        }

    private var flushing = false
    private val flushWaiters = mutableListOf<(Int) -> Unit>()

    // Sends the waiting messages in order. One that the server rejects is dropped; one that hit a
    // limit or no network waits for the next try. `done` gets the number still waiting, also when
    // a flush was already running.
    fun flush(done: (Int) -> Unit = {}) {
        flushWaiters.add(done)
        if (flushing) return
        flushing = true
        io.execute {
            while (true) {
                val q = queue()
                if (q.length() == 0) break
                val status = post("$SERVER/feedback", q.getJSONObject(0).toString())
                if (status == -1 || status == 429 || status >= 500) break
                synchronized(this) {
                    val left = queue()
                    left.remove(0)
                    sp.edit(commit = true) { putString("queue", left.toString()) }
                }
            }
            main.post {
                flushing = false
                changed()
                val left = queue().length()
                val callbacks = flushWaiters.toList()
                flushWaiters.clear()
                callbacks.forEach { it(left) }
            }
        }
    }

    // Queues a message (kind, text, card) and tries to send it; `done` gets true when it went out.
    // It carries its own id, so that it cannot be tied to the statistics of the same install.
    fun sendFeedback(kind: String, text: String, card: JSONObject?, mail: String, done: (Boolean) -> Unit = {}) {
        email = mail
        val m = JSONObject()
            .put("id", feedbackId).put("kind", kind).put("platform", "android").put("app", appVersion)
            .put("os", osName)
        val clean = text.replace(CONTROL, "")
        if (clean.isNotEmpty()) m.put("text", clean)
        if (card != null) m.put("card", card)
        if (mail.isNotEmpty()) m.put("email", mail)
        synchronized(this) {
            sp.edit(commit = true) { putString("queue", queue().put(m).toString()) }
        }
        changed()
        flush { done(it == 0) }
    }

    private fun httpPost(url: String, body: String): Int {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 5000
                c.readTimeout = 5000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
                c.responseCode
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            -1
        }
    }
}
