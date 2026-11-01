import AppIntents
import ReciCore
import SwiftUI
import UIKit
import UniformTypeIdentifiers

// How to put the card on the lock screen; iOS lets an app neither draw there nor set a wallpaper
// itself, so both ways need a few steps from the user.
struct LockSetup: View {
    @EnvironmentObject var model: AppModel
    let mode: Int

    var body: some View {
        let p = model.palette
        VStack(alignment: .leading, spacing: 6) {
            if mode == 0 {
                Text("Карточка и кнопки — виджеты экрана блокировки. Ответ виджетом сразу показывает следующее слово.")
                steps([
                    "Нажмите и удерживайте экран блокировки → Customize → Lock Screen.",
                    "Нажмите на рамку под часами → добавьте «Карточка» (широкий виджет).",
                    "Добавьте рядом «Знаю» и «Не знаю» (круглые виджеты).",
                    "Нажмите Done.",
                ])
            } else {
                Text("Карточка становится обоями экрана блокировки. Её рисует действие «Обои с карточкой» для приложения «Команды»; ответы — круглыми виджетами «Знаю» и «Не знаю».")
                steps([
                    "Откройте «Команды» → Automation → New Automation → Time of Day (например, каждый час) → Run Immediately.",
                    "Добавьте действие «Обои с карточкой» (НЕРодная речь).",
                    "Добавьте действие Set Wallpaper Photo → Lock Screen, выключите Show Preview.",
                    "На экран блокировки добавьте круглые виджеты «Знаю» и «Не знаю».",
                ])
                Text("Обои обновляются, когда срабатывает команда, а не сразу после ответа.")
            }
        }
        .font(.system(size: 13)).foregroundStyle(p.secondary)
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

// The card drawn as a lock screen wallpaper. The top third stays free for the clock and the
// widgets, the bottom for the flashlight and camera buttons; the rest takes as much of the card
// as fits, dropping from the end: antonyms and synonyms, then examples one by one.
struct WallpaperCard: View {
    let w: Word?
    let prefs: Prefs
    let recall: Bool
    let palette: Palette

    var body: some View {
        ZStack(alignment: .topLeading) {
            palette.bg
            Group {
                if let w {
                    ViewThatFits(in: .vertical) {
                        card(w, examples: 3, related: true)
                        card(w, examples: 3, related: false)
                        card(w, examples: 2, related: false)
                        card(w, examples: 1, related: false)
                        card(w, examples: 0, related: false)
                    }
                } else {
                    Text("Слова закончились: выберите другие в приложении").font(.system(size: 44)).foregroundStyle(palette.text)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(.horizontal, 90)
            .padding(.top, 900)
            .padding(.bottom, 300)
        }
    }

    private func card(_ w: Word, examples: Int, related: Bool) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            if recall {
                Text("Вспомните слово").font(.system(size: 34, weight: .semibold)).foregroundStyle(palette.accent)
                meanings(w, size: 58)
                // The Serbian sentences would give the word away; their translations are the hints.
                ForEach(Array(w.examples.prefix(examples).enumerated()), id: \.offset) { _, e in
                    Text("«\(e.ru)»").font(.system(size: 36)).foregroundStyle(palette.secondary)
                }
            } else {
                if let sec = prefs.secondary(w.cyr), !w.isArticle { Text(sec).font(.system(size: 40)).foregroundStyle(palette.secondary) }
                Text(w.isArticle && w.pos == "compare" ? (w.meanings.first ?? w.cyr) : prefs.headword(w))
                    .font(.serif(110, .semibold)).foregroundStyle(palette.word).minimumScaleFactor(0.4).lineLimit(2)
                if !w.isArticle {
                    let gram = [Labels.pos(w.pos), w.gram, w.forms].filter { !$0.isEmpty }.joined(separator: " · ")
                    if !gram.isEmpty { Text(gram).font(.system(size: 34)).foregroundStyle(palette.secondary) }
                    meanings(w, size: 54)
                }
                ForEach(Array(w.examples.prefix(examples).enumerated()), id: \.offset) { _, e in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(highlighted(prefs.primary(e.sr), palette.accent)).font(.serif(44)).foregroundStyle(palette.text)
                        Text(e.ru).font(.system(size: 34)).foregroundStyle(palette.secondary)
                    }
                    .padding(.top, 8)
                }
                if related {
                    if !w.syn.isEmpty { pairs("синонимы", w.syn) }
                    if !w.ant.isEmpty { pairs("антонимы", w.ant) }
                }
            }
        }
    }

    private func meanings(_ w: Word, size: CGFloat) -> some View {
        ForEach(Array(w.meanings.prefix(3).enumerated()), id: \.offset) { i, m in
            Text("\(i + 1). \(m)").font(.serif(size)).foregroundStyle(palette.text)
        }
    }

    private func pairs(_ title: String, _ words: [String]) -> some View {
        (Text(title + ": ").foregroundColor(palette.accent) + Text(words.prefix(6).map { prefs.primary($0) }.joined(separator: ", ")).foregroundColor(palette.text))
            .font(.system(size: 36))
    }
}

struct CardWallpaperIntent: AppIntent {
    static var title: LocalizedStringResource = "Обои с карточкой"
    static var description = IntentDescription("Рисует текущую карточку как картинку для обоев экрана блокировки.")

    // The widgets' short deck lacks examples, forms and synonyms; the app's own dictionary has them.
    @MainActor
    private func fullWord(_ key: String) -> Word? {
        Dict.load(patch: Dict.updater().stored()).words.first { $0.key == key }
    }

    @MainActor
    func perform() async throws -> some IntentResult & ReturnsValue<IntentFile> {
        let repo = LockDeck.repo()
        let w = repo?.current().map { fullWord($0.key) ?? $0 }
        let prefs = repo?.prefs ?? Prefs()
        let palette = palettes.indices.contains(prefs.theme) ? palettes[prefs.theme] : palettes[2]
        let screen = UIScreen.main
        let size = CGSize(width: screen.bounds.width * screen.scale, height: screen.bounds.height * screen.scale)
        let view = WallpaperCard(w: w, prefs: prefs, recall: repo?.current().map { repo?.asksRecall($0) ?? false } ?? false, palette: palette)
            .frame(width: 1290, height: 2796)
        let renderer = ImageRenderer(content: view)
        renderer.scale = size.width / 1290
        guard let data = renderer.uiImage?.pngData() else { throw CocoaError(.fileWriteUnknown) }
        return .result(value: IntentFile(data: data, filename: "reci-card.png", type: .png))
    }
}

struct ReciShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: CardWallpaperIntent(), phrases: ["Обои с карточкой в \(.applicationName)"],
                    shortTitle: "Обои с карточкой", systemImageName: "photo")
    }
}
