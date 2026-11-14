import AppIntents
import Foundation
import OSLog
import ReciCore
import WidgetKit

// What the app and the lock screen widgets share through the App Group container.
enum LockDeck {
    static let appGroup = "group.rs.finist.reci"

    static var dir: URL {
        guard let base = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup) else {
            // Without the entitlement the app and the widgets each get their own directory, and the
            // widgets never see a card.
            Logger(subsystem: "rs.finist.reci", category: "store").error("no App Group \(appGroup, privacy: .public)")
            return FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("reci", isDirectory: true)
        }
        return base.appendingPathComponent("reci", isDirectory: true)
    }

    // The decks and the interface strings, from the resources of the app or of the widgets.
    static func loadTables() {
        if let data = Bundle.main.url(forResource: "decks", withExtension: "json").flatMap({ try? Data(contentsOf: $0) }) { Decks.load(data) }
        if let data = Bundle.main.url(forResource: "ui", withExtension: "json").flatMap({ try? Data(contentsOf: $0) }) { I18n.load(data) }
    }

    // When the wallpaper action last drew a card: the setup tells by it whether the automation runs.
    static var wallpaperDrawn: Date? {
        get { UserDefaults(suiteName: appGroup)?.object(forKey: "wallpaperDrawn") as? Date }
        set { UserDefaults(suiteName: appGroup)?.set(newValue, forKey: "wallpaperDrawn") }
    }

    // The widgets have little memory, so they read a short copy of the dictionary: only the
    // fields the learning rules and a lock screen card need, with the first example only. The
    // app writes it at start and when the deck changes, one copy per deck.
    private static func deckURL(_ deck: Deck) -> URL { dir.appendingPathComponent(deck.scoped("deck.json")) }

    private struct Short: Encodable {
        let key, cyr, acc, pos, gram, topic: String
        let level: Int
        let meanings: [String]
        let examples: [Example]
    }

    static func writeDeck(_ words: [Word], deck: Deck) {
        let short = words.map { Short(key: $0.key, cyr: $0.cyr, acc: $0.acc, pos: $0.pos, gram: $0.gram, topic: $0.topic,
                                      level: $0.level, meanings: Array($0.meanings.prefix(3)), examples: Array($0.examples.prefix(1))) }
        let enc = JSONEncoder()
        enc.userInfo[Deck.userInfoKey] = deck
        if let data = try? enc.encode(short) { try? data.write(to: deckURL(deck), options: .atomic) }
    }

    // The repository as a widget or an intent sees it, or nil before the app ran once.
    static func repo() -> Repo? {
        let store = Store(dir: dir)
        let deck = store.prefs.deck
        guard let data = try? Data(contentsOf: deckURL(deck)), let words = try? Word.decode(data, deck: deck) else { return nil }
        return Repo(store: store, words: words)
    }

    // Answers the card on the lock screen and moves on, as the buttons of the card screen do.
    static func answer(know: Bool) {
        guard let repo = repo(), let w = repo.current() else { return }
        repo.answer(w, know ? Mark.known : Mark.dontKnow)
        repo.step(1)
        if let w = repo.current() { repo.markShown(w) }
        WidgetCenter.shared.reloadTimelines(ofKind: "card")
    }
}

struct AnswerIntent: AppIntent {
    static var title: LocalizedStringResource = "Ответить на карточку"
    static var description = IntentDescription("Отмечает слово на экране блокировки и показывает следующее.")
    // The lock screen buttons work without unlocking the phone.
    static var authenticationPolicy: IntentAuthenticationPolicy = .alwaysAllowed
    static var isDiscoverable = false

    @Parameter(title: "Знаю")
    var know: Bool

    init() {}

    init(know: Bool) {
        self.know = know
    }

    func perform() async throws -> some IntentResult {
        LockDeck.answer(know: know)
        return .result()
    }
}
