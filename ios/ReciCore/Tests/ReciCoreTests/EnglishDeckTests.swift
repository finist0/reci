import Foundation
import XCTest
@testable import ReciCore

// The decks with English translations as the app carries them; the first cards of each are
// translated in any case. The counterpart of EnglishDeckTest.kt.
final class EnglishDeckTests: XCTestCase {
    override func setUp() {
        Fixtures.loadTables()
    }

    override func tearDown() {
        I18n.lang = "ru"
        I18n.latin = false
    }

    private func cards(_ id: String) throws -> [Word] {
        let d = Decks.deck(id)
        XCTAssertEqual(d.id, id)
        return try Word.decode(Data(contentsOf: Fixtures.assets.appendingPathComponent(d.words)), deck: d)
    }

    func testTheSettingsOfferEnglishTranslationsOfBothLanguages() {
        XCTAssertEqual(Decks.targets, ["sr", "ru"])
        XCTAssertEqual(Decks.natives("sr"), ["ru", "en"])
        XCTAssertEqual(Decks.natives("ru"), ["sr", "en"])
        XCTAssertEqual(Decks.pick("ru", "en").id, "ru-en")
        XCTAssertEqual(Decks.pick("sr", "en").id, "sr-en")
    }

    func testSerbianWithEnglishSharesTheProgressOfSerbianWithRussian() throws {
        let store = Fixtures.tempStore()
        let sr = Repo(store: store, words: Fixtures.words)
        sr.answer(try XCTUnwrap(sr.word("бити")), Mark.known, learned: true)
        store.switchDeck("sr-en")
        let en = Repo(store: store, words: try cards("sr-en"))
        let biti = try XCTUnwrap(en.word("бити"))
        XCTAssertEqual(en.progress(biti).state, Mark.known)
        XCTAssertTrue(biti.meanings.contains("to be"))
        XCTAssertEqual(biti.gram, "impf.")
        XCTAssertTrue(biti.examples.allSatisfy { $0.text.contains("[") && !$0.tr.isEmpty })
        XCTAssertTrue(en.words.contains { $0.key == "бити" && Search.matches($0, "to be", byMeaning: true) })
        XCTAssertTrue(Search.matches(biti, "biti", byMeaning: false))
        var p = store.prefs
        p.latinFirst = true
        XCTAssertTrue(p.native(try XCTUnwrap(biti.tips.first)).contains("jesam/jesi/jeste"))
    }

    func testRussianWithEnglishReadsItsCards() throws {
        let ya = try XCTUnwrap(try cards("ru-en").first { $0.key == "я" })
        XCTAssertEqual(ya.meanings, ["I"])
        XCTAssertTrue(ya.examples.allSatisfy { !$0.tr.isEmpty })
        XCTAssertTrue(Search.matches(ya, "i", byMeaning: true))
    }
}
