import Foundation

// Text helpers, the same as in the Android app and the web version.
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

    // Drops the marks of Cyrillic vowels and Serbian р only: the acute of ć or of Montenegrin с́ is part of the letter.
    public static func strip(_ text: String) -> String {
        let s = text.decomposedStringWithCanonicalMapping.unicodeScalars
        var out = String.UnicodeScalarView()
        var vowel = false
        for sc in s {
            let mark = stress.contains(String(sc)) || sc == "\u{304}"
            if !mark || !vowel { out.append(sc) }
            if sc.properties.generalCategory != .nonspacingMark { vowel = "аеиоурАЕИОУРяюыэёЯЮЫЭЁ".unicodeScalars.contains(sc) }
        }
        return out.count == s.count ? text : String(out).precomposedStringWithCanonicalMapping
    }

    // Text with stress marks as the accent setting wants it: 0 as is, 1 the place only, 2 none.
    public static func showAccents(_ text: String, _ mode: Int) -> String {
        mode == 0 ? text : mode == 1 ? placeOnly(text) : strip(text)
    }

    // {...} in a card's text holds words of the deck's other language: Russian quoted in a
    // Serbian note, a Russian gloss in the forms of a Serbian word. Each part keeps the script of
    // its own language.
    public static func mixed(_ text: String, _ own: (String) -> String, _ other: (String) -> String) -> String {
        guard text.contains("{") else { return own(text) }
        var out = ""
        var rest = Substring(text)
        while let open = rest.firstIndex(of: "{"), let close = rest[open...].firstIndex(of: "}") {
            out += own(String(rest[..<open])) + other(String(rest[rest.index(after: open)..<close]))
            rest = rest[rest.index(after: close)...]
        }
        return out + own(String(rest))
    }

    // The text as typed in a search: without the braces.
    public static func plain(_ text: String) -> String { text.filter { $0 != "{" && $0 != "}" } }
}

// How text is shown under the current settings.
public extension Prefs {
    private func inTarget(_ s: String) -> String { latinFirst && deck.target == "sr" ? Translit.toLatin(s) : s }
    private func inNative(_ s: String) -> String { latinFirst && deck.serbianInNative ? Translit.toLatin(s) : s }

    // Text in the language learned: Serbian in the script the user reads first.
    func primary(_ cyr: String) -> String { Translit.mixed(cyr, inTarget, inNative) }

    // Text in the user's own language: a translation, a note, a grammar label.
    func native(_ text: String) -> String { Translit.mixed(text, inNative, inTarget) }

    func headword(_ w: Word) -> String {
        // A note's title is in the user's language.
        if w.pos == "note" { return native(w.cyr) }
        return primary(w.acc.isEmpty ? w.cyr : Translit.showAccents(w.acc, accentMode))
    }

    // Text that may carry stress marks: an example, a note.
    func marked(_ text: String) -> String { Translit.showAccents(text, accentMode) }

    // A word a card refers to, stressed as given or as on its own card when there is one.
    func linked(_ word: String, acc: String = "", _ target: Word?) -> String {
        if !acc.isEmpty { return primary(marked(acc)) }
        guard let a = target?.acc, !a.isEmpty else { return primary(word) }
        return primary(marked(a))
    }

    func secondary(_ cyr: String) -> String? {
        guard showBothScripts, deck.target == "sr" else { return nil }
        return latinFirst ? cyr : Translit.toLatin(cyr)
    }
}

// Search by a word or by its translation: Serbian may be typed in either script, Russian
// without ё. Translations into English are typed in Latin letters, as Serbian Latin is: the
// search picker tells which one is looked for.
public enum Search {
    static func norm(_ s: String) -> String { Translit.plain(s).lowercased().replacingOccurrences(of: "ё", with: "е") }

    public static func matches(_ w: Word, _ query: String, byMeaning: Bool) -> Bool {
        let q = norm(query.trimmingCharacters(in: .whitespaces))
        if q.isEmpty { return false }
        if byMeaning { return w.meanings.contains { norm($0).contains(q) || Translit.toLatin(norm($0)).contains(q) } }
        let c = norm(w.cyr)
        return c.hasPrefix(q) || Translit.toLatin(c).hasPrefix(q)
    }
}
