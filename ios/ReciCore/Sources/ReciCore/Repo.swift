import Foundation

// The dictionary and the learning rules, ported from web/js/repo.js and data/Repo.kt; keep the
// three in step. tests/learning-scenarios.json checks all of them.
public final class Repo {
    public static let intro = 1
    public static let recall = 2
    public static let learned = 3
    private static let introStreak = 1
    private static let recallStreak = 3
    // A word answered «Не знаю» comes back this many cards later, while it is still fresh.
    private static let retryAfter = 5
    private static let day = 24 * 60 * 60 * 1000
    private static let reviewIntervals = [7, 21, 60, 180, 365].map { $0 * day }

    public let store: Store
    public private(set) var words: [Word] = []
    public private(set) var topics: [GrammarTopic] = []
    private var byKey: [String: Word] = [:]

    // Tests move time forward to make checks due.
    public var now: () -> Int = { Int(Date().timeIntervalSince1970 * 1000) }

    public init(store: Store, words: [Word], topics: [GrammarTopic] = []) {
        self.store = store
        self.words = words
        self.topics = topics
        byKey = Dictionary(words.map { ($0.key, $0) }, uniquingKeysWith: { a, _ in a })
        restorePending()
    }

    public var prefs: Prefs {
        get { store.prefs }
        set { store.prefs = newValue; store.savePrefs() }
    }

    public func word(_ key: String) -> Word? { byKey[key] }
    public func topic(_ id: String) -> GrammarTopic? { topics.first { $0.id == id } }

    private lazy var byCyr: [String: Word] = {
        var m: [String: Word] = [:]
        for w in words where (w.level > 0 || w.pos == "phrase") && m[w.cyr] == nil { m[w.cyr] = w }
        return m
    }()

    // The card for a word mentioned elsewhere (a synonym, an antonym), if the dictionary has one.
    public func wordByCyr(_ cyr: String) -> Word? { byCyr[cyr] }

    private lazy var compareIndex: [String: [Word]] = {
        var m: [String: [Word]] = [:]
        for c in words where c.pos == "compare" {
            for r in c.related { m[r.sr, default: []].append(c) }
        }
        return m
    }()

    // Comparison cards that discuss this word, for the links under its grammar line.
    public func comparesFor(_ w: Word) -> [Word] {
        if w.pos == "compare" { return [] }
        return Array((compareIndex[w.cyr] ?? []).prefix(2))
    }

    // Topics worth opening from a word card, most specific first.
    public func linksFor(_ w: Word) -> [String] {
        var ids: [String] = []
        switch w.pos {
        case "noun":
            let g = w.gram
            let own = g.hasPrefix("м") ? "noun-m" : g.hasPrefix("ср") ? "noun-n"
                : g.hasPrefix("ж") && w.cyr.hasSuffix("а") ? "noun-f-a" : g.hasPrefix("ж") ? "noun-f-cons" : "cases"
            ids = [own, "prepositions"]
        case "verb": ids = w.key == "бити" ? ["biti", "perfect"] : ["present", "aspect"]
        case "prep": ids = ["prepositions", "cases"]
        case "pron": ids = ["pronouns", "clitics"]
        case "adj": ids = ["adjectives", "comparison"]
        case "num": ids = ["numbers"]
        case "part", "conj": ids = ["clitics", "questions"]
        default: break
        }
        return ids.filter { topic($0) != nil }
    }

    // MARK: Progress and answers

    public func progress(_ w: Word) -> Progress { store.progress[w.key] ?? Progress() }
    private func progressOf(_ key: String) -> Progress? { store.progress[key] }

    private func update(_ w: Word, _ f: (Progress) -> Progress) {
        store.put(w.key, f(progress(w)))
    }

    private func reviewDue(_ step: Int) -> Int { now() + Repo.reviewIntervals[min(step, Repo.reviewIntervals.count - 1)] }

    private func learnedNow(_ p: Progress) -> Progress {
        var p = p
        p.phase = Repo.learned; p.streak = 0; p.state = Mark.known; p.step = 0; p.due = reviewDue(0)
        return p
    }

