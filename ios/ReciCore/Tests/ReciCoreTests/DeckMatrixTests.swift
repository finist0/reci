import Foundation
import XCTest
@testable import ReciCore

// Every deck the app carries under every interface language: the learning steps over the real
// cards, the cards in the deck's languages, the same cards whatever the interface speaks. The
// counterpart of DeckMatrixTest.kt and web/tests/deck-matrix.test.mjs.
final class DeckMatrixTests: XCTestCase {
    private static let languages = ["ru", "sr", "en"]
    private static let settings = ["ru": "Настройки", "sr": "Подешавања", "en": "Settings"]

    // Letters only one of the languages has: a text with one of them is in that language.
    private static let own: [String: (Character) -> Bool] = [
        "sr": { "ђјљњћџ".contains($0) },
        "ru": { "ыэъьяюёйщ".contains($0) },
        "en": { $0.isASCII && $0.isLetter },
    ]

    // A deck's cards are decoded once for all its interface languages.
    private static var decoded: [String: [Word]] = [:]

    override func setUp() {
        Fixtures.loadTables()
    }

    override func tearDown() {
        I18n.lang = "ru"
        I18n.latin = false
        Decks.systemLanguage = "ru"
    }

    private func cards(_ d: Deck) throws -> [Word] {
        if let w = DeckMatrixTests.decoded[d.id] { return w }
        let w = try Word.decode(Data(contentsOf: Fixtures.assets.appendingPathComponent(d.words)), deck: d)
        DeckMatrixTests.decoded[d.id] = w
        return w
    }

    // A fresh install switched to the deck, with the interface in the language given.
    private func repo(_ d: Deck, _ lang: String) throws -> Repo {
        let store = Fixtures.tempStore(language: lang)
        store.switchDeck(d.id)
        XCTAssertEqual(store.prefs.deckId, d.id)
        var p = store.prefs
        p.uiLang = lang
        store.prefs = p
        store.savePrefs()
        let r = Repo(store: store, words: try cards(d))
        r.now = { 1_767_225_600_000 } // 2026-01-01
        return r
    }

    // The letters of a text without stress marks.
    private func letters(_ s: String) -> [Character] {
        let bare = s.decomposedStringWithCanonicalMapping.unicodeScalars.filter { !(0x300...0x36F).contains($0.value) }
        return Array(String(String.UnicodeScalarView(bare)).lowercased().filter { $0.isLetter })
    }

    // The cards shown on 60 steps with the answers a learner gives, in a fixed shuffled order.
    private func walk(_ r: Repo) -> [Word] {
        r.setCategories(Prefs().categories, order: 1)
        var p = r.prefs
        p.shuffleSeed = 12345
        r.prefs = p
        var shown: [Word] = []
        for i in 0..<60 {
            r.step(1)
            guard let w = r.current() else { break }
            r.markShown(w)
            shown.append(w)
            r.answer(w, i % 3 == 0 ? Mark.dontKnow : Mark.known, learned: i % 7 == 6)
        }
        return shown
    }

    func testEveryDeckUnderEveryInterfaceLanguage() throws {
        XCTAssertEqual(Decks.all.map(\.id), ["sr-ru", "ru-sr", "sr-en", "ru-en"])
        for d in Decks.all {
            var first: [String]?
            for lang in DeckMatrixTests.languages {
                let what = "\(d.id) in \(lang)"
                let r = try repo(d, lang)
                XCTAssertEqual(t("Настройки"), DeckMatrixTests.settings[lang], what)
                XCTAssertEqual(I18n.lang, lang, what)

                let shown = walk(r)
                XCTAssertEqual(shown.count, 60, what)
                let keys = shown.map(\.key)
                if let first {
                    XCTAssertEqual(keys, first, "\(what): the interface language changed the cards")
                } else {
                    first = keys
                }
                // The cards are in the deck's languages.
                let words = shown.filter { !$0.isArticle }
                XCTAssertFalse(words.isEmpty, what)
                for w in words {
                    XCTAssertFalse(w.meanings.isEmpty, "\(what): \(w.key)")
                    XCTAssertFalse(letters(w.cyr).contains { $0.isASCII }, "\(what): \(w.key) \(w.cyr)")
                    for e in w.examples {
                        XCTAssertFalse(e.text.isEmpty || e.tr.isEmpty, "\(what): \(w.key) example")
                    }
                }
                let learned = words.flatMap { letters($0.cyr) + $0.examples.flatMap { letters($0.text) } }
                let native = words.flatMap { $0.meanings.flatMap(letters) + $0.examples.flatMap { letters($0.tr) } }
                XCTAssertTrue(learned.contains(where: DeckMatrixTests.own[d.target]!), "\(what): no \(d.target) on the cards")
                XCTAssertTrue(native.contains(where: DeckMatrixTests.own[d.native]!), "\(what): no \(d.native) in the translations")
                let other = d.target == "sr" ? "ru" : "sr"
                XCTAssertFalse(letters(words.map(\.cyr).joined()).contains(where: DeckMatrixTests.own[other]!), "\(what): \(other) words")
            }
        }
    }

    func testTheLearningStepsOnTheCardsOfEveryDeck() throws {
        for d in Decks.all {
            for lang in DeckMatrixTests.languages {
                let what = "\(d.id) in \(lang)"
                let r = try repo(d, lang)

                // Levels and sections count every card once.
                let counts = r.counts()
                for level in 1...5 { XCTAssertGreaterThan(counts["L\(level)"]?.all ?? 0, 0, "\(what) L\(level)") }
                let total = counts.filter { $0.key.hasPrefix("L") || $0.key.hasPrefix("X:") }.map(\.value.all).reduce(0, +)
                XCTAssertEqual(total, r.words.count, what)

                // A new word is introduced; «Знаю» moves it on to recall, «Не знаю» brings it back.
                r.setCategories(["L1"], order: 0)
                r.step(1)
                let a = try XCTUnwrap(r.current(), what)
                r.markShown(a)
                XCTAssertEqual(a.level, 1, what)
                XCTAssertEqual(r.progress(a).phase, Repo.intro, what)
                r.answer(a, Mark.known)
                XCTAssertEqual(r.progress(a).phase, Repo.recall, what)
                r.step(1)
                let b = try XCTUnwrap(r.current(), what)
                r.markShown(b)
                XCTAssertNotEqual(a.key, b.key, what)
                r.answer(b, Mark.dontKnow)
                XCTAssertEqual(r.progress(b).state, Mark.dontKnow, what)
                XCTAssertEqual(r.progress(b).phase, Repo.intro, what)
                r.step(1)
                let c = try XCTUnwrap(r.current(), what)
                XCTAssertNotEqual(c.key, b.key, what)
                r.answer(c, Mark.known, learned: true)
                XCTAssertEqual(r.progress(c).phase, Repo.learned, what)
                XCTAssertEqual(r.counts()["M:known"]?.all, 1, what)

                // Another level gives cards of that level only.
                r.setCategories(["L4"], order: 2)
                for _ in 0..<10 {
                    r.step(1)
                    XCTAssertEqual(r.current()?.level, 4, what)
                }
                XCTAssertFalse(r.exhausted, what)
            }
        }
    }
}
