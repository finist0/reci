package org.kostja.reci

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Crash
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// A crash is written down for the next start and still reaches the system's own handler:
// ./gradlew testDebugUnitTest.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrashTest {
    private val app = RuntimeEnvironment.getApplication()
    private val system = Thread.getDefaultUncaughtExceptionHandler()

    @After
    fun clean() {
        Crash.clear()
        Thread.setDefaultUncaughtExceptionHandler(system)
    }

    @Test
    fun aCrashIsKeptForTheNextStartAndPassedOn() {
        var passed: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, e -> passed = e }
        Crash.init(app)
        val e = IllegalStateException("boom")
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), e)
        assertSame(e, passed)
        val r = Crash.pending!!
        assertTrue(r, r.contains("java.lang.IllegalStateException: boom"))
        assertTrue(r, r.contains("CrashTest.aCrashIsKeptForTheNextStartAndPassedOn"))
        assertTrue(r, Regex("version [^ ]+ \\(\\d+\\), thread ").containsMatchIn(r))
        Crash.clear()
        assertNull(Crash.pending)
    }

    @Test
    fun aLongReportIsCutToWhatTheServerTakes() {
        Crash.init(app)
        var e: Throwable = RuntimeException("deep")
        repeat(300) { e = RuntimeException("level $it", e) }
        assertEquals(11000, Crash.report(Thread.currentThread(), e).length)
    }
}
