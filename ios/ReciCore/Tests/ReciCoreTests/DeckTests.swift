import Foundation
import XCTest
@testable import ReciCore

// Language pairs: each deck its own cards and progress, the interface in the user's language.
// The counterpart of DeckTest.kt.
final class DeckTests: XCTestCase {
    override func setUp() {
        Fixtures.loadTables()
    }

    // A store sets the interface language; the other tests expect Russian.
    override func tearDown() {
        I18n.lang = "ru"
        I18n.latin = false
    }

    func testARussianPhoneStartsWithSerbianForRussians() {
        let s = Fixtures.tempStore()
        XCTAssertEqual(s.prefs.deckId, "sr-ru")
        XCTAssertEqual(t("Настройки"), "Настройки")
    }

    func testASerbianPhoneStartsWithRussianButAnEarlierUserStays() throws {
        XCTAssertEqual(Fixtures.tempStore(language: "sr").prefs.deckId, "ru-sr")
        XCTAssertEqual(t("Настройки"), "Подешавања")

        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("reci-test-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try Data(#"{"currentKey": "кућа", "latinFirst": true}"#.utf8).write(to: dir.appendingPathComponent("prefs.json"))
        let s = Store(dir: dir)
        XCTAssertEqual(s.prefs.deckId, "sr-ru", "Decks.systemLanguage is still sr")
        XCTAssertEqual(s.prefs.currentKey, "кућа")
        XCTAssertTrue(s.prefs.latinFirst)
        XCTAssertEqual(Store(dir: dir).prefs.deckId, "sr-ru", "the choice is kept")
    }

    func testTheRussianDeckSpeaksSerbianAndKeepsItsOwnProgress() throws {
        let store = Fixtures.tempStore()
        let sr = Repo(store: store, words: Fixtures.words)
        sr.step(1)
        let srCard = try XCTUnwrap(sr.current())
        let kuca = try XCTUnwrap(sr.word("кућа"))
        sr.answer(kuca, Mark.known, learned: true)
        var p = sr.prefs
        p.latinFirst = true
        p.categories = ["L1"]
        sr.prefs = p

        store.switchDeck("ru-sr")
        XCTAssertEqual(store.prefs.deckId, "ru-sr")
        XCTAssertEqual(t("Настройки"), "Podešavanja")
        XCTAssertTrue(store.prefs.latinFirst, "settings are shared")
        XCTAssertNil(store.prefs.currentKey)
        XCTAssertEqual(store.prefs.categories, Prefs().categories)
        XCTAssertTrue(store.progress.isEmpty)
        let ru = Repo(store: store, words: Fixtures.ruWords)
        XCTAssertNil(ru.word("кућа"), "Serbian cards are not in the Russian deck")
        let dom = try XCTUnwrap(ru.word("дом"))
        XCTAssertEqual(ru.progress(dom).state, Mark.new)
        ru.step(1)
        let ruCard = try XCTUnwrap(ru.current())
        ru.answer(dom, Mark.dontKnow)

        // Another process sees the same deck.
        let widget = Store(dir: store.dir)
        XCTAssertEqual(widget.prefs.deckId, "ru-sr")
        XCTAssertEqual(widget.prefs.currentKey, ruCard.key)

        store.switchDeck("sr-ru")
        XCTAssertEqual(t("Настройки"), "Настройки")
        let back = Repo(store: store, words: Fixtures.words)
        XCTAssertEqual(back.current()?.key, srCard.key)
        XCTAssertEqual(back.prefs.categories, ["L1"])
        XCTAssertEqual(back.progress(kuca).state, Mark.known)
        XCTAssertNil(store.progress["дом"])

        // The first deck keeps the old file names, another deck prefixes them.
        let prefs = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: store.dir.appendingPathComponent("prefs.json"))) as? [String: Any])
        XCTAssertEqual(prefs["currentKey"] as? String, srCard.key)
        XCTAssertEqual(prefs["ru-sr.currentKey"] as? String, ruCard.key)
        XCTAssertEqual(prefs["deck"] as? String, "sr-ru")
        let ruProgress = try JSONDecoder().decode([String: ReciCore.Progress].self, from: Data(contentsOf: store.dir.appendingPathComponent("ru-sr.progress.json")))
        XCTAssertEqual(ruProgress["дом"]?.state, Mark.dontKnow)
    }

    func testABackupGoesToTheDeckInUse() throws {
        let store = Fixtures.tempStore()
        store.switchDeck("ru-sr")
        let ru = Repo(store: store, words: Fixtures.ruWords)
        ru.answer(try XCTUnwrap(ru.word("дом")), Mark.known)
        let file = try Backup.export(ru)
        store.switchDeck("sr-ru")
        let sr = Repo(store: store, words: Fixtures.words)
        XCTAssertEqual(try Backup.import(file, into: sr), 1)
        XCTAssertEqual(store.prefs.deckId, "sr-ru")
        XCTAssertEqual(Store(dir: store.dir).prefs.deckId, "sr-ru")
    }

    func testRussianCardsReadTheirLanguages() throws {
        let dom = try XCTUnwrap(Fixtures.ruWords.first { $0.key == "дом" })
        XCTAssertFalse(dom.examples.isEmpty)
        XCTAssertTrue(dom.examples.allSatisfy { $0.text.contains("[") }, "Russian examples carry the headword")
        XCTAssertTrue(dom.examples.allSatisfy { !$0.tr.isEmpty && !$0.tr.contains("[") })
        let related = try XCTUnwrap(dom.related.first)
        XCTAssertFalse(related.word.isEmpty)
        XCTAssertFalse(related.tr.isEmpty)
        XCTAssertNotEqual(Translit.strip(related.acc), related.acc, "the stressed form")
        XCTAssertEqual(Translit.strip(related.acc), related.word)

        let want = try XCTUnwrap(Fixtures.ruWords.first { !$0.synAcc.isEmpty })
        XCTAssertEqual(want.synAcc.count, want.syn.count)
        XCTAssertEqual(want.synAcc.map(Translit.strip), want.syn)

        // The Serbian deck reads the same keys the other way round.
        let kuca = try XCTUnwrap(Fixtures.words.first { $0.key == "кућа" })
        XCTAssertTrue(kuca.examples.allSatisfy { $0.text.contains("[") })

        // The widgets get a copy of the cards written and read for the deck.
        let enc = JSONEncoder()
        enc.userInfo[Deck.userInfoKey] = Decks.deck("ru-sr")
        let copy = try Word.decode(enc.encode([dom]), deck: Decks.deck("ru-sr"))
        XCTAssertEqual(copy, [dom])
    }

    func testTextGoesThroughTheScriptOfItsLanguage() throws {
        var p = Prefs()
        p.deckId = "ru-sr"
        p.latinFirst = true
        p.accentMode = 1
        let dom = try XCTUnwrap(Fixtures.ruWords.first { $0.key == "дом" })
        XCTAssertEqual(p.primary("дом"), "дом", "Russian has one script")
        XCTAssertEqual(p.native("кућа"), "kuća")
        XCTAssertNil(p.secondary(dom.cyr))
        XCTAssertEqual(p.linked("домашний", acc: "дома́шний", nil), "дома́шний")
        p.accentMode = 2
        XCTAssertEqual(p.marked("Я иду [домо́й]"), "Я иду [домой]")

        p.deckId = "sr-ru"
        XCTAssertEqual(p.primary("кућа"), "kuća")
        XCTAssertEqual(p.native("дом"), "дом")
        XCTAssertEqual(p.secondary("кућа"), "кућа")
    }

    func testTheInterfaceLanguageIsChosenApartFromTheDeck() throws {
        let store = Fixtures.tempStore()
        XCTAssertEqual(store.prefs.uiLang, "")
        store.prefs.uiLang = "en"
        store.savePrefs()
        XCTAssertEqual(t("Настройки"), "Settings")
        XCTAssertEqual(plural(5, "карточку", "карточки", "карточек"), "cards")
        // The cards stay in the language of the deck: the search of translations asks for Russian.
        XCTAssertEqual(store.prefs.deck.meaningHint, "word in Russian")

        store.switchDeck("ru-sr")
        XCTAssertEqual(store.prefs.uiLang, "en")
        XCTAssertEqual(t("Настройки"), "Settings")
        XCTAssertEqual(store.prefs.deck.meaningHint, "word in Serbian")
        XCTAssertEqual(Store(dir: store.dir).prefs.uiLang, "en", "the choice is kept")
        let prefs = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: store.dir.appendingPathComponent("prefs.json"))) as? [String: Any])
        XCTAssertEqual(prefs["uiLang"] as? String, "en")
        XCTAssertNil(prefs["ru-sr.uiLang"], "one choice for all decks")

        store.prefs.uiLang = ""
        XCTAssertEqual(t("Настройки"), "Подешавања")
        store.switchDeck("sr-ru")
        XCTAssertEqual(t("Настройки"), "Настройки")

        store.prefs.uiLang = "sr"
        XCTAssertEqual(t("Настройки"), "Подешавања")
        XCTAssertTrue(store.prefs.deck.hasSerbian)
    }

    func testSerbianAndEnglishInterface() {
        I18n.lang = "sr"
        XCTAssertEqual(t("Настройки"), "Подешавања")
        XCTAssertEqual(t("Учить %d %s", 5, plural(5, "карточку", "карточки", "карточек")), "Учи 5 картица")
        XCTAssertEqual(Labels.category("L1"), "A1 · Почетни")
        XCTAssertEqual(Labels.pos("noun"), t("сущ."))
        XCTAssertNotEqual(Labels.pos("noun"), "сущ.")
        I18n.lang = "en"
        XCTAssertEqual(t("Настройки"), "Settings")
        XCTAssertEqual(Labels.orders, ["По частоте", "Вперемешку", "По алфавиту"].map { I18n.table[$0]!["en"]! })
        XCTAssertEqual(t("Учить %d %s", 1, plural(1, "карточку", "карточки", "карточек")), "Learn 1 card")
        // A string without a translation stays Russian rather than empty.
        XCTAssertEqual(t("Нет такой строки"), "Нет такой строки")
        I18n.lang = "ru"
        XCTAssertEqual(t("Учить %d %s", 3, plural(3, "карточку", "карточки", "карточек")), "Учить 3 карточки")
    }

    func testEachDeckIsTitledInItsOwnLanguage() {
        XCTAssertEqual(Decks.title(Decks.deck("sr-ru")), "Учу сербский · перевод на русский")
        XCTAssertEqual(Decks.title(Decks.deck("ru-sr")), "Учим руски · превод на српски")
        I18n.latin = true
        XCTAssertEqual(Decks.title(Decks.deck("ru-sr")), "Učim ruski · maternji srpski")
        XCTAssertEqual(Decks.title(Decks.deck("sr-ru")), "Учу сербский · перевод на русский")
    }

    func testSerbianInterfaceFollowsTheScriptSetting() {
        let s = Fixtures.tempStore()
        s.switchDeck("ru-sr")
        s.prefs.latinFirst = true
        XCTAssertEqual(t("Настройки"), "Podešavanja")
        XCTAssertEqual(plural(5, "карточку", "карточки", "карточек"), "kartica")
        XCTAssertEqual(Labels.category("M:known"), Translit.toLatin(I18n.table["Знаю"]!["sr"]!))
        s.prefs.latinFirst = false
        XCTAssertEqual(t("Настройки"), "Подешавања")
    }

    func testPluralForms() {
        let forms = ["карточку", "карточки", "карточек"]
        let n = [1, 2, 5, 11, 12, 21, 22, 25, 101, 111]
        I18n.lang = "ru"
        XCTAssertEqual(n.map { plural($0, forms[0], forms[1], forms[2]) },
                       ["карточку", "карточки", "карточек", "карточек", "карточек", "карточку", "карточки", "карточек", "карточку", "карточек"])
        I18n.lang = "sr"
        XCTAssertEqual(n.map { plural($0, forms[0], forms[1], forms[2]) },
                       ["картицу", "картице", "картица", "картица", "картица", "картицу", "картице", "картица", "картицу", "картица"])
        I18n.lang = "en"
        XCTAssertEqual(n.map { plural($0, forms[0], forms[1], forms[2]) },
                       ["card", "cards", "cards", "cards", "cards", "cards", "cards", "cards", "cards", "cards"])
    }

    func testFormatPutsArgumentsInTurn() {
        XCTAssertEqual(I18n.format("знаю %d из %d", [3, 10]), "знаю 3 из 10")
        XCTAssertEqual(I18n.format("100%% · %s", ["a"]), "100% · a")
        XCTAssertEqual(I18n.format("%s и %s", ["a"]), "a и ")
    }

    func testSearchTakesBothScriptsAndRussianWithoutYo() throws {
        let vse = try XCTUnwrap(Fixtures.ruWords.first { $0.cyr == "всё" })
        XCTAssertTrue(Search.matches(vse, "все", byMeaning: false))
        XCTAssertTrue(Search.matches(vse, "Всё", byMeaning: false))
        XCTAssertTrue(Search.matches(vse, "sve", byMeaning: true), "a Serbian meaning typed in Latin")
        XCTAssertTrue(Search.matches(vse, "све", byMeaning: true))
        let kuca = try XCTUnwrap(Fixtures.words.first { $0.key == "кућа" })
        XCTAssertTrue(Search.matches(kuca, "kuć", byMeaning: false))
        XCTAssertTrue(Search.matches(kuca, "ку", byMeaning: false))
        XCTAssertFalse(Search.matches(kuca, "  ", byMeaning: false))
    }

    func testTheDeckTextsFollowTheDeck() {
        let sr = Decks.deck("sr-ru"), ru = Decks.deck("ru-sr")
        XCTAssertEqual(sr.howToSay, "Как сказать по-сербски")
        I18n.lang = "sr"
        XCTAssertEqual(ru.howToSay, I18n.table["Как сказать по-русски"]?["sr"])
        XCTAssertEqual(ru.accentSamples.count, 2)
        XCTAssertEqual(sr.accentSamples.count, 3)
        XCTAssertTrue(ru.hasSerbian)
        XCTAssertTrue(ru.isFirst == false && sr.isFirst)
        XCTAssertEqual(ru.scoped("progress.json"), "ru-sr.progress.json")
        XCTAssertEqual(sr.scoped("progress.json"), "progress.json")
    }
}
