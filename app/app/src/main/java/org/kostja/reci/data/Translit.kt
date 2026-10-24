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
}
