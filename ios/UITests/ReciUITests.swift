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

// The statistics notice and switch, reporting a card and writing to the developer. The app answers
// its own requests in UI tests, so nothing reaches the real server.
final class FeedbackUITests: XCTestCase {
    private var app: XCUIApplication!

    private func launch(_ args: [String] = ["-uitest-reset"]) {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = args
        app.launch()
        XCTAssertTrue(app.staticTexts["headword"].waitForExistence(timeout: 20) || app.staticTexts["articleTitle"].exists)
    }

    private func reach(_ el: XCUIElement) -> XCUIElement {
        for _ in 0..<15 where !(el.exists && el.isHittable) { app.swipeUp() }
        return el
    }

    private func openSettings() {
        app.buttons["Настройки"].tap()
        XCTAssertTrue(app.staticTexts["Настройки"].waitForExistence(timeout: 5))
    }

    private func type(_ id: String, _ text: String) {
        let field = app.textFields[id].exists ? app.textFields[id] : app.textViews[id]
        field.tap()
        field.typeText(text)
    }

    private func report(problem: String) {
        app.buttons["Сообщить об ошибке"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Что не так с карточкой?"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["send"].isEnabled)
        app.buttons["problem:\(problem)"].tap()
    }

    func testTheNoticeTellsWhereToTurnStatisticsOffAndStaysClosed() {
        launch()
        let notice = app.staticTexts["statsNotice"]
        XCTAssertTrue(notice.waitForExistence(timeout: 5))
        XCTAssertTrue(notice.label.contains("Настройки → «Анонимная статистика»"))
        app.buttons["Понятно"].tap()
        XCTAssertFalse(notice.waitForExistence(timeout: 2))
        app.terminate()
        launch(["-uitest"])
        XCTAssertFalse(app.staticTexts["statsNotice"].waitForExistence(timeout: 3))
    }

    func testACardIsReportedFromTheCard() {
        launch()
        report(problem: "stress")
        type("message", "ударение на втором слоге")
        type("email", "me@example.org")
        app.buttons["send"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["sentToast"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertTrue(app.descendants(matching: .any)["sentToast"].firstMatch.label.contains("Спасибо"))
        app.buttons["Сообщить об ошибке"].firstMatch.tap()
        XCTAssertTrue(app.textFields["email"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.textFields["email"].value as? String, "me@example.org")
    }

    func testAMistypedEmailIsCaughtBeforeSending() {
        launch()
        report(problem: "translation")
        type("email", "me@example")
        app.buttons["send"].tap()
        XCTAssertTrue(app.staticTexts["Проверьте адрес почты"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.navigationBars["Что не так с карточкой?"].exists)
    }

    func testWithoutNetworkAMessageWaits() {
        launch(["-uitest-reset", "-uitest-offline"])
        report(problem: "other")
        app.buttons["send"].tap()
        let toast = app.descendants(matching: .any)["sentToast"].firstMatch
        XCTAssertTrue(toast.waitForExistence(timeout: 5))
        XCTAssertTrue(toast.label.contains("интернет"))
        openSettings()
        XCTAssertTrue(reach(app.buttons["Написать разработчику"]).label.contains("Ждут отправки: 1"))
    }

    func testWritingToTheDeveloperFromTheSettings() {
        launch()
        openSettings()
        reach(app.buttons["Написать разработчику"]).tap()
        XCTAssertTrue(app.navigationBars["Написать разработчику"].waitForExistence(timeout: 5))
        app.buttons["send"].tap()
        XCTAssertTrue(app.staticTexts["Напишите сообщение"].waitForExistence(timeout: 3))
        type("message", "Добавьте глаголы движения")
        app.buttons["send"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["sentToast"].firstMatch.waitForExistence(timeout: 5))
    }

    func testTheStatisticsSwitchAndWhatIsSent() {
        launch()
        openSettings()
        let row = reach(app.buttons["Что отправляется"])
        XCTAssertTrue(row.label.contains("Последний раз:"))
        row.tap()
        XCTAssertTrue(app.staticTexts["packet"].waitForExistence(timeout: 5))
        app.buttons["Закрыть"].tap()
        let toggle = reach(app.switches["Анонимная статистика"].firstMatch)
        XCTAssertEqual(toggle.value as? String, "1")
        toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        XCTAssertEqual(toggle.value as? String, "0")
    }
}

// Screens of the app for the store pages and for looking at the app without an iPhone. They
// are kept in the test results; the iOS workflow exports them as PNG files.
final class ScreenshotUITests: XCTestCase {
    func testScreens() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-reset"]
        app.launch()
        XCTAssertTrue(app.staticTexts["headword"].waitForExistence(timeout: 20))
        let shot = { (name: String) in
            let a = XCTAttachment(screenshot: app.screenshot())
            a.name = name
            a.lifetime = .keepAlways
            self.add(a)
        }
        shot("1-card-with-notice")
        app.buttons["Сообщить об ошибке"].firstMatch.tap()
        _ = app.navigationBars["Что не так с карточкой?"].waitForExistence(timeout: 5)
        app.buttons["problem:stress"].tap()
        shot("2-report")
        app.buttons["Отмена"].tap()
        app.buttons["Понятно"].tap()
        app.buttons["next"].tap()
        shot("3-card")
        app.buttons["Поиск"].tap()
        app.textFields.firstMatch.tap()
        app.textFields.firstMatch.typeText("кућ")
        shot("4-search")
        app.buttons["Назад"].firstMatch.tap()
        app.buttons["Грамматика"].tap()
        shot("5-grammar")
        app.buttons["Назад"].firstMatch.tap()
        app.buttons["Настройки"].tap()
        shot("6-settings")
        for _ in 0..<15 where !app.buttons["Написать разработчику"].isHittable { app.swipeUp() }
        shot("7-settings-feedback")
        app.buttons["Что отправляется"].tap()
        _ = app.staticTexts["packet"].waitForExistence(timeout: 5)
        shot("8-what-is-sent")
    }
}
