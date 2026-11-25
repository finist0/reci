import XCTest

// The first start screens: the language pair and the lock screen offer with its two ways, the
// counterpart of IntroUiTest.kt. "-uitest-intro" lets a UI test see them.
final class IntroUITests: XCTestCase {
    private var app: XCUIApplication!

    private func launch(_ args: [String]) {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = args
        app.launch()
    }

    private func gone(_ el: XCUIElement) {
        wait(for: [expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: el)], timeout: 10)
    }

    private func choosePair() {
        XCTAssertTrue(app.buttons["target:sr"].waitForExistence(timeout: 20))
        XCTAssertFalse(app.staticTexts["headword"].exists, "no cards behind the choice")
        app.buttons["target:ru"].tap()
        XCTAssertTrue(app.buttons["native:sr"].waitForExistence(timeout: 5), "Russian is translated into Serbian")
        app.buttons["target:sr"].tap()
        // Not the guess, so that the cards of another deck load before the choice goes.
        app.buttons["native:en"].tap()
        app.buttons["pairStart"].tap()
    }

    func testAFirstStartChoosesTheLanguagesThenOffersTheCardAndShowsBothWays() {
        launch(fresh + ["-uitest-intro"])
        choosePair()
        XCTAssertTrue(app.buttons["lockLater"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.buttons["lockSetUp"].label, "Настроить виджеты")

        app.buttons["lookWidgets"].tap()
        XCTAssertTrue(app.buttons["lookBack"].waitForExistence(timeout: 5))
        app.buttons["lookBack"].tap()
        gone(app.buttons["lookBack"])
        app.buttons["lookWallpaper"].tap()
        XCTAssertTrue(app.buttons["lookBack"].waitForExistence(timeout: 5))
        app.buttons["lookBack"].tap()
        gone(app.buttons["lookBack"])

        app.buttons["lockLater"].tap()
        XCTAssertTrue(app.staticTexts["headword"].waitForExistence(timeout: 10))
        app.buttons["Настройки"].tap()
        XCTAssertTrue(app.staticTexts["Настройки"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Перевод на"].firstMatch.label.hasSuffix("Английский"), "the pair chosen, not the guess")
        app.terminate()
        launch(["-uitest", "-uitest-intro"])
        XCTAssertTrue(app.staticTexts["headword"].waitForExistence(timeout: 20))
        XCTAssertFalse(app.buttons["target:sr"].exists, "the languages are asked once")
        XCTAssertFalse(app.buttons["lockLater"].waitForExistence(timeout: 3), "the offer comes back in three days, not now")
    }

    func testStartKeepsTheGuess() {
        launch(fresh + ["-uitest-intro"])
        XCTAssertTrue(app.buttons["pairStart"].waitForExistence(timeout: 20))
        app.buttons["pairStart"].tap()
        XCTAssertTrue(app.buttons["lockLater"].waitForExistence(timeout: 10))
    }

    func testTheWayChosenOpensItsStepsInTheSettings() {
        launch(fresh + ["-uitest-intro"])
        choosePair()
        XCTAssertTrue(app.buttons["lockLater"].waitForExistence(timeout: 10))
        app.otherElements["wayWallpaper"].tap()
        XCTAssertEqual(app.buttons["lockSetUp"].label, "Настроить обои")
        app.buttons["lockSetUp"].tap()
        XCTAssertTrue(app.staticTexts["lockGuide"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.descendants(matching: .any)["wallpaperDrawn"].firstMatch.waitForExistence(timeout: 5))
        app.terminate()
        launch(["-uitest", "-uitest-intro"])
        XCTAssertTrue(app.staticTexts["headword"].waitForExistence(timeout: 20))
        XCTAssertFalse(app.buttons["lockLater"].waitForExistence(timeout: 3), "set up ends the offers")
    }

    func testSetUpFromTheLookOpensTheSteps() {
        launch(fresh + ["-uitest-intro"])
        choosePair()
        XCTAssertTrue(app.buttons["lookWallpaper"].waitForExistence(timeout: 10))
        app.buttons["lookWallpaper"].tap()
        XCTAssertTrue(app.buttons["lookSetUp"].waitForExistence(timeout: 5))
        app.buttons["lookSetUp"].tap()
        XCTAssertTrue(app.staticTexts["lockGuide"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.descendants(matching: .any)["wallpaperDrawn"].firstMatch.waitForExistence(timeout: 5))
    }

    func testACrashReportPutsTheOfferOffToTheNextStart() {
        launch(fresh + ["-uitest-intro", "-uitest-crash"])
        choosePair()
        XCTAssertTrue(app.alerts["Приложение закрылось с ошибкой"].waitForExistence(timeout: 10))
        app.alerts.buttons["Не отправлять"].tap()
        XCTAssertTrue(app.staticTexts["headword"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["lockLater"].waitForExistence(timeout: 3), "not on the start that showed the report")
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(app.buttons["lockLater"].waitForExistence(timeout: 10))
    }

    func testOtherTestsStartOnTheCards() {
        launch(fresh)
        XCTAssertTrue(app.staticTexts["headword"].waitForExistence(timeout: 20))
        XCTAssertFalse(app.buttons["lockLater"].waitForExistence(timeout: 3))
    }
}
