import Foundation

// A pair of languages: the one learned (target) and the user's own (native), the language of
// the translations. Decks are listed in decks.json, so a new pair needs data only. The
// counterpart of data/Deck.kt.
public struct Deck: Codable, Hashable, Sendable {
    public var id: String
    public var target: String
    public var native: String
    // The dictionary and the grammar reference among the app's resources; no grammar when empty.
    public var words: String
    public var grammar: String

    public init(id: String, target: String, native: String, words: String, grammar: String) {
        (self.id, self.target, self.native, self.words, self.grammar) = (id, target, native, words, grammar)
    }

    enum CodingKeys: String, CodingKey { case id, target, native, words, grammar }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        target = try c.decode(String.self, forKey: .target)
        native = try c.decode(String.self, forKey: .native)
        words = try c.decode(String.self, forKey: .words)
        grammar = try c.decodeIfPresent(String.self, forKey: .grammar) ?? ""
    }

    // The deck of words.json: the dictionary patches (DictUpdate) are made for it only.
    public var isFirst: Bool { id == Decks.first }

    // Progress belongs to the language learned, not to the deck: a Serbian word learned with
    // Russian translations stays learned with English ones. The decks of one language learned
    // keep it under the id of the first of them listed.
    public var store: String { Decks.all.first { $0.target == target }?.id ?? id }

    // Storage names of what belongs to the language learned: the first deck keeps the names it
    // had before there were others.
    public func scoped(_ name: String) -> String { store == Decks.first ? name : "\(store).\(name)" }

    // Storage names of what belongs to this deck alone.
    public func ownScoped(_ name: String) -> String { isFirst ? name : "\(id).\(name)" }

    // Cyrillic in the translations that the script setting turns into Latin: Serbian ones, or
    // Serbian words quoted in translations into a language not written in Cyrillic.
    public var serbianInNative: Bool { native == "sr" || target == "sr" && !Decks.cyrillic.contains(native) }

    // Cards are read for a deck: its examples and related words are keyed by language.
    public static let userInfoKey = CodingUserInfoKey(rawValue: "reci.deck")!

    static func of(_ info: [CodingUserInfoKey: Any]) -> Deck { info[userInfoKey] as? Deck ?? Decks.deck(nil) }
}

public enum Decks {
    public static let first = "sr-ru"
    static let cyrillic: Set<String> = ["ru", "sr"]

    // decks.json as built in; the app replaces it with the file it carries.
    public static var all: [Deck] = [
        Deck(id: "sr-ru", target: "sr", native: "ru", words: "words.json", grammar: "grammar.json"),
        Deck(id: "ru-sr", target: "ru", native: "sr", words: "words-ru-sr.json", grammar: ""),
    ]

    public static func load(_ data: Data) {
        if let list = try? JSONDecoder().decode([Deck].self, from: data), !list.isEmpty { all = list }
    }

    // An unknown or missing id means the first deck.
    public static func deck(_ id: String?) -> Deck { all.first { $0.id == id } ?? all[0] }

    // The language the phone is set to, as a tag (sr-Latn-RS) and as a language (sr); tests set
    // them.
    public static var systemTag = Locale.preferredLanguages.first ?? ""
    public static var systemLanguage = String(systemTag.prefix { $0 != "-" && $0 != "_" }).lowercased()

    // Serbian and the languages close to it.
    static let serbian: Set<String> = ["sr", "hr", "bs", "sh", "cnr", "mk", "sl"]

    static let russianReaders: Set<String> = ["ru", "uk", "be"]

    // A first start opens Serbian with Russian translations on a phone set to Russian, Ukrainian
    // or Belarusian, Russian for Serbs on one set to Serbian or a close language, and Serbian with
    // English translations elsewhere.
    public static func guess(_ language: String) -> String {
        let id = russianReaders.contains(language) ? first : serbian.contains(language) ? "ru-sr" : "sr-en"
        return all.contains { $0.id == id } ? id : first
    }

    // The interface language of a first start: the phone's, if the interface speaks it, and
    // Russian where the translations are Russian.
    public static func guessUi(_ language: String) -> String {
        russianReaders.contains(language) ? "ru" : serbian.contains(language) ? "sr" : "en"
    }

    static let latinReaders: Set<String> = ["hr", "bs", "sl", "sh", "cnr"]

    // A first start shows Serbian in Latin letters on a phone set to a language written in them.
    public static func guessLatin(_ tag: String) -> Bool {
        let parts = tag.lowercased().split { $0 == "-" || $0 == "_" }
        guard let language = parts.first.map(String.init) else { return false }
        return latinReaders.contains(language) || language == "sr" && parts.contains("latn")
    }

    // The values of Prefs.uiLang, in the order they are offered.
    public static let uiLangs = ["ru", "sr", "en"]

    // An interface language named in itself, so that a user who cannot read the interface finds
    // their own.
    public static func uiName(_ code: String) -> String {
        switch code {
        case "ru": return "Русский"
        case "sr": return I18n.latin ? "Srpski" : "Српски"
        default: return "English"
        }
    }

    private static func langName(_ code: String, _ l: String) -> String {
        switch code {
        case "sr": return I18n.tIn(l, "сербский")
        case "ru": return I18n.tIn(l, "русский")
        case "en": return I18n.tIn(l, "английский")
        default: return code
        }
    }

    // A language as an option of the settings, named in the interface language.
    public static func name(_ code: String) -> String {
        let s = langName(code, I18n.lang)
        return s.prefix(1).uppercased() + String(s.dropFirst())
    }

    // The settings choose the language learned and the language of the translations, not a deck.
    public static var targets: [String] {
        var seen = Set<String>()
        return all.map(\.target).filter { seen.insert($0).inserted }
    }

