import Foundation
import XCTest
@testable import ReciCore

// Loads tests/progress-sample.json, the file the Android and web tests load too, so a progress
// file saved by any version opens in the others.
final class BackupTests: XCTestCase {
    private let sample = try! Data(contentsOf: Fixtures.root.appendingPathComponent("tests/progress-sample.json"))
    private var json: [String: Any] { try! JSONSerialization.jsonObject(with: sample) as! [String: Any] }
    // Settings of one device or one session, which the Android app does not carry over either.
    private let local: Set<String> = ["currentKey", "todayDate", "todayCount", "shuffleSeed"]

    private func repo() -> Repo { Repo(store: Fixtures.tempStore(), words: Fixtures.words) }

    func testLoadsEveryWordAndSetting() throws {
        let r = repo()
        let n = try Backup.import(sample, into: r)
        let progress = json["progress"] as! [String: [String: Any]]
        XCTAssertEqual(n, progress.count)
        for (key, p) in progress {
            let got = r.store.progress[key]
            XCTAssertEqual(got?.state, p["state"] as? Int, key)
            XCTAssertEqual(got?.fav, p["fav"] as? Bool, key)
            XCTAssertEqual(got?.last, p["last"] as? Int, key)
            XCTAssertEqual(got?.showAt, p["showAt"] as? Int, key)
        }
        XCTAssertTrue(r.prefs.latinFirst)
        XCTAssertEqual(r.prefs.theme, 5)
        XCTAssertEqual(r.prefs.categories.sorted(), ["L2", "T:food", "X:note"])
        XCTAssertNil(r.prefs.currentKey)
    }

    func testSavedFileHasTheSameFields() throws {
        let r = repo()
        try Backup.import(sample, into: r)
        let out = try JSONSerialization.jsonObject(with: Backup.export(r)) as! [String: Any]
        XCTAssertEqual(out["app"] as? String, "reci")
        XCTAssertEqual(out["format"] as? Int, json["format"] as? Int)
        XCTAssertEqual(NSDictionary(dictionary: out["progress"] as! [String: Any]), NSDictionary(dictionary: json["progress"] as! [String: Any]))
        let saved = out["prefs"] as! [String: Any]
        for (k, v) in json["prefs"] as! [String: Any] where !local.contains(k) {
            if k == "categories" {
                XCTAssertEqual(Set(saved[k] as! [String]), Set(v as! [String]), k)
            } else {
                XCTAssertEqual(saved[k] as? NSObject, v as? NSObject, k)
            }
        }
    }

    func testRefusesAnotherKindOfFile() throws {
        let r = repo()
        try Backup.import(sample, into: r)
        XCTAssertThrowsError(try Backup.import(Data(#"{"hello": 1}"#.utf8), into: r))
        XCTAssertThrowsError(try Backup.import(Data("not json".utf8), into: r))
        XCTAssertEqual(r.store.progress.count, (json["progress"] as! [String: Any]).count)
    }

    func testSettingsAndProgressSurviveARestart() throws {
        let r = repo()
        r.step(1)
        let w = r.current()!
        r.answer(w, Mark.known, learned: true)
        let again = Repo(store: Store(dir: r.store.dir), words: Fixtures.words)
        XCTAssertEqual(again.progress(w).phase, Repo.learned)
        XCTAssertEqual(again.prefs.currentKey, w.key)
    }
}
