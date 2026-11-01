import Foundation
import ReciCore

// The dictionary the app uses: the one built in with the downloaded patch on top (DictUpdate).
enum Dict {
    static let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("dict", isDirectory: true)

    private static func bundled(_ name: String) -> Data? {
        Bundle.main.url(forResource: name, withExtension: "json").flatMap { try? Data(contentsOf: $0) }
    }

    // Changes with every build and with the built-in dictionary, so a patch kept for one is not
    // applied to another.
    static var install: String {
        let info = Bundle.main.infoDictionary ?? [:]
        let size = Bundle.main.url(forResource: "words", withExtension: "json")
            .flatMap { try? $0.resourceValues(forKeys: [.fileSizeKey]).fileSize } ?? 0
        return "\(info["CFBundleShortVersionString"] ?? "")-\(info["CFBundleVersion"] ?? "")-\(size)"
    }

    // The id build_patches.py gives the built-in dictionary. Hashing it takes a while, so it is
    // never called on the main actor.
    static func baseId() -> String {
        DictPatch.baseId(words: bundled("words") ?? Data(), grammar: bundled("grammar") ?? Data())
    }

    static func load(patch: DictPatch?) -> (words: [Word], topics: [GrammarTopic]) {
        let dec = JSONDecoder()
        let words = bundled("words").flatMap { try? dec.decode([Word].self, from: $0) } ?? []
        let topics = bundled("grammar").flatMap { try? dec.decode([GrammarTopic].self, from: $0) } ?? []
        guard let patch else { return (words, topics) }
        return (patch.apply(to: words), patch.grammar ?? topics)
    }

    // UI tests never reach the site.
    @MainActor
    static func updater() -> DictUpdate {
        let offline = ProcessInfo.processInfo.arguments.contains { $0.hasPrefix("-uitest") }
        return DictUpdate(dir: dir, install: install) { url in
            guard !offline, let u = URL(string: url) else { return nil }
            return await Task.detached { await Dict.download(u) }.value
        }
    }

    // Stops reading past DictUpdate.maxSize instead of taking in whatever the answer holds.
    private static func download(_ url: URL) async -> Data? {
        var req = URLRequest(url: url, timeoutInterval: 10)
        req.cachePolicy = .reloadIgnoringLocalCacheData
        guard let answer = try? await URLSession.shared.bytes(for: req),
              (answer.1 as? HTTPURLResponse)?.statusCode == 200,
              answer.1.expectedContentLength <= Int64(DictUpdate.maxSize) else { return nil }
        var data = Data()
        do {
            for try await b in answer.0 {
                data.append(b)
                if data.count > DictUpdate.maxSize { return nil }
            }
        } catch {
            return nil
        }
        return data
    }
}
