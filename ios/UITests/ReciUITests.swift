import XCTest

extension XCUIElement {
    // The newest simulator now and then loses a tap on a button in a scroll view.
    @discardableResult
    func tap(opening screen: XCUIElement) -> Bool {
        tap()
        if screen.waitForExistence(timeout: 5) { return true }
        if isHittable { tap() }
        return screen.waitForExistence(timeout: 5)
    }
}

// A fresh install on a phone in Russian: a first start takes the interface language from it.
let fresh = ["-uitest-reset", "-AppleLanguages", "(ru)"]

// UI tests of the iPhone app in the simulator, the counterpart of CardScreenTest.kt and
// web/tests/ui/*.test.mjs. Each test starts from a fresh install.
final class ReciUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = fresh
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
        for _ in 0..<12 {
            if el.exists && el.isHittable { break }
            app.swipeUp()
        }
        return el
    }

    func testNextShowsAnotherWord() {
        let first = headword
        app.buttons["next"].tap()
        XCTAssertTrue(app.staticTexts["headword"].waitForExistence(timeout: 5))
        XCTAssertNotEqual(headword, first)
    }

    // CI runs this on an iPhone SE, below 700pt with one row of buttons, and on larger iPhones.
    func testTheCardScreenFitsTheScreen() {
        let screen = app.windows.firstMatch.frame
        for icon in ["Поиск", "Грамматика", "Настройки"] {
            XCTAssertLessThanOrEqual(app.buttons[icon].frame.maxX, screen.maxX, "\(icon) sticks out of the screen")
        }
        let know = mark("markKnow").frame, next = app.buttons["next"].frame
        let sameRow = know.minY < next.maxY && next.minY < know.maxY
        if screen.height < 700 {
            XCTAssertTrue(sameRow)
            XCTAssertTrue(app.buttons["Предыдущее слово"].frame.minY < know.maxY)
        } else if screen.height > 760 {
            XCTAssertFalse(sameRow)
        }
        let first = headword
        app.buttons["next"].tap()
        XCTAssertTrue(app.staticTexts["headword"].waitForExistence(timeout: 5))
        XCTAssertNotEqual(headword, first)
    }

    func testTheFlagComesAfterTheTextAndTheStarScrollsAway() {
        let flag = app.buttons["Сообщить об ошибке"].firstMatch, star = app.buttons["Избранное"].firstMatch
        XCTAssertTrue(star.isHittable)
        if !flag.isHittable {
            reach(flag)
            XCTAssertTrue(flag.isHittable)
            XCTAssertFalse(star.isHittable)
        }
        XCTAssertTrue(flag.tap(opening: app.navigationBars["Что не так?"]))
    }

    func testArticleButtonsKeepTheirFullNamesForVoiceOver() {
        for _ in 0..<6 {
            if app.staticTexts["articleTitle"].exists { break }
            app.buttons["next"].tap()
        }
        XCTAssertTrue(app.staticTexts["articleTitle"].waitForExistence(timeout: 5), "no article in the first cards")
        XCTAssertEqual(mark("markDontKnow").label, "Перечитать позже")
        XCTAssertEqual(mark("markKnow").label, "Не показывать")
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

    func testSchoolsOpenFromTheCardAndFromTheSettings() {
        let empty = app.staticTexts["schoolsEmpty"]
        // The school button and the flag come after the text of a long card.
        reach(app.buttons["Куда пойти учиться"].firstMatch).tap()
        XCTAssertTrue(empty.waitForExistence(timeout: 5))
        back()
        XCTAssertFalse(empty.waitForExistence(timeout: 2))
        openSettings()
        reach(app.buttons["Куда пойти учиться?"].firstMatch).tap()
        XCTAssertTrue(empty.waitForExistence(timeout: 5))
    }

    func testTheWallpaperSetupTellsWhetherTheShortcutRan() {
        openSettings()
        reach(app.buttons["Обои"].firstMatch).tap()
        let drawn = reach(mark("wallpaperDrawn"))
        XCTAssertTrue(drawn.waitForExistence(timeout: 5))
        XCTAssertTrue(drawn.label.contains("Card Wallpaper ещё не запускалась"))
        XCTAssertTrue(app.buttons["Создать команду"].exists)
        XCTAssertTrue(app.buttons["Запустить Card Wallpaper"].exists)
    }

    func testLatinAlphabet() {
        openSettings()
        reach(app.buttons["Latinica"].firstMatch).tap()
        back()
        XCTAssertNil(headword.range(of: "[\\u0400-\\u04FF]", options: .regularExpression))
    }

    // An option of the open menu, not a menu row whose value reads the same.
    private func option(_ label: String) -> XCUIElement {
        let rows = ["Учу", "Перевод на", "Язык интерфейса", "Учим", "Превод на", "Језик интерфејса"]
        return app.buttons.matching(NSPredicate(format: "label == %@ AND NOT (identifier IN %@)", label, rows)).firstMatch
    }

    // Russian for Serbs: the language learned and the interface language are chosen apart.
    func testTheRussianDeckWithTheSerbianInterface() {
        openSettings()
        reach(app.buttons["Учу"].firstMatch).tap()
        XCTAssertTrue(option("Сербский").waitForExistence(timeout: 5))
        option("Русский").tap()
        // The schools row is for those who learn Serbian only.
        let schools = app.buttons["Куда пойти учиться?"].firstMatch
        wait(for: [expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: schools)], timeout: 10)
        // No deck translates Russian into Russian.
        app.buttons["Перевод на"].firstMatch.tap()
        XCTAssertTrue(option("Сербский").waitForExistence(timeout: 5))
        XCTAssertFalse(option("Русский").exists)
        option("Сербский").tap()
        XCTAssertTrue(app.staticTexts["Настройки"].exists, "the interface is not the deck's")

        app.buttons["Язык интерфейса"].firstMatch.tap()
        XCTAssertTrue(option("Српски").waitForExistence(timeout: 5))
        XCTAssertTrue(option("English").exists)
        XCTAssertFalse(option("Как у колоды").exists)
        option("Српски").tap()
        XCTAssertTrue(app.staticTexts["Подешавања"].waitForExistence(timeout: 10))
        back()
        XCTAssertTrue(app.staticTexts["stage"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.staticTexts["stage"].label, "Нова реч")
        XCTAssertTrue(app.buttons["Подешавања"].exists)
    }
}

