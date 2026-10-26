package org.kostja.reci.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import android.util.JsonReader
import java.text.Collator
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.random.Random

object Repo {
    private lateinit var ctx: Context
    private lateinit var db: ProgressDb
    private val io = Executors.newSingleThreadExecutor()
    private val progress = ConcurrentHashMap<String, Progress>()

    @Volatile
    private var loaded: List<Word>? = null
    private var byKey: Map<String, Word> = emptyMap()

    // Bumped on every progress change so that Compose readers recompose.
    val version = mutableIntStateOf(0)

    fun init(c: Context) {
        ctx = c.applicationContext
        db = ProgressDb(ctx)
    }

    // Empty until loaded; never blocks the caller.
    val words: List<Word>
        get() {
            version.intValue
            return loaded ?: emptyList()
        }

    val isLoaded: Boolean
        get() {
            version.intValue
            return loaded != null
        }

    fun word(key: String): Word? {
        words
        return byKey[key]
    }

    @Volatile
    private var byCyr: Map<String, Word>? = null

    // The card for a word mentioned elsewhere (a synonym, an antonym), if the dictionary has one.
    fun wordByCyr(cyr: String): Word? {
        if (!isLoaded) return null
        val m = byCyr ?: HashMap<String, Word>().also { m ->
            for (w in words) if (w.level > 0 || w.pos == "phrase") m.putIfAbsent(w.cyr, w)
        }.also { byCyr = it }
        return m[cyr]
    }

    fun loadAsync() {
        if (loaded == null) io.execute { load(); Grammar.preload() }
    }

    // Runs f on the io thread once the dictionary is loaded.
    fun afterLoad(f: () -> Unit) = io.execute { load(); f() }

    @Synchronized
    private fun load(): List<Word> {
        loaded?.let { return it }
        val list = ArrayList<Word>(4096)
        JsonReader(ctx.assets.open("words.json").bufferedReader()).use { r ->
            r.beginArray()
            while (r.hasNext()) list += WordJson.read(r)
            r.endArray()
        }
        progress.putAll(db.loadAll())
        byKey = list.associateBy { it.key }
        loaded = list
        version.intValue++
        return list
    }

    fun progress(w: Word): Progress {
        version.intValue
        return progress[w.key] ?: Progress()
    }

    private fun update(w: Word, f: (Progress) -> Progress) {
        // Progress is read together with the dictionary; a change made before that is applied after.
        if (loaded == null) {
            afterLoad { update(w, f) }
            return
        }
        val p = f(progress[w.key] ?: Progress())
        progress[w.key] = p
        io.execute { db.put(w.key, p) }
        version.intValue++
    }

    private const val DAY = 24 * 60 * 60 * 1000L

    // Tests move time forward to make checks due.
    internal var now: () -> Long = System::currentTimeMillis

    // A word goes: not started -> INTRO (word and translation shown) -> RECALL (translation
    // only, the user names the word) -> LEARNED, then comes back for checks.
    const val INTRO = 1
    const val RECALL = 2
    const val LEARNED = 3
    private const val INTRO_STREAK = 1
    private const val RECALL_STREAK = 3

    // A word answered «Не знаю» comes back this many cards later, while it is still fresh.
    private const val RETRY_AFTER = 5

    // After five right answers in a row the first check comes in a week; each passed check
    // roughly triples the gap, as in spaced-repetition schedules.
    private val reviewIntervals = longArrayOf(7 * DAY, 21 * DAY, 60 * DAY, 180 * DAY, 365 * DAY)

    private fun reviewDue(step: Int) = now() + reviewIntervals[minOf(step, reviewIntervals.size - 1)]

    private fun learned(p: Progress) =
        p.copy(phase = LEARNED, streak = 0, state = State.KNOWN, step = 0, due = reviewDue(0))

    private fun passedCheck(p: Progress) = p.copy(step = p.step + 1, due = reviewDue(p.step + 1))

    fun isArticle(w: Word) = w.pos == "compare" || w.pos == "note"

    fun isDue(p: Progress?) =
        p != null && p.phase == LEARNED && Prefs.reviewKnown && p.due in 1..now()

