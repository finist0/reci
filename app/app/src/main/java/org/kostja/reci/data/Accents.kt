package org.kostja.reci.data

import java.text.Normalizer

object Accents {
    private const val ACUTE = '́'
    private val stress = setOf('̏', '̑', '̀', '́')
    private const val LENGTH = '̄'
    private const val VOWELS = "аеиоурАЕИОУР"

    // Keeps where the stress falls and drops its type and the length marks.
    fun placeOnly(acc: String): String {
        val sb = StringBuilder()
        for (ch in Normalizer.normalize(acc, Normalizer.Form.NFD)) {
            when (ch) {
                in stress -> sb.append(ACUTE)
                LENGTH -> {}
                else -> sb.append(ch)
            }
        }
        return Normalizer.normalize(sb, Normalizer.Form.NFC)
    }

    // Drops the marks of Cyrillic vowels and р only: the acute of ć or of Montenegrin с́ is part of the letter.
    fun strip(text: String): String {
        val s = Normalizer.normalize(text, Normalizer.Form.NFD)
        val sb = StringBuilder(s.length)
        var vowel = false
        for (ch in s) {
            val mark = ch in stress || ch == LENGTH
            if (!mark || !vowel) sb.append(ch)
            if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) vowel = ch in VOWELS
        }
        return if (sb.length == s.length) text else Normalizer.normalize(sb, Normalizer.Form.NFC)
    }

    // Text with stress marks as the accent setting wants it: 0 as is, 1 the place only, 2 none.
    fun show(text: String, mode: Int) = when (mode) {
        0 -> text
        1 -> placeOnly(text)
        else -> strip(text)
    }
}
