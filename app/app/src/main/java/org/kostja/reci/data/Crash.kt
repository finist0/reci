package org.kostja.reci.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// A crash is written down as it happens, in the app and in the lock card alike, and offered
// to the developer at the next start. Nothing leaves the phone without the user's yes.
object Crash {
    // The server takes up to 12000 characters.
    private const val MAX = 11000

    private lateinit var file: File
    private var version = ""

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "crash.txt")
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        version = "${info.versionName} (${info.versionCode})"
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { file.writeText(report(t, e)) }
            previous?.uncaughtException(t, e)
        }
    }

    fun report(t: Thread, e: Throwable): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date())
        return "$time, version $version, thread ${t.name}\n${e.stackTraceToString()}".take(MAX)
    }

    // The report of the last crash, until it is sent or declined.
    val pending: String? get() = runCatching { file.readText() }.getOrNull()?.ifBlank { null }

    fun clear() {
        file.delete()
    }
}
