package org.kostja.reci

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.Speaker
import org.kostja.reci.data.State
import org.kostja.reci.lock.LockActivity
import org.kostja.reci.lock.LockService
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The lock screen settings, which act when the screen goes off: the card, the pause, calls,
// a new word each time, review words in the notification, speech on the lock card.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LockSettingsTest {
    private val app = RuntimeEnvironment.getApplication()

    private fun waitIo() {
        val done = CountDownLatch(1)
        Repo.afterLoad { done.countDown() }
        assertTrue("dictionary did not load", done.await(60, TimeUnit.SECONDS))
    }

    @Before
    fun freshStart() {
        waitIo()
        Repo.resetAll()
        waitIo()
        Repo.resetPosition()
        Repo.step(1)
        ShadowSettings.setCanDrawOverlays(true)
    }

    @After
    fun restoreSpeech() {
        Speaker.say = Speaker::speak
    }

    // What the service does when the screen goes off.
    private fun cardStarts(): Boolean {
        LockActivity.show(app)
        return shadowOf(app).nextStartedActivity?.component?.className == LockActivity::class.java.name
    }

    @Test
    fun theCardStartsWhenTheScreenGoesOff() {
        assertTrue(cardStarts())
    }

    @Test
    fun noCardWithoutTheOverlayPermission() {
        ShadowSettings.setCanDrawOverlays(false)
        assertFalse(cardStarts())
    }

    @Test
    fun noCardDuringAPause() {
        Prefs.pausedUntil = System.currentTimeMillis() + 60 * 60_000L
        assertFalse(cardStarts())
        Prefs.pausedUntil = 0
        assertTrue(cardStarts())
    }

    @Test
    fun noCardDuringACallUnlessAllowed() {
        app.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_CALL
        assertFalse(cardStarts())
        Prefs.skipDuringCalls = false
        assertTrue(cardStarts())
    }

    @Test
    fun theLockToggleStartsAndStopsTheService() {
        Prefs.lockEnabled = true
        LockService.sync(app)
        assertEquals(LockService::class.java.name, shadowOf(app).nextStartedService?.component?.className)
        Prefs.lockEnabled = false
        LockService.sync(app)
        assertEquals(LockService::class.java.name, shadowOf(app).nextStoppedService?.component?.className)
    }

    @Test
    fun aNewWordEachTimeTheScreenTurnsOn() {
        val card = Robolectric.buildActivity(LockActivity::class.java).setup()
        val first = Repo.current()!!.key
        card.newIntent(Intent(app, LockActivity::class.java))
        assertEquals(first, Repo.current()!!.key)
        Prefs.newWordEachTime = true
        card.newIntent(Intent(app, LockActivity::class.java))
        assertNotEquals(first, Repo.current()!!.key)
    }

    @Test
    fun reviewWordsInTheNotification() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val w = Repo.current()!!
        Repo.answer(w, State.DONT_KNOW)
        val nm = app.getSystemService(NotificationManager::class.java)
        val title = { shadowOf(nm).allNotifications.last().extras.getCharSequence(Notification.EXTRA_TITLE).toString() }

        Robolectric.buildService(LockService::class.java).create()
        waitIo()
        assertEquals("Слова на экране блокировки", title())

        Prefs.reviewNotification = true
        Robolectric.buildService(LockService::class.java, Intent().setAction("refresh")).create().startCommand(0, 1)
        waitIo()
        assertEquals(Prefs.primary(w.cyr), title())
    }

    @Test
    fun theLockCardSpeaksANewWordButNotAWordToRecall() {
        val spoken = ArrayList<String>()
        Speaker.say = { spoken += it }
        Robolectric.buildActivity(LockActivity::class.java).setup().destroy()
        assertEquals(emptyList<String>(), spoken)

        Prefs.autoSpeak = true
        Robolectric.buildActivity(LockActivity::class.java).setup().destroy()
        assertEquals(listOf(Repo.current()!!.cyr), spoken)

        Prefs.cardMode = 1
        Robolectric.buildActivity(LockActivity::class.java).setup().destroy()
        assertEquals(1, spoken.size)
    }
}
