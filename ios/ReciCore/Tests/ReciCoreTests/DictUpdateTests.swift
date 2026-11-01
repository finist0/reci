import Foundation
import XCTest
@testable import ReciCore

// Card fixes without a new release: the shared cases of tests/dict-patches.json, which the build
// and Android run too, and the updater against a fake site.
@MainActor
final class DictUpdateTests: XCTestCase {
    private let fixture = Fixtures.json("tests/dict-patches.json") as! [String: Any]
    private var site: [String: Data] = [:]
    private var gets: [String] = []
    private var clock = DateComponents(calendar: Calendar.current, year: 2026, month: 11, day: 2, hour: 9).date!
    private var dir: URL!
    private var u: DictUpdate!
    private let base = "b" + String(repeating: "0", count: 31)

    // XCTest calls setUp off the main actor, where DictUpdate lives.
    override func setUp() async throws {
        await fresh()
    }

    private func fresh(install: String = "1.0-12") {
        dir = dir ?? FileManager.default.temporaryDirectory.appendingPathComponent("reci-dict-\(UUID().uuidString)")
        u = DictUpdate(dir: dir, install: install) { [unowned self] url in
            let name = String(url.split(separator: "/").last ?? "")
            gets.append(name)
            return site[name]
        }
        u.now = { [unowned self] in clock }
    }

    private func check() async -> DictUpdate.Result {
        let b = base
        return await u.check { b }
    }

    private func json(_ o: Any) -> Data { try! JSONSerialization.data(withJSONObject: o) }

    private func card(_ key: String, _ rank: Int, _ meanings: [String]) -> [String: Any] {
        ["key": key, "cyr": key, "pos": "noun", "meanings": meanings, "level": 1, "topic": "basic", "rank": rank]
    }

    // Puts a patch on the fake site, as build_patches.py would.
    @discardableResult
    private func publish(words: [[String: Any]] = [], removed: [String] = [], grammar: Any = NSNull(), forBase: String? = nil) -> Data {
        let data = json(["format": 1, "base": forBase ?? base, "words": words, "removed": removed, "grammar": grammar])
        site["patch-x.json"] = data
        site["manifest.json"] = json(["format": 1, "patches": [base: ["file": "patch-x.json", "sha256": SHA256.hex(data)]]])
        return data
    }

