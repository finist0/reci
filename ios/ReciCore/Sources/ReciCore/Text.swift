import Foundation

// Labels and text helpers, the same as in the Android app and the web version.
public enum Labels {
    public static let levels: [(Int, String)] = [
        (1, "A1 · Начальный"), (2, "A2 · Элементарный"), (3, "B1 · Средний"), (4, "B2 · Выше среднего"), (5, "C1 · Продвинутый"),
    ]

    public static let topics: [(String, String)] = [
        ("basic", "Базовые слова"), ("people", "Люди"), ("family", "Семья"), ("body", "Тело"), ("health", "Здоровье"),
        ("home", "Дом"), ("food", "Еда и напитки"), ("clothes", "Одежда"), ("city", "Город"), ("transport", "Транспорт"),
        ("travel", "Путешествия"), ("nature", "Природа"), ("weather", "Погода"), ("animals", "Животные"), ("time", "Время"),
        ("numbers", "Числа и количество"), ("colors", "Цвета и формы"), ("work", "Работа"), ("education", "Учёба"),
        ("money", "Деньги и покупки"), ("communication", "Общение"), ("emotions", "Чувства"), ("character", "Характер"),
        ("leisure", "Отдых и спорт"), ("culture", "Культура"), ("tech", "Техника"), ("society", "Общество"),
        ("actions", "Действия"), ("qualities", "Качества"), ("abstract", "Абстрактные понятия"),
    ]

    public static let sections: [(String, String)] = [("X:phrase", "Выражения"), ("X:compare", "Похожие слова"), ("X:note", "Заметки")]
    public static let lists: [(String, String)] = [("M:dontknow", "Не знаю"), ("M:known", "Знаю"), ("M:fav", "Избранное")]
    public static let orders = ["По частоте", "Вперемешку", "По алфавиту"]

    public static func category(_ id: String) -> String {
        if id.hasPrefix("L") { return levels.first { "L\($0.0)" == id }?.1 ?? id }
        if id.hasPrefix("T:") { return topics.first { "T:" + $0.0 == id }?.1 ?? id }
        if id.hasPrefix("X:") { return sections.first { $0.0 == id }?.1 ?? id }
        return lists.first { $0.0 == id }?.1 ?? id
    }

    public static func levelShort(_ level: Int) -> String {
        (levels.first { $0.0 == level }?.1 ?? "").components(separatedBy: " ").first ?? ""
    }

    private static let posNames = [
        "noun": "сущ.", "verb": "гл.", "adj": "прил.", "adv": "нар.", "pron": "мест.", "prep": "предл.",
        "conj": "союз", "num": "числ.", "part": "част.", "interj": "межд.", "phrase": "выражение",
        "compare": "похожие слова", "note": "заметка",
    ]

    public static func pos(_ code: String) -> String { posNames[code] ?? code }
}

public func plural(_ n: Int, _ one: String, _ few: String, _ many: String) -> String {
    let m10 = n % 10, m100 = n % 100
    if m10 == 1 && m100 != 11 { return one }
    if (2...4).contains(m10) && (m100 < 12 || m100 > 14) { return few }
    return many
}

public enum Translit {
    private static let latin: [Character: String] = [
        "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "ђ": "đ", "е": "e", "ж": "ž", "з": "z",
        "и": "i", "ј": "j", "к": "k", "л": "l", "љ": "lj", "м": "m", "н": "n", "њ": "nj", "о": "o",
        "п": "p", "р": "r", "с": "s", "т": "t", "ћ": "ć", "у": "u", "ф": "f", "х": "h", "ц": "c",
        "ч": "č", "џ": "dž", "ш": "š",
    ]

    private static func isUpper(_ s: String?) -> Bool { s.map { $0 != $0.lowercased() } ?? false }
    private static func isLetter(_ s: String?) -> Bool { s.map { $0.lowercased() != $0.uppercased() } ?? false }

    // Stress marks are combining characters and pass through: ку̏ћа -> kȕća.
    public static func toLatin(_ text: String) -> String {
        let s = text.decomposedStringWithCanonicalMapping.unicodeScalars.map { String($0) }
        var out = ""
        for (i, ch) in s.enumerated() {
            guard let c = ch.lowercased().first, ch.count == 1, let lat = latin[c] else {
                out += ch
                continue
            }
            if !isUpper(ch) {
                out += lat
                continue
            }
            // Љ, Њ, Џ become "LJ" inside an all-caps word and "Lj" otherwise.
            let next = i + 1 < s.count ? s[i + 1] : nil
            let prev = i > 0 ? s[i - 1] : nil
            let allCaps = isUpper(next) || ((next == nil || !isLetter(next)) && isUpper(prev))
            out += allCaps ? lat.uppercased() : lat.prefix(1).uppercased() + lat.dropFirst()
        }
        return out.precomposedStringWithCanonicalMapping
    }

    private static let stress: Set<String> = ["\u{30F}", "\u{311}", "\u{300}", "\u{301}"]

    // Keeps where the stress falls and drops its type and the length marks.
    public static func placeOnly(_ acc: String) -> String {
        var out = ""
        for sc in acc.decomposedStringWithCanonicalMapping.unicodeScalars {
            let ch = String(sc)
            if stress.contains(ch) { out += "\u{301}" } else if ch != "\u{304}" { out += ch }
        }
        return out.precomposedStringWithCanonicalMapping
    }
}

// How Serbian text is shown under the current settings.
public extension Prefs {
    func primary(_ cyr: String) -> String { latinFirst ? Translit.toLatin(cyr) : cyr }

    func headword(_ w: Word) -> String {
        // A note's title is Russian; transliterating it would garble it.
        if w.pos == "note" { return w.cyr }
        let shown = w.acc.isEmpty || accentMode == 2 ? w.cyr : accentMode == 1 ? Translit.placeOnly(w.acc) : w.acc
        return primary(shown)
    }

    func secondary(_ cyr: String) -> String? {
        guard showBothScripts else { return nil }
        return latinFirst ? cyr : Translit.toLatin(cyr)
    }
}
