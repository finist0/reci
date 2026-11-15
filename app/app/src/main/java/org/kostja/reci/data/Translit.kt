package org.kostja.reci.data

import java.text.Normalizer

object Translit {
    private val map = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ђ' to "đ",
        'е' to "e", 'ж' to "ž", 'з' to "z", 'и' to "i", 'ј' to "j", 'к' to "k",
        'л' to "l", 'љ' to "lj", 'м' to "m", 'н' to "n", 'њ' to "nj", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'ћ' to "ć", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "č", 'џ' to "dž", 'ш' to "š",
    )

    // Stress marks are combining characters and pass through: ку̏ћа -> kȕća.
    fun toLatin(text: String): String {
        val s = Normalizer.normalize(text, Normalizer.Form.NFD)
        val sb = StringBuilder(s.length + 8)
        for ((i, ch) in s.withIndex()) {
            val lat = map[ch.lowercaseChar()]
            if (lat == null) {
                sb.append(ch)
                continue
            }
            if (!ch.isUpperCase()) {
                sb.append(lat)
                continue
            }
            // Љ, Њ, Џ become "LJ" inside an all-caps word and "Lj" otherwise.
            val next = s.getOrNull(i + 1)
            val prev = s.getOrNull(i - 1)
            val allCaps = next?.isUpperCase() == true ||
                ((next == null || !next.isLetter()) && prev?.isUpperCase() == true)
            sb.append(if (allCaps) lat.uppercase() else lat.replaceFirstChar { it.uppercaseChar() })
        }
        return Normalizer.normalize(sb, Normalizer.Form.NFC)
    }

    // {...} in a card's text holds words of the deck's other language: Russian quoted in a
    // Serbian note, a Russian gloss in the forms of a Serbian word. Each part keeps the script of
    // its own language.
    fun mixed(text: String, own: (String) -> String, other: (String) -> String): String {
        if ('{' !in text) return own(text)
        val sb = StringBuilder(text.length + 8)
        var i = 0
        while (i < text.length) {
            val open = text.indexOf('{', i)
            val close = if (open < 0) -1 else text.indexOf('}', open + 1)
            if (close < 0) {
                sb.append(own(text.substring(i)))
                break
            }
            sb.append(own(text.substring(i, open))).append(other(text.substring(open + 1, close)))
            i = close + 1
        }
        return sb.toString()
    }

    // The text as typed in a search: without the braces.
    fun plain(text: String) = text.replace("{", "").replace("}", "")
}
