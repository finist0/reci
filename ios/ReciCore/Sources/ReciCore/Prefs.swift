import Foundation

// Settings under the names of the web version, so progress files move between the versions.
public struct Prefs: Codable, Equatable {
    public var latinFirst = false
    public var showBothScripts = true
    public var autoSpeak = false
    // 0: learning, 1: translation only, 2: word only (translation hidden until tapped).
    public var cardMode = 0
    public var activeSize = 20
    public var cardCount = 0
    public var useMarks = true
    public var reviewKnown = true
    // 0: full accentuation (ку̏ћа), 1: stressed syllable only (ку́ћа), 2: none.
    public var accentMode = 1
    public var theme = 2
    public var wordSize = 42
    public var order = 0
    public var shuffleSeed = 1
    public var currentKey: String? = nil
    public var categories = ["L3", "L4", "X:phrase", "X:compare", "X:note"]
    public var todayDate = 0
    public var todayCount = 0
    public var hintDone = false
    public var newKnownCount = 0
    // iPhone only: how the card reaches the lock screen, 0: widgets, 1: wallpaper via Shortcuts.
    public var lockMode = 0
    // The showing counted last, "<key>#<card number>".
    public var lastShown = ""

    public init() {}

    enum CodingKeys: String, CodingKey {
        case latinFirst, showBothScripts, autoSpeak, cardMode, activeSize, cardCount, useMarks, reviewKnown, accentMode
        case theme, wordSize, order, shuffleSeed, currentKey, categories, todayDate, todayCount, hintDone, newKnownCount
        case lockMode, lastShown
    }

    public init(from decoder: Decoder) throws {
        let d = Prefs()
        let c = try decoder.container(keyedBy: CodingKeys.self)
        latinFirst = try c.decodeIfPresent(Bool.self, forKey: .latinFirst) ?? d.latinFirst
        showBothScripts = try c.decodeIfPresent(Bool.self, forKey: .showBothScripts) ?? d.showBothScripts
        autoSpeak = try c.decodeIfPresent(Bool.self, forKey: .autoSpeak) ?? d.autoSpeak
        cardMode = try c.decodeIfPresent(Int.self, forKey: .cardMode) ?? d.cardMode
        activeSize = try c.decodeIfPresent(Int.self, forKey: .activeSize) ?? d.activeSize
        cardCount = try c.decodeIfPresent(Int.self, forKey: .cardCount) ?? d.cardCount
        useMarks = try c.decodeIfPresent(Bool.self, forKey: .useMarks) ?? d.useMarks
        reviewKnown = try c.decodeIfPresent(Bool.self, forKey: .reviewKnown) ?? d.reviewKnown
        accentMode = try c.decodeIfPresent(Int.self, forKey: .accentMode) ?? d.accentMode
        theme = try c.decodeIfPresent(Int.self, forKey: .theme) ?? d.theme
        wordSize = try c.decodeIfPresent(Int.self, forKey: .wordSize) ?? d.wordSize
        order = try c.decodeIfPresent(Int.self, forKey: .order) ?? d.order
        shuffleSeed = try c.decodeIfPresent(Int.self, forKey: .shuffleSeed) ?? d.shuffleSeed
        currentKey = try c.decodeIfPresent(String.self, forKey: .currentKey)
        categories = try c.decodeIfPresent([String].self, forKey: .categories) ?? d.categories
        todayDate = try c.decodeIfPresent(Int.self, forKey: .todayDate) ?? d.todayDate
        todayCount = try c.decodeIfPresent(Int.self, forKey: .todayCount) ?? d.todayCount
        hintDone = try c.decodeIfPresent(Bool.self, forKey: .hintDone) ?? d.hintDone
        newKnownCount = try c.decodeIfPresent(Int.self, forKey: .newKnownCount) ?? d.newKnownCount
        lockMode = try c.decodeIfPresent(Int.self, forKey: .lockMode) ?? d.lockMode
        lastShown = try c.decodeIfPresent(String.self, forKey: .lastShown) ?? d.lastShown
    }

    // The long-press hint is shown on the 1st, 10th and 30th «Знаю» given to a new word, until
    // it is closed or the long press is used once.
    public mutating func hintAfterNewKnown() -> Bool {
        if hintDone { return false }
        newKnownCount += 1
        if newKnownCount >= 30 { hintDone = true }
        return newKnownCount == 1 || newKnownCount == 10 || newKnownCount == 30
    }
}
