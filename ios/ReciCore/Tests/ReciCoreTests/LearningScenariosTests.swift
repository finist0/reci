import Foundation
import XCTest
@testable import ReciCore

// Runs tests/learning-scenarios.json against Repo.swift, as LearningScenariosTest.kt does for
// Android and web/tests/learning.test.mjs for the web version.
final class LearningScenariosTests: XCTestCase {
    private static let day = 24 * 60 * 60 * 1000

    func testAllScenarios() {
        let scenarios = Fixtures.json("tests/learning-scenarios.json") as! [[String: Any]]
        XCTAssertFalse(scenarios.isEmpty)
        for sc in scenarios {
            let name = sc["name"] as! String
            do {
                try Runner(sc).run()
            } catch {
                XCTFail("\(name): \(error)")
            }
        }
    }

    struct Failure: Error, CustomStringConvertible {
        let description: String
    }

    final class Runner {
        let sc: [String: Any]
        let repo: Repo
        var clock = 1_767_225_600_000 // 2026-01-01
        var vars: [String: (key: String, n: Int)] = [:]
        var distance = -1
        var collected: [(n: Int, key: String, kind: String)] = []

        init(_ sc: [String: Any]) {
            self.sc = sc
            repo = Repo(store: Fixtures.tempStore(), words: Fixtures.words)
            repo.now = { [unowned self] in self.clock }
            let s = sc["settings"] as? [String: Any] ?? [:]
            var p = repo.prefs
            p.activeSize = s["activeSize"] as? Int ?? 20
            p.order = s["order"] as? Int ?? 0
            p.reviewKnown = s["reviewKnown"] as? Bool ?? true
            p.cardMode = 0
            p.cardCount = 0
            repo.prefs = p
            repo.setCategories(s["categories"] as? [String] ?? ["L3"], order: p.order)
        }

        func check(_ ok: Bool, _ what: @autoclosure () -> String) throws {
            if !ok { throw Failure(description: what()) }
        }

        func current() throws -> Word {
            guard let w = repo.current() else { throw Failure(description: "no card on screen") }
            return w
        }

        func word(_ name: String) throws -> Word {
            guard let v = vars[name], let w = repo.word(v.key) else { throw Failure(description: "no word \(name)") }
            return w
        }

        func kind(_ w: Word) -> String { w.isArticle ? w.pos : w.pos == "phrase" ? "phrase" : "word" }

        // What the card screen does after a step: the card on screen is counted as shown.
        func move(_ delta: Int) {
            clock += 1000
            repo.step(delta)
            if let w = repo.current() { repo.markShown(w) }
        }

        func answer(_ a: String) throws {
            let w = try current()
            switch a {
            case "know", "hide": repo.answer(w, Mark.known)
            case "dontknow", "later": repo.answer(w, Mark.dontKnow)
            case "long": repo.answer(w, Mark.known, learned: true)
            default: throw Failure(description: "unknown answer \(a)")
            }
        }

        // A card is answered once, as a person would.
        func answerAll(_ st: [String: Any]) throws {
            let w = try current()
            if repo.answerOf(w) != nil { return }
            let policy = w.isArticle ? st["articles"] as? String ?? "none" : st["answer"] as? String ?? "none"
            if policy != "none" { try answer(policy) }
        }

        func doStep(_ st: [String: Any]) throws {
            switch st["do"] as? String ?? "" {
            case "next": move(1)
            case "prev": move(-1)
            case "answer": try answer(st["a"] as! String)
            case "remember": vars[st["as"] as! String] = (try current().key, repo.prefs.cardCount)
            case "fav": repo.toggleFav(try current())
            case "advanceDays": clock += (st["days"] as! Int) * LearningScenariosTests.day
            case "set":
                var p = repo.prefs
                p.activeSize = st["activeSize"] as! Int
                repo.prefs = p
            case "showArticlesAgain": repo.showArticlesAgain(st["pos"] as! String)
            case "pick":
                let pool = repo.words.filter { $0.level == st["level"] as! Int }
                let w = st["by"] as? String == "alpha" ? pool.sorted { Repo.collate($0.cyr, $1.cyr) }[0] : pool[0]
                vars[st["as"] as! String] = (w.key, 0)
            case "skim":
                var seen: [(Int, String, String)] = []
                for _ in 0..<(st["count"] as! Int) {
                    try answerAll(st)
                    move(1)
                    let w = try current()
                    seen.append((repo.prefs.cardCount, w.key, kind(w)))
                }
                if st["collect"] as? Bool == true { collected = seen }
            case "until":
                let target = vars[st["word"] as! String]!
                for _ in 0..<(st["max"] as! Int) {
                    try answerAll(st)
                    move(1)
                    if try current().key == target.key {
                        distance = repo.prefs.cardCount - target.n
                        return
                    }
                }
                throw Failure(description: "\(st["word"]!) did not come back within \(st["max"]!) cards")
            case "untilArticle":
                for _ in 0..<(st["max"] as! Int) {
                    try answerAll(st)
                    move(1)
                    if try current().isArticle { return }
                }
                throw Failure(description: "no article within \(st["max"]!) cards")
            case let other:
                throw Failure(description: "unknown step \(other)")
            }
        }