    private func passedCheck(_ p: Progress) -> Progress {
        var p = p
        p.step += 1
        p.due = reviewDue(p.step)
        return p
    }

    public func isDue(_ p: Progress?) -> Bool {
        guard let p else { return false }
        return p.phase == Repo.learned && prefs.reviewKnown && p.due >= 1 && p.due <= now()
    }

    // Whether the card should hide the Serbian side so the user recalls it.
    public func asksRecall(_ w: Word) -> Bool {
        if w.isArticle { return false }
        if prefs.cardMode == 1 { return true }
        if prefs.cardMode == 2 { return false }
        let p = shownProgress(w)
        return p.phase == Repo.recall || isDue(p)
    }

    private struct Answer {
        var before: Progress
        var state: Int
        var learned: Bool
    }

    // Answers to recently shown cards with the progress they were given on, so an answer can
    // be changed, also after going back to the card.
    private var answers: [String: Answer] = [:]

    public func answerOf(_ w: Word) -> Int? { answers[w.key]?.state }

    // The progress the card was shown with, before it was answered.
    public func shownProgress(_ w: Word) -> Progress { answers[w.key]?.before ?? progress(w) }

    // Mark.known or Mark.dontKnow; learned skips the stages for a word the user already knows.
    // A new answer to the same card replaces the previous one.
    public func answer(_ w: Word, _ a: Int, learned: Bool = false) {
        let old = answers[w.key]
        if let old, old.state == a, old.learned == learned { return }
        let cur = progress(w)
        let before = old?.before ?? cur
        answers[w.key] = Answer(before: before, state: a, learned: learned)
        var p = cur
        p.state = before.state; p.phase = before.phase; p.streak = before.streak; p.step = before.step
        p.due = before.due; p.showAt = before.showAt
        if !learned {
            store.put(w.key, advance(w, p, a))
        } else {
            store.put(w.key, p.phase == Repo.learned ? p : learnedNow(p))
        }
        prefs.pendingAnswer = PendingAnswer(key: w.key, state: a, learned: learned, before: before)
    }

    // The answer to the card on screen outlives the process and reaches the widgets, so that a
    // changed answer replaces the first one instead of adding to it.
    private func restorePending() {
        if let a = prefs.pendingAnswer, a.key == prefs.currentKey {
            answers[a.key] = Answer(before: a.before, state: a.state, learned: a.learned)
        }
    }

    public func resetAll() {
        answers.removeAll()
        prefs.pendingAnswer = nil
        store.replaceProgress([:])
    }

    // After a progress file was loaded: the old answers and position mean nothing any more.
    public func progressReplaced() {
        answers.removeAll()
        resetPosition()
    }

    // MARK: Categories

    public func inCategory(_ w: Word, _ id: String) -> Bool {
        let p = progressOf(w.key)
        if id.hasPrefix("L") { return Int(id.dropFirst()) == w.level }
        if id.hasPrefix("T:") { return String(id.dropFirst(2)) == w.topic }
        if id.hasPrefix("X:") { return String(id.dropFirst(2)) == w.pos }
        // UNSURE is left over from the removed «Почти» answer.
        if id == "M:dontknow" { return p?.state == Mark.dontKnow || p?.state == Mark.unsure }
        if id == "M:known" { return p?.state == Mark.known }
        if id == "M:fav" { return p?.fav == true }
        return false
    }

    // Per category: all words and words marked known, in one pass.
    public func counts() -> [String: (all: Int, known: Int)] {
        var out: [String: (all: Int, known: Int)] = [:]
        func add(_ id: String, _ known: Bool) {
            var a = out[id] ?? (0, 0)
            a.all += 1
            if known { a.known += 1 }
            out[id] = a
        }
        for w in words {
            let known = progressOf(w.key)?.state == Mark.known
            if w.level > 0 { add("L\(w.level)", known) } else { add("X:" + w.pos, known) }
            add("T:" + w.topic, known)
            for (id, _) in Labels.lists where inCategory(w, id) { add(id, known) }
        }
        return out
    }

    // MARK: Order of cards

