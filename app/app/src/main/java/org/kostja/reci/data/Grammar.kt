package org.kostja.reci.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException

data class GrammarBlock(
    val type: String,
    val text: String,
    val items: List<String>,
    val head: List<String>,
    val rows: List<List<String>>,
    val sr: String,
    val ru: String,
)

data class GrammarTopic(
    val id: String,
    val group: String,
    val title: String,
    val blocks: List<GrammarBlock>,
    // Serbian words the topic is about; comparison topics are linked from their cards.
    val words: List<String> = emptyList(),
)

object Grammar {
    private lateinit var ctx: Context

    @Volatile
    private var loaded: List<GrammarTopic>? = null

    // Bumped once the reference is parsed so that cards pick up their links.
    val version = mutableIntStateOf(0)

    fun init(c: Context) {
        ctx = c.applicationContext
    }

    // Empty until loaded; never blocks the caller.
    val topics: List<GrammarTopic>
        get() {
            version.intValue
            return loaded ?: emptyList()
        }

    fun topic(id: String) = topics.firstOrNull { it.id == id }

    fun preload() {
        load()
    }

    @Synchronized
    private fun load(): List<GrammarTopic> {
        loaded?.let { return it }
        val text = try {
            ctx.assets.open("grammar.json").bufferedReader().use { it.readText() }
        } catch (_: FileNotFoundException) {
            "[]"
        }
        val arr = JSONArray(text)
        val list = List(arr.length()) { parse(arr.getJSONObject(it)) }
        loaded = list
        version.intValue++
        return list
    }

    private fun strings(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { a.getString(it) }

    private fun parse(o: JSONObject): GrammarTopic {
        val blocks = o.getJSONArray("blocks")
        return GrammarTopic(
            id = o.getString("id"),
            group = o.optString("group"),
            title = o.getString("title"),
            words = strings(o.optJSONArray("words")),
            blocks = List(blocks.length()) {
                val b = blocks.getJSONObject(it)
                val rows = b.optJSONArray("rows")
                GrammarBlock(
                    type = b.getString("type"),
                    text = b.optString("text"),
                    items = strings(b.optJSONArray("items")),
                    head = strings(b.optJSONArray("head")),
                    rows = if (rows == null) emptyList() else List(rows.length()) { r -> strings(rows.getJSONArray(r)) },
                    sr = b.optString("sr"),
                    ru = b.optString("ru"),
                )
            },
        )
    }

    // Topics worth opening from a word card, most specific first.
    fun linksFor(w: Word): List<String> = when (w.pos) {
        "noun" -> listOf(
            when {
                w.gram.startsWith("м") -> "noun-m"
                w.gram.startsWith("ср") -> "noun-n"
                w.gram.startsWith("ж") && w.cyr.endsWith("а") -> "noun-f-a"
                w.gram.startsWith("ж") -> "noun-f-cons"
                else -> "cases"
            },
            "prepositions",
        )
        "verb" -> if (w.key == "бити") listOf("biti", "perfect") else listOf("present", "aspect")
        "prep" -> listOf("prepositions", "cases")
        "pron" -> listOf("pronouns", "clitics")
        "adj" -> listOf("adjectives", "comparison")
        "num" -> listOf("numbers")
        "part", "conj" -> listOf("clitics", "questions")
        else -> emptyList()
    }.filter { topic(it) != null }
}
