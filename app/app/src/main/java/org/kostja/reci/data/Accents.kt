package org.kostja.reci.data

import java.text.Normalizer

object Accents {
    private const val ACUTE = '́'
    private val stress = setOf('̏', '̑', '̀', '́')
    private const val LENGTH = '̄'

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
}
