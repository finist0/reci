import Foundation
import ReciCore
import UIKit
import WidgetKit

// The daily ping as the app and the widgets send it. Someone who only answers on the lock screen
// may not open the app for days, so the widgets send it too, from the same file and with the
// same fields.
enum Stats {
    // Next to the App Group directory the widgets read: they need the id and the day of the last
    // ping. Out of the backup, so that the id never moves to another phone with a restored backup.
    static var dir: URL {
        var dir = LockDeck.dir.deletingLastPathComponent().appendingPathComponent("telemetry", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? dir.setResourceValues(values)
        return dir
    }

    static var file: URL { dir.appendingPathComponent("telemetry.json") }

    static func post(_ url: String, _ body: Data) async -> Int {
        var req = URLRequest(url: URL(string: url)!, timeoutInterval: 5)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = body
        guard let answer = try? await URLSession.shared.data(for: req) else { return -1 }
        return (answer.1 as? HTTPURLResponse)?.statusCode ?? -1
    }

    static var appVersion: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown" }
    static var osName: String { "iOS " + UIDevice.current.systemVersion }

    // The accessory families are the lock screen ones on a phone. iOS may report a widget for a
    // while after it is removed, so a missing one is the sure finding.
    static func widgets() async -> Widgets? {
        await withCheckedContinuation { done in
            WidgetCenter.shared.getCurrentConfigurations { result in
                let lock: [WidgetFamily] = [.accessoryCircular, .accessoryRectangular, .accessoryInline]
                done.resume(returning: (try? result.get()).map { infos in
                    Widgets(lock: Set(infos.filter { lock.contains($0.family) }.map(\.kind)),
                            home: Set(infos.filter { !lock.contains($0.family) }.map(\.kind)))
                })
            }
        }
    }

    static func learned(_ repo: Repo) -> Int {
        repo.words.reduce(0) { $0 + (repo.progress($1).state == Mark.known ? 1 : 0) }
    }

    @MainActor
    static func send(_ telemetry: Telemetry, prefs: Prefs, learned: Int) async {
        await telemetry.sendPing(app: appVersion, os: osName, locale: Locale.current.identifier(.bcp47), prefs: prefs,
                                 learned: learned, widgets: widgets,
                                 wallpaper: Telemetry.wallpaper(drawn: LockDeck.wallpaperDrawn, now: Date()))
    }

    // The statistics as a widget or an intent sees them, or nil until the app has moved its file
    // here: a widget must not start a statistics file with an id of its own.
    @MainActor
    static func shared() -> Telemetry? {
        guard FileManager.default.fileExists(atPath: file.path) else { return nil }
        return Telemetry(file: file, post: post)
    }

    // From the lock screen: counts an answer given there, if any, and sends today's ping when it
    // is due.
    @MainActor
    static func fromLockScreen(_ repo: Repo?, answer: String? = nil) async {
        guard let telemetry = shared() else { return }
        if let answer { telemetry.count("lock", answer) }
        guard let repo else { return }
        await send(telemetry, prefs: repo.prefs, learned: learned(repo))
    }
}