    func testSHA256() {
        XCTAssertEqual(SHA256.hex(Data()), "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        XCTAssertEqual(SHA256.hex(Data("abc".utf8)), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        let big = Data((0..<1000).map { UInt8($0 % 251) })
        XCTAssertEqual(SHA256.hex(big), "4e4c294b331f7a2099a379bec34b9f9fc03dc46ab465d998f4d683da53487e6d")
        // Fed in pieces across block boundaries, the hash is the same.
        var s = SHA256()
        s.update(big.prefix(63))
        s.update(big.dropFirst(63).prefix(70))
        s.update(big.dropFirst(133))
        XCTAssertEqual(s.finish(), SHA256.hex(big))
    }

    func testTheSharedCases() throws {
        let base = try JSONDecoder().decode([Word].self, from: json(fixture["base"]!))
        let grammar = try JSONDecoder().decode([GrammarTopic].self, from: json(fixture["grammar"]!))
        for c in fixture["cases"] as! [[String: Any]] {
            var p = c["patch"] as! [String: Any]
            p["base"] = "b"
            let patch = try XCTUnwrap(DictPatch.parse(json(p), base: "b"), c["name"] as! String)
            let words = patch.apply(to: base)
            XCTAssertEqual(words.map(\.key), c["keys"] as! [String], c["name"] as! String)
            for (k, m) in c["changed"] as! [String: [String]] {
                XCTAssertEqual(words.first { $0.key == k }?.meanings, m)
            }
            XCTAssertEqual((patch.grammar ?? grammar)[0].blocks[0].text, c["grammar"] as? String)
        }
    }

    func testTheBaseIdIsTheOneTheBuildGives() {
        let b = fixture["baseId"] as! [String: String]
        XCTAssertEqual(DictPatch.baseId(words: Data(b["words"]!.utf8), grammar: Data(b["grammar"]!.utf8)), b["id"])
    }

    func testAPatchIsFetchedKeptAndAppliedOnTheNextStart() async {
        publish(words: [card("кућа", 1, ["исправлено"])])
        let r = await check()
        XCTAssertEqual(r, .patch)
        XCTAssertEqual(gets, ["manifest.json", "patch-x.json"])
        fresh()
        XCTAssertEqual(u.stored()?.words.first?.meanings, ["исправлено"])
    }

    func testADayLaterOnlyTheManifestIsAskedAndAnUnchangedPatchIsNotFetched() async {
        publish(words: [card("кућа", 1, ["раз"])])
        _ = await check()
        var r = await check()
        XCTAssertEqual(r, .none)
        XCTAssertEqual(gets.count, 2)
        clock += 24 * 3600
        r = await check()
        XCTAssertEqual(r, .none)
        XCTAssertEqual(gets, ["manifest.json", "patch-x.json", "manifest.json"])
    }

    func testAWrongHashIsRefused() async {
        publish(words: [card("кућа", 1, ["подменено"])])
        site["patch-x.json"]!.append(0x20)
        let r = await check()
        XCTAssertEqual(r, .none)
        XCTAssertNil(u.stored())
    }

    func testAPatchForAnotherDictionaryIsRefused() async {
        publish(words: [card("кућа", 1, ["чужой"])], forBase: "c" + String(repeating: "0", count: 31))
        let r = await check()
        XCTAssertEqual(r, .none)
        XCTAssertNil(u.stored())
    }

    func testBrokenJsonWithTheRightHashIsRefused() async {
        let broken = Data("{\"format\":1,\"base\":\"\(base)\",\"words\":[{\"key\":".utf8)
        site["patch-x.json"] = broken
        site["manifest.json"] = json(["patches": [base: ["file": "patch-x.json", "sha256": SHA256.hex(broken)]]])
        let r = await check()
        XCTAssertEqual(r, .none)
        XCTAssertNil(u.stored())
    }

    func testAFileNameFromTheManifestCannotLeaveTheSite() async {
        for name in ["../../evil.json", "пач.json", "x.json\n", "x.json/../y.json"] {
            site["manifest.json"] = json(["patches": [base: ["file": name, "sha256": "0"]]])
            clock += 24 * 3600
            let r = await check()
            XCTAssertEqual(r, .none, name)
        }
        XCTAssertEqual(gets, Array(repeating: "manifest.json", count: 4))
    }

    func testAKeptPatchThatDidNotReachTheDiskIsFetchedAgain() async throws {
        publish(words: [card("кућа", 1, ["снова"])])
        _ = await check()
        try Data().write(to: dir.appendingPathComponent("patch.json"))
        clock += 24 * 3600
        let r = await check()
        XCTAssertEqual(r, .patch)
        XCTAssertEqual(gets, ["manifest.json", "patch-x.json", "manifest.json", "patch-x.json"])
        XCTAssertNotNil(u.stored())
    }

    func testANewDictionaryKeepsTheWayBack() throws {
        let words = Fixtures.words
        let old = Repo(store: Fixtures.tempStore(), words: words)
        old.step(1)
        let first = try XCTUnwrap(old.current()).key
        old.step(1)
        let second = try XCTUnwrap(old.current()).key
        old.step(1)
        let fresh = Repo(store: old.store, words: words.filter { $0.key != second })
        fresh.takePlace(of: old)
        fresh.step(-1)
        XCTAssertEqual(fresh.current()?.key, first)
    }

    func testATooLargePatchIsRefused() async {
        let big = Data(repeating: 0x20, count: DictUpdate.maxSize + 1)
        site["patch-x.json"] = big
        site["manifest.json"] = json(["patches": [base: ["file": "patch-x.json", "sha256": SHA256.hex(big)]]])
        let r = await check()
        XCTAssertEqual(r, .none)
        XCTAssertNil(u.stored())
    }

    func testNoNetworkTriesAgainAnHourLater() async {
        var r = await check()
        XCTAssertEqual(r, .none)
        publish(words: [card("кућа", 1, ["позже"])])
        clock += 1800
        r = await check()
        XCTAssertEqual(r, .none)
        XCTAssertEqual(gets, ["manifest.json"])
        clock += 1800
        r = await check()
        XCTAssertEqual(r, .patch)
    }

    func testNoPatchForThisDictionaryChangesNothing() async {
        site["manifest.json"] = json(["format": 1, "patches": [:] as [String: Any]])
        let r = await check()
        XCTAssertEqual(r, .none)
        XCTAssertEqual(gets, ["manifest.json"])
    }

    func testAPatchTakenBackReturnsTheBuiltInCards() async {
        publish(words: [card("кућа", 1, ["временно"])])
        _ = await check()
        site["manifest.json"] = json(["format": 1, "patches": [:] as [String: Any]])
        clock += 24 * 3600
        let r = await check()
        XCTAssertEqual(r, .builtIn)
        XCTAssertNil(u.stored())
    }

    func testAPatchKeptForAnotherInstallIsNotApplied() async {
        publish(words: [card("кућа", 1, ["старая сборка"])])
        _ = await check()
        fresh(install: "1.1-13")
        XCTAssertNil(u.stored())
    }

    func testTheBaseIsHashedOncePerInstall() async {
        var hashed = 0
        publish()
        _ = await u.check { hashed += 1; return self.base }
        clock += 24 * 3600
        _ = await u.check { hashed += 1; return self.base }
        XCTAssertEqual(hashed, 1)
    }

    func testOverlappingChecksFetchOnce() async {
        publish(words: [card("кућа", 1, ["раз"])])
        async let a = check()
        async let b = check()
        let both = await [a, b]
        XCTAssertEqual(both.filter { $0 == .patch }.count, 1)
        XCTAssertEqual(gets, ["manifest.json", "patch-x.json"])
    }

    func testAHangingNetworkLeavesTheMainActorFree() async {
        var release: CheckedContinuation<Void, Never>?
        publish(words: [card("кућа", 1, ["медленно"])])
        u.get = { [unowned self] url in
            await withCheckedContinuation { release = $0 }
            return site[String(url.split(separator: "/").last ?? "")]
        }
        let task = Task { await check() }
        while release == nil { await Task.yield() }
        // The main actor answers while the request hangs.
        XCTAssertNil(u.stored())
        release?.resume()
        release = nil
        while release == nil { await Task.yield() }
        release?.resume()
        let r = await task.value
        XCTAssertEqual(r, .patch)
    }
}
