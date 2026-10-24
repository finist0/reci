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
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.Speaker
import org.kostja.reci.lock.LockService

sealed interface Screen {
    data object Card : Screen
    data object Categories : Screen
    data object Search : Screen
    data object Settings : Screen
    data class Detail(val key: String) : Screen
    data class Words(val category: String) : Screen
    data object GrammarIndex : Screen
    data class GrammarTopic(val id: String) : Screen

    companion object {
        fun fromRoute(route: String?): Screen? = when (route) {
            "categories" -> Categories
            "search" -> Search
            "settings" -> Settings
            "grammar" -> GrammarIndex
            else -> when {
                route == null || !route.startsWith("grammar:") -> null
                route.startsWith("grammar:cmp:") -> Detail(route.substringAfter(':'))
                route.startsWith("grammar:w:") -> Detail(route.removePrefix("grammar:w:"))
                else -> GrammarTopic(route.substringAfter(':'))
            }
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
        handle(intent)
        setContent {
            Prefs.version.intValue
            ReciTheme {
                // Each screen in the stack keeps its saveable state (tab, query, scroll)
                // until it is popped, so coming back from a card restores it.
                val holder = rememberSaveableStateHolder()
                val stateKey = { i: Int -> "$i:${stack[i]}" }
                val go = { s: Screen -> stack.add(s); Unit }
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
                        LaunchedEffect(w?.key) { if (w != null && Prefs.autoSpeak) Speaker.speak(w.cyr) }
                        CardScreen(
                            onNext = { Repo.step(1) },
                            onSearch = { go(Screen.Search) },
                            onSettings = { go(Screen.Settings) },
                            onCategories = { go(Screen.Categories) },
                            onGrammar = { id ->
                                go(
                                    when {
                                        id == null -> Screen.GrammarIndex
                                        id.startsWith("cmp:") -> Screen.Detail(id)
                                        id.startsWith("w:") -> Screen.Detail(id.drop(2))
                                        else -> Screen.GrammarTopic(id)
                                    }
                                )
                            },
                        )
                    }
                    Screen.Categories -> CategoriesScreen(openList = { go(Screen.Words(it)) }, back = back)
                    is Screen.Words -> WordListScreen(s.category, open = { go(Screen.Detail(it)) }, back = back)
                    Screen.Search -> SearchScreen(open = { go(Screen.Detail(it)) }, back = back)
                    Screen.Settings -> SettingsScreen(openCategories = { go(Screen.Categories) }, back = back)
                    is Screen.Detail -> DetailScreen(s.key, back) { id ->
                        go(
                            when {
                                id.startsWith("cmp:") -> Screen.Detail(id)
                                id.startsWith("w:") -> Screen.Detail(id.drop(2))
                                else -> Screen.GrammarTopic(id)
                            }
                        )
                    }
                    Screen.GrammarIndex -> GrammarIndexScreen(
                        open = { go(Screen.GrammarTopic(it)) },
                        openCard = { go(Screen.Detail(it)) },
                        back = back,
                    )
                    is Screen.GrammarTopic -> GrammarTopicScreen(s.id, open = { go(Screen.GrammarTopic(it)) }, back = back)
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val s = Screen.fromRoute(intent?.getStringExtra(EXTRA_ROUTE)) ?: return
        if (stack.last() != s) stack.add(s)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
