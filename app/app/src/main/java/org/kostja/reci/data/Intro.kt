package org.kostja.reci.data

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
}
