import Foundation
import ReciCore
import SwiftUI
import UIKit
import WidgetKit

// One showing of a card. Answering moves the word to its next stage, but the card on screen keeps
// the layout it was shown with; a later answer replaces the earlier one.
struct Showing {
    let id: String
    let key: String
    var recall: Bool
    let stage: String?
    var answered: Int?
    var revealed: Bool
    var covered: Bool
    let fresh: Bool
    var hintCounted = false
    var hint = false
    var mode: Int
}

// The repository and the settings for the views. Every change bumps `version`, which redraws
// the screens.
@MainActor
final class AppModel: ObservableObject {
    let repo: Repo
    @Published private(set) var version = 0
    @Published private(set) var showing: Showing?
    @Published var learnedToast = false
    @Published var sentToast: String?
    private var toastShown = 0
    let speech = Speech()
    let telemetry: Telemetry

    init() {
        let dec = JSONDecoder()
        let words = Bundle.main.url(forResource: "words", withExtension: "json")
            .flatMap { try? Data(contentsOf: $0) }.flatMap { try? dec.decode([Word].self, from: $0) } ?? []
        let topics = Bundle.main.url(forResource: "grammar", withExtension: "json")
            .flatMap { try? Data(contentsOf: $0) }.flatMap { try? dec.decode([GrammarTopic].self, from: $0) } ?? []
        // UI tests start from a fresh install.
        if ProcessInfo.processInfo.arguments.contains("-uitest-reset") { try? FileManager.default.removeItem(at: LockDeck.dir) }
        repo = Repo(store: Store(dir: LockDeck.dir), words: words, topics: topics)
        LockDeck.writeDeck(words)
        telemetry = Telemetry(file: Self.telemetryFile(), post: Self.transport())
    }

