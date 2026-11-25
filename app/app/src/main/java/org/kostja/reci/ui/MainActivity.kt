package org.kostja.reci.ui

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import org.kostja.reci.R
import org.kostja.reci.data.DictUpdate
import org.kostja.reci.data.Intro
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.Speaker
import org.kostja.reci.data.Telemetry
import org.kostja.reci.data.WhatsNew
import org.kostja.reci.lock.LockService

sealed interface Screen {
    data object Card : Screen
    data object Categories : Screen
    data object Search : Screen
    // From the lock screen offer, the settings show the steps to set up the card.
    data class Settings(val lockGuide: Boolean = false) : Screen
    data object Schools : Screen
    data class Detail(val key: String, val report: Boolean = false) : Screen
    data class Words(val category: String) : Screen
    data object GrammarIndex : Screen
    data class GrammarTopic(val id: String) : Screen

    // The screen's name in the statistics of opened screens.
    val statName: String
        get() = when (this) {
            Card -> "card"
            Categories -> "categories"
            Search -> "search"
            is Settings -> "settings"
            Schools -> "schools"
            is Detail -> "detail"
            is Words -> "list"
            GrammarIndex -> "grammar"
            is GrammarTopic -> "topic"
        }

    // The route back to this screen, which fromSaved reads: a recreated activity keeps the stack.
    val route: String
        get() = when (this) {
            Card -> "card"
            Categories -> "categories"
            Search -> "search"
            is Settings -> if (lockGuide) "settings:guide" else "settings"
            Schools -> "schools"
            is Detail -> if (report) "report:$key" else "grammar:w:$key"
            is Words -> "list:$category"
            GrammarIndex -> "grammar"
            is GrammarTopic -> "grammar:$id"
        }

    companion object {
        // The routes the lock card sends; an intent from another app may carry them too.
        fun fromRoute(route: String?): Screen? = when (route) {
            "categories" -> Categories
            "search" -> Search
            "settings" -> Settings()
            "schools" -> Schools
            "grammar" -> GrammarIndex
            else -> when {
                route?.startsWith("report:") == true -> Detail(route.removePrefix("report:"), report = true)
                route?.startsWith("grammar:") == true -> fromGrammar(route.substringAfter(':'))
                else -> null
            }
        }

        // A link from a card or a grammar topic: a comparison, a word or another topic.
        fun fromGrammar(id: String): Screen = when {
            id.startsWith("cmp:") -> Detail(id)
            id.startsWith("w:") -> Detail(id.drop(2))
            else -> GrammarTopic(id)
        }

        // Also the screens that only the app itself opens.
        fun fromSaved(route: String): Screen? = when {
            route == "card" -> Card
            route == "settings:guide" -> Settings(lockGuide = true)
            route.startsWith("list:") -> Words(route.removePrefix("list:"))
            else -> fromRoute(route)
        }
    }
}

class MainActivity : ComponentActivity() {
    private val stack = mutableStateListOf<Screen>(Screen.Card)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The activity label is the short one for the launcher; recents get the full name.
        @Suppress("DEPRECATION")
        setTaskDescription(ActivityManager.TaskDescription(getString(R.string.app_name)))
        LockService.sync(this)
        WhatsNew.checkOnce(this)
        // A recreated activity (a turned screen, a new theme) has handled its intent already.
        if (savedInstanceState == null) {
            handle(intent)
        } else {
            stack.clear()
            stack.addAll(savedInstanceState.getStringArrayList(STACK)!!.map { Screen.fromSaved(it)!! })
        }
        setContent {
            Prefs.version.intValue
            ReciTheme {
                if (Intro.showing.value == Intro.Show.PAIR) {
                    PairScreen { Intro.choose(applicationContext, it) }
                    return@ReciTheme
                }
                // Each screen in the stack keeps its saveable state (tab, query, scroll)
                // until it is popped, so coming back from a card restores it.
                val holder = rememberSaveableStateHolder()
                val stateKey = { i: Int -> "$i:${stack[i]}" }
                val go = { s: Screen ->
                    Telemetry.count("opened", s.statName)
                    stack.add(s)
                    Unit
                }
                val back = {
                    if (stack.size > 1) {
                        holder.removeState(stateKey(stack.lastIndex))
                        stack.removeAt(stack.lastIndex)
                    }
                    Unit
                }
                BackHandler(enabled = stack.size > 1) { back() }
                val s = stack.last()
                holder.SaveableStateProvider(stateKey(stack.lastIndex)) {
                when (s) {
                    Screen.Card -> {
                        val w = Repo.current()
                        LaunchedEffect(w?.key) { if (w != null) Speaker.autoSpeak(w) }
                        CardScreen(
                            onNext = { Repo.step(1) },
                            onSearch = { go(Screen.Search) },
                            onSettings = { go(Screen.Settings()) },
                            onCategories = { go(Screen.Categories) },
                            onSchools = { go(Screen.Schools) },
                            onGrammar = { id -> go(if (id == null) Screen.GrammarIndex else Screen.fromGrammar(id)) },
                        )
                    }
                    Screen.Categories -> CategoriesScreen(openList = { go(Screen.Words(it)) }, back = back)
                    is Screen.Words -> WordListScreen(s.category, open = { go(Screen.Detail(it)) }, back = back)
                    Screen.Search -> SearchScreen(open = { go(Screen.Detail(it)) }, back = back)
                    is Screen.Settings -> SettingsScreen(
                        lockGuide = s.lockGuide,
                        openCategories = { go(Screen.Categories) },
                        openSchools = { go(Screen.Schools) },
                        back = back,
                    )
                    Screen.Schools -> SchoolsScreen(back)
                    is Screen.Detail -> DetailScreen(s.key, back, s.report, onSchools = { go(Screen.Schools) }) { go(Screen.fromGrammar(it)) }
                    Screen.GrammarIndex -> GrammarIndexScreen(
                        open = { go(Screen.GrammarTopic(it)) },
                        openCard = { go(Screen.Detail(it)) },
                        back = back,
                    )
                    is Screen.GrammarTopic -> GrammarTopicScreen(s.id, open = { go(Screen.GrammarTopic(it)) }, back = back)
                }
                }
                if (Intro.showing.value == Intro.Show.OFFER) {
                    LockOffer(onLater = Intro::later, onSetUp = {
                        Intro.setUp()
                        go(Screen.Settings(lockGuide = true))
                    })
                }
                WhatsNewDialog()
                CrashDialog()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intro.check(this)
        sendStats()
        DictUpdate.check()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) Intro.leftScreen()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STACK, ArrayList(stack.map { it.route }))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        // Before Android 12, Back on the card finishes the activity, and the recent apps start it
        // again with the intent that first opened it.
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val s = Screen.fromRoute(intent.getStringExtra(EXTRA_ROUTE)) ?: return
        // The same word reported again from the lock card needs its form up again.
        if (stack.last() != s || (s is Screen.Detail && s.report)) stack.add(s)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        private const val STACK = "stack"
    }
}