    // mulberry32, the generator of the web version, so the shuffled order is the same there.
    private static func rng(_ seed: Int) -> () -> Double {
        var a = UInt32(truncatingIfNeeded: seed)
        return {
            a = a &+ 0x6D2B_79F5
            var t = a
            t = (t ^ (t >> 15)) &* (t | 1)
            t ^= t &+ ((t ^ (t >> 7)) &* (t | 61))
            return Double(t ^ (t >> 14)) / 4_294_967_296
        }
    }

    private var shuffledList: [Word] = []
    private var shuffledSeed = 0

    // The whole dictionary is shuffled once per seed, so the order stays stable as words move on.
    private func shuffled(_ seed: Int) -> [Word] {
        if shuffledList.isEmpty || seed != shuffledSeed {
            let r = Repo.rng(seed)
            var list = words
            var i = list.count - 1
            while i > 0 {
                let j = Int(floor(r() * Double(i + 1)))
                list.swapAt(i, j)
                i -= 1
            }
            shuffledList = list
            shuffledSeed = seed
        }
        return shuffledList
    }

    // Words and phrases are learned; comparisons and notes are only read and checked.
    private func learnable(_ w: Word, _ cats: [String]) -> Bool {
        if w.isArticle { return false }
        if w.pos == "phrase" { return cats.contains("X:phrase") || cats.contains { $0.hasPrefix("M:") && inCategory(w, $0) } }
        return cats.contains { !$0.hasPrefix("X:") && inCategory(w, $0) }
    }

    public static func collate(_ a: String, _ b: String) -> Bool {
        a.compare(b, locale: Locale(identifier: "sr")) == .orderedAscending
    }

    private var queueKey = ""
    private var queueCache: [Word] = []

    // Everything learnable in the chosen order; every fifth item is a phrase when both are chosen.
    private func queue() -> [Word] {
        let cats = prefs.categories, order = prefs.order, seed = prefs.shuffleSeed
        let key = "\(cats)|\(order)|\(seed)|\(words.count)"
        if key == queueKey { return queueCache }
        let chosen = (order == 1 ? shuffled(seed) : words).filter { learnable($0, cats) }
        let phrases = shuffled(seed).filter { $0.pos == "phrase" && learnable($0, cats) }
        var plain = chosen.filter { $0.pos != "phrase" }
        if order == 2 { plain.sort { Repo.collate($0.cyr, $1.cyr) } }
        var out: [Word] = []
        var ph = 0
        for (i, w) in plain.enumerated() {
            out.append(w)
            if i % 4 == 3 && ph < phrases.count { out.append(phrases[ph]); ph += 1 }
        }
        out.append(contentsOf: phrases[ph...])
        queueKey = key
        queueCache = out
        return out
    }

    private func phaseOf(_ w: Word) -> Int { progressOf(w.key)?.phase ?? 0 }
    private func lastOf(_ w: Word) -> Int { progressOf(w.key)?.last ?? 0 }
    private func showAtOf(_ w: Word) -> Int { progressOf(w.key)?.showAt ?? 0 }
    private func untouched(_ w: Word) -> Bool {
        guard let p = progressOf(w.key) else { return false }
        return p.phase == Repo.intro && p.state == Mark.new && p.showAt == 0
    }

    // The words being learned now; topped up from the queue when some were learned.
    private func activeSet() -> [Word] {
        let q = queue()
        let active = q.filter { phaseOf($0) == Repo.intro || phaseOf($0) == Repo.recall }
        let missing = prefs.activeSize - active.count
        if missing < 0 {
            // A smaller set takes effect at once: words that were only shown, never answered,
            // give way from the end of the queue.
            let drop = Set(active.filter(untouched).suffix(-missing).map(\.key))
            for w in active where drop.contains(w.key) { update(w) { var p = $0; p.phase = 0; return p } }
            return active.filter { !drop.contains($0.key) }
        }
        if missing == 0 { return active }
        var fresh: [Word] = []
        for w in q {
            if fresh.count >= missing { break }
            if phaseOf(w) == 0 && progressOf(w.key)?.state != Mark.known { fresh.append(w) }
        }
        for w in fresh { update(w) { var p = $0; p.phase = Repo.intro; p.streak = 0; return p } }
        return active + fresh
    }

