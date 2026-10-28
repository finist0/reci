import XCTest
@testable import ReciCore

// The app and its lock screen widgets run in two processes over the same files.
final class SharedStoreTests: XCTestCase {
    func testTheWidgetsAndTheAppCountAShowingOnce() {
        let app = Repo(store: Fixtures.tempStore(), words: Fixtures.words)
        app.step(1)
        app.markShown(app.current()!)
        let widget = Repo(store: Store(dir: app.store.dir), words: Fixtures.words)
        let w = widget.current()!
        widget.answer(w, Mark.known)
        widget.step(1)
        let next = widget.current()!
        widget.markShown(next)

        app.store.reload()
        XCTAssertEqual(app.current()?.key, next.key)
        app.markShown(next)
        XCTAssertEqual(app.progress(next).seen, 1)
        XCTAssertEqual(app.todayCount, 2)
    }

    func testBackReachesTheCardAWidgetAnswered() {
        let app = Repo(store: Fixtures.tempStore(), words: Fixtures.words)
        app.step(1)
        let first = app.current()!
        let widget = Repo(store: Store(dir: app.store.dir), words: Fixtures.words)
        widget.answer(first, Mark.dontKnow)
        widget.step(1)

        app.reloadFromStore()
        XCTAssertNotEqual(app.current()?.key, first.key)
        app.step(-1)
        XCTAssertEqual(app.current()?.key, first.key)
    }

    func testARestartDoesNotCountTheCardAgain() {
        let first = Repo(store: Fixtures.tempStore(), words: Fixtures.words)
        first.step(1)
        let w = first.current()!
        first.markShown(w)
        let again = Repo(store: Store(dir: first.store.dir), words: Fixtures.words)
        again.markShown(again.current()!)
        XCTAssertEqual(again.progress(w).seen, 1)
    }
}
