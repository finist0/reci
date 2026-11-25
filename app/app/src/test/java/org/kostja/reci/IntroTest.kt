package org.kostja.reci

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Intro
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

// When a start asks for the languages and offers the lock screen card, by the scenarios of
// tests/intro.json, which ReciCore/Tests/IntroTests.swift runs too: ./gradlew testDebugUnitTest.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IntroTest {
    private val day = 24 * 60 * 60 * 1000L

    private fun run(scenario: JSONObject) {
        val name = scenario.getString("name")
        var s = Intro.begin(scenario.getBoolean("fresh"))
        val steps = scenario.getJSONArray("steps")
        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            val now = (step.optDouble("day", 0.0) * day).toLong()
            when (step.optString("do")) {
                "pair" -> s = Intro.chosen(s)
                "later" -> s = Intro.postponed(s, now)
                "setup" -> s = Intro.done(s)
                else -> {
                    val (show, after) = Intro.next(s, now, step.optBoolean("works"), step.optBoolean("news"))
                    assertEquals("$name, step ${i + 1}", step.getString("expect"), show.name.lowercase())
                    s = after
                }
            }
        }
    }

    @Test
    fun scenarios() {
        val all = JSONArray(File("../../tests/intro.json").readText())
        for (i in 0 until all.length()) run(all.getJSONObject(i))
    }
}
