package org.kostja.reci

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.kostja.reci.data.Telemetry
import java.util.concurrent.CopyOnWriteArrayList

// Stands in for the statistics server in UI tests, so that no test reaches the real one.
object FakeServer {
    val posts = CopyOnWriteArrayList<Pair<String, JSONObject>>()

    @Volatile
    var status = 204

    // A new install as far as statistics go; `closeNotice` hides the notice about them.
    fun install(closeNotice: Boolean) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("telemetry", Context.MODE_PRIVATE).edit().clear().commit()
        Telemetry.init(ctx)
        if (closeNotice) {
            Telemetry.markNoticeShown()
            Telemetry.closeNotice()
        }
        posts.clear()
        status = 204
        Telemetry.post = { url, body ->
            posts.add(url.substringAfterLast('/') to JSONObject(body))
            status
        }
    }

    fun of(kind: String) = posts.filter { it.first == kind }.map { it.second }
}