// The statistics notice and switch, reporting a card and writing to the developer. The app answers
// its own requests in UI tests, so nothing reaches the real server.
final class FeedbackUITests: XCTestCase {
    private var app: XCUIApplication!

    private func launch(_ args: [String] = fresh) {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = args
        app.launch()
        XCTAssertTrue(app.staticTexts["headword"].waitForExistence(timeout: 20) || app.staticTexts["articleTitle"].exists)
    }

    private func reach(_ el: XCUIElement) -> XCUIElement {
        for _ in 0..<15 {
            if el.exists && el.isHittable { break }
            app.swipeUp()
        }
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
        XCTAssertTrue(reach(app.buttons["Сообщить об ошибке"].firstMatch).tap(opening: app.navigationBars["Что не так?"]))
        XCTAssertFalse(app.buttons["send"].isEnabled)
        app.buttons["problem:\(problem)"].tap()
    }

    func testTheNoticeTellsWhereToTurnStatisticsOffAndStaysClosed() {
        launch()
        let notice = app.staticTexts["statsNotice"]
        XCTAssertTrue(notice.waitForExistence(timeout: 5))
        XCTAssertTrue(notice.label.contains("Настройки → «Анонимная статистика»"))
        app.buttons["Понятно"].tap()
        // waitForExistence answers at once while the notice is still on its way out.
        let gone = expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: notice)
        wait(for: [gone], timeout: 5)
        app.terminate()
        launch(["-uitest"])
        XCTAssertFalse(app.staticTexts["statsNotice"].waitForExistence(timeout: 3))
    }

    func testTheLastCrashIsOfferedOnceAndSentOnYes() {
        launch(fresh + ["-uitest-crash"])
        XCTAssertTrue(app.alerts["Приложение закрылось с ошибкой"].waitForExistence(timeout: 10))
        app.alerts.buttons["Отправить"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["sentToast"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertTrue(app.descendants(matching: .any)["sentToast"].firstMatch.label.contains("Спасибо"))
        app.terminate()
        launch(["-uitest"])
        XCTAssertFalse(app.alerts["Приложение закрылось с ошибкой"].waitForExistence(timeout: 3))
    }

    func testADeclinedCrashReportIsNotOfferedAgain() {
        launch(fresh + ["-uitest-crash"])
        XCTAssertTrue(app.alerts["Приложение закрылось с ошибкой"].waitForExistence(timeout: 10))
        app.alerts.buttons["Не отправлять"].tap()
        XCTAssertFalse(app.descendants(matching: .any)["sentToast"].firstMatch.waitForExistence(timeout: 2))
        app.terminate()
        launch(["-uitest"])
        XCTAssertFalse(app.alerts["Приложение закрылось с ошибкой"].waitForExistence(timeout: 3))
    }

    func testTheNewsOfAnUpdateAreShownOnce() {
        let news = { self.app.alerts.matching(NSPredicate(format: "label BEGINSWITH 'Что нового'")).firstMatch }
        launch(fresh + ["-uitest-whatsnew"])
        XCTAssertTrue(news().waitForExistence(timeout: 10))
        XCTAssertTrue(news().staticTexts.matching(NSPredicate(format: "label CONTAINS '• '")).firstMatch.exists)
        news().buttons["Понятно"].tap()
        let gone = expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: news())
        wait(for: [gone], timeout: 5)
        app.terminate()
        launch(["-uitest"])
        XCTAssertFalse(news().waitForExistence(timeout: 3))
    }

    func testAFreshInstallIsNotToldAboutChanges() {
        launch()
        XCTAssertFalse(app.alerts.matching(NSPredicate(format: "label BEGINSWITH 'Что нового'")).firstMatch.waitForExistence(timeout: 3))
    }

    func testACardIsReportedFromTheCard() {
        launch()
        report(problem: "stress")
        app.buttons["send"].tap()
        XCTAssertTrue(app.staticTexts["Напишите, что не так"].waitForExistence(timeout: 3))
        type("message", "ударение на втором слоге")
        type("email", "me@example.org")
        app.buttons["send"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["sentToast"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertTrue(app.descendants(matching: .any)["sentToast"].firstMatch.label.contains("Спасибо"))
        XCTAssertTrue(reach(app.buttons["Сообщить об ошибке"].firstMatch).tap(opening: app.textFields["email"]))
        XCTAssertEqual(app.textFields["email"].value as? String, "me@example.org")
    }

    func testAMistypedEmailIsCaughtBeforeSending() {
        launch()
        report(problem: "translation")
        type("message", "не тот перевод")
        type("email", "me@example")
        app.buttons["send"].tap()
        XCTAssertTrue(app.staticTexts["Проверьте адрес почты"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.navigationBars["Что не так?"].exists)
    }

    func testWithoutNetworkAMessageWaits() {
        launch(fresh + ["-uitest-offline"])
        report(problem: "other")
        type("message", "карточка повторяется")
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
        XCTAssertTrue(app.navigationBars["Сообщение"].waitForExistence(timeout: 5))
        app.buttons["send"].tap()
        XCTAssertTrue(app.staticTexts["Напишите сообщение"].waitForExistence(timeout: 3))
        type("message", "Добавьте глаголы движения")
        app.buttons["send"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["sentToast"].firstMatch.waitForExistence(timeout: 5))
    }

    func testHelpTheAuthor() {
        launch()
        openSettings()
        reach(app.buttons["helpTheAuthor"]).tap()
        XCTAssertTrue(app.navigationBars["Помочь автору"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["share"].exists)
        XCTAssertTrue(app.buttons["star"].exists)
        app.buttons["share"].tap()
        // The system share sheet comes up over the app.
        XCTAssertTrue(app.otherElements["ActivityListView"].waitForExistence(timeout: 5) || app.collectionViews.firstMatch.waitForExistence(timeout: 1))
    }

    func testWhatIsSentShowsBothIdsAndForgetsThemOnRequest() {
        launch()
        openSettings()
        reach(app.buttons["whatIsSent"]).tap()
        let installId = app.staticTexts["installId"]
        XCTAssertTrue(installId.waitForExistence(timeout: 5))
        let before = installId.label
        XCTAssertNotNil(before.range(of: #"^[0-9a-f-]{36}$"#, options: .regularExpression))
        XCTAssertNotEqual(app.staticTexts["feedbackId"].label, before)
        reach(app.buttons["Удалить мои данные с сервера"]).tap()
        app.alerts.buttons["Отмена"].tap()
        XCTAssertFalse(app.staticTexts["forgetResult"].exists)
        app.buttons["Удалить мои данные с сервера"].tap()
        app.alerts.buttons["Удалить"].tap()
        XCTAssertTrue(app.staticTexts["forgetResult"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["forgetResult"].label, "Данные удалены с сервера")
        XCTAssertNotEqual(app.staticTexts["installId"].label, before)
    }

    func testTheStatisticsSwitchAndWhatIsSent() {
        launch()
        openSettings()
        let row = reach(app.buttons["whatIsSent"])
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

// A first start on a phone in this language picks the deck and the interface language
// (Decks.guess, Decks.guessUi); the store page of the language gets these screens.
struct ShotLanguage {
    let code: String
    // The start of a word of the language learned, for the search screen.
    let query: String

    var args: [String] { ["-uitest-reset", "-AppleLanguages", "(\(code))"] }
    // The Russian shots keep the names they had before there were others.
    var prefix: String { code == "ru" ? "" : "\(code)-" }

    static let ru = ShotLanguage(code: "ru", query: "кућ")
    static let en = ShotLanguage(code: "en", query: "кућ")
    // Russian with Serbian translations.
    static let sr = ShotLanguage(code: "sr", query: "дом")
}

// Screens of the app for the store pages and for looking at the app without an iPhone. They
// are kept in the test results; the iOS workflow exports them as PNG files.
final class ScreenshotUITests: XCTestCase {
    func testScreens() { screens(.ru) }

    func testScreensInEnglish() throws {
        try skipUnlessAsked(.en)
        screens(.en)
    }

    func testScreensInSerbian() throws {
        try skipUnlessAsked(.sr)
        screens(.sr)
    }

    func testLockScreen() { lockScreen(.ru) }

    func testLockScreenInEnglish() throws {
        try skipUnlessAsked(.en)
        lockScreen(.en)
    }

    func testLockScreenInSerbian() throws {
        try skipUnlessAsked(.sr)
        lockScreen(.sr)
    }

    // Only the devices that feed the App Store need the other languages: CI sets
    // TEST_RUNNER_SCREENSHOT_LANGUAGES there, and xcodebuild passes it on without the prefix.
    private func skipUnlessAsked(_ lang: ShotLanguage) throws {
        let asked = (ProcessInfo.processInfo.environment["SCREENSHOT_LANGUAGES"] ?? "").split(separator: " ").map(String.init)
        try XCTSkipUnless(asked.contains(lang.code), "SCREENSHOT_LANGUAGES has no \(lang.code)")
    }

    private func screens(_ lang: ShotLanguage) {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = lang.args
        app.launch()
        XCTAssertTrue(app.staticTexts["headword"].waitForExistence(timeout: 20))
        let shot = { (name: String) in
            // Let screen transitions and button highlights finish.
            sleep(1)
            let a = XCTAttachment(screenshot: app.screenshot())
            a.name = lang.prefix + name
            a.lifetime = .keepAlways
            self.add(a)
        }
        shot("1-card-with-notice")
        let flag = app.buttons["report"].firstMatch
        for _ in 0..<15 {
            if flag.isHittable { break }
            app.swipeUp()
        }
        flag.tap(opening: app.buttons["problem:stress"])
        app.buttons["problem:stress"].tap()
        shot("2-report")
        app.buttons["cancel"].tap()
        app.buttons["closeNotice"].tap()
        app.buttons["next"].tap()
        shot("3-card")
        app.buttons["search"].tap()
        app.textFields.firstMatch.tap()
        // A fresh simulator explains swipe typing over the keyboard first.
        if app.buttons["Continue"].waitForExistence(timeout: 2) { app.buttons["Continue"].tap() }
        app.textFields.firstMatch.typeText(lang.query)
        shot("4-search")
        app.buttons["back"].firstMatch.tap()
        app.buttons["grammar"].tap()
        shot("5-grammar")
        app.buttons["back"].firstMatch.tap()
        app.buttons["settings"].tap()
        _ = app.staticTexts["knownCount"].waitForExistence(timeout: 5)
        shot("6-settings")
        let help = app.buttons["helpTheAuthor"]
        for _ in 0..<15 {
            if help.isHittable { break }
            app.swipeUp()
        }
        shot("7-settings-feedback")
        app.buttons["whatIsSent"].tap()
        _ = app.staticTexts["packet"].waitForExistence(timeout: 5)
        shot("8-what-is-sent")
        app.buttons["close"].tap()
        help.tap()
        _ = app.buttons["share"].waitForExistence(timeout: 5)
        shot("9-help-the-author")
    }

    // The real widgets and wallpaper on a stand-in lock screen, see LockPreview.swift.
    private func lockScreen(_ lang: ShotLanguage) {
        continueAfterFailure = false
        for mode in ["widgets", "wallpaper"] {
            let app = XCUIApplication()
            app.launchArguments = lang.args + ["-uitest-lockpreview", mode]
            app.launch()
            XCTAssertTrue(app.descendants(matching: .any)["lockPreview"].firstMatch.waitForExistence(timeout: 20))
            let a = XCTAttachment(screenshot: app.screenshot())
            a.name = "\(lang.prefix)10-lock-\(mode)"
            a.lifetime = .keepAlways
            add(a)
            app.terminate()
        }
    }
}
