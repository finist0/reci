import Foundation

// A key of an object keyed by language code.
struct LangKey: CodingKey {
    let stringValue: String
    init(_ s: String) { stringValue = s }
    init?(stringValue: String) { self.stringValue = stringValue }
    var intValue: Int? { nil }
    init?(intValue: Int) { nil }
}

// In the cards an example is {"sr": ..., "ru": ...}: the deck the cards are read for (Deck.userInfoKey)
// says which language is learned. text has the headword in brackets; tr is its translation.
public struct Example: Codable, Hashable {
    public var text: String
    public var tr: String

    public init(text: String, tr: String) {
        (self.text, self.tr) = (text, tr)
    }

    public init(from decoder: Decoder) throws {
        let d = Deck.of(decoder.userInfo)
        let c = try decoder.container(keyedBy: LangKey.self)
        text = try c.decodeIfPresent(String.self, forKey: LangKey(d.target)) ?? ""
        tr = try c.decodeIfPresent(String.self, forKey: LangKey(d.native)) ?? ""
    }

    public func encode(to encoder: Encoder) throws {
        let d = Deck.of(encoder.userInfo)
        var c = encoder.container(keyedBy: LangKey.self)
        try c.encode(text, forKey: LangKey(d.target))
        try c.encode(tr, forKey: LangKey(d.native))
    }
}

// word is bare, as cards are looked up by; acc is the same word with stress marks, or empty.
public struct Related: Codable, Hashable {
    public var word: String
    public var tr: String
    public var acc: String

    public init(word: String, tr: String, acc: String = "") {
        (self.word, self.tr, self.acc) = (word, tr, acc)
    }

    public init(from decoder: Decoder) throws {
        let d = Deck.of(decoder.userInfo)
        let c = try decoder.container(keyedBy: LangKey.self)
        word = try c.decodeIfPresent(String.self, forKey: LangKey(d.target)) ?? ""
        tr = try c.decodeIfPresent(String.self, forKey: LangKey(d.native)) ?? ""
        acc = try c.decodeIfPresent(String.self, forKey: LangKey("acc")) ?? ""
    }

    public func encode(to encoder: Encoder) throws {
        let d = Deck.of(encoder.userInfo)
        var c = encoder.container(keyedBy: LangKey.self)
        try c.encode(word, forKey: LangKey(d.target))
        try c.encode(tr, forKey: LangKey(d.native))
        if !acc.isEmpty { try c.encode(acc, forKey: LangKey("acc")) }
    }
}

// One card of a deck's dictionary (words.json for the first deck): a word, a phrase, a comparison ("compare") or a note ("note").
public struct Word: Codable, Hashable, Identifiable {
    public var key: String
    public var cyr: String
    public var acc: String
    public var pos: String
    public var gram: String
    public var ruGram: String
    public var ruNoun: String
    public var forms: String
    public var meanings: [String]
    public var level: Int
    public var topic: String
    public var rank: Int
    public var examples: [Example]
    public var syn: [String]
    public var ant: [String]
    public var related: [Related]
    public var note: String
    public var tips: [String]
    // The synonyms and antonyms with stress marks, when the dictionary has them.
    public var synAcc: [String]
    public var antAcc: [String]

    public var id: String { key }

    enum CodingKeys: String, CodingKey {
        case key, cyr, acc, pos, gram, ruGram, ruNoun, forms, meanings, level, topic, rank, examples, syn, ant, related, note, tips
        case synAcc, antAcc
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        key = try c.decode(String.self, forKey: .key)
        cyr = try c.decode(String.self, forKey: .cyr)
        acc = try c.decodeIfPresent(String.self, forKey: .acc) ?? ""
        pos = try c.decodeIfPresent(String.self, forKey: .pos) ?? ""
        gram = try c.decodeIfPresent(String.self, forKey: .gram) ?? ""
        ruGram = try c.decodeIfPresent(String.self, forKey: .ruGram) ?? ""
        ruNoun = try c.decodeIfPresent(String.self, forKey: .ruNoun) ?? ""
        forms = try c.decodeIfPresent(String.self, forKey: .forms) ?? ""
        meanings = try c.decodeIfPresent([String].self, forKey: .meanings) ?? []
        level = try c.decodeIfPresent(Int.self, forKey: .level) ?? 1
        topic = try c.decodeIfPresent(String.self, forKey: .topic) ?? "basic"
        rank = try c.decodeIfPresent(Int.self, forKey: .rank) ?? 0
        examples = try c.decodeIfPresent([Example].self, forKey: .examples) ?? []
        syn = try c.decodeIfPresent([String].self, forKey: .syn) ?? []
        ant = try c.decodeIfPresent([String].self, forKey: .ant) ?? []
        related = try c.decodeIfPresent([Related].self, forKey: .related) ?? []
        note = try c.decodeIfPresent(String.self, forKey: .note) ?? ""
        tips = try c.decodeIfPresent([String].self, forKey: .tips) ?? []
        synAcc = try c.decodeIfPresent([String].self, forKey: .synAcc) ?? []
        antAcc = try c.decodeIfPresent([String].self, forKey: .antAcc) ?? []
    }

    // The cards of a deck's dictionary file.
    public static func decode(_ data: Data, deck: Deck) throws -> [Word] {
        let dec = JSONDecoder()
        dec.userInfo[Deck.userInfoKey] = deck
        return try dec.decode([Word].self, from: data)
    }

