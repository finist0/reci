import Foundation

// A patch from the dictionary built into a release to the current one (data/scripts/build_patches.py).
public struct DictPatch: Decodable {
    public var format: Int
    public var base: String
    public var words: [Word]
    public var removed: [String]
    public var grammar: [GrammarTopic]?

    public init(base: String, words: [Word] = [], removed: [String] = [], grammar: [GrammarTopic]? = nil) {
        (self.format, self.base, self.words, self.removed, self.grammar) = (1, base, words, removed, grammar)
    }

    // The id build_patches.py gives a dictionary: a hash of words.json, a newline and grammar.json.
    public static func baseId(words: Data, grammar: Data) -> String {
        var s = SHA256()
        s.update(words)
        s.update(Data([0x0A]))
        s.update(grammar)
        return String(s.finish().prefix(32))
    }

    // The built-in cards with the patch on top, in the order the build sorts them.
    public func apply(to base: [Word]) -> [Word] {
        var byKey = Dictionary(base.map { ($0.key, $0) }, uniquingKeysWith: { first, _ in first })
        for k in removed { byKey[k] = nil }
        for w in words { byKey[w.key] = w }
        return byKey.values.sorted { $0.rank != $1.rank ? $0.rank < $1.rank : $0.key.unicodeScalars.lexicographicallyPrecedes($1.key.unicodeScalars) }
    }

    public static func read(_ file: URL, base: String) -> DictPatch? {
        (try? Data(contentsOf: file)).flatMap { parse($0, base: base) }
    }

    public static func parse(_ data: Data, base: String) -> DictPatch? {
        guard let p = try? JSONDecoder().decode(DictPatch.self, from: data), p.format == 1, p.base == base,
              !p.words.contains(where: { $0.key.isEmpty }) else { return nil }
        return p
    }
}

struct DictState: Codable {
    var base = ""
    var baseOf = ""
    var patchSha = ""
    var patchOf = ""
    var checkedDay = ""
    var triedAt: Double = 0
}

// Card fixes without a new release: once a day the app fetches the patch made for the dictionary
// it carries, keeps it and applies it on top of that dictionary. Requests are awaited, never
// waited for: until a patch is complete and checked, the cards come from what the app has.
@MainActor
public final class DictUpdate {
    public static let site = "https://finist0.github.io/reci/dict/"
    public nonisolated static let maxSize = 16 * 1024 * 1024
    static let retry: TimeInterval = 60 * 60

    // What a check found: a new patch, a patch taken back, or nothing new.
    public enum Result: Equatable {
        case none
        case patch
        case builtIn
    }

    let dir: URL
    // Tells one install from another: a patch kept for one is never applied to the next.
    let install: String
    private var state: DictState
    // The body of a 200 answer, or nil on anything else.
    public var get: @MainActor (String) async -> Data?
    public var now: () -> Date = Date.init
    public var calendar = Calendar.current
    private var checking = false

    public init(dir: URL, install: String, get: @escaping @MainActor (String) async -> Data?) {
        self.dir = dir
        self.install = install
        self.get = get
        state = (try? Data(contentsOf: dir.appendingPathComponent("state.json"))).flatMap { try? JSONDecoder().decode(DictState.self, from: $0) } ?? DictState()
    }

    private var patchFile: URL { dir.appendingPathComponent("patch.json") }

    private func save() {
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        if let data = try? JSONEncoder().encode(state) { try? data.write(to: dir.appendingPathComponent("state.json"), options: .atomic) }
    }

    // Where the patch kept for this install is and the dictionary it is for, so that it can be read
    // off the main actor with DictPatch.read.
    public var kept: (file: URL, base: String)? {
        state.patchOf == install && !state.patchSha.isEmpty ? (patchFile, state.base) : nil
    }

    // Which dictionary the cards come from, for reports.
    public var describe: String { kept == nil ? "built-in" : "patch " + state.patchSha.prefix(8) }

    // The patch kept for this install, or nil. Reads only the small patch file.
    public func stored() -> DictPatch? {
        kept.flatMap { DictPatch.read($0.file, base: $0.base) }
    }

    private func day(_ d: Date) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: d)
        return "\(c.year!)-\(c.month!)-\(c.day!)"
    }

    // Looks for a new patch once a local day; a failed look is tried again an hour later at the
    // earliest. `baseId` hashes the built-in dictionary, off the main actor, once per install.
    public func check(baseId: @escaping @Sendable () async -> String) async -> Result {
        let t = now()
        if checking || state.checkedDay == day(t) || t.timeIntervalSince1970 - state.triedAt < Self.retry { return .none }
        checking = true
        defer { checking = false }
        state.triedAt = t.timeIntervalSince1970
        save()
        if state.baseOf != install {
            state.base = await baseId()
            state.baseOf = install
            save()
        }
        guard let result = await fetch() else { return .none }
        state.checkedDay = day(t)
        save()
        return result
    }

    private func fetch() async -> Result? {
        let base = state.base
        guard let m = await get(Self.site + "manifest.json"),
              let manifest = try? JSONSerialization.jsonObject(with: m) as? [String: Any] else { return nil }
        guard let entry = (manifest["patches"] as? [String: Any])?[base] as? [String: Any] else {
            // The manifest no longer has a patch for this dictionary: back to the built-in one.
            guard state.patchOf == install, !state.patchSha.isEmpty else { return Result.none }
            try? FileManager.default.removeItem(at: patchFile)
            state.patchSha = ""
            state.patchOf = ""
            return .builtIn
        }
        let sha = entry["sha256"] as? String ?? ""
        let file = patchFile
        if sha == state.patchSha, state.patchOf == install,
           await Task.detached(operation: { (try? Data(contentsOf: file)).map(SHA256.hex) }).value == sha { return Result.none }
        guard let name = entry["file"] as? String, name.range(of: #"\A[A-Za-z0-9._-]+\.json\z"#, options: .regularExpression) != nil,
              let data = await get(Self.site + name), data.count <= Self.maxSize,
              await Task.detached(operation: { SHA256.hex(data) == sha && DictPatch.parse(data, base: base) != nil }).value
        else { return nil }
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        guard (try? data.write(to: patchFile, options: .atomic)) != nil else { return nil }
        state.patchSha = sha
        state.patchOf = install
        save()
        return .patch
    }
}
