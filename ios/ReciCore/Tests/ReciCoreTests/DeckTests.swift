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

    // A first start of a store in dir, on a phone in the given language, with prefs.json as given.
    private func start(_ language: String, _ prefs: String = "{}") throws -> Store {
        Decks.systemLanguage = language
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("reci-test-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try Data(prefs.utf8).write(to: dir.appendingPathComponent("prefs.json"))
        return Store(dir: dir)
    }

    func testAFirstStartTakesTheInterfaceLanguageFromThePhoneAndKeepsIt() throws {
        XCTAssertEqual(try start("ru").prefs.uiLang, "ru")
        XCTAssertEqual(try start("sr").prefs.uiLang, "sr")
        XCTAssertEqual(try start("hr").prefs.uiLang, "sr")
        let de = try start("de")
        XCTAssertEqual(de.prefs.uiLang, "en")
        XCTAssertEqual(t("Настройки"), "Settings")

        Decks.systemLanguage = "ru"
        XCTAssertEqual(Store(dir: de.dir).prefs.uiLang, "en", "the stored language stays")
        de.switchDeck("ru-sr")
        XCTAssertEqual(de.prefs.uiLang, "en")
        XCTAssertEqual(Store(dir: de.dir).prefs.uiLang, "en")

        XCTAssertEqual(try start("sr", #"{"uiLang": ""}"#).prefs.uiLang, "sr", "an empty value of an early build counts as none")
        XCTAssertEqual(try start("en", #"{"currentKey": "кућа"}"#).prefs.uiLang, "ru", "an earlier user keeps the interface of the deck")
        XCTAssertEqual(try start("en", #"{"deck": "ru-sr", "ru-sr.currentKey": "дом"}"#).prefs.uiLang, "sr")
        Decks.systemLanguage = "ru"
    }

    func testAFirstStartTakesTheScriptFromThePhone() throws {
        XCTAssertEqual(try start("uk").prefs.uiLang, "ru", "Russian translations, Russian interface")
        XCTAssertEqual(try start("be").prefs.uiLang, "ru")
        for tag in ["hr-HR", "bs", "sl-SI", "sr-Latn-RS", "cnr"] { XCTAssertTrue(Decks.guessLatin(tag), tag) }
        for tag in ["sr-RS", "sr-Cyrl-RS", "mk", "ru-RU", "en-US"] { XCTAssertFalse(Decks.guessLatin(tag), tag) }

        let was = Decks.systemTag
        defer { Decks.systemTag = was }
        Decks.systemTag = "hr-HR"
        XCTAssertTrue(try start("hr").prefs.latinFirst)
        XCTAssertFalse(try start("hr", #"{"currentKey": "кућа"}"#).prefs.latinFirst, "an earlier user keeps the script")
        Decks.systemTag = "ru-RU"
        _ = try start("ru")
    }

    func testTheRussianDeckKeepsItsOwnProgress() throws {
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
        XCTAssertEqual(t("Настройки"), "Настройки", "the interface is not the deck's")
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

    func testBracesKeepTheWordsOfTheOtherLanguageInTheirOwnScript() throws {
        var p = Prefs()
        p.latinFirst = true
        XCTAssertEqual(p.primary("{мн.} паре ({деньги})"), "мн. pare (деньги)")
        XCTAssertEqual(p.native("сказать — то же, что {рећи}"), "сказать — то же, что reći")
        p.deckId = "ru-sr"
        XCTAssertEqual(p.native("{на мой взгляд} = по мом мишљењу"), "на мой взгляд = po mom mišljenju")
        XCTAssertEqual(p.native("{живот} или {жизнь}?"), "живот ili жизнь?")
        XCTAssertEqual(p.primary("{ген}. взгля́да"), "gen. взгля́да")
        XCTAssertEqual(p.native("{ничего"), "{ničego", "an unclosed brace is text")
        p.latinFirst = false
        XCTAssertEqual(p.native("{на мой взгляд} = по мом мишљењу"), "на мой взгляд = по мом мишљењу")
        XCTAssertEqual(p.primary("{ген}. взгля́да"), "ген. взгля́да")
        let cmp = try XCTUnwrap(Fixtures.ruWords.first { $0.key == "cmp:живот-жизнь" })
        XCTAssertTrue(Search.matches(cmp, "живот или", byMeaning: true))
    }

    // A Latin letter next to a Cyrillic one, or fewer letters Serbian lacks than the text had (й
    // and ё come out as ĭ and ë): a Russian word turned into Latin.
    private func transliterated(_ text: String, _ shown: String) -> Bool {
        let letters = shown.unicodeScalars.filter { $0.properties.generalCategory != .nonspacingMark }
        let cyrillic = { (c: Unicode.Scalar) in (0x400...0x4FF).contains(c.value) }
        let latin = { (c: Unicode.Scalar) in c.properties.isAlphabetic && c.value < 0x250 }
        let russianOnly = { (s: String) in s.filter { "ыэъьяюёйщЫЭЪЬЯЮЁЙЩ".contains($0) }.count }
        return zip(letters, letters.dropFirst()).contains { (cyrillic($0) && latin($1)) || (latin($0) && cyrillic($1)) }
            || russianOnly(shown) != russianOnly(text)
    }

    func testTheLatinSettingTurnsNoRussianWordIntoLatin() {
        var p = Prefs()
        p.latinFirst = true
        for (id, words) in [("sr-ru", Fixtures.words), ("ru-sr", Fixtures.ruWords)] {
            p.deckId = id
            let bad = words.flatMap { w -> [String] in
                let native = w.meanings + w.examples.map(\.tr) + w.related.map(\.tr) + [w.note] + w.tips + (w.pos == "note" ? [w.cyr] : [])
                let primary = [w.forms] + w.examples.map(\.text) + (w.pos == "note" ? [] : [w.cyr])
                let shown = native.map { ($0, p.native($0)) } + primary.map { ($0, p.primary($0)) }
                return shown.filter { transliterated($0.0, $0.1) }.map { "\(w.key): \($0.1)" }
            }
            XCTAssertEqual(bad, [], id)
        }
    }

    func testTheInterfaceLanguageIsChosenApartFromTheDeck() throws {
        let store = Fixtures.tempStore()
        XCTAssertEqual(store.prefs.uiLang, "ru")
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

        store.switchDeck("sr-ru")
        XCTAssertEqual(t("Настройки"), "Settings")
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

    func testLanguagesAreNamedInTheInterfaceLanguage() {
        I18n.lang = "ru"
        XCTAssertEqual(Decks.name("sr"), "Сербский")
        I18n.lang = "sr"
        XCTAssertEqual(Decks.name("ru"), "Руски")
        I18n.latin = true
        XCTAssertEqual(Decks.name("ru"), "Ruski")
        I18n.lang = "en"
        XCTAssertEqual(Decks.name("en"), "English")
    }

    // What the settings do on a tap of the language learned.
    private func learn(_ store: Store, _ target: String) {
        store.switchDeck(Decks.pick(target, store.prefs.deck.native).id)
        XCTAssertEqual(store.prefs.deck.target, target)
    }

    func testTheLanguageLearnedSwitchesTheDeckAndEachKeepsItsProgress() throws {
        XCTAssertEqual(Decks.targets, ["sr", "ru"])
        XCTAssertEqual(Decks.natives("sr").first, "ru")
        XCTAssertEqual(Decks.natives("ru").first, "sr")
        let store = Fixtures.tempStore()
        let sr = Repo(store: store, words: Fixtures.words)
        let kuca = try XCTUnwrap(sr.word("кућа"))
        sr.answer(kuca, Mark.known, learned: true)

        learn(store, "ru")
        XCTAssertEqual(store.prefs.deckId, "ru-sr")
        let ru = Repo(store: store, words: Fixtures.ruWords)
        let dom = try XCTUnwrap(ru.word("дом"))
        XCTAssertEqual(ru.progress(dom).state, Mark.new)
        ru.answer(dom, Mark.dontKnow)

        learn(store, "sr")
        XCTAssertEqual(store.prefs.deckId, "sr-ru")
        XCTAssertEqual(Repo(store: store, words: Fixtures.words).progress(kuca).state, Mark.known)
        XCTAssertNil(store.progress["дом"])
        learn(store, "ru")
        XCTAssertEqual(store.progress["дом"]?.state, Mark.dontKnow)
    }

    func testTheTranslationsFollowTheLanguageLearnedAndStayWhereTheyCan() {
        let srRu = Decks.deck("sr-ru"), ruSr = Decks.deck("ru-sr")
        let srEn = Deck(id: "sr-en", target: "sr", native: "en", words: "words.json", grammar: "")
        let decks = Decks.all
        Decks.all = [srRu, ruSr, srEn]
        defer { Decks.all = decks }
        XCTAssertEqual(Decks.targets, ["sr", "ru"])
        XCTAssertEqual(Decks.natives("sr"), ["ru", "en"])
        XCTAssertEqual(Decks.natives("ru"), ["sr"])
        XCTAssertEqual(Decks.pick("sr", "en"), srEn, "English translations stay")
        XCTAssertEqual(Decks.pick("sr", "ru"), srRu)
        XCTAssertEqual(Decks.pick("sr", "sr"), srRu, "no Serbian deck translates into Serbian")
        XCTAssertEqual(Decks.pick("ru", "en"), ruSr, "no Russian deck translates into English")
    }

    func testSerbianInterfaceFollowsTheScriptSetting() {
        let s = Fixtures.tempStore()
        s.prefs.uiLang = "sr"
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

    // Decks with English translations over the cards the app carries in any case: their own
    // assets may not be built yet.
    private let srEn = Deck(id: "sr-en", target: "sr", native: "en", words: "words.json", grammar: "")
    private let ruEn = Deck(id: "ru-en", target: "ru", native: "en", words: "words-ru-sr.json", grammar: "")

    private func withEnglish(_ f: () throws -> Void) rethrows {
        let decks = Decks.all
        Decks.all = [Decks.deck("sr-ru"), Decks.deck("ru-sr"), srEn, ruEn]
        defer { Decks.all = decks }
        try f()
    }

    func testTheDecksOfOneLanguageLearnedShareTheProgressAndThePlaceInTheCards() throws {
        try withEnglish {
            XCTAssertEqual(srEn.store, "sr-ru")
            XCTAssertEqual(ruEn.store, "ru-sr")
            XCTAssertEqual(srEn.scoped("currentKey"), "currentKey")
            XCTAssertEqual(ruEn.scoped("currentKey"), "ru-sr.currentKey")
            XCTAssertEqual(srEn.ownScoped("deck.json"), "sr-en.deck.json", "the widgets' cards hold the translations")
            let store = Fixtures.tempStore()
            let sr = Repo(store: store, words: Fixtures.words)
            sr.step(1)
            let card = try XCTUnwrap(sr.current())
            sr.answer(try XCTUnwrap(sr.word("кућа")), Mark.known, learned: true)
            var p = sr.prefs
            p.categories = ["L1", "L2"]
            sr.prefs = p

            store.switchDeck("sr-en")
            XCTAssertEqual(store.prefs.deckId, "sr-en")
            let en = Repo(store: store, words: Fixtures.words)
            XCTAssertEqual(en.current()?.key, card.key)
            XCTAssertEqual(en.prefs.categories, ["L1", "L2"])
            XCTAssertEqual(en.progress(try XCTUnwrap(en.word("кућа"))).state, Mark.known)
            en.answer(try XCTUnwrap(en.word("вода")), Mark.dontKnow)

            learn(store, "ru")
            XCTAssertEqual(store.prefs.deckId, "ru-en")
            let ru = Repo(store: store, words: Fixtures.ruWords)
            ru.answer(try XCTUnwrap(ru.word("дом")), Mark.known, learned: true)
            store.switchDeck("ru-sr")
            XCTAssertEqual(store.progress["дом"]?.state, Mark.known, "Russian with Serbian shares it too")

            store.switchDeck("sr-ru")
            XCTAssertEqual(store.progress["вода"]?.state, Mark.dontKnow)
            let prefs = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: store.dir.appendingPathComponent("prefs.json"))) as? [String: Any])
            XCTAssertFalse(prefs.keys.contains { $0.hasPrefix("sr-en.") || $0.hasPrefix("ru-en.") })
            let files = try FileManager.default.contentsOfDirectory(atPath: store.dir.path)
            XCTAssertFalse(files.contains { $0.hasPrefix("sr-en.") || $0.hasPrefix("ru-en.") }, "\(files)")
        }
    }

    func testAFirstStartOnAPhoneInAnotherLanguageLearnsSerbianWithEnglish() throws {
        try withEnglish {
            XCTAssertEqual(Decks.guess("ru"), "sr-ru")
            XCTAssertEqual(Decks.guess("sr"), "ru-sr")
            XCTAssertEqual(Decks.guess("hr"), "ru-sr")
            XCTAssertEqual(Decks.guess("uk"), "sr-ru")
            XCTAssertEqual(Decks.guess("be"), "sr-ru")
            XCTAssertEqual(Decks.guess("en"), "sr-en")
            XCTAssertEqual(Decks.guess("de"), "sr-en")
            XCTAssertEqual(Decks.guess(""), "sr-en")
            let de = try start("de")
            XCTAssertEqual(de.prefs.deckId, "sr-en")
            Decks.systemLanguage = "ru"
            XCTAssertEqual(Store(dir: de.dir).prefs.deckId, "sr-en", "the choice stays")
            XCTAssertEqual(try start("en", #"{"currentKey": "кућа"}"#).prefs.deckId, "sr-ru", "an earlier user stays")
            Decks.systemLanguage = "ru"
        }
        let decks = Decks.all
        Decks.all = [Decks.deck("sr-ru"), Decks.deck("ru-sr")]
        defer { Decks.all = decks }
        XCTAssertEqual(Decks.guess("en"), "sr-ru", "without English translations the first deck")
    }

    func testSerbianInEnglishTextFollowsTheScriptSetting() {
        withEnglish {
            let note = "«да» + present tense: хоћу да идем"
            var p = Prefs()
            p.latinFirst = true
            XCTAssertEqual(p.native(note), note, "Russian translations stay in Cyrillic")
            p.deckId = "sr-en"
            XCTAssertTrue(p.deck.hasSerbian)
            XCTAssertEqual(p.native(note), "«da» + present tense: hoću da idem")
            XCTAssertEqual(p.primary("кућа"), "kuća")
            p.latinFirst = false
            XCTAssertEqual(p.native(note), note)
            p.latinFirst = true
            p.deckId = "ru-en"
            XCTAssertEqual(p.native("«дом» is a house"), "«дом» is a house", "Russian learned: nothing is Serbian")
            XCTAssertEqual(p.primary("дом"), "дом")
        }
    }

    func testTheTextsOfTheEnglishDecks() {
        I18n.lang = "en"
        XCTAssertEqual(srEn.shareText, "NERodnaya rech: Serbian words for English speakers, cards with stress marks and examples. Free.")
        XCTAssertEqual(ruEn.shareText, "NERodnaya rech: Russian words for English speakers, cards with stress marks and examples. Free.")
        XCTAssertEqual(Decks.deck("sr-ru").shareText, "NERodnaya rech: Serbian words for Russian speakers, cards with stress marks, examples and grammar. Free.")
        XCTAssertEqual(srEn.meaningHint, "word in English")
        XCTAssertEqual(srEn.wordChip, "Serbian word")
        XCTAssertEqual(ruEn.wordChip, "Russian word")
        XCTAssertEqual(srEn.accentSamples.count, 3)
        XCTAssertEqual(ruEn.accentSamples.count, 2)
        XCTAssertTrue(Repo(store: Fixtures.tempStore(), words: Fixtures.words).linksFor(Fixtures.words[0]).isEmpty, "no reference, no links")
    }

    func testEnglishTranslationsAreFoundInLatinLettersAsSerbianLatinIs() throws {
        let w = try XCTUnwrap(Word.decode(Data(#"[{"key": "реч", "cyr": "реч", "meanings": ["word", "speech"]}]"#.utf8), deck: srEn).first)
        XCTAssertTrue(Search.matches(w, "speech", byMeaning: true))
        XCTAssertTrue(Search.matches(w, "Wor", byMeaning: true))
        XCTAssertTrue(Search.matches(w, "reč", byMeaning: false), "Serbian Latin")
        XCTAssertTrue(Search.matches(w, "ре", byMeaning: false))
        XCTAssertFalse(Search.matches(w, "word", byMeaning: false), "an English word is not a Serbian one")
        XCTAssertFalse(Search.matches(w, "reč", byMeaning: true))
    }
}
