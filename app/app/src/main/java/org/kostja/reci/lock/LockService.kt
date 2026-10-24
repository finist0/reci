package org.kostja.reci.lock

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.kostja.reci.R
import org.kostja.reci.data.Labels
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.ui.MainActivity

class LockService : Service() {
    // The card is started when the screen goes off, so it is already in place
    // by the time the user turns the screen back on.
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LockActivity.show(context)
            if (Prefs.reviewNotification) updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        } catch (_: IllegalStateException) {
            // A background start the system does not allow; better silent than a crash loop.
            stopSelf()
            return
        }
        ContextCompat.registerReceiver(
            this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true
        // The review word needs the dictionary that App.onCreate is loading.
        Repo.afterLoad { updateNotification() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) updateNotification()
        return START_STICKY
    }

    private var registered = false

    override fun onDestroy() {
        if (registered) unregisterReceiver(screenOff)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
        val w = if (Prefs.reviewNotification) Repo.reviewWord() else null
        if (w == null) {
            b.setContentTitle("Слова на экране блокировки")
        } else {
            b.setContentTitle(Prefs.primary(w.cyr))
            b.setContentText(w.meanings.mapIndexed { i, m -> "${Labels.circled(i)} $m" }.joinToString("  "))
        }
        return b.build()
    }

    private fun updateNotification() {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification())
        } catch (_: SecurityException) {
            // Notifications are not allowed; the service keeps running without them.
        }
    }

    companion object {
        const val CHANNEL = "lock"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_REFRESH = "refresh"

        fun sync(ctx: Context) {
            val i = Intent(ctx, LockService::class.java)
            if (Prefs.lockEnabled) ContextCompat.startForegroundService(ctx, i) else ctx.stopService(i)
        }

        fun refresh(ctx: Context) {
            if (!Prefs.lockEnabled) return
            ContextCompat.startForegroundService(ctx, Intent(ctx, LockService::class.java).setAction(ACTION_REFRESH))
        }
    }
}
