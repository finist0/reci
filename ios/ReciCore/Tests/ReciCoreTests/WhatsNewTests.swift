import XCTest
@testable import ReciCore

// Which news the first start after an update shows.
final class WhatsNewTests: XCTestCase {
    private let entries = WhatsNew.parse(Data("""
        [
            {"version": "2.0", "items": [{"text": "future"}]},
            {"version": "1.10", "items": [{"text": "ten"}, {"text": "ten, web only", "only": ["web"]}]},
            {"version": "1.8", "items": [{"text": "eight, Android only", "only": ["android"]}]},
            {"version": "1.7", "items": [{"text": "seven"}, {"text": "seven, iPhone", "only": ["ios", "web"]}]}
        ]
        """.utf8), platform: "ios")

    private func versions(_ seen: String?, _ version: String, fresh: Bool = false) -> [String] {
        WhatsNew.pick(entries, version: version, seen: seen, fresh: fresh).map(\.version)
    }

    func testOnlyThePlatformsItemsAreKept() {
        XCTAssertEqual(entries.map(\.version), ["2.0", "1.10", "1.7"])
        XCTAssertEqual(entries[1].items, ["ten"])
        XCTAssertEqual(entries[2].items, ["seven", "seven, iPhone"])
    }

    func testAnUpdateShowsEveryVersionSinceTheLastSeenNewestFirst() {
        XCTAssertEqual(versions("1.6", "1.7"), ["1.7"])
        XCTAssertEqual(versions("1.6", "1.10"), ["1.10", "1.7"])
        XCTAssertEqual(versions("1.7", "1.10"), ["1.10"])
    }

    func testTheSameVersionAnOlderOneOrAVersionWithoutNewsShowNothing() {
        XCTAssertEqual(versions("1.7", "1.7"), [])
        XCTAssertEqual(versions("1.10", "1.7"), [])
        XCTAssertEqual(versions("1.7", "1.8"), [])
    }

    func testAFreshInstallShowsNothingButAnUpdateFromBeforeTheWindowDoes() {
        XCTAssertEqual(versions(nil, "1.10", fresh: true), [])
        XCTAssertEqual(versions(nil, "1.10"), ["1.10", "1.7"])
    }

    func testTheSharedFileHasNewsForTheIPhone() throws {
        let data = try Data(contentsOf: Fixtures.root.appendingPathComponent("app/app/src/main/assets/whatsnew.json"))
        let all = WhatsNew.parse(data, platform: "ios")
        XCTAssertFalse(all.isEmpty)
        XCTAssertTrue(all.allSatisfy { WhatsNew.compare($0.version, WhatsNew.before) > 0 })
    }
}
