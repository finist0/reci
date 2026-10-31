package org.kostja.reci

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.lock.LockActivity
import org.kostja.reci.lock.LockService
import org.kostja.reci.ui.MainActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The screenshots for Google Play and the README, at 1080x2160: Google Play takes no phone
// screenshot longer than twice its width. Skipped unless asked for, see docs/development.md.
@RunWith(AndroidJUnit4::class)
class StoreScreenshots {
    companion object {
        const val PULL = "/data/local/tmp/reci-shots"
    }

    @get:Rule
    val compose = createEmptyComposeRule()

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val out = File(ctx.getExternalFilesDir(null), "shots")

    private fun shell(cmd: String) {
        inst.uiAutomation.executeShellCommand(cmd).use { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).readBytes()
        }
    }

    private fun waitIo() {
        val done = CountDownLatch(1)
        Repo.afterLoad { done.countDown() }
        done.await(60, TimeUnit.SECONDS)
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(1200)
        val bmp = inst.uiAutomation.takeScreenshot()
        val file = File(out, "$name.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // The test run uninstalls the app and its files; this copy stays for adb pull.
        shell("mkdir -p $PULL")
        shell("cp ${file.absolutePath} $PULL/")
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun demoStatusBar() {
        shell("settings put global sysui_demo_allowed 1")
        for (args in listOf(
            "enter", "battery -e level 100 -e plugged false",
            "network -e wifi show -e level 4 -e mobile show -e datatype none -e level 4", "notifications -e visible false",
        )) {
            shell("am broadcast -a com.android.systemui.demo -e command ${args.substringBefore(' ')} ${args.substringAfter(' ', "")}")
        }
    }

    @Test
    fun takeScreenshots() {
        // A skipped test shows as failed in the Gradle report, so without the argument it passes empty.
        if (InstrumentationRegistry.getArguments().getString("screenshots") != "true") return
        out.mkdirs()
        shell("wm size 1080x2160")
        shell("wm density 420")
        demoStatusBar()
        val theme = Prefs.theme
        try {
            FakeServer.install(closeNotice = true)
            ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().clear().commit()
            waitIo()
            Repo.resetAll()
            waitIo()
            Repo.resetPosition()
            var activity = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(10_000) { Repo.current() != null }
            shot("1-card")

            compose.onAllNodesWithContentDescription("Сообщить об ошибке")[0].performClick()
            click("Ударение")
            shot("7-report")
            click("Отмена")

            compose.onNodeWithContentDescription("Грамматика").performClick()
            compose.waitForIdle()
            click("Падежи: обзор")
            shot("5-grammar")
            compose.onNodeWithContentDescription("Назад").performClick()
            compose.onNodeWithContentDescription("Назад").performClick()

            compose.onNodeWithContentDescription("Настройки").performClick()
            shot("6-settings")
            compose.onNodeWithText("Помочь автору").performScrollTo()
            shot("8-feedback")
            compose.onNodeWithContentDescription("Назад").performClick()

            Repo.setCategories(setOf("X:compare"))
            compose.waitUntil(10_000) { Repo.current()?.key?.startsWith("cmp:") == true }
            shot("3-compare")

            Repo.setCategories(setOf("L3", "L4"))
            Prefs.theme = 5
            compose.waitUntil(10_000) { Repo.current()?.key?.startsWith("cmp:") == false }
            shot("4-dark")
            Prefs.theme = theme

            Repo.setCategories(setOf("X:phrase"))
            activity.close()
            if (Build.VERSION.SDK_INT >= 33) {
                inst.uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.POST_NOTIFICATIONS)
            }
            shell("appops set ${ctx.packageName} SYSTEM_ALERT_WINDOW allow")
            Prefs.lockEnabled = true
            LockService.sync(ctx)
            Thread.sleep(1000)
            shell("input keyevent KEYCODE_SLEEP")
            Thread.sleep(1500)
            shell("input keyevent KEYCODE_WAKEUP")
            var card: LockActivity? = null
            for (i in 0 until 50) {
                inst.runOnMainSync {
                    card = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<LockActivity>().firstOrNull()
                }
                if (card != null) break
                Thread.sleep(200)
            }
            shot("2-lock-phrase")
            inst.runOnMainSync { card?.finish() }
        } finally {
            Prefs.theme = theme
            Prefs.lockEnabled = false
            LockService.sync(ctx)
            shell("wm dismiss-keyguard")
            shell("am broadcast -a com.android.systemui.demo -e command exit")
            shell("settings put global sysui_demo_allowed 0")
            shell("wm size reset")
            shell("wm density reset")
        }
    }
}
