package org.kostja.reci

import org.junit.Assert.assertEquals
import org.junit.Test
import org.kostja.reci.data.Accents
import org.kostja.reci.data.Translit

class AccentsTest {
    private val ex = "Имамо малу [ку̏ћу] на [сѐлу]."

    @Test
    fun exampleFollowsTheAccentSetting() {
        assertEquals(ex, Accents.show(ex, 0))
        assertEquals("Имамо малу [ку́ћу] на [се́лу].", Accents.show(ex, 1))
        assertEquals("Имамо малу [кућу] на [селу].", Accents.show(ex, 2))
        assertEquals("Imamo malu [kúću] na [sélu].", Translit.toLatin(Accents.show(ex, 1)))
    }

    @Test
    fun stripLeavesOtherMarksAlone() {
        assertEquals("йё кућа", Accents.strip("йё ку̏ћа̄"))
        assertEquals("кућа", Accents.strip("кућа"))
        assertEquals("ćevapi, kȕća, с́", Accents.strip("ćevapi, kȕća, с́"))
    }
}
