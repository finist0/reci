import XCTest
@testable import ReciCore

final class TextTests: XCTestCase {
    func testLatin() {
        XCTAssertEqual(Translit.toLatin("ћерка, ђак, џем"), "ćerka, đak, džem")
        XCTAssertEqual(Translit.toLatin("Љубав"), "Ljubav")
        XCTAssertEqual(Translit.toLatin("ЉУБАВ"), "LJUBAV")
        XCTAssertEqual(Translit.toLatin("ку̏ћа"), "kȕća")
    }

    func testStressPlaceOnly() {
        XCTAssertEqual(Translit.placeOnly("ку̏ћа"), "ку́ћа")
    }

    func testExampleFollowsAccentSetting() {
        let ex = "Имамо малу [ку̏ћу] на [сѐлу]."
        XCTAssertEqual(Translit.showAccents(ex, 0), ex)
        XCTAssertEqual(Translit.showAccents(ex, 1), "Имамо малу [ку́ћу] на [се́лу].")
        XCTAssertEqual(Translit.showAccents(ex, 2), "Имамо малу [кућу] на [селу].")
        XCTAssertEqual(Translit.toLatin(Translit.showAccents(ex, 1)), "Imamo malu [kúću] na [sélu].")
    }

    func testStripLeavesOtherMarksAlone() {
        XCTAssertEqual(Translit.strip("йё ку̏ћа̄"), "йё кућа")
        XCTAssertEqual(Translit.strip("кућа"), "кућа")
        XCTAssertEqual(Translit.strip("ćevapi, kȕća, с́"), "ćevapi, kȕća, с́")
    }

    func testHeadwordFollowsSettings() {
        let w = Fixtures.words.first { !$0.acc.isEmpty && $0.pos != "note" }!
        var p = Prefs()
        p.accentMode = 0
        XCTAssertEqual(p.headword(w), w.acc)
        p.accentMode = 2
        XCTAssertEqual(p.headword(w), w.cyr)
        p.latinFirst = true
        XCTAssertEqual(p.headword(w), Translit.toLatin(w.cyr))
        XCTAssertEqual(p.secondary(w.cyr), w.cyr)
        p.showBothScripts = false
        XCTAssertNil(p.secondary(w.cyr))
    }

    func testPlural() {
        XCTAssertEqual(plural(1, "карточку", "карточки", "карточек"), "карточку")
        XCTAssertEqual(plural(3, "карточку", "карточки", "карточек"), "карточки")
        XCTAssertEqual(plural(12, "карточку", "карточки", "карточек"), "карточек")
        XCTAssertEqual(plural(21, "карточку", "карточки", "карточек"), "карточку")
    }
}
