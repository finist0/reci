package org.kostja.reci.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.kostja.reci.data.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.lockEnabled) LockService.sync(context)
    }
}