    private func minBy(_ list: [Word], _ f: (Word) -> Int) -> Word? {
        var best: Word?
        var bestV = Int.max
        for x in list {
            let v = f(x)
            if v < bestV { best = x; bestV = v }
        }
        return best
    }

    // The article shown longest ago, a comparison and a note in turn while both are left.
    private func articleFor(_ exclude: String?) -> Word? {
        let cats = prefs.categories
        let pool = words.filter { $0.isArticle && cats.contains("X:" + $0.pos) && $0.key != exclude && phaseOf($0) != Repo.learned }
        var latest: Word?
        for w in words where w.isArticle && lastOf(w) > 0 && (latest == nil || lastOf(w) > lastOf(latest!)) { latest = w }
        let other = pool.filter { $0.pos != latest?.pos }
        return minBy(other.isEmpty ? pool : other, lastOf)
    }

    // Articles of one kind ("compare" or "note") hidden with «Не показывать».
    public func hiddenArticles(_ pos: String) -> Int { words.filter { $0.pos == pos && phaseOf($0) == Repo.learned }.count }

    public func showArticlesAgain(_ pos: String) {
        for w in words where w.pos == pos && phaseOf(w) == Repo.learned {
            update(w) { var p = $0; p.state = Mark.new; p.phase = 0; p.step = 0; p.due = 0; p.last = 0; return p }
        }
    }

    private func dueFor(_ exclude: String?) -> Word? {
        minBy(queue().filter { $0.key != exclude && isDue(progressOf($0.key)) }) { progressOf($0.key)!.due }
    }

    // Next card: a word whose return after «Не знаю» is due, every fifth an article, every fourth
    // a check of a learned word when one is due, otherwise the active word shown longest ago.
    private func pickNext(_ exclude: String?) -> Word? {
        let n = prefs.cardCount
        let active = activeSet()
        if let back = minBy(active.filter({ $0.key != exclude && showAtOf($0) >= 1 && showAtOf($0) <= n }), showAtOf) { return back }
        if n % 5 == 4, let a = articleFor(exclude) { return a }
        if n % 4 == 3, let d = dueFor(exclude) { return d }
        return minBy(active.filter { $0.key != exclude && showAtOf($0) <= n }, lastOf) ?? active.first ?? dueFor(exclude) ?? articleFor(exclude)
    }

    // MARK: Moving between cards

    private var history: [String] = []
    // Cards left by going back, shown again in the same order by going forward.
    private var forward: [String] = []
    // The card on screen was reached by going back or forward, so it was already counted.
    private var revisit = false
    // Set when nothing is left to show for the chosen sections.
    public private(set) var exhausted = false

    public func setCategories(_ ids: [String], order: Int) {
        var p = prefs
        p.categories = ids
        p.order = order
        prefs = p
        resetPosition()
    }

    public func resetPosition() {
        history.removeAll()
        forward.removeAll()
        answers.removeAll()
        exhausted = false
        var p = prefs
        p.currentKey = nil
        p.shuffleSeed = Int.random(in: 0..<(1 << 31))
        p.pendingAnswer = nil
        prefs = p
    }

    public func current() -> Word? { prefs.currentKey.flatMap { word($0) } }

    public var canGoBack: Bool { !history.isEmpty }

    public func step(_ delta: Int) {
        let cur = prefs.currentKey
        if delta < 0 {
            guard let prev = history.popLast() else { return }
            if let cur { forward.append(cur) }
            revisit = true
            prefs.currentKey = prev
            return
        }
        if let cur {
            history.append(cur)
            if history.count > 100 { history.removeFirst() }
        }
        if let fwd = forward.popLast() {
            revisit = true
            prefs.currentKey = fwd
            return
        }
        revisit = false
        var p = prefs
        p.cardCount += 1
        store.prefs = p
        let next = pickNext(cur)
        exhausted = next == nil
        // A fresh showing of a card takes a fresh answer.
        if let next { answers[next.key] = nil }
        let keep = Set(history + [next?.key].compactMap { $0 })
        for k in answers.keys where !keep.contains(k) { answers[k] = nil }
        p = prefs
        p.currentKey = next?.key
        prefs = p
    }
}
