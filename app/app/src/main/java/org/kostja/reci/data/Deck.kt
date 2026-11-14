package org.kostja.reci.data

import android.content.Context
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

// A pair of languages: the one learned (target) and the user's own (native), the language of
// the translations. Decks are listed in assets/decks.json, so a new pair needs data only.
data class Deck(val id: String, val target: String, val native: String, val words: String, val grammar: String) {
    // The deck of words.json: the dictionary patches (DictUpdate) are made for it only.
    val isFirst: Boolean get() = id == Decks.FIRST

    // Progress belongs to the language learned, not to the deck: a Serbian word learned with
    // Russian translations stays learned with English ones. The decks of one language learned
    // keep it under the id of the first of them listed.
    val store: String get() = Decks.all.firstOrNull { it.target == target }?.id ?: id

    // Cyrillic in the translations that the script setting turns into Latin: Serbian ones, or
    // Serbian words quoted in translations into a language not written in Cyrillic.
    val serbianInNative: Boolean get() = native == "sr" || target == "sr" && native !in Decks.cyrillic
}

object Decks {
    const val FIRST = "sr-ru"
    internal val cyrillic = setOf("ru", "sr")
    private lateinit var ctx: Context

    fun init(c: Context) {
        ctx = c.applicationContext
    }

    private val listed: List<Deck> by lazy {
        val a = JSONArray(ctx.assets.open("decks.json").bufferedReader().use { it.readText() })
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            Deck(it.getString("id"), it.getString("target"), it.getString("native"), it.getString("words"), it.optString("grammar"))
        }
    }

    // Tests set it to see a list of decks the app does not carry.
    internal var allOverride: List<Deck>? = null

    val all: List<Deck>
        get() = allOverride ?: listed

    // The settings choose the language learned and the language of the translations, not a deck.
    val targets: List<String>
        get() = all.map { it.target }.distinct()

    fun natives(target: String) = all.filter { it.target == target }.map { it.native }

    // The deck of the two languages, else the first one of the language learned: a change of
    // the language learned keeps the translations where a deck has them.
    fun pick(target: String, native: String) =
        all.firstOrNull { it.target == target && it.native == native } ?: all.first { it.target == target }

    val current: Deck
        get() = all.firstOrNull { it.id == Prefs.deck } ?: all.first()

    // Serbian and the languages close to it.
    private val serbian = setOf("sr", "hr", "bs", "sh", "cnr", "mk", "sl")

    private val russianReaders = setOf("ru", "uk", "be")

    // A first start opens Serbian with Russian translations on a phone set to Russian, Ukrainian
    // or Belarusian, Russian for Serbs on one set to Serbian or a close language, and Serbian with
    // English translations elsewhere.
    fun guess(lang: String = Locale.getDefault().language): String {
        val id = when {
            lang in russianReaders -> FIRST
            lang in serbian -> "ru-sr"
            else -> "sr-en"
        }
        return if (all.any { it.id == id }) id else FIRST
    }

    // The interface language of a first start: the phone's, if the interface speaks it.
    fun guessUi(lang: String = Locale.getDefault().language) = when (lang) {
        "ru" -> "ru"
        in serbian -> "sr"
        else -> "en"
    }

    // Storage names of what belongs to the language learned (Deck.store): the first deck keeps
    // the names it had before there were others.
    fun scoped(name: String, deck: Deck = current) = if (deck.store == FIRST) name else "${deck.store}.$name"

    fun howToSay() = t(
        when (current.target) {
            "ru" -> "Как сказать по-русски"
            "en" -> "Как сказать по-английски"
            else -> "Как сказать по-сербски"
        }
    )

    private fun langName(code: String, l: String) = I18n.tIn(
        l,
        when (code) {
            "sr" -> "сербский"
            "ru" -> "русский"
            "en" -> "английский"
            else -> code
        },
    )

    // A language as an option of the settings, named in the interface language.
    fun name(code: String) = langName(code, I18n.lang).replaceFirstChar { it.titlecase() }

    // A word to show the font size and the stress marks on.
    val sample: String
        get() = when (current.target) {
            "ru" -> "сло́во"
            "en" -> "word"
            else -> "реч"
        }

    // How the stress setting looks: Serbian marks four accents, Russian only the place.
    val accentSamples: List<String>
        get() = if (current.target == "sr") listOf("ку̏ћа", "ку́ћа", "кућа") else listOf("доро́га", "дорога")

    // Search: a word in the user's language, or in the language learned.
    val meaningHint: String
        get() = t(
            when (current.native) {
                "sr" -> "слово по-сербски"
                "en" -> "слово по-английски"
                else -> "слово по-русски"
            }
        )
    val wordHint: String
        get() = when (current.target) {
            "ru" -> "слово"
            "en" -> "word"
            else -> "реч, reč"
        }
    val wordChip: String
        get() = t(
            when (current.target) {
                "ru" -> "Русское слово"
                "en" -> "Английское слово"
                else -> "Сербское слово"
            }
        )

    val noVoice: String
        get() = t(
            when (current.target) {
                "ru" -> "Нет русского голоса"
                "en" -> "Нет английского голоса"
                else -> "Нет сербского, хорватского или боснийского голоса"
            }
        )

    val shareText: String
        get() = t(
            when (current.id) {
                "ru-sr" -> "НЕРодная речь — русские слова для тех, кто говорит по-сербски: карточки с ударениями и примерами. Бесплатно."
                "sr-en" -> "НЕРодная речь — сербские слова для тех, кто говорит по-английски: карточки с ударениями и примерами. Бесплатно."
                "ru-en" -> "НЕРодная речь — русские слова для тех, кто говорит по-английски: карточки с ударениями и примерами. Бесплатно."
                else -> "НЕРодная речь — сербские слова для тех, кто говорит по-русски: карточки с ударениями, примерами и грамматикой. Бесплатно."
            }
        )

    // The script setting matters wherever Serbian is shown.
    val hasSerbian: Boolean
        get() = current.target == "sr" || current.native == "sr" || I18n.lang == "sr"
}

object I18n {
    private lateinit var ctx: Context

    fun init(c: Context) {
        ctx = c.applicationContext
    }

    // Russian text -> language -> translation. Russian is the key, so code reads as before.
    private val table: Map<String, Map<String, String>> by lazy {
        val o = JSONObject(ctx.assets.open("ui.json").bufferedReader().use { it.readText() })
        o.keys().asSequence().associateWith { k ->
            val tr = o.getJSONObject(k)
            tr.keys().asSequence().associateWith { tr.getString(it) }
        }
    }

    // Tests set it to see a language without switching decks.
    internal var langOverride: String? = null

    val lang: String
        get() = langOverride ?: Prefs.uiLang

    fun t(ru: String): String = tIn(lang, ru)

    fun tIn(l: String, ru: String): String {
        if (l == "ru") return ru
        val s = table[ru]?.get(l) ?: ru
        return if (l == "sr" && Prefs.latinFirst) Translit.toLatin(s) else s
    }

    fun has(ru: String, l: String) = table[ru]?.containsKey(l) == true
}

fun t(ru: String): String = I18n.t(ru)

fun t(ru: String, vararg args: Any?): String = I18n.t(ru).format(*args)

// Russian and Serbian have three plural forms, English two: the forms are given in Russian.
fun plural(n: Int, one: String, few: String, many: String): String {
    if (I18n.lang == "en") return t(if (n == 1) one else many)
    val m10 = n % 10
    val m100 = n % 100
    return t(
        when {
            m10 == 1 && m100 != 11 -> one
            m10 in 2..4 && m100 !in 12..14 -> few
            else -> many
        }
    )
}
