package org.kostja.reci

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Order
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.State
import org.kostja.reci.data.Word
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import java.io.File
import java.text.Collator
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

// Runs tests/learning-scenarios.json against Repo; web/tests/learning.test.mjs runs the same
// file against web/js/repo.js, so the two implementations cannot drift apart unnoticed.
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35])
class LearningScenariosTest(private val name: String, private val index: Int) {

    companion object {
        private val file = File("../../tests/learning-scenarios.json")

        // Parameters are listed outside the Robolectric sandbox, where org.json is only a stub.
        @JvmStatic
        @Parameters(name = "{0}")
        fun scenarios(): List<Array<Any>> =
            Regex("\"name\":\\s*\"([^\"]*)\"").findAll(file.readText()).mapIndexed { i, m -> arrayOf<Any>(m.groupValues[1], i) }.toList()

        private const val DAY = 24 * 60 * 60 * 1000L
        private val phases = mapOf("new" to 0, "intro" to Repo.INTRO, "recall" to Repo.RECALL, "learned" to Repo.LEARNED)
        private val states = mapOf("new" to State.NEW, "dontknow" to State.DONT_KNOW, "known" to State.KNOWN)
    }

    private class Mark(val key: String, val n: Int)

    private var clock = 1_767_225_600_000L // 2026-01-01
    private val vars = HashMap<String, Mark>()
    private var distance = -1
    private var collected = emptyList<Triple<Int, String, String>>()

    private fun waitIo() {
        val done = CountDownLatch(1)
        Repo.afterLoad { done.countDown() }
        assertTrue("dictionary did not load", done.await(60, TimeUnit.SECONDS))
    }

    private fun current(): Word = Repo.current() ?: error("no card on screen")

    private fun kind(w: Word) = when {
        Repo.isArticle(w) -> "article"
        w.pos == "phrase" -> "phrase"
        else -> "word"
    }

    // What the card screen does after a step: the card on screen is counted as shown.
    private fun move(delta: Int) {
        clock += 1000
        Repo.step(delta)
        Repo.current()?.let { Repo.markShown(it) }
    }

    private fun answer(a: String) {
        val w = current()
        when (a) {
            "know", "hide" -> Repo.answer(w, State.KNOWN)
            "dontknow", "later" -> Repo.answer(w, State.DONT_KNOW)
            "long" -> Repo.answer(w, State.KNOWN, learned = true)
            else -> error("unknown answer $a")
        }
    }

    // Answers the card on screen the way a skim or an until step asks for, unless it already has
    // an answer: a card is answered once, as a person would.
    private fun answerAll(step: JSONObject) {
        val w = current()
        if (Repo.answerOf(w) != null) return
        val policy = if (Repo.isArticle(w)) step.optString("articles", "none") else step.optString("answer", "none")
        if (policy != "none") answer(policy)
    }

    private fun word(name: String) = Repo.word(vars[name]?.key ?: error("no word $name")) ?: error("no card ${vars[name]}")

    private fun run(step: JSONObject) {
        when (step.optString("do")) {
            "next" -> move(1)
            "prev" -> move(-1)
            "answer" -> answer(step.getString("a"))
            "remember" -> vars[step.getString("as")] = Mark(current().key, Prefs.cardCount)
            "fav" -> Repo.toggleFav(current())
            "advanceDays" -> clock += step.getInt("days") * DAY
            "set" -> Prefs.activeSize = step.getInt("activeSize")
            "showAgain" -> Repo.markShown(current())
            "showArticlesAgain" -> Repo.showArticlesAgain(step.getString("pos"))
            "pick" -> {
                val level = step.getInt("level")
                val pool = Repo.words.filter { it.level == level }
                val coll = Collator.getInstance(Locale.forLanguageTag("sr"))
                val w = if (step.getString("by") == "alpha") pool.minWith { a, b -> coll.compare(a.cyr, b.cyr) } else pool.first()
                vars[step.getString("as")] = Mark(w.key, 0)
            }
            "skim" -> {
                val seen = ArrayList<Triple<Int, String, String>>()
                repeat(step.getInt("count")) {
                    answerAll(step)
                    move(1)
                    val w = current()
                    seen += Triple(Prefs.cardCount, w.key, if (Repo.isArticle(w)) w.pos else kind(w))
                }
                if (step.optBoolean("collect")) collected = seen
            }
            "until" -> {
                val target = vars[step.getString("word")] ?: error("no word")
                repeat(step.getInt("max")) {
                    answerAll(step)
                    move(1)
                    if (current().key == target.key) {
                        distance = Prefs.cardCount - target.n
                        return
                    }
                }
                fail("${step.getString("word")} did not come back within ${step.getInt("max")} cards")
            }
            "untilArticle" -> {
                repeat(step.getInt("max")) {
                    answerAll(step)
                    move(1)
                    if (Repo.isArticle(current())) return
                }
                fail("no article within ${step.getInt("max")} cards")
            }
            "" -> expect(step.getJSONObject("expect"))
            else -> error("unknown step $step")
        }
    }

