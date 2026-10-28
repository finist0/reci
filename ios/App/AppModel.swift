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
    let speech = Speech()

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
    }

    var prefs: Prefs { repo.prefs }
    var palette: Palette { palettes.indices.contains(prefs.theme) ? palettes[prefs.theme] : palettes[2] }

    func changed() {
        version += 1
        // Reloads asked for while the app is in front do not count against the widgets' budget.
        if UIApplication.shared.applicationState == .active { WidgetCenter.shared.reloadAllTimelines() }
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
            repo.markShown(w)
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
        repo.answer(w, a, learned: learned)
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
