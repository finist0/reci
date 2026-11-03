package org.kostja.reci.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.Locale

// Where the app runs, sent with every message and crash report so that a card can be seen as
// the user saw it: device, screen, font, settings and the permissions of the lock card. No
// words, answers or progress.
object Env {
    fun of(ctx: Context): JSONObject {
        val c = ctx.resources.configuration
        val o = JSONObject()
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".take(120))
            .put("sdk", Build.VERSION.SDK_INT)
            .put("screen", "${c.screenWidthDp}x${c.screenHeightDp} dp")
            .put("density", ctx.resources.displayMetrics.density.toDouble())
            .put("font", c.fontScale.toDouble())
            .put("dark", c.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
            .put("locale", Locale.getDefault().toLanguageTag().take(120))
            .put("dict", DictUpdate.describe())
            .put("mode", Prefs.cardMode)
            .put("theme", Prefs.theme)
            .put("latin", Prefs.latinFirst)
            .put("both", Prefs.showBothScripts)
            .put("stress", Prefs.accentMode)
            .put("size", Prefs.wordSize)
            .put("marks", Prefs.useMarks)
            .put("review", Prefs.reviewKnown)
            .put("speak", Prefs.autoSpeak)
            .put("active", Prefs.activeSize)
            .put("order", Prefs.order.ordinal)
            .put("lock", Prefs.lockEnabled)
            .put("calls", Prefs.skipDuringCalls)
            .put("newEach", Prefs.newWordEachTime)
            .put("overlay", Settings.canDrawOverlays(ctx))
            .put("battery", ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName))
            .put("phone", granted(ctx, Manifest.permission.READ_PHONE_STATE))
        if (Build.VERSION.SDK_INT >= 33) o.put("notify", granted(ctx, Manifest.permission.POST_NOTIFICATIONS))
        return o
    }

    private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
}
