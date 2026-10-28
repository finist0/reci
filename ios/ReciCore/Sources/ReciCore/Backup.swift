import Foundation

// Progress and settings as a file, in the format of the web version and the Android app.
public enum Backup {
    public enum Failure: Error { case notAProgressFile }

    public static func export(_ repo: Repo, date: Date = Date()) throws -> Data {
        let prefs = try JSONSerialization.jsonObject(with: JSONEncoder().encode(repo.prefs))
        let progress = try JSONSerialization.jsonObject(with: JSONEncoder().encode(repo.store.progress))
        let iso = ISO8601DateFormatter()
        let file: [String: Any] = ["app": "reci", "format": 1, "exported": iso.string(from: date), "prefs": prefs, "progress": progress]
        return try JSONSerialization.data(withJSONObject: file, options: [.sortedKeys])
    }

    // Replaces the progress and returns the number of words in the file.
    @discardableResult
    public static func `import`(_ data: Data, into repo: Repo) throws -> Int {
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              obj["app"] as? String == "reci", let progressObj = obj["progress"] as? [String: Any]
        else { throw Failure.notAProgressFile }
        let progress = try JSONDecoder().decode([String: Progress].self, from: JSONSerialization.data(withJSONObject: progressObj))
        if let prefsObj = obj["prefs"] as? [String: Any] {
            var prefs = try JSONDecoder().decode(Prefs.self, from: JSONSerialization.data(withJSONObject: prefsObj))
            prefs.currentKey = nil
            prefs.lastShown = ""
            prefs.pendingAnswer = nil
            // How the card reaches the lock screen belongs to this phone, not to the file.
            prefs.lockMode = repo.prefs.lockMode
            repo.prefs = prefs
        }
        repo.store.replaceProgress(progress)
        repo.progressReplaced()
        return progress.count
    }
}