    // Whether the card should hide the Serbian side so the user recalls it.
    fun asksRecall(w: Word): Boolean {
        if (isArticle(w)) return false
        if (Prefs.cardMode == 1) return true
        if (Prefs.cardMode == 2) return false
        val p = shownProgress(w)
        return p.phase == RECALL || isDue(p)
    }

    private class Answer(val before: Progress, val state: State, val learned: Boolean)

    // Answers to recently shown cards with the progress they were given on, so an answer can
    // be changed, also after going back to the card.
    private val answers = ConcurrentHashMap<String, Answer>()

    fun answerOf(w: Word): State? = answers[w.key]?.state

    // The progress the card was shown with, before it was answered.
    fun shownProgress(w: Word): Progress = answers[w.key]?.before ?: progress(w)

    // KNOWN or DONT_KNOW; learned skips the stages for a word the user already knows. A new
    // answer to the same card replaces the previous one.
    fun answer(w: Word, a: State, learned: Boolean = false) {
        val old = answers[w.key]
        if (old != null && old.state == a && old.learned == learned) return
        update(w) { cur ->
            val before = answers[w.key]?.before ?: cur
            answers[w.key] = Answer(before, a, learned)
            val p = cur.copy(
                state = before.state, phase = before.phase, streak = before.streak, step = before.step,
                due = before.due, showAt = before.showAt,
            )
            when {
                !learned -> advance(w, p, a)
                p.phase == LEARNED -> p
                else -> learned(p)
            }
        }
    }

    private fun advance(w: Word, p: Progress, a: State): Progress {
        val n = Prefs.cardCount
        return when {
            // «Не показывать» hides an article for good; «Перечитать позже» leaves it in the round.
            isArticle(w) ->
                if (a == State.KNOWN) p.copy(state = State.KNOWN, phase = LEARNED, streak = 0, step = 0, due = 0)
                else p.copy(state = State.NEW, phase = 0, streak = 0, due = 0)
            // A forgotten learned word skips the introduction and is asked again a full round later.
            p.phase == LEARNED ->
                if (a == State.KNOWN) passedCheck(p)
                else p.copy(phase = RECALL, streak = 0, state = a, step = 0, due = 0, showAt = n + Prefs.activeSize)
            p.phase == RECALL -> when (a) {
                State.KNOWN -> if (p.streak + 1 >= RECALL_STREAK) learned(p) else p.copy(streak = p.streak + 1, state = State.NEW)
                State.UNSURE -> p.copy(streak = 0, state = a)
                else -> p.copy(phase = INTRO, streak = 0, state = State.DONT_KNOW, showAt = n + RETRY_AFTER)
            }
            a == State.KNOWN ->
                if (p.streak + 1 >= INTRO_STREAK) p.copy(phase = RECALL, streak = 0, state = State.NEW)
                else p.copy(phase = INTRO, streak = p.streak + 1, state = State.NEW)
            else -> p.copy(phase = INTRO, streak = 0, state = a, showAt = n + RETRY_AFTER)
        }
    }

    fun toggleFav(w: Word) = update(w) { it.copy(fav = !it.fav) }