    public static func natives(_ target: String) -> [String] { all.filter { $0.target == target }.map(\.native) }

    // The deck of the two languages, else the first one of the language learned: a change of
    // the language learned keeps the translations where a deck has them.
    public static func pick(_ target: String, _ native: String) -> Deck {
        all.first { $0.target == target && $0.native == native } ?? all.first { $0.target == target } ?? all[0]
    }
}

// What the interface says differently for each deck.
public extension Deck {
    var howToSay: String {
        switch target {
        case "ru": return t("Как сказать по-русски")
        case "en": return t("Как сказать по-английски")
        default: return t("Как сказать по-сербски")
        }
    }

    // A word to show the font size on.
    var sample: String {
        switch target {
        case "ru": return "сло́во"
        case "en": return "word"
        default: return "реч"
        }
    }

    // How the stress setting looks: Serbian marks four accents, Russian only the place.
    var accentSamples: [String] { target == "sr" ? ["ку̏ћа", "ку́ћа", "кућа"] : ["доро́га", "дорога"] }

    // Search: a word in the user's language, or in the language learned.
    var meaningHint: String {
        switch native {
        case "sr": return t("слово по-сербски")
        case "en": return t("слово по-английски")
        default: return t("слово по-русски")
        }
    }

    var wordHint: String {
        switch target {
        case "ru": return "слово"
        case "en": return "word"
        default: return "реч, reč"
        }
    }

    var wordChip: String {
        switch target {
        case "ru": return t("Русское слово")
        case "en": return t("Английское слово")
        default: return t("Сербское слово")
        }
    }

    var noVoice: String {
        switch target {
        case "ru": return t("Нет русского голоса")
        case "en": return t("Нет английского голоса")
        default: return t("Нет сербского, хорватского или боснийского голоса")
        }
    }

    // Said by the voice row of the settings.
    var voiceSample: String {
        switch target {
        case "ru": return "Добрый день"
        case "en": return "Good afternoon"
        default: return "Добар дан"
        }
    }

    // Voices to try, by language code, and whether the voice reads Latin text only. Croatian
    // and Bosnian voices read Serbian Latin text well enough.
    var voices: [(String, Bool)] {
        switch target {
        case "ru": return [("ru", false)]
        case "en": return [("en", false)]
        default: return [("sr", false), ("hr", true), ("bs", true)]
        }
    }

    var shareText: String {
        switch id {
        case "ru-sr": return t("НЕРодная речь — русские слова для тех, кто говорит по-сербски: карточки с ударениями и примерами. Бесплатно.")
        case "sr-en": return t("НЕРодная речь — сербские слова для тех, кто говорит по-английски: карточки с ударениями и примерами. Бесплатно.")
        case "ru-en": return t("НЕРодная речь — русские слова для тех, кто говорит по-английски: карточки с ударениями и примерами. Бесплатно.")
        default: return t("НЕРодная речь — сербские слова для тех, кто говорит по-русски: карточки с ударениями, примерами и грамматикой. Бесплатно.")
        }
    }

    // The script setting matters wherever Serbian is shown.
    var hasSerbian: Bool { target == "sr" || native == "sr" || I18n.lang == "sr" }
}

// The interface in the user's language. Russian text is the key, so code reads as before:
// t("Настройки") looks it up in ui.json ({"Настройки": {"sr": "...", "en": "..."}}).
public enum I18n {
    // Russian text -> language -> translation.
    public static var table: [String: [String: String]] = [:]
    // The language chosen for the interface and the script setting; the store keeps them.
    public static var lang = "ru"
    public static var latin = false

    public static func load(_ data: Data) {
        if let t = try? JSONDecoder().decode([String: [String: String]].self, from: data) { table = t }
    }

    public static func follow(_ p: Prefs) {
        lang = p.uiLang
        latin = p.latinFirst
    }

    public static func tIn(_ l: String, _ ru: String) -> String {
        if l == "ru" { return ru }
        let s = table[ru]?[l] ?? ru
        return l == "sr" && latin ? Translit.toLatin(s) : s
    }

    public static func has(_ ru: String, _ l: String) -> Bool { table[ru]?[l] != nil }

    // Puts the arguments in place of %s and %d in turn, as String.format does on Android, so
    // that the keys are the same there.
    public static func format(_ s: String, _ args: [Any]) -> String {
        var out = ""
        var next = 0
        var i = s.startIndex
        while i < s.endIndex {
            let c = s[i]
            let j = s.index(after: i)
            if c == "%", j < s.endIndex {
                let spec = s[j]
                if spec == "%" {
                    out.append("%")
                    i = s.index(after: j)
                    continue
                }
                if spec == "s" || spec == "d" || spec == "@" {
                    out += next < args.count ? "\(args[next])" : ""
                    next += 1
                    i = s.index(after: j)
                    continue
                }
            }
            out.append(c)
            i = j
        }
        return out
    }
}

public func t(_ ru: String) -> String { I18n.tIn(I18n.lang, ru) }

// With arguments for its %s and %d; a separate first one keeps t("…") unambiguous.
public func t(_ ru: String, _ first: Any, _ rest: Any...) -> String { I18n.format(t(ru), [first] + rest) }

// Russian and Serbian have three plural forms, English two: the forms are given in Russian.
public func plural(_ n: Int, _ one: String, _ few: String, _ many: String) -> String {
    if I18n.lang == "en" { return t(n == 1 ? one : many) }
    let m10 = n % 10, m100 = n % 100
    if m10 == 1 && m100 != 11 { return t(one) }
    if (2...4).contains(m10) && (m100 < 12 || m100 > 14) { return t(few) }
    return t(many)
}
