package org.kostja.reci.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.content.edit
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

// Card fixes without a new release: once a day the app fetches the patch made for the dictionary
// it carries (data/scripts/build_patches.py), keeps it and applies it on top of that dictionary.
// Everything runs on a thread of its own with short timeouts, so a slow or dropping network never
// holds up a card; until a patch is complete and checked, the cards come from what the app has.
object DictUpdate {
    const val SITE = "https://finist0.github.io/reci/dict/"
    private const val MAX_SIZE = 16 * 1024 * 1024
    private const val RETRY = 60 * 60 * 1000L

    class Patch(val base: String, val words: List<Word>, val removed: List<String>, val grammar: List<GrammarTopic>?)

    private lateinit var ctx: Context
    private lateinit var sp: SharedPreferences

    // The body of a 200 answer, or null on anything else. Tests replace it.
    var get: (url: String) -> ByteArray? = ::httpGet
    var now: () -> Long = System::currentTimeMillis

    // Below the threads that draw the cards and load the dictionary at start.
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "dict-update")
    }
    private val main = Handler(Looper.getMainLooper())

    fun init(c: Context) {
        ctx = c.applicationContext
        sp = ctx.getSharedPreferences("dict", Context.MODE_PRIVATE)
    }

    private val file get() = File(ctx.filesDir, "dict/patch.json")

    // Changes with every install and update, so a patch kept for one build is not applied to another.
    private val installed get() = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime

    // The id build_patches.py gives this dictionary: a hash of words.json, a newline and grammar.json.
    fun baseId(): String {
        if (sp.contains("base_of") && sp.getLong("base_of", 0) == installed) sp.getString("base", null)?.let { return it }
        val id = baseIdOf(ctx.assets.open("words.json"), ctx.assets.open("grammar.json"))
        sp.edit { putString("base", id).putLong("base_of", installed) }
        return id
    }

    fun baseIdOf(words: InputStream, grammar: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        fun add(s: InputStream) = s.use {
            val buf = ByteArray(65536)
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        add(words)
        md.update('\n'.code.toByte())
        add(grammar)
        return hex(md.digest()).take(32)
    }

    private val hasPatch get() = sp.contains("patch_of") && sp.getLong("patch_of", 0) == installed

    // The patch kept for this build, or null. Reads only the small patch file, never the dictionary.
    fun stored(): Patch? {
        if (!hasPatch) return null
        return try {
            parse(file.readBytes(), sp.getString("patch_base", null) ?: return null)
        } catch (e: Exception) {
            null
        }
    }

    // The built-in cards with the patch on top, in the order the build sorts them.
    fun apply(base: List<Word>, p: Patch): List<Word> {
        val byKey = LinkedHashMap<String, Word>(base.size * 2)
        for (w in base) byKey[w.key] = w
        for (k in p.removed) byKey.remove(k)
        for (w in p.words) byKey[w.key] = w
        return byKey.values.sortedWith(compareBy<Word> { it.rank }.thenBy { it.key })
    }

    fun parse(data: ByteArray, base: String): Patch? = try {
        val o = JSONObject(String(data))
        val words = o.getJSONArray("words")
        val removed = o.getJSONArray("removed")
        val list = List(words.length()) { WordJson.parse(words.getJSONObject(it).toString()) ?: error("bad card") }
        if (o.getInt("format") != 1 || o.getString("base") != base || list.any { it.key.isEmpty() }) null
        else Patch(base, list, List(removed.length()) { removed.getString(it) }, o.optJSONArray("grammar")?.let { Grammar.parseAll(it.toString()) })
    } catch (e: Exception) {
        null
    }

    private fun localDay(t: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    private var checking = false

    // Looks for a new patch once a local day; a failed look is tried again an hour later at the
    // earliest. Returns at once. `done` gets true when a new patch was applied.
    fun check(done: (Boolean) -> Unit = {}) {
        val t = now()
        if (checking || sp.getInt("checked_day", 0) == localDay(t) || t - sp.getLong("tried_at", 0) < RETRY) return
        checking = true
        sp.edit { putLong("tried_at", t) }
        io.execute {
            val result = runCatching { fetch() }.getOrNull()
            // The new cards are built here, so the main thread only swaps them in.
            val fresh = runCatching { (result as? Patch)?.let { apply(Repo.builtIn(), it) to (it.grammar ?: Grammar.parseAll(null)) } }.getOrNull()
            main.post {
                checking = false
                if (result != null) sp.edit { putInt("checked_day", localDay(t)) }
                fresh?.let { (words, topics) ->
                    Repo.swap(words)
                    Grammar.swap(topics)
                }
                done(fresh != null)
            }
        }
    }

    // A new patch, Unit when there is nothing new, null when the look failed.
    private fun fetch(): Any? {
        val base = baseId()
        val manifest = get(SITE + "manifest.json")?.let { runCatching { JSONObject(String(it)) }.getOrNull() } ?: return null
        val entry = manifest.optJSONObject("patches")?.optJSONObject(base) ?: return dropIfAny()
        val sha = entry.optString("sha256")
        if (sha == sp.getString("patch_sha", null) && hasPatch && runCatching { sha(file.readBytes()) }.getOrNull() == sha) return Unit
        val name = entry.optString("file")
        if (!Regex("""[\w.-]+\.json""").matches(name)) return null
        val data = get(SITE + name) ?: return null
        if (data.size > MAX_SIZE || sha(data) != sha) return null
        val p = parse(data, base) ?: return null
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(file)) return null
        sp.edit(commit = true) { putString("patch_sha", sha).putString("patch_base", base).putLong("patch_of", installed) }
        return p
    }

    // The manifest has no patch for this dictionary any more: back to the built-in one.
    private fun dropIfAny(): Any {
        if (!hasPatch) return Unit
        file.delete()
        sp.edit(commit = true) { remove("patch_sha").remove("patch_of") }
        return Patch(sp.getString("base", "") ?: "", emptyList(), emptyList(), null)
    }

    private fun sha(b: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(b))

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(Locale.ROOT, it) }

    private fun httpGet(url: String): ByteArray? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 5000
            c.readTimeout = 10000
            c.useCaches = false
            if (c.responseCode != 200 || c.contentLengthLong > MAX_SIZE) null
            else c.inputStream.use { it.readNBytesCompat(MAX_SIZE + 1) }
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(65536)
        while (out.size() < limit) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
