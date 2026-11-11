import Foundation

// What changed in each version, from whatsnew.json (shared with Android and the web), shown once
// on the first start after an update. The counterpart of data/WhatsNew.kt.
public enum WhatsNew {
    // The last version without this window: an update from it has no version seen yet.
    public static let before = "1.6"

    public struct Entry: Equatable {
        public let version: String
        public let items: [String]
    }

    private struct Raw: Decodable {
        struct Item: Decodable {
            let text: String
            let only: [String]?
        }
        let version: String
        let items: [Item]
    }

    public static func parse(_ data: Data, platform: String) -> [Entry] {
        let raw = (try? JSONDecoder().decode([Raw].self, from: data)) ?? []
        return raw.map { e in
            Entry(version: e.version, items: e.items.filter { $0.only?.contains(platform) ?? true }.map(\.text))
        }.filter { !$0.items.isEmpty }
    }

    public static func compare(_ a: String, _ b: String) -> Int {
        let x = a.split(separator: ".").map { Int($0) ?? 0 }
        let y = b.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(x.count, y.count) {
            let p = i < x.count ? x[i] : 0, q = i < y.count ? y[i] : 0
            if p != q { return p < q ? -1 : 1 }
        }
        return 0
    }

    // The entries after the version seen last, up to the running one, newest first. A fresh
    // install has nothing to compare with, so it is told nothing.
    public static func pick(_ entries: [Entry], version: String, seen: String?, fresh: Bool) -> [Entry] {
        let from = seen ?? (fresh ? version : before)
        return entries.filter { compare($0.version, from) > 0 && compare($0.version, version) <= 0 }
            .sorted { compare($0.version, $1.version) > 0 }
    }
}