    private fun expect(e: JSONObject) {
        if (e.has("word")) {
            val w = word(e.getString("word"))
            val p = Repo.progress(w)
            if (e.has("phase")) assertEquals("phase of ${w.key}", phases[e.getString("phase")], p.phase)
            if (e.has("streak")) assertEquals("streak of ${w.key}", e.getInt("streak"), p.streak)
            if (e.has("state")) assertEquals("state of ${w.key}", states[e.getString("state")], p.state)
            if (e.has("fav")) assertEquals("fav of ${w.key}", e.getBoolean("fav"), p.fav)
            if (e.has("seen")) assertEquals("times ${w.key} was shown", e.getInt("seen"), p.seen)
            if (e.has("dueInDays")) {
                if (e.isNull("dueInDays")) assertEquals("no check for ${w.key}", 0L, p.due)
                else assertEquals("days to check of ${w.key}", e.getLong("dueInDays"), ((p.due - clock) / DAY.toDouble()).roundToLong())
            }
        }
        if (e.has("current")) assertEquals(vars[e.getString("current")]?.key, current().key)
        if (e.has("currentNot")) {
            val names = e.getJSONArray("currentNot")
            for (i in 0 until names.length()) assertNotEquals(vars[names.getString(i)]?.key, current().key)
        }
        if (e.has("distance")) assertEquals("cards until it came back", e.getInt("distance"), distance)
        if (e.has("cardNumber")) assertEquals("card number", e.getInt("cardNumber"), Prefs.cardCount)
        if (e.has("activeCount")) {
            assertEquals("active words", e.getInt("activeCount"), Repo.words.count { Repo.progress(it).phase in Repo.INTRO..Repo.RECALL })
        }
        if (e.has("distinctWords")) {
            assertEquals("different words", e.getInt("distinctWords"), collected.filter { it.third != "compare" && it.third != "note" }.map { it.second }.toSet().size)
        }
        if (e.has("hidden")) {
            val h = e.getJSONObject("hidden")
            for (pos in h.keys()) assertEquals("hidden $pos", h.getInt(pos), Repo.hiddenArticles(pos))
        }
        if (e.has("articleSlots")) {
            for ((n, _, kind) in collected) {
                assertEquals("card $n is an article", n % 5 == 4, kind == "compare" || kind == "note")
            }
        }
        if (e.has("articlesAlternate")) {
            val kinds = collected.map { it.third }.filter { it == "compare" || it == "note" }
            assertTrue("too few articles: $kinds", kinds.size >= 4)
            assertTrue("articles do not alternate: $kinds", kinds.zipWithNext().all { (a, b) -> a != b })
        }
        if (e.has("kindsAt")) {
            val k = e.getJSONObject("kindsAt")
            for (n in k.keys()) assertEquals("kind of card $n", k.getString(n), collected.first { it.first == n.toInt() }.third)
        }
        if (e.has("keyAt")) {
            val k = e.getJSONObject("keyAt")
            for (n in k.keys()) assertEquals("card $n", vars[k.getString(n)]?.key, collected.first { it.first == n.toInt() }.second)
        }
        if (e.has("notSeen")) {
            val key = vars[e.getString("notSeen")]?.key
            assertTrue("$key came back", collected.none { it.second == key })
        }
    }

    @Test
    fun scenario() {
        val scenario = JSONArray(file.readText()).getJSONObject(index)
        waitIo()
        Repo.now = { clock }
        Repo.resetAll()
        waitIo()
        val s = scenario.optJSONObject("settings") ?: JSONObject()
        Prefs.activeSize = s.optInt("activeSize", 20)
        Prefs.order = Order.entries[s.optInt("order", 0)]
        Prefs.reviewKnown = s.optBoolean("reviewKnown", true)
        Prefs.cardMode = 0
        val cats = s.optJSONArray("categories")
        Repo.setCategories(if (cats == null) setOf("L3") else List(cats.length()) { cats.getString(it) }.toSet())
        val steps = scenario.getJSONArray("steps")
        for (i in 0 until steps.length()) {
            try {
                run(steps.getJSONObject(i))
            } catch (e: AssertionError) {
                throw AssertionError("$name, step ${i + 1} ${steps.getJSONObject(i)}: ${e.message}", e)
            }
        }
    }
}