    public var isArticle: Bool { pos == "compare" || pos == "note" }
}

// Answer marks, numbered as in the Android enum so that progress files mean the same everywhere.
public enum Mark {
    public static let new = 0
    public static let dontKnow = 1
    public static let unsure = 2
    public static let known = 3
}

public struct Progress: Codable, Equatable {
    public var state = Mark.new
    public var fav = false
    public var seen = 0
    public var last = 0
    public var due = 0
    public var step = 0
    public var phase = 0
    public var streak = 0
    // The card number from which a word answered «Не знаю» is shown again; 0 when none.
    public var showAt = 0

    public init() {}

    enum CodingKeys: String, CodingKey { case state, fav, seen, last, due, step, phase, streak, showAt }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        state = try c.decodeIfPresent(Int.self, forKey: .state) ?? 0
        fav = try c.decodeIfPresent(Bool.self, forKey: .fav) ?? false
        seen = try c.decodeIfPresent(Int.self, forKey: .seen) ?? 0
        last = try c.decodeIfPresent(Int.self, forKey: .last) ?? 0
        due = try c.decodeIfPresent(Int.self, forKey: .due) ?? 0
        step = try c.decodeIfPresent(Int.self, forKey: .step) ?? 0
        phase = try c.decodeIfPresent(Int.self, forKey: .phase) ?? 0
        streak = try c.decodeIfPresent(Int.self, forKey: .streak) ?? 0
        showAt = try c.decodeIfPresent(Int.self, forKey: .showAt) ?? 0
    }
}

public struct GrammarBlock: Codable, Hashable {
    public var type: String
    public var text: String?
    public var items: [String]?
    public var head: [String]?
    public var rows: [[String]]?
    public var sr: String?
    public var ru: String?
}

public struct GrammarTopic: Codable, Hashable, Identifiable {
    public var id: String
    public var group: String
    public var title: String
    public var blocks: [GrammarBlock]
}

// Russian names, which t() turns into the user's language when they are read.
public enum Labels {
    private static func translated<K>(_ list: KeyValuePairs<K, String>) -> [(K, String)] { list.map { ($0.key, t($0.value)) } }

    public static var levels: [(Int, String)] { translated(levelsRu) }
    public static var topics: [(String, String)] { translated(topicsRu) }
    public static var sections: [(String, String)] { translated(sectionsRu) }
    public static var lists: [(String, String)] { translated(listsRu) }
    public static var orders: [String] { ordersRu.map { t($0.value) } }

    private static let levelsRu: KeyValuePairs<Int, String> = [
        1: "A1 · Начальный", 2: "A2 · Элементарный", 3: "B1 · Средний", 4: "B2 · Выше среднего", 5: "C1 · Продвинутый",
    ]

    private static let topicsRu: KeyValuePairs<String, String> = [
        "basic": "Базовые слова", "people": "Люди", "family": "Семья", "body": "Тело", "health": "Здоровье",
        "home": "Дом", "food": "Еда и напитки", "clothes": "Одежда", "city": "Город", "transport": "Транспорт",
        "travel": "Путешествия", "nature": "Природа", "weather": "Погода", "animals": "Животные", "time": "Время",
        "numbers": "Числа и количество", "colors": "Цвета и формы", "work": "Работа", "education": "Учёба",
        "money": "Деньги и покупки", "communication": "Общение", "emotions": "Чувства", "character": "Характер",
        "leisure": "Отдых и спорт", "culture": "Культура", "tech": "Техника", "society": "Общество",
        "actions": "Действия", "qualities": "Качества", "abstract": "Абстрактные понятия",
    ]

    private static let sectionsRu: KeyValuePairs<String, String> = ["X:phrase": "Выражения", "X:compare": "Похожие слова", "X:note": "Заметки"]
    private static let listsRu: KeyValuePairs<String, String> = ["M:dontknow": "Не знаю", "M:known": "Знаю", "M:fav": "Избранное"]
    private static let ordersRu: KeyValuePairs<Int, String> = [0: "По частоте", 1: "Вперемешку", 2: "По алфавиту"]

    // The ids of the marked lists, without translating their names.
    public static var listIds: [String] { listsRu.map(\.key) }

    public static func category(_ id: String) -> String {
        if id.hasPrefix("L") { return levelsRu.first { "L\($0.0)" == id }.map { t($0.1) } ?? id }
        if id.hasPrefix("T:") { return topicsRu.first { "T:" + $0.0 == id }.map { t($0.1) } ?? id }
        if id.hasPrefix("X:") { return sectionsRu.first { $0.0 == id }.map { t($0.1) } ?? id }
        return listsRu.first { $0.0 == id }.map { t($0.1) } ?? id
    }

    public static func levelShort(_ level: Int) -> String {
        (levelsRu.first { $0.0 == level }?.1 ?? "").components(separatedBy: " ").first ?? ""
    }

    private static let posNames = [
        "noun": "сущ.", "verb": "гл.", "adj": "прил.", "adv": "нар.", "pron": "мест.", "prep": "предл.",
        "conj": "союз", "num": "числ.", "part": "част.", "interj": "межд.", "phrase": "выражение",
        "compare": "похожие слова", "note": "заметка",
    ]

    public static func pos(_ code: String) -> String { posNames[code].map { t($0) } ?? code }
}
