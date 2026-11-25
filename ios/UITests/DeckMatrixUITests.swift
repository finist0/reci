import XCTest

// One cell of the language matrix chosen through the settings menus: Russian with English
// translations under the Serbian interface, three languages that cannot be mistaken for one
// another. The counterpart of DeckMatrixUiTest.kt and web/tests/ui/deck-matrix.test.mjs.
final class DeckMatrixUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = fresh
        app.launch()
        XCTAssertTrue(app.staticTexts["headword"].waitForExistence(timeout: 20) || app.staticTexts["articleTitle"].exists)
    }

    private func mark(_ id: String) -> XCUIElement { app.descendants(matching: .any)[id].firstMatch }

    // Settings rows below the fold are not hittable until scrolled to.
    private func reach(_ el: XCUIElement) -> XCUIElement {
        for _ in 0..<12 {
            if el.exists && el.isHittable { break }
            app.swipeUp()
        }
        return el
    }

    // An option of the open menu, not a menu row whose value reads the same.
    private func option(_ label: String) -> XCUIElement {
        let rows = ["Учу", "Перевод на", "Язык интерфейса", "Учим", "Превод на", "Језик интерфејса"]
        return app.buttons.matching(NSPredicate(format: "label == %@ AND NOT (identifier IN %@)", label, rows)).firstMatch
    }

    // Waits for a settings row to show the value; a menu row is named in the interface language.
    private func shows(_ row: String, _ label: String) {
        wait(for: [expectation(for: NSPredicate(format: "label ENDSWITH %@", label), evaluatedWith: app.buttons[row].firstMatch)], timeout: 10)
    }

    // Opens the menu of a settings row and picks the option.
    private func choose(_ row: String, _ label: String) {
        reach(app.buttons[row].firstMatch).tap()
        XCTAssertTrue(option(label).waitForExistence(timeout: 5), "\(row): no \(label)")
        option(label).tap()
    }

    func testRussianWithEnglishTranslationsUnderTheSerbianInterface() {
        app.buttons["Настройки"].tap()
        XCTAssertTrue(app.staticTexts["Настройки"].waitForExistence(timeout: 5))
        choose("Язык интерфейса", "Српски")
        XCTAssertTrue(app.staticTexts["Подешавања"].waitForExistence(timeout: 10))
        shows("Језик интерфејса", "Српски")
        choose("Учим", "Руски")
        shows("Учим", "Руски")
        // Russian is translated into Serbian first; English is the other choice.
        shows("Превод на", "Српски")
        choose("Превод на", "Енглески")
        shows("Превод на", "Енглески")
        XCTAssertTrue(app.buttons["Учим"].firstMatch.label.hasSuffix("Руски"), "the language learned stays")
        XCTAssertTrue(app.buttons["Језик интерфејса"].firstMatch.label.hasSuffix("Српски"), "the interface is not the deck's")
        XCTAssertTrue(app.staticTexts["Подешавања"].exists)
        app.buttons["Назад"].firstMatch.tap()

        // The interface speaks Serbian.
        let headword = app.staticTexts["headword"]
        XCTAssertTrue(headword.waitForExistence(timeout: 10))
        XCTAssertEqual(app.staticTexts["stage"].label, "Нова реч")
        XCTAssertEqual(mark("markKnow").label, "Знам")
        XCTAssertEqual(mark("markDontKnow").label, "Не знам")
        for button in ["Претрага", "Граматика", "Подешавања"] { XCTAssertTrue(app.buttons[button].exists, button) }
        XCTAssertFalse(app.buttons["Настройки"].exists)

        // The cards: Russian words with English translations.
        let english = app.staticTexts.matching(NSPredicate(format: "label MATCHES %@", "[A-Za-z(][A-Za-z ,.;:()'/!?-]{2,}"))
        var words: [String] = []
        for _ in 0..<8 {
            if headword.exists {
                let word = headword.label
                words.append(word)
                XCTAssertNil(word.range(of: "[A-Za-z]", options: .regularExpression), word)
                XCTAssertGreaterThan(english.count, 0, "no English translation of \(word)")
            }
            app.buttons["next"].tap()
            _ = headword.waitForExistence(timeout: 5) || app.staticTexts["articleTitle"].waitForExistence(timeout: 5)
        }
        let all = words.joined()
        XCTAssertGreaterThan(words.count, 3, "too few words among the cards")
        XCTAssertNotNil(all.range(of: "[ыэъьяюёйщ]", options: .regularExpression), "no Russian word: \(words)")
        XCTAssertNil(all.range(of: "[ђјљњћџ]", options: .regularExpression), "a Serbian word: \(words)")
    }
}
