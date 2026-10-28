import XCTest

// UI tests of the iPhone app in the simulator, the counterpart of CardScreenTest.kt and
// web/tests/ui/*.test.mjs. Each test starts from a fresh install.
final class ReciUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-uitest-reset"]
        app.launch()
        XCTAssertTrue(app.staticTexts["headword"].waitForExistence(timeout: 20) || app.staticTexts["articleTitle"].exists)
    }

    private var headword: String { app.staticTexts["headword"].label }

    private func openSettings() {
        app.buttons["Настройки"].tap()
        XCTAssertTrue(app.staticTexts["Настройки"].waitForExistence(timeout: 5))
    }

    private func back() { app.buttons["Назад"].firstMatch.tap() }

    private func mark(_ id: String) -> XCUIElement { app.descendants(matching: .any)[id].firstMatch }

    // Settings rows below the fold are not hittable until scrolled to.
    private func reach(_ el: XCUIElement) -> XCUIElement {
        for _ in 0..<12 where !(el.exists && el.isHittable) { app.swipeUp() }
        return el
    }

    func testNextShowsAnotherWord() {
        let first = headword
        app.buttons["next"].tap()
        XCTAssertTrue(app.staticTexts["headword"].waitForExistence(timeout: 5))
        XCTAssertNotEqual(headword, first)
    }

    func testLongPressOnKnowShowsToastAndMovesOn() {
        let first = headword
        mark("markKnow").press(forDuration: 1.0)
        XCTAssertTrue(app.descendants(matching: .any)["learnedToast"].firstMatch.waitForExistence(timeout: 3))
        XCTAssertNotEqual(headword, first)
        app.buttons["Предыдущее слово"].tap()
        XCTAssertEqual(headword, first)
    }

    func testTranslationOnlyModeHidesTheWord() {
        openSettings()
        reach(app.buttons["Перевод"].firstMatch).tap()
        back()
        XCTAssertTrue(app.buttons["Показать слово"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["headword"].exists)
        app.buttons["Показать слово"].tap()
        XCTAssertTrue(app.staticTexts["headword"].waitForExistence(timeout: 5))
    }

    func testKnowButtonsCanBeHidden() {
        XCTAssertTrue(mark("markKnow").exists)
        openSettings()
        // The label of a SwiftUI Toggle does not toggle it, so tap the switch itself at the right edge.
        reach(app.switches["Кнопки «Не знаю / Знаю»"].firstMatch).coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        back()
        XCTAssertFalse(mark("markKnow").waitForExistence(timeout: 2))
    }

    func testLatinAlphabet() {
        openSettings()
        reach(app.buttons["Latinica"].firstMatch).tap()
        back()
        XCTAssertNil(headword.range(of: "[\\u0400-\\u04FF]", options: .regularExpression))
    }
}
