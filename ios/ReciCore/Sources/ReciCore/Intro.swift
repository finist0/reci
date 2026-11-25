import Foundation

// The screens of a first start: which languages to learn, then the offer to put the card on the
// lock screen. The counterpart of data/Intro.kt; tests/intro.json checks both.
//
// A new user is offered the card right after choosing the languages and, after "Later", once
// more three days on. An update from a version without the offer gets it once. Neither comes
// while the card already works, nor on a start that shows the news of an update or the report
// of a crash ("news" below).
public enum Intro {
    public static let later: TimeInterval = 3 * 24 * 60 * 60

    public enum Show: Equatable { case pair, offer, none }

    public struct State: Equatable {
        public var pairAsked: Bool
        public var offerLeft: Int
        public var offerAt: Date

        public init(pairAsked: Bool, offerLeft: Int, offerAt: Date) {
            self.pairAsked = pairAsked
            self.offerLeft = offerLeft
            self.offerAt = offerAt
        }
    }

    public static func begin(fresh: Bool) -> State {
        State(pairAsked: !fresh, offerLeft: fresh ? 2 : 1, offerAt: .distantPast)
    }

    // What this start shows, and the state after it: a card that already works ends the offers.
    public static func next(_ s: State, now: Date, works: Bool, news: Bool) -> (Show, State) {
        if !s.pairAsked { return (.pair, s) }
        if s.offerLeft <= 0 { return (.none, s) }
        if works { return (.none, done(s)) }
        if now < s.offerAt || news { return (.none, s) }
        return (.offer, s)
    }

    public static func chosen(_ s: State) -> State {
        var s = s
        s.pairAsked = true
        return s
    }

    public static func postponed(_ s: State, now: Date) -> State {
        var s = s
        s.offerLeft -= 1
        s.offerAt = now.addingTimeInterval(later)
        return s
    }

    public static func done(_ s: State) -> State {
        var s = s
        s.offerLeft = 0
        return s
    }
}
