import Foundation

// What the app does, summed since the last ping.
public struct Counts: Codable, Equatable {
    public var cards = 0
    public var answers: [String: Int] = [:]
    public var opened: [String: Int] = [:]

    // What was counted while a ping was on its way stays for the next one.
    func minus(_ sent: Counts) -> Counts {
        let less = { (now: [String: Int], sent: [String: Int]) in
            now.reduce(into: [String: Int]()) { out, kv in
                let n = kv.value - (sent[kv.key] ?? 0)
                if n > 0 { out[kv.key] = n }
            }
        }
        return Counts(cards: max(0, cards - sent.cards), answers: less(answers, sent.answers), opened: less(opened, sent.opened))
    }
}

// The daily ping, field for field what the server accepts (tests/telemetry.json).
public struct Ping: Codable, Equatable {
    public var id: String
    public var day: String
    public var platform: String
    public var app: String
    public var os: String
    public var locale: String
    public var cats: [String]
    public var mode: Int
    public var theme: Int
    public var latin: Bool
    public var lock: String
    public var learned: Int
    public var cards: Int?
    public var answers: [String: Int]?
    public var opened: [String: Int]?
}

public struct CardInfo: Codable, Equatable {
    public var key: String
    public var word: String
    public var problem: String
    public var latin: Bool
    public var stress: Int

    public init(key: String, word: String, problem: String, latin: Bool, stress: Int) {
        (self.key, self.word, self.problem, self.latin, self.stress) = (key, word, problem, latin, stress)
    }
}

// A message to the developer: an error in a card ("card") or anything else ("app").
public struct Message: Codable, Equatable {
    public var id: String
    public var kind: String
    public var platform: String
    public var app: String
    public var os: String
    public var text: String?
    public var email: String?
    public var card: CardInfo?
}

public struct TelemetryState: Codable {
    public var id = UUID().uuidString.lowercased()
    public var feedbackId = UUID().uuidString.lowercased()
    public var enabled = true
    public var noticeShown = false
    public var noticeClosed = false
    public var sentDay = ""
    public var triedAt: Double = 0
    public var counts = Counts()
    public var last: Ping? = nil
    public var email = ""
    public var queue: [Message] = []

    public init() {}

    enum CodingKeys: String, CodingKey {
        case id, feedbackId, enabled, noticeShown, noticeClosed, sentDay, triedAt, counts, last, email, queue
    }

    public init(from decoder: Decoder) throws {
        let d = TelemetryState()
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? d.id
        feedbackId = try c.decodeIfPresent(String.self, forKey: .feedbackId) ?? d.feedbackId
        enabled = try c.decodeIfPresent(Bool.self, forKey: .enabled) ?? d.enabled
        noticeShown = try c.decodeIfPresent(Bool.self, forKey: .noticeShown) ?? d.noticeShown
        noticeClosed = try c.decodeIfPresent(Bool.self, forKey: .noticeClosed) ?? d.noticeClosed
        sentDay = try c.decodeIfPresent(String.self, forKey: .sentDay) ?? d.sentDay
        triedAt = try c.decodeIfPresent(Double.self, forKey: .triedAt) ?? d.triedAt
        counts = try c.decodeIfPresent(Counts.self, forKey: .counts) ?? d.counts
        last = try c.decodeIfPresent(Ping.self, forKey: .last)
        email = try c.decodeIfPresent(String.self, forKey: .email) ?? d.email
        queue = try c.decodeIfPresent([Message].self, forKey: .queue) ?? d.queue
    }
}

// Anonymous daily statistics and messages to the developer, sent to the project's server. Kept in
// a file of its own, out of the progress file and the shared App Group, so that a progress file
// or a restored install never carries the same id.
// On the main actor, so that the checks before a request and the changes after its answer
// cannot interleave with another call.
@MainActor
public final class Telemetry {
    public static let server = "https://reci.finist.workers.dev"
    public static let privacy = "https://finist0.github.io/reci/web/privacy.html"
    static let retry: TimeInterval = 60 * 60

    public private(set) var state: TelemetryState
    public let file: URL
    // Answers an HTTP POST with its status, or -1 when there was no answer.
    public var post: @MainActor (String, Data) async -> Int
    public var now: () -> Date = Date.init
    public var calendar = Calendar.current

    public init(file: URL, post: @escaping @MainActor (String, Data) async -> Int) {
        self.file = file
        self.post = post
        state = (try? Data(contentsOf: file)).flatMap { try? JSONDecoder().decode(TelemetryState.self, from: $0) } ?? TelemetryState()
        save()
    }

    private func save() {
        try? FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        if let data = try? JSONEncoder().encode(state) { try? data.write(to: file, options: .atomic) }
    }

