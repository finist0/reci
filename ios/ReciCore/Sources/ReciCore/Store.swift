import Foundation

// Settings and progress as two JSON files in one directory. The app and the lock screen widgets
// share the directory through an App Group, so each change is written at once.
//
// Each deck keeps its own progress and its own place in the cards: Russian вода and Serbian вода
// are different cards. The first deck keeps the names it had before there were others; another
// deck prefixes them with its id: "ru-sr.progress.json", "ru-sr.currentKey" in prefs.json.
public final class Store {
    public let dir: URL
    // The interface follows the language chosen and the script setting.
    public var prefs = Prefs() {
        didSet { I18n.follow(prefs) }
    }
    // key -> progress; only words that were touched.
    public private(set) var progress: [String: Progress] = [:]
    // The deck whose own fields prefs holds; switchDeck changes it.
    public private(set) var deck = Decks.deck(nil)
    // prefs.json as read: the settings and the own fields of every deck.
    private var stored: [String: Any] = [:]

    // What belongs to one deck.
    static let perDeck: Set<String> = ["cardCount", "shuffleSeed", "currentKey", "categories", "lastShown", "pendingAnswer"]

    public init(dir: URL) {
        self.dir = dir
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        reload()
    }

    private var prefsURL: URL { dir.appendingPathComponent("prefs.json") }
    private func progressURL(_ d: Deck) -> URL { dir.appendingPathComponent(d.scoped("progress.json")) }

    // Picks up what the other process (the app or a widget) wrote meanwhile.
    public func reload() {
        let dec = JSONDecoder()
        stored = (try? Data(contentsOf: prefsURL)).flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] } ?? [:]
        // A first start picks a deck by the phone's language. Earlier users learned the first
        // deck before there was a choice.
        let id = stored["deck"] as? String ?? (stored["currentKey"] != nil
            || FileManager.default.fileExists(atPath: progressURL(Decks.deck(nil)).path) ? Decks.first : Decks.guess(Decks.systemLanguage))
        let d = Decks.deck(id)
        deck = d
        var view = stored
        for k in Store.perDeck { view[k] = stored[d.scoped(k)] }
        var p = (try? JSONSerialization.data(withJSONObject: view)).flatMap { try? dec.decode(Prefs.self, from: $0) } ?? Prefs()
        p.deckId = d.id
        // A first start takes the interface language from the phone. An earlier user keeps the
        // native language of the deck, which the interface spoke before there was a choice.
        let settle = p.uiLang.isEmpty
        if settle {
            let earlier = stored.keys.contains { $0 == "currentKey" || $0.hasSuffix(".currentKey") }
                || FileManager.default.fileExists(atPath: progressURL(d).path)
            p.uiLang = earlier ? d.native : Decks.guessUi(Decks.systemLanguage)
        }
        prefs = p
        progress = (try? Data(contentsOf: progressURL(d))).flatMap { try? dec.decode([String: Progress].self, from: $0) } ?? [:]
        if settle || stored["deck"] as? String != d.id { savePrefs() }
    }

    public func savePrefs() {
        guard let data = try? JSONEncoder().encode(prefs),
              let now = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
        for k in Prefs.keys { stored[Store.perDeck.contains(k) ? deck.scoped(k) : k] = now[k] }
        stored["deck"] = deck.id
        writeStored()
    }

    private func writeStored() {
        if let data = try? JSONSerialization.data(withJSONObject: stored, options: [.sortedKeys]) {
            try? data.write(to: prefsURL, options: .atomic)
        }
    }

    public func saveProgress() {
        if let data = try? JSONEncoder().encode(progress) { try? data.write(to: progressURL(deck), options: .atomic) }
    }

    public func put(_ key: String, _ p: Progress) {
        progress[key] = p
        saveProgress()
    }

    public func replaceProgress(_ all: [String: Progress]) {
        progress = all
        saveProgress()
    }

    // Goes over to another deck: its progress and its place in its cards. The cards themselves
    // are the caller's: a Repo over them takes this store.
    public func switchDeck(_ id: String) {
        guard id != deck.id, Decks.all.contains(where: { $0.id == id }) else { return }
        savePrefs()
        stored["deck"] = id
        writeStored()
        reload()
    }
}
