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
    var stage: String?
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
    private(set) var repo: Repo
    @Published private(set) var version = 0
    @Published private(set) var showing: Showing?
    @Published var learnedToast = false
    @Published var sentToast: String?
    private var toastShown = 0
    let speech = Speech()
    let telemetry: Telemetry
    let dict: DictUpdate
    let crash: CrashReport
    // The report of the last crash while the user has not answered whether to send it.
    @Published private(set) var crashOffer: String?
    private var crashWatcher: CrashWatcher?
    // The news of an update, shown once over the main screen until closed.
    @Published var news: [WhatsNew.Entry] = []
    // What a first start shows over the cards (Intro), kept between starts in introState.
    @Published private(set) var intro: Intro.Show = .none
    // News or a crash report came up while the app is in front: the offer waits until the app
    // comes to the front again with them closed.
    private var held = false

    init() {
        let crash = CrashReport(dir: FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("crash", isDirectory: true))
        self.crash = crash
        // UI tests start from a fresh install.
        if ProcessInfo.processInfo.arguments.contains("-uitest-reset") {
            try? FileManager.default.removeItem(at: LockDeck.dir)
            LockDeck.wallpaperDrawn = nil
            try? FileManager.default.removeItem(at: Dict.dir)
            UserDefaults.standard.removeObject(forKey: Self.newsSeen)
            for k in Self.introKeys { UserDefaults.standard.removeObject(forKey: k) }
            crash.clear()
        }
        // UI tests cannot crash the app on purpose; "-uitest-crash" stands for a crash at the last start.
        if ProcessInfo.processInfo.arguments.contains("-uitest-crash") {
            crash.keep("2026-11-02T10:00:00Z, build 14\nexception type 1\nsignal 11")
        }
        LockDeck.loadTables()
        dict = Dict.updater()
        let store = Store(dir: LockDeck.dir)
        let deck = store.prefs.deck
        let (words, topics) = Dict.load(deck: deck, patch: deck.isFirst ? dict.stored() : nil)
        repo = Repo(store: store, words: words, topics: topics)
        LockDeck.writeDeck(words, deck: deck)
        telemetry = Telemetry(file: Self.telemetryFile(), post: Self.transport())
        crashOffer = crash.pending
        Self.beginIntro(fresh: UserDefaults.standard.string(forKey: Self.newsSeen) == nil && repo.store.progress.isEmpty)
        news = Self.checkNews(fresh: repo.store.progress.isEmpty)
        if !introState.pairAsked { intro = .pair }
        checkIntro()
        crashWatcher = CrashWatcher { [weak self] text in
            Task { @MainActor in
                guard let self else { return }
                self.crash.keep(text)
                self.crashOffer = self.crash.pending
                // The report comes after the offer was checked: it waits for the next start.
                self.held = true
                if self.intro == .offer { self.intro = .none }
            }
        }
        speech.deck = deck
    }

    private static let newsSeen = "whatsNewSeen"

    // The news after the version seen last; the running version counts as seen from now on.
    private static func checkNews(fresh: Bool) -> [WhatsNew.Entry] {
        var version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        var seen = UserDefaults.standard.string(forKey: newsSeen)
        var fresh = fresh
        // UI tests: "-uitest-whatsnew" stands for an update from before the window to a version past all the news.
        if ProcessInfo.processInfo.arguments.contains("-uitest-whatsnew") {
            (version, seen, fresh) = ("999", WhatsNew.before, false)
        }
        guard !version.isEmpty, let url = Bundle.main.url(forResource: "whatsnew", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return [] }
        UserDefaults.standard.set(version, forKey: newsSeen)
        return WhatsNew.pick(WhatsNew.parse(data, platform: "ios"), version: version, seen: seen, fresh: fresh)
    }

    private static let introKeys = ["introPairAsked", "lockOfferLeft", "lockOfferAt"]

    // Once per install, before the first news mark the app as seen: a phone without them is a new
    // user, one with them an update from a version without the intro. UI tests start on the cards
    // unless they ask for the intro with "-uitest-intro".
    private static func beginIntro(fresh: Bool) {
        let args = ProcessInfo.processInfo.arguments
        if args.contains(where: { $0.hasPrefix("-uitest") }) && !args.contains("-uitest-intro") {
            save(Intro.State(pairAsked: true, offerLeft: 0, offerAt: .distantPast))
        } else if UserDefaults.standard.object(forKey: "lockOfferLeft") == nil {
            save(Intro.begin(fresh: fresh))
        }
    }

    private static func save(_ s: Intro.State) {
        UserDefaults.standard.set(s.pairAsked, forKey: "introPairAsked")
        UserDefaults.standard.set(s.offerLeft, forKey: "lockOfferLeft")
        UserDefaults.standard.set(s.offerAt.timeIntervalSince1970, forKey: "lockOfferAt")
    }

    private var introState: Intro.State {
        get {
            let d = UserDefaults.standard
            return Intro.State(pairAsked: d.object(forKey: "introPairAsked") as? Bool ?? true, offerLeft: d.integer(forKey: "lockOfferLeft"),
                               offerAt: Date(timeIntervalSince1970: d.double(forKey: "lockOfferAt")))
        }
        set { Self.save(newValue) }
    }

    // The card works once iOS shows the card widget or Card Wallpaper has drawn it.
    func checkIntro() {
        guard intro == .none, introState.offerLeft > 0 else { return }
        held = held || !news.isEmpty || crashOffer != nil
        Task { @MainActor in
            let works = await Stats.widgets()?.lock.contains("card") == true || LockDeck.wallpaperDrawn != nil
            guard intro == .none else { return }
            let (show, s) = Intro.next(introState, now: Date(), works: works, news: held)
            introState = s
            intro = show
        }
    }

    func leftScreen() {
        held = !news.isEmpty || crashOffer != nil
    }

    // The choice stays on screen until the cards of the pair chosen are in, so that no card of
    // the guess shows and counts meanwhile.
    func choosePair(_ id: String) {
        let finish: @MainActor () -> Void = { [weak self] in
            guard let self else { return }
            self.introState = Intro.chosen(self.introState)
            self.intro = .none
            self.checkIntro()
        }
        if id == deck.id { finish() } else { switchDeck(id, then: finish) }
    }

    func postponeLockOffer() {
        introState = Intro.postponed(introState, now: Date())
        intro = .none
    }

    func setUpLock(mode: Int) {
        introState = Intro.done(introState)
        intro = .none
        set { $0.lockMode = mode }
    }

    func answerCrash(send: Bool) {
        if send, let text = crashOffer { sendFeedback(kind: "crash", text: text, card: nil, email: "") }
        crash.clear()
        crashOffer = nil
    }

    // Where the app runs, sent with every message and crash report so that a card can be seen as
    // the user saw it. No words, answers or progress.
    func environment() -> [String: String] {
        var e = Env.of(prefs)
        let size = UIScreen.main.bounds.size
        e["device"] = Self.deviceModel
        e["idiom"] = UIDevice.current.userInterfaceIdiom == .pad ? "pad" : "phone"
        e["screen"] = "\(Int(size.width))x\(Int(size.height)) pt"
        e["scale"] = "\(UIScreen.main.scale)"
        e["font"] = UIApplication.shared.preferredContentSizeCategory.rawValue
        e["dark"] = "\(UITraitCollection.current.userInterfaceStyle == .dark)"
        e["locale"] = Locale.current.identifier(.bcp47)
        e["dict"] = dict.describe
        return e.mapValues { String($0.prefix(120)) }
    }

    private static var deviceModel: String {
        if let sim = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] { return sim + " simulator" }
        var u = utsname()
        uname(&u)
        return withUnsafeBytes(of: &u.machine) { String(decoding: $0.prefix { $0 != 0 }, as: UTF8.self) }
    }

    // Looks for a dictionary patch now and then; a new one is built off the main actor and swapped
    // in, while the cards keep coming from the dictionary in use.
    func checkDictionary() {
        guard deck.isFirst else { return }
        Task { @MainActor in
            let result = await dict.check { Dict.baseId() }
            guard result != .none else { return }
            let kept = dict.kept
            let first = Decks.deck(Decks.first)
            let (words, topics) = await Task.detached { Dict.load(deck: first, patch: kept.flatMap { DictPatch.read($0.file, base: $0.base) }) }.value
            // The patch is kept for the first deck; another one may be in use by now.
            guard deck.isFirst, !switching else { return }
            await Task.detached { LockDeck.writeDeck(words, deck: first) }.value
            let fresh = Repo(store: repo.store, words: words, topics: topics)
            fresh.takePlace(of: repo)
            repo = fresh
            syncShowing()
            changed()
            WidgetCenter.shared.reloadTimelines(ofKind: "card")
        }
    }

    // The file moves from the app's own directory, where versions up to 1.7 kept it, to the App
    // Group, where the widgets find it.
    private static func telemetryFile() -> URL {
        let old = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("telemetry/telemetry.json")
        let file = Stats.file
        if ProcessInfo.processInfo.arguments.contains("-uitest-reset") {
            try? FileManager.default.removeItem(at: old)
            try? FileManager.default.removeItem(at: file)
        }
        if old.path != file.path && FileManager.default.fileExists(atPath: old.path) {
            try? FileManager.default.removeItem(at: file)
            try? FileManager.default.moveItem(at: old, to: file)
        }
        return file
    }

    // UI tests never reach the real server: "-uitest" or "-uitest-reset" answer every request at
    // once, "-uitest-offline" as if there was no network.
    private static func transport() -> @MainActor (String, Data) async -> Int {
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-uitest-offline") { return { _, _ in -1 } }
        if args.contains("-uitest") || args.contains("-uitest-reset") { return { _, _ in 204 } }
        return Stats.post
    }

    // UI tests on a slow simulator wait out the closing sheet and keyboard before they look,
    // and a toast of three seconds is gone by then.
    private static let toastTime: UInt64 =
        ProcessInfo.processInfo.arguments.contains { $0.hasPrefix("-uitest") } ? 15_000_000_000 : 3_000_000_000

    private var appVersion: String { Stats.appVersion }
    private var osName: String { Stats.osName }

    // Sends the ping and the waiting messages when it is time; cheap to call on every start.
    func sendStats() {
        let learned = Stats.learned(repo)
        // The widgets may have sent today's ping meanwhile; the settings show it.
        telemetry.reload()
        Task { @MainActor in
            await Stats.send(telemetry, prefs: prefs, learned: learned)
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
            let sent = await telemetry.sendFeedback(kind: kind, text: text, card: card, email: email, app: appVersion, os: osName,
                                                    env: environment())
            sentToast = sent ? t("Спасибо! Сообщение отправлено") : t("Сообщение уйдёт, когда появится интернет")
            toastShown += 1
            let shown = toastShown
            changed()
            try? await Task.sleep(nanoseconds: Self.toastTime)
            // A later toast keeps its own three seconds.
            if shown == toastShown { sentToast = nil }
        }
    }

    var prefs: Prefs { repo.prefs }
    var deck: Deck { repo.prefs.deck }

    // Dates and system texts in the language of the interface.
    var locale: Locale {
        switch I18n.lang {
        case "sr": return Locale(identifier: prefs.latinFirst ? "sr_Latn_RS" : "sr_RS")
        case "en": return Locale(identifier: "en_US")
        default: return Locale(identifier: "ru_RU")
        }
    }

    // Set while the cards of another deck load.
    private(set) var switching = false

    // Another deck: its cards, its progress and its place in the cards, the interface in its
    // native language unless another one is chosen, and the voice of the language it teaches.
    // The cards load off the main actor; the old ones stay until the new ones are in.
    func switchDeck(_ id: String, then done: (@MainActor () -> Void)? = nil) {
        guard id != deck.id, !switching else { return }
        let d = Decks.deck(id)
        let kept = d.isFirst ? dict.kept : nil
        switching = true
        Task { @MainActor in
            let (words, topics) = await Task.detached { Dict.load(deck: d, patch: kept.flatMap { DictPatch.read($0.file, base: $0.base) }) }.value
            await Task.detached { LockDeck.writeDeck(words, deck: d) }.value
            repo.store.switchDeck(d.id)
            repo = Repo(store: repo.store, words: words, topics: topics)
            speech.deck = d
            showing = nil
            switching = false
            syncShowing()
            changed()
            WidgetCenter.shared.reloadTimelines(ofKind: "card")
            done?()
        }
    }
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
        checkDictionary()
        checkIntro()
    }

    // MARK: The card screen

    private func stageLabel(_ w: Word) -> String? {
        if w.isArticle { return nil }
        let p = repo.shownProgress(w)
        let what = prefs.cardMode == 2 ? t("перевод") : t("слово")
        if repo.isDue(p) { return t("Проверка: вспомните %s", what) }
        if p.phase == Repo.recall { return t("Вспомните %s · «Знаю» %d из 3", what, p.streak) }
        if p.phase == Repo.learned { return t("Выучено") }
        if p.state == Mark.dontKnow || p.state == Mark.unsure { return t("Ещё раз: запомните слово") }
        return t("Новое слово")
    }

    // Makes sure the card on screen is picked and counted as shown once.
    func syncShowing() {
        // Behind the choice of languages no card is on screen, so none may count as shown.
        guard intro != .pair else { return }
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
        // A new interface language or card mode relabels the card on screen too.
        if var s = showing, s.key == w.key, s.stage != stageLabel(w) {
            s.stage = stageLabel(w)
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
