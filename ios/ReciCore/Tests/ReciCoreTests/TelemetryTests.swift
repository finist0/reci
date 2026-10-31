import Foundation
import XCTest
@testable import ReciCore

// The daily statistics and the message queue, with the server faked. The fields are checked
// against tests/telemetry.json, the list the Android and web tests check too.
@MainActor
final class TelemetryTests: XCTestCase {
    private var posts: [(url: String, body: [String: Any])] = []
    private var reply: (String) -> Int = { _ in 204 }
    private var clock = DateComponents(calendar: Calendar.current, year: 2026, month: 11, day: 2, hour: 12).date!
    private var t: Telemetry!
    private let fields = Fixtures.json("tests/telemetry.json") as! [String: Any]

    // XCTest calls setUp off the main actor, where Telemetry lives.
    override func setUp() async throws {
        await fresh()
    }

    private func fresh() {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent("reci-telemetry-\(UUID().uuidString)/telemetry.json")
        t = Telemetry(file: file) { [unowned self] url, body in
            posts.append((url, try! JSONSerialization.jsonObject(with: body) as! [String: Any]))
            return reply(url)
        }
        t.now = { [unowned self] in clock }
        t.markNoticeShown()
    }

    private func names(_ path: String...) -> [String] {
        var o: Any = fields
        for k in path { o = (o as! [String: Any])[k]! }
        return o as! [String]
    }

    private func ping(learned: Int = 0, prefs: Prefs = Prefs()) async -> Bool {
        await t.sendPing(app: "0.12", os: "iOS 18.6", locale: "ru-RS", prefs: prefs, learned: learned)
    }

