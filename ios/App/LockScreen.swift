import AppIntents
import ReciCore
import SwiftUI
import UIKit
import UniformTypeIdentifiers
import WidgetKit

// How to put the card on the lock screen. iOS lets an app neither draw there nor set a wallpaper
// itself, so both ways need a few steps from the user. The steps follow the iOS on the phone,
// the buttons open Shortcuts where the steps begin, and the checks show what the app can see of
// the result.
struct LockSetup: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var phase
    let mode: Int
    // The kinds of our widgets iOS reports on lock screens; nil until it answers.
    @State private var placed: Set<String>?
    @State private var drawn: Date?

    // iOS 26 opens the lock screen editor at once; iOS 27 keeps automations inside shortcuts.
    private let ios = ProcessInfo.processInfo.operatingSystemVersion.majorVersion

    var body: some View {
        let p = model.palette
        VStack(alignment: .leading, spacing: 6) {
            if mode == 0 {
                Text(t("Карточка и кнопки — виджеты экрана блокировки. Ответ виджетом сразу показывает следующее слово."))
                steps([
                    ios >= 26 ? t("Нажмите и удерживайте экран блокировки → Customize.")
                        : t("Нажмите и удерживайте экран блокировки → Customize → Lock Screen."),
                    t("Нажмите Add Widgets (поле под часами) → НЕРодная речь → «Карточка» (широкий виджет)."),
                    t("Добавьте рядом «Знаю» и «Не знаю» (круглые виджеты)."),
                    t("Нажмите Done."),
                ])
                widgetCheck(["card", "know", "dontknow"])
            } else {
                Text(t("Карточка становится обоями экрана блокировки. Её рисует действие Card Wallpaper для приложения Shortcuts; ответы — круглыми виджетами «Знаю» и «Не знаю»."))
                steps(wallpaperSteps)
                Text(t("Если Set Wallpaper Photo не даёт выбрать обои, сначала поставьте обои из фото: Settings → Wallpaper → Add New Wallpaper → Photos."))
                HStack(spacing: 20) {
                    link(t("Создать команду"), "shortcuts://create-shortcut")
                    link(t("Запустить Card Wallpaper"), "shortcuts://run-shortcut?name=Card%20Wallpaper")
                }
                .padding(.vertical, 4)
                Text(t("Обои обновляются, когда срабатывает команда, а не сразу после ответа."))
                check(drawn != nil, drawn.map { t("Card Wallpaper последний раз рисовала карточку: %s.", $0.formatted(Date.FormatStyle(date: .abbreviated, time: .shortened, locale: model.locale))) }
                      ?? t("Card Wallpaper ещё не запускалась."))
                    .accessibilityIdentifier("wallpaperDrawn")
                widgetCheck(["know", "dontknow"])
            }
        }
        .font(.ui(13)).foregroundStyle(p.secondary)
        .onAppear(perform: refresh)
        .onChange(of: phase) { _, now in
            if now == .active { refresh() }
        }
    }

    private var wallpaperSteps: [String] {
        var s = [
            t("Нажмите «Создать команду» ниже: в Shortcuts откроется новая команда. Нажмите New Shortcut → Rename и назовите её Card Wallpaper."),
            t("Нажмите Add Action, найдите действие Card Wallpaper (НЕРодная речь) и добавьте его."),
            t("Добавьте действие Set Wallpaper Photo: выберите Lock Screen, раскройте его стрелкой и выключите Show Preview и Crop to Subject."),
        ]
        if ios >= 27 {
            s += [
                t("В этой же команде добавьте из Automation триггеры Time of Day (время, Daily) и App → НЕРодная речь → Is Closed."),
                t("Чтобы команда не спрашивала разрешения: Info (ⓘ) → Privacy → включите Allow Running When Locked. Нажмите Done."),
            ]
        } else {
            s += [
                t("Нажмите Done. Откройте Automation → New Automation (или +) → Time of Day: время, Daily → Run Immediately → Next → команда Card Wallpaper."),
                t("Так же добавьте вторую: App → Choose → НЕРодная речь, отметьте только Is Closed → Run Immediately → Next → Card Wallpaper."),
            ]
        }
        // iOS 26 lets the widgets go down to the bottom, where the card is drawn.
        s.append(ios >= 26 ? t("Нажмите и удерживайте экран блокировки → Customize → Add Widgets: добавьте под часами круглые «Знаю» и «Не знаю». Внизу их не ставьте: там карточка.")
                 : t("Нажмите и удерживайте экран блокировки → Customize → Lock Screen → Add Widgets: добавьте круглые «Знаю» и «Не знаю»."))
        return s
    }

    private func refresh() {
        drawn = LockDeck.wallpaperDrawn
        Task { placed = await Stats.widgets().map { Set($0.lock) } }
    }

    @ViewBuilder
    private func widgetCheck(_ kinds: [String]) -> some View {
        if let placed {
            let names = ["card": t("«Карточка»"), "know": t("«Знаю»"), "dontknow": t("«Не знаю»")]
            let missing = kinds.filter { !placed.contains($0) }.compactMap { names[$0] }
            check(missing.isEmpty, missing.isEmpty ? t("iOS видит все виджеты на экране блокировки.")
                  : t("iOS пока не видит на экране блокировки: %s.", missing.joined(separator: ", ")))
                .accessibilityIdentifier("widgetsPlaced")
        }
    }

    private func check(_ ok: Bool, _ text: String) -> some View {
        let p = model.palette
        return HStack(alignment: .firstTextBaseline, spacing: 6) {
            Image(systemName: ok ? "checkmark.circle.fill" : "exclamationmark.circle").foregroundStyle(ok ? p.known : p.accent)
            Text(text).foregroundStyle(p.text)
        }
        .padding(.top, 4)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
    }

    private func link(_ title: String, _ url: String) -> some View {
        Button(title) { openURL(URL(string: url)!) }
            .font(.ui(14, .semibold)).foregroundStyle(model.palette.accent)
            .accessibilityIdentifier(title)
    }

    private func steps(_ items: [String]) -> some View {
        let p = model.palette
        return VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(items.enumerated()), id: \.offset) { i, s in
                HStack(alignment: .top, spacing: 6) {
                    Text("\(i + 1).").fontWeight(.semibold).foregroundStyle(p.accent).frame(width: 18, alignment: .leading)
                    Text(s).foregroundStyle(p.text)
                }
            }
        }
        .padding(.vertical, 4)
    }
}

