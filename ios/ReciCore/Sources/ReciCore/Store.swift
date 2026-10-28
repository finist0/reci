import Foundation

// Settings and progress as two JSON files in one directory. The app and the lock screen widgets
// share the directory through an App Group, so each change is written at once.
public final class Store {
    public let dir: URL
    public var prefs = Prefs()
    // key -> progress; only words that were touched.
    public private(set) var progress: [String: Progress] = [:]

    public init(dir: URL) {
        self.dir = dir
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        reload()
    }

    private var prefsURL: URL { dir.appendingPathComponent("prefs.json") }
    private var progressURL: URL { dir.appendingPathComponent("progress.json") }

    // Picks up what the other process (the app or a widget) wrote meanwhile.
    public func reload() {
        let dec = JSONDecoder()
        prefs = (try? Data(contentsOf: prefsURL)).flatMap { try? dec.decode(Prefs.self, from: $0) } ?? Prefs()
        progress = (try? Data(contentsOf: progressURL)).flatMap { try? dec.decode([String: Progress].self, from: $0) } ?? [:]
    }

    public func savePrefs() {
        if let data = try? JSONEncoder().encode(prefs) { try? data.write(to: prefsURL, options: .atomic) }
    }

    public func saveProgress() {
        if let data = try? JSONEncoder().encode(progress) { try? data.write(to: progressURL, options: .atomic) }
    }

    public func put(_ key: String, _ p: Progress) {
        progress[key] = p
        saveProgress()
    }

    public func replaceProgress(_ all: [String: Progress]) {
        progress = all
        saveProgress()
    }
}
