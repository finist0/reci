import Foundation

public struct Example: Codable, Hashable {
    public var sr: String
    public var ru: String
}

public struct Related: Codable, Hashable {
    public var sr: String
    public var ru: String
}

// One card of words.json: a word, a phrase, a comparison ("compare") or a note ("note").
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

    public var id: String { key }

    enum CodingKeys: String, CodingKey {
        case key, cyr, acc, pos, gram, ruGram, ruNoun, forms, meanings, level, topic, rank, examples, syn, ant, related, note, tips
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
