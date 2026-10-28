import Foundation

// What the lock screen card shows. The app picks the card and counts it; the lock screen only
// shows what the app picked, so this never moves on or counts anything.
public struct LockCard: Equatable {
    // nil when there is no card to show.
    public let title: String?
    public let subtitle: String
    public let recall: Bool

    public static func of(_ repo: Repo?) -> LockCard {
        guard let repo else { return LockCard(title: nil, subtitle: "Откройте приложение", recall: false) }
        guard let w = repo.current() else {
            return LockCard(title: nil, subtitle: "Слова закончились: выберите другие в приложении", recall: false)
        }
        let meanings = w.meanings.prefix(2).joined(separator: ", ")
        if w.isArticle {
            return LockCard(title: w.pos == "compare" ? (w.meanings.first ?? w.cyr) : w.cyr,
                            subtitle: w.pos == "compare" ? "похожие слова" : "заметка", recall: false)
        }
        // A word to recall shows its translation; the word itself opens with the app.
        if repo.asksRecall(w) { return LockCard(title: meanings, subtitle: "вспомните слово", recall: true) }
        return LockCard(title: repo.prefs.headword(w), subtitle: meanings, recall: false)
    }
}