        func expect(_ e: [String: Any]) throws {
            let phases = ["new": 0, "intro": Repo.intro, "recall": Repo.recall, "learned": Repo.learned]
            let states = ["new": Mark.new, "dontknow": Mark.dontKnow, "known": Mark.known]
            if let name = e["word"] as? String {
                let w = try word(name)
                let p = repo.progress(w)
                if let v = e["phase"] as? String { try check(p.phase == phases[v], "phase of \(w.key): \(p.phase)") }
                if let v = e["streak"] as? Int { try check(p.streak == v, "streak of \(w.key): \(p.streak)") }
                if let v = e["state"] as? String { try check(p.state == states[v], "state of \(w.key): \(p.state)") }
                if let v = e["fav"] as? Bool { try check(p.fav == v, "fav of \(w.key)") }
                if e.keys.contains("dueInDays") {
                    if let days = e["dueInDays"] as? Int {
                        let got = Int((Double(p.due - clock) / Double(LearningScenariosTests.day)).rounded())
                        try check(got == days, "days to check of \(w.key): \(got)")
                    } else {
                        try check(p.due == 0, "no check for \(w.key): \(p.due)")
                    }
                }
            }
            if let v = e["current"] as? String {
                let key = try current().key
                try check(key == vars[v]?.key, "current is \(key)")
            }
            if let names = e["currentNot"] as? [String] {
                for n in names { try check(try current().key != vars[n]?.key, "current is \(n)") }
            }
            if let v = e["distance"] as? Int { try check(distance == v, "cards until it came back: \(distance)") }
            if let v = e["cardNumber"] as? Int { try check(repo.prefs.cardCount == v, "card number \(repo.prefs.cardCount)") }
            if let v = e["activeCount"] as? Int {
                let n = repo.words.filter { [Repo.intro, Repo.recall].contains(repo.progress($0).phase) }.count
                try check(n == v, "active words: \(n)")
            }
            if let v = e["distinctWords"] as? Int {
                let n = Set(collected.filter { $0.kind != "compare" && $0.kind != "note" }.map(\.key)).count
                try check(n == v, "different words: \(n)")
            }
            if let h = e["hidden"] as? [String: Int] {
                for (pos, n) in h { try check(repo.hiddenArticles(pos) == n, "hidden \(pos): \(repo.hiddenArticles(pos))") }
            }
            if e["articleSlots"] != nil {
                for c in collected {
                    try check((c.kind == "compare" || c.kind == "note") == (c.n % 5 == 4), "card \(c.n) is an article: \(c.kind)")
                }
            }
            if e["articlesAlternate"] != nil {
                let kinds = collected.map(\.kind).filter { $0 == "compare" || $0 == "note" }
                try check(kinds.count >= 4, "too few articles: \(kinds)")
                for i in 1..<kinds.count { try check(kinds[i] != kinds[i - 1], "articles do not alternate: \(kinds)") }
            }
            if let k = e["kindsAt"] as? [String: String] {
                for (n, kind) in k { try check(collected.first { $0.n == Int(n) }?.kind == kind, "kind of card \(n)") }
            }
            if let k = e["keyAt"] as? [String: String] {
                for (n, v) in k { try check(collected.first { $0.n == Int(n) }?.key == vars[v]?.key, "card \(n)") }
            }
            if let v = e["notSeen"] as? String {
                try check(!collected.contains { $0.key == vars[v]?.key }, "\(v) came back")
            }
        }

        func run() throws {
            for (i, st) in (sc["steps"] as! [[String: Any]]).enumerated() {
                do {
                    if let e = st["expect"] as? [String: Any] { try expect(e) } else { try doStep(st) }
                } catch {
                    throw Failure(description: "step \(i + 1) \(st): \(error)")
                }
            }
        }
    }
}
