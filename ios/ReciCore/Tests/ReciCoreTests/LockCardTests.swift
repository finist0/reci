import XCTest
@testable import ReciCore

// What the lock screen card widget shows, before the app ran, with words left and without.
final class LockCardTests: XCTestCase {
    private func repo() -> Repo { Repo(store: Fixtures.tempStore(), words: Fixtures.words) }

    func testBeforeTheAppRanItAsksToOpenIt() {
        XCTAssertEqual(LockCard.of(nil), LockCard(title: nil, subtitle: "Откройте приложение", recall: false))
    }

    func testWithTheWordsUsedUpItSuggestsOthers() {
        let r = repo()
        r.setCategories(["M:fav"], order: 0)
        r.step(1)
        XCTAssertNil(r.current())
        XCTAssertEqual(LockCard.of(r).subtitle, "Слова закончились: выберите другие в приложении")
    }

    func testANewWordShowsTheWordAndARecalledOneItsTranslation() {
        let r = repo()
        r.step(1)
        let w = r.current()!
        XCTAssertEqual(LockCard.of(r), LockCard(title: r.prefs.headword(w), subtitle: w.meanings.prefix(2).joined(separator: ", "), recall: false))
        var p = r.prefs
        p.cardMode = 1
        r.prefs = p
        XCTAssertEqual(LockCard.of(r).subtitle, "вспомните слово")
        XCTAssertTrue(LockCard.of(r).recall)
    }

    func testShowingTheCardNeitherMovesOnNorCounts() {
        let r = repo()
        r.step(1)
        let before = (r.prefs.cardCount, r.current()?.key, r.store.progress)
        _ = LockCard.of(Repo(store: Store(dir: r.store.dir), words: Fixtures.words))
        r.store.reload()
        XCTAssertEqual(r.prefs.cardCount, before.0)
        XCTAssertEqual(r.current()?.key, before.1)
        XCTAssertEqual(r.store.progress, before.2)

        let untouched = repo()
        _ = LockCard.of(untouched)
        XCTAssertEqual(untouched.prefs.cardCount, 0)
        XCTAssertNil(untouched.prefs.currentKey)
    }
}