    fun markShown(w: Word) {
        if (revisit) return
        update(w) { it.copy(seen = it.seen + 1, last = now(), showAt = 0) }
        val c = Calendar.getInstance()
        Prefs.countShown(c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR))
    }

    // Waits for the dictionary and for pending writes, so call it off the main thread.
    fun allProgress(): Map<String, Progress> {
        load()
        io.submit {}.get()
        return HashMap(progress)
    }

    fun replaceProgress(all: Map<String, Progress>) {
        load()
        io.submit { db.replaceAll(all) }.get()
        progress.clear()
        progress.putAll(all)
        resetPosition()
        version.intValue++
    }

    fun resetAll() = io.execute {
        db.clear()
        progress.clear()
        answers.clear()
        version.intValue++
    }

    fun inCategory(w: Word, id: String): Boolean {
        val p = progress[w.key]
        return when {
            id.startsWith("L") -> id.length == 2 && id[1] - '0' == w.level
            id.startsWith("T:") -> id.length == w.topic.length + 2 && id.endsWith(w.topic)
            id.startsWith("X:") -> w.pos.length == id.length - 2 && id.endsWith(w.pos)
            // UNSURE is left over from the removed «Почти» answer.
            id == "M:dontknow" -> p?.state == State.DONT_KNOW || p?.state == State.UNSURE
            id == "M:known" -> p?.state == State.KNOWN
            id == "M:fav" -> p?.fav == true
            else -> false
        }
    }

    // Per category: all words and words marked ✓, in one pass.
    fun counts(): Map<String, IntArray> {
        val out = HashMap<String, IntArray>()
        fun add(id: String, known: Boolean) {
            val a = out.getOrPut(id) { IntArray(2) }
            a[0]++
            if (known) a[1]++
        }
        for (w in words) {
            val known = progress[w.key]?.state == State.KNOWN
            if (w.level > 0) add("L${w.level}", known)
            if (w.level == 0) add("X:${w.pos}", known)
            add("T:${w.topic}", known)
            for (id in Labels.lists.keys) if (inCategory(w, id)) add(id, known)
        }
        return out
    }

    private var shuffled: List<Word> = emptyList()
    private var shuffledSeed = 0L

    // The whole dictionary is shuffled once per seed, so the order stays stable as words move on.
    private fun shuffled(seed: Long): List<Word> {
        if (shuffled.isEmpty() || seed != shuffledSeed) {
            shuffled = words.shuffled(Random(seed))
            shuffledSeed = seed
        }
        return shuffled
    }

    // Words and phrases are learned; comparisons and notes are only read and checked.
    private fun learnable(w: Word, cats: Set<String>): Boolean {
        if (isArticle(w)) return false
        if (w.pos == "phrase") return "X:phrase" in cats || cats.any { it.startsWith("M:") && inCategory(w, it) }
        return cats.any { !it.startsWith("X:") && inCategory(w, it) }
    }

    private var queueKey: List<Any> = emptyList()
    private var queueCache: List<Word> = emptyList()

    // Everything learnable in the chosen order; every fifth item is a phrase when both are chosen.
    private fun queue(): List<Word> {
        val cats = Prefs.categories
        val order = Prefs.order
        val seed = Prefs.shuffleSeed
        val key = listOf(cats, order, seed, words.size)
        if (key == queueKey) return queueCache
        val chosen = (if (order == Order.RANDOM) shuffled(seed) else words).filter { learnable(it, cats) }
        val phrases = shuffled(seed).filter { it.pos == "phrase" && learnable(it, cats) }
        val plain = chosen.filter { it.pos != "phrase" }.let { list ->
            if (order != Order.ALPHA) list
            else {
                val coll = Collator.getInstance(Locale.forLanguageTag("sr"))
                list.sortedWith { a, b -> coll.compare(a.cyr, b.cyr) }
            }
        }
        val out = ArrayList<Word>(plain.size + phrases.size)
        var ph = 0
        plain.forEachIndexed { i, w ->
            out += w
            if (i % 4 == 3 && ph < phrases.size) out += phrases[ph++]
        }
        out += phrases.drop(ph)
        queueKey = key
        queueCache = out
        return out
    }

    private fun phaseOf(w: Word) = progress[w.key]?.phase ?: 0

    private fun showAtOf(w: Word) = progress[w.key]?.showAt ?: 0

    // The words being learned now; topped up from the queue when some were learned.
    private fun activeSet(): List<Word> {
        val q = queue()
        val active = q.filter { phaseOf(it) == INTRO || phaseOf(it) == RECALL }
        val missing = Prefs.activeSize - active.size
        if (missing <= 0) return active
        val fresh = q.asSequence().filter { phaseOf(it) == 0 && progress[it.key]?.state != State.KNOWN }.take(missing).toList()
        for (w in fresh) update(w) { it.copy(phase = INTRO, streak = 0) }
        return active + fresh
    }

    // The article shown longest ago, a comparison and a note in turn while both are left.
    private fun articleFor(exclude: String?): Word? {
        val cats = Prefs.categories
        val pool = words.filter { isArticle(it) && "X:${it.pos}" in cats && it.key != exclude && phaseOf(it) != LEARNED }
        val lastPos = words.filter { isArticle(it) }.maxByOrNull { progress[it.key]?.last ?: 0 }
            ?.takeIf { (progress[it.key]?.last ?: 0) > 0 }?.pos
        return pool.filter { it.pos != lastPos }.ifEmpty { pool }.minByOrNull { progress[it.key]?.last ?: 0 }
    }

    // Articles of one kind ("compare" or "note") hidden with «Не показывать».
    fun hiddenArticles(pos: String): Int = words.count { it.pos == pos && phaseOf(it) == LEARNED }

    fun showArticlesAgain(pos: String) {
        for (w in words) {
            if (w.pos == pos && phaseOf(w) == LEARNED) update(w) { it.copy(state = State.NEW, phase = 0, step = 0, due = 0, last = 0) }
        }
    }

    private fun dueFor(exclude: String?): Word? =
        queue().asSequence().filter { it.key != exclude && isDue(progress[it.key]) }.minByOrNull { progress[it.key]!!.due }

    // Next card: a word whose return after «Не знаю» is due, every fifth an article, every fourth
    // a check of a learned word when one is due, otherwise the active word shown longest ago.
    private fun pickNext(exclude: String?): Word? {
        val n = Prefs.cardCount
        val active = activeSet()
        active.filter { it.key != exclude && showAtOf(it) in 1..n }.minByOrNull { showAtOf(it) }?.let { return it }
        if (n % 5 == 4) articleFor(exclude)?.let { return it }
        if (n % 4 == 3) dueFor(exclude)?.let { return it }
        return active.filter { it.key != exclude && showAtOf(it) <= n }.minByOrNull { progress[it.key]?.last ?: 0 }
            ?: active.firstOrNull()
            ?: dueFor(exclude)
            ?: articleFor(exclude)
    }

    private val history = ArrayDeque<String>()

    // Cards left by going back, shown again in the same order by going forward.
    private val forward = ArrayDeque<String>()

    // The card on screen was reached by going back or forward, so it was already counted.
    @Volatile
    private var revisit = false

    // Set when nothing is left to show for the chosen sections.
    val exhausted = mutableStateOf(false)

    fun setCategories(ids: Set<String>) {
        Prefs.categories = ids
        resetPosition()
    }

    fun resetPosition() {
        history.clear()
        forward.clear()
        answers.clear()
        exhausted.value = false
        Prefs.setCurrent(0, null, null, System.nanoTime())
    }

    // The card on screen. Null until the first step(1) picks one.
    fun current(): Word? {
        if (!isLoaded) return Prefs.snapshot?.let { WordJson.parse(it) }?.takeIf { it.key == Prefs.currentKey }
        return Prefs.currentKey?.let { word(it) }
    }

    fun step(delta: Int) {
        if (!isLoaded) return
        val cur = Prefs.currentKey
        if (delta < 0) {
            val prev = history.removeLastOrNull()?.let { word(it) } ?: return
            if (cur != null) forward.addLast(cur)
            revisit = true
            Prefs.setCurrent(0, prev.key, WordJson.write(prev))
            return
        }
        if (cur != null) {
            history.addLast(cur)
            if (history.size > 100) history.removeFirst()
        }
        forward.removeLastOrNull()?.let { word(it) }?.let {
            revisit = true
            Prefs.setCurrent(0, it.key, WordJson.write(it))
            return
        }
        revisit = false
        Prefs.bumpCardCount()
        val next = pickNext(cur)
        exhausted.value = next == null
        // A fresh showing of a card takes a fresh answer.
        next?.let { answers.remove(it.key) }
        answers.keys.retainAll(history.toSet() + listOfNotNull(next?.key))
        Prefs.setCurrent(0, next?.key, next?.let { WordJson.write(it) })
    }

    @Volatile
    private var compareIndex: Map<String, List<Word>>? = null

    // Comparison cards that discuss this word, for the links under its grammar line.
    fun comparesFor(w: Word): List<Word> {
        if (!isLoaded || w.pos == "compare") return emptyList()
        val index = compareIndex ?: HashMap<String, MutableList<Word>>().also { m ->
            for (c in words) if (c.pos == "compare") for (r in c.related) m.getOrPut(r.sr) { mutableListOf() } += c
        }.also { compareIndex = it }
        return index[w.cyr].orEmpty().take(2)
    }

    fun reviewWord(): Word? {
        if (!isLoaded) return null
        val marked = words.filter {
            progress[it.key]?.state.let { s -> s == State.DONT_KNOW || s == State.UNSURE }
        }
        return marked.randomOrNull()
    }
}
