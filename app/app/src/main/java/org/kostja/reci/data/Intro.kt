package org.kostja.reci.data

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf

// The screens of a first start: which languages to learn, then the offer to put the card on the
// lock screen. The counterpart of ReciCore/Intro.swift; tests/intro.json checks both.
//
// A new user is offered the card right after choosing the languages and, after "Later", once
// more three days on. An update from a version without the offer gets it once. Neither comes
// while the card already works, nor on a start that shows the news of an update or the report
// of a crash ("news" below).
object Intro {
    const val LATER = 3 * 24 * 60 * 60 * 1000L

    enum class Show { PAIR, OFFER, NONE }

    data class State(val pairAsked: Boolean, val offerLeft: Int, val offerAt: Long)

    fun begin(fresh: Boolean) = State(pairAsked = !fresh, offerLeft = if (fresh) 2 else 1, offerAt = 0)

    // What this start shows, and the state after it: a card that already works ends the offers.
    fun next(s: State, now: Long, works: Boolean, news: Boolean): Pair<Show, State> = when {
        !s.pairAsked -> Show.PAIR to s
        s.offerLeft <= 0 -> Show.NONE to s
        works -> Show.NONE to s.copy(offerLeft = 0)
        now < s.offerAt || news -> Show.NONE to s
        else -> Show.OFFER to s
    }

    fun chosen(s: State) = s.copy(pairAsked = true)

    fun postponed(s: State, now: Long) = s.copy(offerLeft = s.offerLeft - 1, offerAt = now + LATER)

    fun done(s: State) = s.copy(offerLeft = 0)

    // What the main screen shows over the cards, kept in Prefs.intro between starts.
    val showing = mutableStateOf(Show.NONE)

    // News or a crash report came up while the app is in front: the offer waits until the app
    // comes to the front again with them closed. A turned screen is not a new start.
    private var held = false

    // The card works once it is on and Android lets it over the lock screen.
    fun check(ctx: Context) {
        if (showing.value != Show.NONE) return
        held = held || WhatsNew.told || Crash.pending != null
        val (show, s) = next(Prefs.intro, System.currentTimeMillis(), Prefs.lockEnabled && Settings.canDrawOverlays(ctx), held)
        if (s != Prefs.intro) Prefs.intro = s
        showing.value = show
    }

    // The cards of the pair chosen are loading. Kept here rather than in the screen's saved
    // state: a process that died meanwhile has no load to wait for.
    val loading = mutableStateOf(false)

    // The choice stays on screen until the cards of the pair chosen are in, so that no card of
    // the guess comes up and counts meanwhile.
    fun choose(ctx: Context, deck: String) {
        val finish = {
            Prefs.intro = chosen(Prefs.intro)
            showing.value = Show.NONE
            check(ctx)
        }
        if (deck == Prefs.deck) return finish()
        loading.value = true
        Repo.switchDeck(deck) {
            loading.value = false
            finish()
        }
    }

    fun later() {
        Prefs.intro = postponed(Prefs.intro, System.currentTimeMillis())
        showing.value = Show.NONE
    }

    fun setUp() {
        Prefs.intro = done(Prefs.intro)
        showing.value = Show.NONE
    }

    fun leftScreen() {
        held = WhatsNew.told || Crash.pending != null
    }
}