    // Outside the App Group the widgets share and outside the backup, so that the statistics id
    // never moves to another phone with a restored backup.
    private static func telemetryFile() -> URL {
        var dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        if ProcessInfo.processInfo.arguments.contains("-uitest-reset") {
            try? FileManager.default.removeItem(at: dir.appendingPathComponent("telemetry"))
        }
        try? FileManager.default.createDirectory(at: dir.appendingPathComponent("telemetry"), withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        dir.appendPathComponent("telemetry")
        try? dir.setResourceValues(values)
        return dir.appendingPathComponent("telemetry.json")
    }

    // UI tests never reach the real server: "-uitest" or "-uitest-reset" answer every request at
    // once, "-uitest-offline" as if there was no network.
    private static func transport() -> @MainActor (String, Data) async -> Int {
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-uitest-offline") { return { _, _ in -1 } }
        if args.contains("-uitest") || args.contains("-uitest-reset") { return { _, _ in 204 } }
        return { url, body in
            var req = URLRequest(url: URL(string: url)!, timeoutInterval: 5)
            req.httpMethod = "POST"
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = body
            guard let answer = try? await URLSession.shared.data(for: req) else { return -1 }
            return (answer.1 as? HTTPURLResponse)?.statusCode ?? -1
        }
    }

    // UI tests on a slow simulator wait out the closing sheet and keyboard before they look,
    // and a toast of three seconds is gone by then.
    private static let toastTime: UInt64 =
        ProcessInfo.processInfo.arguments.contains { $0.hasPrefix("-uitest") } ? 15_000_000_000 : 3_000_000_000

    private var appVersion: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown" }
    private var osName: String { "iOS " + UIDevice.current.systemVersion }

    // Sends the ping and the waiting messages when it is time; cheap to call on every start.
    func sendStats() {
        let learned = repo.words.reduce(0) { $0 + (repo.progress($1).state == Mark.known ? 1 : 0) }
        Task { @MainActor in
            await telemetry.sendPing(app: appVersion, os: osName, locale: Locale.current.identifier(.bcp47), prefs: prefs, learned: learned)
            await telemetry.flush()
            version += 1
        }
    }

    func noticeShown() {
        telemetry.markNoticeShown()
        sendStats()
    }

    func closeNotice() {
        telemetry.closeNotice()
        changed()
    }

    func setStatsEnabled(_ on: Bool) {
        telemetry.setEnabled(on)
        changed()
    }

    func countAnswer(_ w: Word, _ a: Int, learned: Bool) {
        let what = w.isArticle ? (a == Mark.known ? "hide" : "later") : learned ? "long" : a == Mark.known ? "know" : "dontknow"
        telemetry.count("answers", what)
    }

    func sendFeedback(kind: String, text: String, card: CardInfo?, email: String) {
        Task { @MainActor in
            let sent = await telemetry.sendFeedback(kind: kind, text: text, card: card, email: email, app: appVersion, os: osName)
            sentToast = sent ? "Спасибо! Сообщение отправлено" : "Сообщение уйдёт, когда появится интернет"
            toastShown += 1
            let shown = toastShown
            changed()
            try? await Task.sleep(nanoseconds: Self.toastTime)
            // A later toast keeps its own three seconds.
            if shown == toastShown { sentToast = nil }
        }
    }

    var prefs: Prefs { repo.prefs }
    var palette: Palette { palettes.indices.contains(prefs.theme) ? palettes[prefs.theme] : palettes[2] }

    func changed() {
        version += 1
        // Reloads asked for while the app is in front do not count against the widgets' budget.
        if UIApplication.shared.applicationState == .active { WidgetCenter.shared.reloadTimelines(ofKind: "card") }
    }

    func set(_ f: (inout Prefs) -> Void) {
        var p = repo.prefs
        f(&p)
        repo.prefs = p
        syncShowing()
        changed()
    }

    // The widgets may have answered cards while the app was in the background.
    func reload() {
        repo.reloadFromStore()
        syncShowing()
        changed()
        sendStats()
    }

    // MARK: The card screen

    private func stageLabel(_ w: Word) -> String? {
        if w.isArticle { return nil }
        let p = repo.shownProgress(w)
        let what = prefs.cardMode == 2 ? "перевод" : "слово"
        if repo.isDue(p) { return "Проверка: вспомните \(what)" }
        if p.phase == Repo.recall { return "Вспомните \(what) · «Знаю» \(p.streak) из 3" }
        if p.phase == Repo.learned { return "Выучено" }
        if p.state == Mark.dontKnow || p.state == Mark.unsure { return "Ещё раз: запомните слово" }
        return "Новое слово"
    }

    // Makes sure the card on screen is picked and counted as shown once.
    func syncShowing() {
        if repo.current() == nil && !repo.exhausted { repo.step(1) }
        guard let w = repo.current() else {
            showing = nil
            return
        }
        let id = "\(w.key)#\(prefs.cardCount)"
        if showing?.id != id {
            let answered = repo.answerOf(w)
            let p = repo.shownProgress(w)
            showing = Showing(
                id: id, key: w.key, recall: repo.asksRecall(w), stage: stageLabel(w), answered: answered,
                revealed: answered != nil, covered: prefs.cardMode == 2 && !w.isArticle,
                fresh: !w.isArticle && p.phase <= Repo.intro && p.streak == 0, mode: prefs.cardMode
            )
            if repo.markShown(w) { telemetry.count("cards") }
        } else if var s = showing, s.mode != prefs.cardMode {
            // A card mode chosen in the settings applies to the card on screen as well.
            s.mode = prefs.cardMode
            s.recall = repo.asksRecall(w)
            s.revealed = s.answered != nil
            s.covered = prefs.cardMode == 2 && !w.isArticle
            showing = s
        }
    }

    func step(_ delta: Int) {
        repo.step(delta)
        syncShowing()
        // Speech has to start from the tap; a word to recall would be given away.
        if delta > 0, prefs.autoSpeak, let w = repo.current(), !w.isArticle, !repo.asksRecall(w) {
            speech.speak(w.cyr)
        }
        changed()
    }

    func reveal() {
        showing?.revealed = true
        changed()
    }

    func uncover() {
        showing?.covered = false
        changed()
    }

    func closeHint() {
        showing?.hint = false
        set { $0.hintDone = true }
    }

    func answer(_ a: Int, learned: Bool) {
        guard var s = showing, let w = repo.word(s.key) else { return }
        if repo.answer(w, a, learned: learned) { countAnswer(w, a, learned: learned) }
        s.answered = a
        s.revealed = true
        if learned {
            s.hint = false
            showing = s
            set { $0.hintDone = true }
            learnedToast = true
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 2_500_000_000)
                self.learnedToast = false
            }
            step(1)
            return
        }
        if a == Mark.known && s.fresh && !s.hintCounted {
            s.hintCounted = true
            var p = repo.prefs
            s.hint = p.hintAfterNewKnown()
            repo.prefs = p
        }
        showing = s
        changed()
    }

    func toggleFav(_ w: Word) {
        repo.toggleFav(w)
        changed()
    }
}