    public func count(_ name: String, _ sub: String? = nil) {
        guard state.enabled else { return }
        switch (name, sub) {
        case ("cards", _): state.counts.cards += 1
        case ("answers", let s?): state.counts.answers[s, default: 0] += 1
        case ("opened", let s?): state.counts.opened[s, default: 0] += 1
        default: return
        }
        save()
    }

    public func setEnabled(_ on: Bool) {
        state.enabled = on
        // Sums collected while statistics were off are never sent.
        if !on { state.counts = Counts() }
        save()
    }

    public func markNoticeShown() {
        guard !state.noticeShown else { return }
        state.noticeShown = true
        save()
    }

    public func closeNotice() {
        state.noticeClosed = true
        save()
    }

    public func localDay(_ d: Date) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: d)
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }

    private static func matches(_ s: String, _ pattern: String) -> Bool {
        s.range(of: pattern, options: .regularExpression) != nil
    }

    public func ping(day: String, app: String, os: String, locale: String, prefs: Prefs, learned: Int, counts: Counts? = nil) -> Ping {
        let c = counts ?? state.counts
        return Ping(
            id: state.id, day: day, platform: "ios", app: app, os: os,
            locale: Self.matches(locale, #"^[\w-]{1,40}$"#) ? locale : "other",
            cats: Array(prefs.categories.filter { Self.matches($0, #"^[\w:.-]{1,32}$"#) }.sorted().prefix(100)),
            mode: prefs.cardMode, theme: prefs.theme, latin: prefs.latinFirst,
            lock: prefs.lockMode == 1 ? "wallpaper" : "widget", learned: learned,
            cards: c.cards > 0 ? c.cards : nil, answers: c.answers.isEmpty ? nil : c.answers,
            opened: c.opened.isEmpty ? nil : c.opened
        )
    }

    private var sending = false

    // Sends today's ping once: when statistics are on, the notice about them has been shown and
    // nothing went out today. A failed attempt is retried an hour later at the earliest.
    @discardableResult
    public func sendPing(app: String, os: String, locale: String, prefs: Prefs, learned: Int) async -> Bool {
        let t = now()
        let day = localDay(t)
        if sending || !state.enabled || !state.noticeShown || state.sentDay == day { return false }
        if t.timeIntervalSince1970 - state.triedAt < Self.retry { return false }
        sending = true
        defer { sending = false }
        state.triedAt = t.timeIntervalSince1970
        save()
        let sent = state.counts
        let p = ping(day: day, app: app, os: os, locale: locale, prefs: prefs, learned: learned, counts: sent)
        guard let body = try? JSONEncoder().encode(p) else { return false }
        let status = await post(Self.server + "/ping", body)
        // A rejected ping would be rejected again: drop it with the sums, as if it was sent.
        guard (200..<300).contains(status) || status == 400 || status == 413 else { return false }
        state.sentDay = day
        state.counts = state.counts.minus(sent)
        if (200..<300).contains(status) { state.last = p }
        save()
        return (200..<300).contains(status)
    }

    // The server's check, so that a mistyped address is caught before the message is queued.
    public static func isEmail(_ s: String) -> Bool {
        matches(s, #"^[^\s@<>"]{1,64}@[^\s@<>"]{1,128}\.[^\s@<>"]{2,32}$"#)
    }

    private var flushing: Task<Void, Never>?

    // Sends the waiting messages in order. One that the server rejects is dropped; one that hit a
    // limit or no network waits for the next try. A call during a running flush waits for it and
    // then sends what is still waiting.
    public func flush() async {
        while let running = flushing { await running.value }
        guard !state.queue.isEmpty else { return }
        let task = Task {
            await drain()
            flushing = nil
        }
        flushing = task
        await task.value
    }

    private func drain() async {
        while let m = state.queue.first, let body = try? JSONEncoder().encode(m) {
            let status = await post(Self.server + "/feedback", body)
            if status == -1 || status == 429 || status >= 500 { return }
            state.queue.removeFirst()
            save()
        }
    }

    // Queues a message and tries to send it; true when it went out at once. It carries its own
    // id, so that it cannot be tied to the statistics of the same install.
    @discardableResult
    public func sendFeedback(kind: String, text: String, card: CardInfo?, email: String, app: String, os: String) async -> Bool {
        state.email = email
        // The server refuses control characters; pasted text may carry some.
        let clean = String(String.UnicodeScalarView(text.unicodeScalars.filter {
            !(0...8 ~= $0.value || $0.value == 0x0B || $0.value == 0x0C || 0x0E...0x1F ~= $0.value || $0.value == 0x7F)
        }))
        let m = Message(
            id: state.feedbackId, kind: kind, platform: "ios", app: app, os: os,
            text: clean.isEmpty ? nil : clean, email: email.isEmpty ? nil : email, card: card
        )
        state.queue.append(m)
        save()
        await flush()
        return !state.queue.contains(m)
    }
}