    func testANewInstallHasTwoDifferentLowercaseIdsAndStatisticsOn() {
        XCTAssertNotNil(t.state.id.range(of: #"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"#, options: .regularExpression))
        XCTAssertNotEqual(t.state.id, t.state.feedbackId)
        XCTAssertTrue(t.state.enabled)
    }

    func testThePingCarriesOnlyTheFieldsTheServerAccepts() async {
        t.count("cards")
        t.count("answers", "know")
        t.count("opened", "search")
        var prefs = Prefs()
        prefs.lockMode = 1
        let sent = await ping(learned: 7, prefs: prefs)
        XCTAssertTrue(sent)
        let p = posts[0].body
        XCTAssertTrue(posts[0].url.hasSuffix("/ping"))
        let allowed = Set(names("ping", "required") + names("ping", "optional"))
        XCTAssertEqual(Set(p.keys).subtracting(allowed), [])
        for k in names("ping", "required") { XCTAssertNotNil(p[k], k) }
        XCTAssertEqual(Set((p["answers"] as! [String: Int]).keys).subtracting(names("answers")), [])
        XCTAssertTrue(names("platforms").contains(p["platform"] as! String))
        XCTAssertTrue(names("lock").contains(p["lock"] as! String))
        XCTAssertEqual(p["day"] as? String, "2026-11-02")
        XCTAssertEqual(p["platform"] as? String, "ios")
        XCTAssertEqual(p["lock"] as? String, "wallpaper")
        XCTAssertEqual(p["cards"] as? Int, 1)
        XCTAssertEqual(p["learned"] as? Int, 7)
        XCTAssertEqual(p["answers"] as? [String: Int], ["know": 1])
        XCTAssertEqual(p["opened"] as? [String: Int], ["search": 1])
    }

    func testAnEmptyDayCarriesNoSums() async {
        let sent = await ping()
        XCTAssertTrue(sent)
        XCTAssertNil(posts[0].body["cards"])
        XCTAssertNil(posts[0].body["answers"])
    }

    func testOnePingADayAndTheSumsStartAgainAfterIt() async {
        t.count("cards")
        var sent = await ping()
        XCTAssertTrue(sent)
        XCTAssertEqual(t.state.counts, Counts())
        t.count("cards")
        clock += 2 * 3600
        sent = await ping()
        XCTAssertFalse(sent)
        clock += 24 * 3600
        sent = await ping()
        XCTAssertTrue(sent)
        XCTAssertEqual(posts.count, 2)
        XCTAssertEqual(posts[1].body["day"] as? String, "2026-11-03")
        XCTAssertEqual(posts[1].body["cards"] as? Int, 1)
    }

    func testNothingIsSentOrCollectedWhileStatisticsAreOff() async {
        t.setEnabled(false)
        t.count("cards")
        var sent = await ping()
        XCTAssertFalse(sent)
        XCTAssertTrue(posts.isEmpty)
        t.setEnabled(true)
        sent = await ping()
        XCTAssertTrue(sent)
        XCTAssertNil(posts[0].body["cards"])
    }

    func testNothingIsSentBeforeTheNoticeWasShown() async {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent("reci-telemetry-\(UUID().uuidString)/telemetry.json")
        t = Telemetry(file: file) { [unowned self] url, body in
            posts.append((url, [:]))
            return reply(url)
        }
        var sent = await ping()
        XCTAssertFalse(sent)
        t.markNoticeShown()
        sent = await ping()
        XCTAssertTrue(sent)
    }

    func testAFailedPingKeepsTheSumsAndWaitsAnHour() async {
        reply = { _ in -1 }
        t.count("cards")
        var sent = await ping()
        XCTAssertFalse(sent)
        XCTAssertEqual(t.state.counts.cards, 1)
        reply = { _ in 204 }
        clock += 1800
        sent = await ping()
        XCTAssertFalse(sent)
        clock += 1800
        sent = await ping()
        XCTAssertTrue(sent)
        XCTAssertEqual(posts[1].body["cards"] as? Int, 1)
    }

    func testAPingOverTheDailyLimitIsTriedAgainLater() async {
        reply = { _ in 503 }
        t.count("cards")
        let sent = await ping()
        XCTAssertFalse(sent)
        XCTAssertEqual(t.state.sentDay, "")
        XCTAssertEqual(t.state.counts.cards, 1)
    }

    func testCountsMadeWhileAPingIsOnItsWayAreKept() async {
        t.count("cards")
        reply = { [unowned self] _ in
            t.count("cards")
            t.count("answers", "know")
            return 204
        }
        let sent = await ping()
        XCTAssertTrue(sent)
        XCTAssertEqual(posts[0].body["cards"] as? Int, 1)
        XCTAssertEqual(t.state.counts, Counts(cards: 1, answers: ["know": 1], opened: [:]))
    }

    func testOverlappingPingsSendOnce() async {
        async let a = ping()
        async let b = ping()
        let both = await [a, b]
        XCTAssertEqual(both.filter { $0 }.count, 1)
        XCTAssertEqual(posts.count, 1)
    }

    func testARejectedPingIsNotSentAgainThatDay() async {
        reply = { _ in 400 }
        t.count("cards")
        let sent = await ping()
        XCTAssertFalse(sent)
        XCTAssertEqual(t.state.sentDay, "2026-11-02")
        XCTAssertEqual(t.state.counts, Counts())
        XCTAssertNil(t.state.last)
    }

    func testTheStateSurvivesARestart() async {
        t.count("cards")
        let again = Telemetry(file: t.file) { _, _ in 204 }
        XCTAssertEqual(again.state.id, t.state.id)
        XCTAssertEqual(again.state.counts.cards, 1)
    }

    func testAMessageGoesOutWithItsOwnIdAndOnlyTheAcceptedFields() async {
        let card = CardInfo(key: "бити", word: "бити", problem: "stress", latin: false, stress: 1)
        let sent = await t.sendFeedback(kind: "card", text: "", card: card, email: "me@example.org", app: "0.12", os: "iOS 18.6")
        XCTAssertTrue(sent)
        let m = posts[0].body
        XCTAssertTrue(posts[0].url.hasSuffix("/feedback"))
        XCTAssertEqual(Set(m.keys).subtracting(names("feedback", "required") + names("feedback", "optional")), [])
        XCTAssertEqual(Set((m["card"] as! [String: Any]).keys).subtracting(names("feedback", "card")), [])
        XCTAssertTrue(names("feedback", "problems", "word").contains(card.problem))
        XCTAssertEqual(m["id"] as? String, t.state.feedbackId)
        XCTAssertNotEqual(m["id"] as? String, t.state.id)
        XCTAssertNil(m["text"])
        XCTAssertEqual(m["email"] as? String, "me@example.org")
        XCTAssertEqual(t.state.email, "me@example.org")
        XCTAssertTrue(t.state.queue.isEmpty)
    }

    func testWithoutNetworkAMessageWaitsAndGoesOutLater() async {
        reply = { _ in -1 }
        var sent = await t.sendFeedback(kind: "app", text: "Привет", card: nil, email: "", app: "0.12", os: "iOS 18.6")
        XCTAssertFalse(sent)
        XCTAssertEqual(Telemetry(file: t.file) { _, _ in 204 }.state.queue.count, 1)
        reply = { _ in 204 }
        await t.flush()
        sent = t.state.queue.isEmpty
        XCTAssertTrue(sent)
        XCTAssertEqual(posts.count, 2)
    }

    func testAMessageOverTheLimitWaitsARejectedOneIsDropped() async {
        reply = { _ in 429 }
        await t.sendFeedback(kind: "app", text: "один", card: nil, email: "", app: "0.12", os: "iOS 18.6")
        XCTAssertEqual(t.state.queue.count, 1)
        reply = { _ in 400 }
        await t.flush()
        XCTAssertTrue(t.state.queue.isEmpty)
    }

    func testMessagesGoOutInOrder() async {
        reply = { _ in -1 }
        await t.sendFeedback(kind: "app", text: "первое", card: nil, email: "", app: "0.12", os: "iOS 18.6")
        await t.sendFeedback(kind: "app", text: "второе", card: nil, email: "", app: "0.12", os: "iOS 18.6")
        posts.removeAll()
        reply = { _ in 204 }
        await t.flush()
        XCTAssertEqual(posts.map { $0.body["text"] as? String }, ["первое", "второе"])
    }

    func testControlCharactersAreDroppedFromAMessage() async {
        await t.sendFeedback(kind: "app", text: "a\u{0C}b\nc\u{00}", card: nil, email: "", app: "0.12", os: "iOS 18.6")
        XCTAssertEqual(posts[0].body["text"] as? String, "ab\nc")
    }

    func testAMessageSentDuringARunningFlushGoesOutToo() async {
        var release: CheckedContinuation<Void, Never>?
        reply = { _ in 204 }
        t.post = { [unowned self] url, body in
            posts.append((url, try! JSONSerialization.jsonObject(with: body) as! [String: Any]))
            if posts.count == 1 { await withCheckedContinuation { release = $0 } }
            return 204
        }
        async let first = t.sendFeedback(kind: "app", text: "первое", card: nil, email: "", app: "0.12", os: "iOS 18.6")
        await Task.yield()
        async let second = t.sendFeedback(kind: "app", text: "второе", card: nil, email: "", app: "0.12", os: "iOS 18.6")
        await Task.yield()
        release?.resume()
        let both = await [first, second]
        XCTAssertEqual(both, [true, true])
        XCTAssertEqual(posts.map { $0.body["text"] as? String }, ["первое", "второе"])
    }

    func testMessagesAreSentWithStatisticsOff() async {
        t.setEnabled(false)
        let sent = await t.sendFeedback(kind: "app", text: "Привет", card: nil, email: "", app: "0.12", os: "iOS 18.6")
        XCTAssertTrue(sent)
    }

    func testTheEmailCheckMatchesTheServer() {
        for ok in ["a@b.cd", "me.name+tag@mail.example.org"] { XCTAssertTrue(Telemetry.isEmail(ok), ok) }
        for bad in ["", "a@b", "a b@c.de", "@c.de", "a@.de", "<a>@c.de"] { XCTAssertFalse(Telemetry.isEmail(bad), bad) }
    }

    func testTheProgressFileCarriesNoStatisticsIds() throws {
        let repo = Repo(store: Fixtures.tempStore(), words: Fixtures.words, topics: [])
        let text = String(decoding: try Backup.export(repo), as: UTF8.self)
        XCTAssertFalse(text.contains(t.state.id))
        XCTAssertFalse(text.contains(t.state.feedbackId))
    }
}
