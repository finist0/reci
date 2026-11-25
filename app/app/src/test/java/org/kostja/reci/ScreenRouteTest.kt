package org.kostja.reci

import org.junit.Assert.assertEquals
import org.junit.Test
import org.kostja.reci.ui.Screen

// A recreated activity rebuilds its screens from their routes: ./gradlew testDebugUnitTest.
class ScreenRouteTest {
    @Test
    fun everyScreenComesBackFromItsRoute() {
        val all = listOf(
            Screen.Card, Screen.Categories, Screen.Search, Screen.Settings(), Screen.Settings(lockGuide = true),
            Screen.Schools, Screen.Detail("кућа"), Screen.Detail("cmp:pa-a"), Screen.Detail("кућа", report = true),
            Screen.Words("L3"), Screen.Words("M:моја: листа"), Screen.GrammarIndex, Screen.GrammarTopic("padezi"),
        )
        for (s in all) assertEquals(s, Screen.fromSaved(s.route))
    }

    @Test
    fun theRoutesOfTheLockCardKeepTheirMeaning() {
        assertEquals(Screen.Settings(), Screen.fromRoute("settings"))
        assertEquals(Screen.Detail("кућа", report = true), Screen.fromRoute("report:кућа"))
        assertEquals(Screen.Detail("кућа"), Screen.fromRoute("grammar:w:кућа"))
        assertEquals(Screen.Detail("cmp:pa-a"), Screen.fromRoute("grammar:cmp:pa-a"))
        assertEquals(Screen.GrammarTopic("padezi"), Screen.fromRoute("grammar:padezi"))
        assertEquals(null, Screen.fromRoute(null))
        assertEquals(null, Screen.fromRoute("card"))
        assertEquals(null, Screen.fromRoute("settings:guide"))
        assertEquals(null, Screen.fromRoute("list:L3"))
    }
}
