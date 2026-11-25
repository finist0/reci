import Foundation
import XCTest
@testable import ReciCore

// When a start asks for the languages and offers the lock screen card, by the scenarios of
// tests/intro.json, which app/src/test/.../IntroTest.kt runs too.
final class IntroTests: XCTestCase {
    private func run(_ scenario: [String: Any]) {
        let name = scenario["name"] as! String
        var s = Intro.begin(fresh: scenario["fresh"] as! Bool)
        for (i, step) in (scenario["steps"] as! [[String: Any]]).enumerated() {
            let now = Date(timeIntervalSince1970: ((step["day"] as? NSNumber)?.doubleValue ?? 0) * 24 * 60 * 60)
            switch step["do"] as? String {
            case "pair": s = Intro.chosen(s)
            case "later": s = Intro.postponed(s, now: now)
            case "setup": s = Intro.done(s)
            default:
                let (show, after) = Intro.next(s, now: now, works: step["works"] as? Bool ?? false, news: step["news"] as? Bool ?? false)
                XCTAssertEqual("\(show)", step["expect"] as? String, "\(name), step \(i + 1)")
                s = after
            }
        }
    }

    func testScenarios() {
        for scenario in Fixtures.json("tests/intro.json") as! [[String: Any]] { run(scenario) }
    }
}
