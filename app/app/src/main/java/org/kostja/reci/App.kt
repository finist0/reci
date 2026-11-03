package org.kostja.reci

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import org.kostja.reci.data.Crash
import org.kostja.reci.data.DictUpdate
import org.kostja.reci.data.Grammar
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.Speaker
import org.kostja.reci.data.Telemetry
import org.kostja.reci.lock.LockService

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Crash.init(this)
        Prefs.init(this)
        Telemetry.init(this)
        DictUpdate.init(this)
        Repo.init(this)
        Grammar.init(this)
        Speaker.init(this)
        Repo.loadAsync()
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(LockService.CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Слова на экране блокировки")
                .setShowBadge(false)
                .build()
        )
    }
}
