package org.kostja.reci.data

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

object Speaker : TextToSpeech.OnInitListener {
    private lateinit var ctx: Context
    private var tts: TextToSpeech? = null
    private var ready = false
    private var latinOnly = false
    private var pending: String? = null

    // Null until the engine has answered, then the name of the voice language in use.
    val voice = mutableStateOf<String?>(null)
    val failed = mutableStateOf(false)

    fun init(c: Context) {
        ctx = c.applicationContext
    }

    // Binding keeps the engine process alive, so it is done on first use only.
    fun connect() {
        if (tts == null) tts = TextToSpeech(ctx, this)
    }

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            failed.value = true
            return
        }
        // Croatian and Bosnian voices read Serbian Latin text well enough.
        val candidates = listOf("sr-RS" to false, "sr" to false, "hr-HR" to true, "bs-BA" to true)
        for ((tag, latin) in candidates) {
            val loc = Locale.forLanguageTag(tag)
            if (t.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) {
                t.language = loc
                latinOnly = latin
                ready = true
                voice.value = loc.getDisplayName(Locale.forLanguageTag("ru"))
                pending?.let { speak(it) }
                pending = null
                return
            }
        }
        failed.value = true
    }

    fun speak(cyr: String) {
        connect()
        if (!ready) {
            pending = cyr
            return
        }
        tts?.speak(if (latinOnly) Translit.toLatin(cyr) else cyr, TextToSpeech.QUEUE_FLUSH, null, "w")
    }
}
