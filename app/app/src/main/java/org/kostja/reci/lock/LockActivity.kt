package org.kostja.reci.lock

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.Speaker
import org.kostja.reci.ui.CardScreen
import org.kostja.reci.ui.MainActivity
import org.kostja.reci.ui.ReciTheme

class LockActivity : ComponentActivity() {
    private var spokenKey: String? = null

    // A call that starts while the card is waiting behind the dark screen closes it.
    private val modeListener = AudioManager.OnModeChangedListener { if (isInCall(this)) finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Prefs.autoSpeak) Speaker.connect()
        if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(AudioManager::class.java).addOnModeChangedListener(mainExecutor, modeListener)
        }
        setContent {
            Prefs.version.intValue
            ReciTheme {
                CardScreen(
                    onNext = { Repo.step(1) },
                    onDone = {
                        Repo.step(1)
                        finish()
                    },
                    onSearch = { openApp("search") },
                    onSettings = { openApp("settings") },
                    onCategories = { openApp("categories") },
                    onGrammar = { id -> openApp(if (id == null) "grammar" else "grammar:$id") },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (isInCall(this)) finish()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(AudioManager::class.java).removeOnModeChangedListener(modeListener)
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Prefs.newWordEachTime) Repo.step(1)
    }

    override fun onResume() {
        super.onResume()
        // Also resumed briefly when started on screen-off; speak only on a screen the user sees.
        if (!getSystemService(PowerManager::class.java).isInteractive) return
        val w = Repo.current() ?: return
        if (spokenKey != w.key) {
            spokenKey = w.key
            Speaker.autoSpeak(w)
        }
    }

    private fun openApp(route: String) {
        val go = {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_ROUTE, route)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
        }
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isKeyguardLocked) {
            go()
            return
        }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = go()
        })
    }

    companion object {
        // Every mode above NORMAL is call related: ringing, a phone or VoIP call, call screening.
        fun isInCall(ctx: Context) =
            Prefs.skipDuringCalls && ctx.getSystemService(AudioManager::class.java).mode > AudioManager.MODE_NORMAL

        fun show(ctx: Context) {
            // Without the overlay permission Android blocks activity starts from the background.
            if (!Settings.canDrawOverlays(ctx)) return
            if (Prefs.pausedUntil > System.currentTimeMillis()) return
            if (isInCall(ctx)) return
            ctx.startActivity(
                Intent(ctx, LockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }
}
