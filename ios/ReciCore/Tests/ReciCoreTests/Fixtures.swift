import Foundation
@testable import ReciCore

enum Fixtures {
    // ios/ReciCore/Tests/ReciCoreTests/Fixtures.swift -> the repository root.
    static let root = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent()

    static let words: [Word] = {
        let url = root.appendingPathComponent("app/app/src/main/assets/words.json")
        return try! JSONDecoder().decode([Word].self, from: Data(contentsOf: url))
    }()

    static let assets = root.appendingPathComponent("app/app/src/main/assets")

    // The cards of the Russian deck, for Serbs.
    static let ruWords: [Word] = try! Word.decode(Data(contentsOf: assets.appendingPathComponent("words-ru-sr.json")), deck: Decks.deck("ru-sr"))

    // The decks and the interface strings the apps carry.
    static func loadTables() {
        Decks.load(try! Data(contentsOf: assets.appendingPathComponent("decks.json")))
        I18n.load(try! Data(contentsOf: assets.appendingPathComponent("ui.json")))
    }

    // A fresh install on a phone in the given language, which picks the deck.
    static func tempStore(language: String = "ru") -> Store {
        Decks.systemLanguage = language
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("reci-test-\(UUID().uuidString)")
        return Store(dir: dir)
    }

    static func json(_ path: String) -> Any {
        try! JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent(path)))
    }
}
