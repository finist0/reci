import Foundation

// What the lock screen card shows. The app picks the card and counts it; the lock screen only
// shows what the app picked, so this never moves on or counts anything.
public struct LockCard: Equatable {
    // nil when there is no card to show.
    public let title: String?
    public let subtitle: String
    public let recall: Bool
    // The first example, or its translation for a word to recall.
    public var example: String? = nil

    public static func of(_ repo: Repo?) -> LockCard {
        guard let repo else { return LockCard(title: nil, subtitle: t("Откройте приложение"), recall: false) }
        guard let w = repo.current() else {
            return LockCard(title: nil, subtitle: t("Слова закончились: выберите другие в приложении"), recall: false)
        }
        let prefs = repo.prefs
        let meanings = prefs.native(w.meanings.prefix(2).joined(separator: ", "))
        if w.isArticle {
            return LockCard(title: w.pos == "compare" ? prefs.native(w.meanings.first ?? w.cyr) : prefs.native(w.cyr),
                            subtitle: w.pos == "compare" ? t("похожие слова") : t("заметка"), recall: false)
        }
        // A word to recall shows its translation; the word itself opens with the app.
        let first = w.examples.first
        if repo.asksRecall(w) {
            return LockCard(title: meanings, subtitle: t("вспомните слово"), recall: true, example: first.map { "«\(prefs.native($0.tr))»" })
        }
        let text = first.map { prefs.primary(prefs.marked($0.text.replacingOccurrences(of: "[", with: "").replacingOccurrences(of: "]", with: ""))) }
        return LockCard(title: prefs.headword(w), subtitle: meanings, recall: false, example: text)
    }
}
