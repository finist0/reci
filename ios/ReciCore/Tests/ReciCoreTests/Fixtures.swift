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

    static func tempStore() -> Store {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("reci-test-\(UUID().uuidString)")
        return Store(dir: dir)
    }

    static func json(_ path: String) -> Any {
        try! JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent(path)))
    }
}