// The card drawn as a lock screen wallpaper, 1290 pixels wide in the proportions of the screen.
// The top stays free for the clock and the widgets, the bottom for the flashlight and camera
// buttons. A short card is drawn larger to fill the rest; a long one drops from the end:
// antonyms and synonyms, then examples one by one.
struct WallpaperCard: View {
    static let width: CGFloat = 1290

    let w: Word?
    let prefs: Prefs
    let recall: Bool
    let palette: Palette
    // In points.
    let screen: CGSize

    static func height(_ screen: CGSize) -> CGFloat { width * screen.height / screen.width }

    var body: some View {
        // Pixels of the drawing per point of the screen: the clock and the buttons keep their size in points.
        let k = Self.width / screen.width
        ZStack(alignment: .topLeading) {
            palette.bg
            Group {
                if let w {
                    ViewThatFits(in: .vertical) {
                        card(w, examples: 3, related: true, scale: 1.6)
                        card(w, examples: 3, related: true, scale: 1.35)
                        card(w, examples: 3, related: true, scale: 1.15)
                        card(w, examples: 3, related: true, scale: 1)
                        card(w, examples: 3, related: false, scale: 1)
                        card(w, examples: 2, related: false, scale: 1)
                        card(w, examples: 1, related: false, scale: 1)
                        card(w, examples: 0, related: false, scale: 1)
                    }
                } else {
                    Text(t("Слова закончились: выберите другие в приложении")).font(.ui(44 / uiScale)).foregroundStyle(palette.text)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(.horizontal, 90)
            .padding(.top, 310 * k)
            .padding(.bottom, 100 * k)
        }
    }

    private func card(_ w: Word, examples: Int, related: Bool, scale s: CGFloat) -> some View {
        // A picture of a fixed size: its fonts do not grow on an iPad.
        let f = s / uiScale
        return VStack(alignment: .leading, spacing: 18 * s) {
            if recall {
                Text(t("Вспомните слово")).font(.ui(34 * f, .semibold)).foregroundStyle(palette.accent)
                meanings(w, size: 58 * f)
                // The examples would give the word away; their translations are the hints.
                ForEach(Array(w.examples.prefix(examples).enumerated()), id: \.offset) { _, e in
                    Text("«\(prefs.native(e.tr))»").font(.ui(36 * f)).foregroundStyle(palette.secondary)
                }
            } else {
                if let sec = prefs.secondary(w.cyr), !w.isArticle { Text(sec).font(.ui(40 * f)).foregroundStyle(palette.secondary) }
                Text(w.isArticle && w.pos == "compare" ? (w.meanings.first.map { prefs.native($0) } ?? w.cyr) : prefs.headword(w))
                    .font(.serif(110 * f, .semibold)).foregroundStyle(palette.word).minimumScaleFactor(0.4).lineLimit(2)
                if !w.isArticle {
                    let gram = [Labels.pos(w.pos), prefs.native(w.gram), prefs.primary(w.forms)].filter { !$0.isEmpty }.joined(separator: " · ")
                    if !gram.isEmpty { Text(gram).font(.ui(34 * f)).foregroundStyle(palette.secondary) }
                    meanings(w, size: 54 * f)
                }
                ForEach(Array(w.examples.prefix(examples).enumerated()), id: \.offset) { _, e in
                    VStack(alignment: .leading, spacing: 4 * s) {
                        Text(highlighted(prefs.primary(prefs.marked(e.text)), palette.accent, size: 44 * f)).font(.serif(44 * f)).foregroundStyle(palette.text)
                        Text(prefs.native(e.tr)).font(.ui(34 * f)).foregroundStyle(palette.secondary)
                    }
                    .padding(.top, 8 * s)
                }
                if related {
                    if !w.syn.isEmpty { pairs(t("синонимы"), w.syn, w.synAcc, size: 36 * f) }
                    if !w.ant.isEmpty { pairs(t("антонимы"), w.ant, w.antAcc, size: 36 * f) }
                }
            }
        }
    }

    private func meanings(_ w: Word, size: CGFloat) -> some View {
        ForEach(Array(w.meanings.prefix(3).enumerated()), id: \.offset) { i, m in
            Text("\(i + 1). \(prefs.native(m))").font(.serif(size)).foregroundStyle(palette.text)
        }
    }

    // Stressed as the card gives them, when it does.
    private func pairs(_ title: String, _ words: [String], _ accs: [String], size: CGFloat) -> some View {
        let shown = words.prefix(6).indices.map { i in prefs.linked(words[i], acc: i < accs.count ? accs[i] : "", nil) }
        return (Text(title + ": ").foregroundColor(palette.accent) + Text(shown.joined(separator: ", ")).foregroundColor(palette.text))
            .font(.ui(size))
    }
}

struct CardWallpaperIntent: AppIntent {
    // One English name on every phone, which the setup steps in every interface language quote.
    static var title: LocalizedStringResource = "Card Wallpaper"
    static var description = IntentDescription("Draws the current word card as a picture for the lock screen wallpaper.")

    // The widgets' short deck lacks examples, forms and synonyms; the app's own dictionary has them.
    @MainActor
    private func fullWord(_ key: String, _ deck: Deck) -> Word? {
        Dict.load(deck: deck, patch: deck.isFirst ? Dict.updater().stored() : nil).words.first { $0.key == key }
    }

    @MainActor
    func perform() async throws -> some IntentResult & ReturnsValue<IntentFile> {
        LockDeck.loadTables()
        let repo = LockDeck.repo()
        let prefs = repo?.prefs ?? Prefs()
        let w = repo?.current().map { fullWord($0.key, prefs.deck) ?? $0 }
        let palette = palettes.indices.contains(prefs.theme) ? palettes[prefs.theme] : palettes[2]
        let screen = UIScreen.main
        let view = WallpaperCard(w: w, prefs: prefs, recall: repo?.current().map { repo?.asksRecall($0) ?? false } ?? false,
                                 palette: palette, screen: screen.bounds.size)
            .frame(width: WallpaperCard.width, height: WallpaperCard.height(screen.bounds.size))
        let renderer = ImageRenderer(content: view)
        // The picture has the pixels of the screen, so the lock screen shows it whole, without cropping.
        renderer.scale = screen.bounds.width * screen.scale / WallpaperCard.width
        guard let data = renderer.uiImage?.pngData() else { throw CocoaError(.fileWriteUnknown) }
        LockDeck.wallpaperDrawn = Date()
        // The automation draws the card without opening the app, so the daily ping goes from here too.
        await Stats.fromLockScreen(repo)
        return .result(value: IntentFile(data: data, filename: "reci-card.png", type: .png))
    }
}

struct ReciShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: CardWallpaperIntent(), phrases: ["Card wallpaper in \(.applicationName)", "Обои с карточкой в \(.applicationName)"],
                    shortTitle: "Card Wallpaper", systemImageName: "photo")
    }
}
