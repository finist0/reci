import AppIntents
import Foundation
import OSLog
import ReciCore
import WidgetKit

// What the app and the lock screen widgets share through the App Group container.
enum LockDeck {
    static let appGroup = "group.org.kostja.reci"

    static var dir: URL {
        guard let base = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup) else {
            // Without the entitlement the app and the widgets each get their own directory, and the
            // widgets never see a card.
            Logger(subsystem: "org.kostja.reci", category: "store").error("no App Group \(appGroup, privacy: .public)")
            return FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("reci", isDirectory: true)
        }
        return base.appendingPathComponent("reci", isDirectory: true)
    }

    // The widgets have little memory, so they read a short copy of the dictionary: only the
    // fields the learning rules and a lock screen card need. The app writes it at start.
    private static var deckURL: URL { dir.appendingPathComponent("deck.json") }

    private struct Short: Encodable {
        let key, cyr, acc, pos, gram, topic: String
        let level: Int
        let meanings: [String]
    }

    static func writeDeck(_ words: [Word]) {
        let short = words.map { Short(key: $0.key, cyr: $0.cyr, acc: $0.acc, pos: $0.pos, gram: $0.gram, topic: $0.topic,
                                      level: $0.level, meanings: Array($0.meanings.prefix(3))) }
        if let data = try? JSONEncoder().encode(short) { try? data.write(to: deckURL, options: .atomic) }
    }

    // The repository as a widget or an intent sees it, or nil before the app ran once.
    static func repo() -> Repo? {
        guard let data = try? Data(contentsOf: deckURL), let words = try? JSONDecoder().decode([Word].self, from: data)
        else { return nil }
        return Repo(store: Store(dir: dir), words: words)
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
