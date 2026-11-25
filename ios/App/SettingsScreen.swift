import ReciCore
import SwiftUI
import UniformTypeIdentifiers

struct ProgressFile: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data

    init(data: Data) { self.data = data }

    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

struct SettingsScreen: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router
    var lockGuide = false
    // The guide scrolls to the lock screen section once, not on the way back from a screen
    // opened from the settings.
    @State private var guided = false
    @State private var exportFile: ProgressFile?
    @State private var exporting = false
    @State private var importing = false
    @State private var message: String?
    @State private var confirmReset = false
    @State private var confirmArticles: String?
    @State private var writing = false
    @State private var whatSent = false
    @State private var helping = false

    private let sizes = [10, 15, 20, 30]
    // The values of Prefs.uiLang in the order of the interface language choice.
    private let uiLangs = ["ru", "sr", "en"]

    var body: some View {
        let p = model.palette
        let prefs = model.prefs
        let deck = model.deck
        VStack(spacing: 0) {
            Header(title: t("Настройки"))
            ScrollViewReader { scroll in
                ScrollView {
                    VStack(spacing: 0) {
                        progressCard
                        Group {
                            GroupTitle(text: t("Язык"))
                            Panel {
                                let targets = Decks.targets
                                let natives = Decks.natives(deck.target)
                                menuChoice(t("Учу"), targets.map(Decks.name), targets.firstIndex(of: deck.target) ?? 0) { i in
                                    model.switchDeck(Decks.pick(targets[i], deck.native).id)
                                }
                                Line()
                                menuChoice(t("Перевод на"), natives.map(Decks.name), natives.firstIndex(of: deck.native) ?? 0) { i in
                                    model.switchDeck(Decks.pick(deck.target, natives[i]).id)
                                }
                                Line()
                                // Each language is named in itself, so that a user who cannot read
                                // the interface finds their own.
                                menuChoice(t("Язык интерфейса"), ["Русский", "Српски", "English"],
                                           uiLangs.firstIndex(of: prefs.uiLang) ?? 0) { i in model.set { $0.uiLang = uiLangs[i] } }
                            }
                        }
                        Group {
                            GroupTitle(text: t("Учёба"))
                            Panel {
                                navRow(t("Что учим"), studySummary) { router.go(.categories) }
                                Line()
                                choice(t("Карточки"), [t("Обучение"), t("Перевод"), t("Слово")], prefs.cardMode) { i in model.set { $0.cardMode = i } }
                                Text(modeText).font(.ui(13)).foregroundStyle(p.secondary).padding(.horizontal, 18).padding(.bottom, 12)
                                Line()
                                choice(t("Слов в работе одновременно"), sizes.map(String.init), max(0, sizes.firstIndex(of: prefs.activeSize) ?? 0)) { i in
                                    model.set { $0.activeSize = sizes[i] }
                                }
                                Line()
                                toggle(t("Кнопки «Не знаю / Знаю»"), nil, prefs.useMarks) { v in model.set { $0.useMarks = v } }
                                Line()
                                toggle(t("Проверять выученные слова"), t("Через неделю, потом через 3\u{00A0}недели, 2\u{00A0}месяца, полгода и год"), prefs.reviewKnown) { v in
                                    model.set { $0.reviewKnown = v }
                                }
                                if deck.target == "sr" {
                                    Line()
                                    navRow(t("Куда пойти учиться?"), t("Школы сербского языка")) { router.go(.schools) }
                                }
                            }
                        }
                        Group {
                            GroupTitle(text: t("Экран блокировки")).id("lock")
                            if lockGuide {
                                Text(t("Сделайте шаги ниже — и карточка будет на экране блокировки."))
                                    .font(.ui(14)).foregroundStyle(p.text)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(14).background(RoundedRectangle(cornerRadius: 16).fill(p.highlight))
                                    .padding(.horizontal, 14).padding(.bottom, 8)
                                    .accessibilityIdentifier("lockGuide")
                            }
                            Panel {
                                choice(t("Карточка на экране блокировки"), [t("Виджеты"), t("Обои")], prefs.lockMode) { i in model.set { $0.lockMode = i } }
                                LockSetup(mode: prefs.lockMode).padding(.horizontal, 18).padding(.bottom, 14)
                            }
                            .overlay(RoundedRectangle(cornerRadius: 20).stroke(lockGuide ? p.accent : .clear, lineWidth: 2).padding(.horizontal, 14))
                        }
                        Group {
                            GroupTitle(text: t("Оформление"))
                            Panel {
                                themes
                                Line()
                                if deck.hasSerbian {
                                    choice(t("Алфавит"), [t("Кириллица"), "Latinica"], prefs.latinFirst ? 1 : 0) { i in model.set { $0.latinFirst = i == 1 } }
                                    Line()
                                }
                                if deck.target == "sr" {
                                    toggle(t("Подпись другим алфавитом"), nil, prefs.showBothScripts) { v in model.set { $0.showBothScripts = v } }
                                    Line()
                                    choice(t("Ударения"), deck.accentSamples, prefs.accentMode) { i in model.set { $0.accentMode = i } }
                                } else {
                                    // One kind of mark only: "with" is mode 1, "without" is mode 2.
                                    choice(t("Ударения"), deck.accentSamples, prefs.accentMode == 2 ? 1 : 0) { i in model.set { $0.accentMode = i + 1 } }
                                }
                                Line()
                                fontSize
                            }
                        }
                        Group {
                            GroupTitle(text: t("Звук"))
                            Panel {
                                toggle(t("Произносить слово сразу"), nil, prefs.autoSpeak) { v in model.set { $0.autoSpeak = v } }
                                Line()
                                navRow(t("Голос"), model.speech.voiceName.map { t("%s · нажмите, чтобы послушать", $0.prefix(1).uppercased() + String($0.dropFirst())) }
                                    ?? deck.noVoice) { model.speech.speak(deck.voiceSample) }
                            }
                        }
                        Group {
                            GroupTitle(text: t("Данные"))
                            Panel {
                                navRow(t("Сохранить прогресс в файл"), t("Чтобы перенести его на другое устройство")) {
                                    if let data = try? Backup.export(model.repo) {
                                        exportFile = ProgressFile(data: data)
                                        exporting = true
                                    }
                                }
                                Line()
                                navRow(t("Загрузить прогресс из файла"), t("Заменит прогресс на этом устройстве")) { importing = true }
                                Line()
                                ForEach([("compare", t("Снова показывать разборы")), ("note", t("Снова показывать заметки"))], id: \.0) { pos, title in
                                    let hidden = model.repo.hiddenArticles(pos)
                                    navRow(title, hidden > 0 ? t("Скрыто: %d", hidden) : t("Скрытых нет")) { if hidden > 0 { confirmArticles = pos } }
                                    Line()
                                }
                                Button { confirmReset = true } label: {
                                    Text(t("Сбросить прогресс")).font(.ui(16)).foregroundStyle(markDontKnow)
                                        .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 18).padding(.vertical, 15)
                                }
                                if let message {
                                    Text(message).font(.ui(14)).foregroundStyle(p.secondary).padding(.horizontal, 18).padding(.bottom, 12)
                                        .accessibilityIdentifier("dataMessage")
                                }
                            }
                        }
                        Group {
                            GroupTitle(text: t("Обратная связь"))
                            Panel {
                                let waiting = model.telemetry.state.queue.count
                                navRow(t("Написать разработчику"), waiting > 0 ? t("Ждут отправки: %d", waiting) : t("Идея, ошибка или вопрос")) { writing = true }
                                Line()
                                toggle(t("Анонимная статистика"), t("Раз в день: сколько карточек показано и ответов дано, сколько слов выучено, разделы и настройки"),
                                       model.telemetry.state.enabled) { v in model.setStatsEnabled(v) }
                                Line()
                                navRow(t("Что отправляется"), model.telemetry.state.last.map { t("Последний раз: %s", $0.day) } ?? t("Ещё ничего не отправлялось"),
                                       id: "whatIsSent") {
                                    whatSent = true
                                }
                                Line()
                                navRow(t("Помочь автору"), t("Поделиться, поставить звезду"), id: "helpTheAuthor") { helping = true }
                            }
                            if let toast = model.sentToast { SentToast(text: toast).padding(.top, 10) }
                        }
                        Group {
                            GroupTitle(text: t("О приложении"))
                            Panel { about }
                        }
                        Spacer(minLength: 28)
                    }
                }
                .onAppear {
                    guard lockGuide, !guided else { return }
                    guided = true
                    // After the push animation, so that the scroll is not lost.
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { withAnimation { scroll.scrollTo("lock", anchor: .top) } }
                }
            }
        }
        .background(p.bg.ignoresSafeArea())
        .sheet(isPresented: $writing) { WriteSheet().environmentObject(model) }
        .sheet(isPresented: $whatSent) { WhatSentSheet().environmentObject(model) }
        .sheet(isPresented: $helping) { HelpSheet().environmentObject(model) }
        .fileExporter(isPresented: $exporting, document: exportFile, contentType: .json, defaultFilename: "reci-progress-\(today()).json") { _ in }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .data, .plainText]) { result in
            guard case .success(let url) = result else { return }
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            do {
                let n = try Backup.import(Data(contentsOf: url), into: model.repo)
                message = t("Загружен прогресс по %d %s.", n, plural(n, "слову", "словам", "словам"))
            } catch {
                message = t("Это не файл с прогрессом.")
            }
            model.syncShowing()
            model.changed()
        }
        .alert(t("Сбросить прогресс?"), isPresented: $confirmReset) {
            Button(t("Отмена"), role: .cancel) {}
            Button(t("Сбросить"), role: .destructive) {
                model.repo.resetAll()
                model.repo.resetPosition()
                model.syncShowing()
                message = t("Прогресс сброшен.")
                model.changed()
            }
        } message: {
            Text(t("Все слова снова станут новыми, избранное очистится."))
        }
        .alert(articlesTitle,
               isPresented: Binding(get: { confirmArticles != nil }, set: { if !$0 { confirmArticles = nil } })) {
            Button(t("Отмена"), role: .cancel) { confirmArticles = nil }
            Button(t("Показывать")) {
                if let pos = confirmArticles { model.repo.showArticlesAgain(pos) }
                confirmArticles = nil
                model.changed()
            }
        } message: {
            Text(t("Скрытые кнопкой «Не показывать» станут новыми и снова будут приходить на карточках."))
        }
    }

    private var studySummary: String {
        let cats = model.prefs.categories
        let what: String = cats.count == 1 ? Labels.category(cats[0]) : t("Разделов: %d", cats.count)
        return what + " · " + Labels.orders[model.prefs.order]
    }

    private var articlesTitle: String {
        let what: String = confirmArticles == "compare" ? t("разборы похожих слов") : t("заметки")
        return t("Снова показывать %s?", what)
    }

    private var modeText: String {
        switch model.prefs.cardMode {
        case 1: return t("Видно только перевод: вспомните слово и откройте его кнопкой или ответом.")
        case 2: return t("Видно только слово: перевод открывается по нажатию.")
        default:
            return t("Слово учится по шагам: сначала показано целиком, после «Знаю» ") +
                t("надо вспомнить его по переводу, после трёх «Знаю» подряд оно выучено. ") +
                t("«Не знаю» вернёт слово через пять карточек. Долгое нажатие на «Знаю» — слово уже знакомо.")
        }
    }

    private func today() -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date())
    }

    private var progressCard: some View {
        let p = model.palette
        let all = model.repo.words.count
        let known = model.repo.words.filter { model.repo.progress($0).state == Mark.known }.count
        let dontKnow = model.repo.words.filter { [Mark.dontKnow, Mark.unsure].contains(model.repo.progress($0).state) }.count
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .lastTextBaseline) {
                Text("\(known)").font(.serif(40, .semibold)).foregroundStyle(p.word).accessibilityIdentifier("knownCount")
                Text(t("из %d слов знаю", all)).font(.ui(15)).foregroundStyle(p.secondary)
            }
            ProgressView(value: all > 0 ? Double(known) / Double(all) : 0).tint(p.known)
            HStack(spacing: 16) {
                Label("\(dontKnow) " + t("не знаю"), systemImage: "circle.fill").labelStyle(DotLabel(color: markDontKnow))
                Label("\(model.repo.todayCount) " + t("сегодня"), systemImage: "circle.fill").labelStyle(DotLabel(color: p.accent))
            }
            .font(.ui(13)).foregroundStyle(p.secondary)
        }
        .padding(18)
        .background(RoundedRectangle(cornerRadius: 20).fill(p.card))
        .padding(.horizontal, 14).padding(.top, 8)
    }

    private var themes: some View {
        let p = model.palette
        return VStack(alignment: .leading, spacing: 10) {
            Text(t("Тема")).font(.ui(16)).foregroundStyle(p.text).padding(.horizontal, 18)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(palettes.indices, id: \.self) { i in
                        let pal = palettes[i]
                        let on = model.prefs.theme == i
                        Button { model.set { $0.theme = i } } label: {
                            VStack(spacing: 4) {
                                Circle().fill(pal.bg).frame(width: 52, height: 52)
                                    .overlay(Circle().fill(pal.accent).frame(width: 26, height: 26))
                                    .overlay(Circle().stroke(on ? p.accent : p.divider, lineWidth: on ? 3 : 1))
                                Text(pal.name).font(.ui(12)).foregroundStyle(on ? p.accent : p.secondary)
                            }
                        }
                        .accessibilityIdentifier("theme-\(pal.name)")
                    }
                }
                .padding(.horizontal, 14)
            }
        }
        .padding(.vertical, 12)
    }

    private var fontSize: some View {
        let p = model.palette
        return VStack(alignment: .leading) {
            HStack(alignment: .lastTextBaseline) {
                Text(t("Размер шрифта: %d", model.prefs.wordSize)).font(.ui(16)).foregroundStyle(p.text)
                Spacer()
                Text(model.deck.sample).font(.serif(CGFloat(model.prefs.wordSize) * 0.6)).foregroundStyle(p.word)
            }
            Slider(value: Binding(get: { Double(model.prefs.wordSize) }, set: { v in model.set { $0.wordSize = Int(v) } }), in: 28...72, step: 1)
                .tint(p.accent)
                .accessibilityIdentifier("wordSize")
        }
        .padding(.horizontal, 18).padding(.vertical, 12)
    }

    private var about: some View {
        let p = model.palette
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        return VStack(alignment: .leading, spacing: 4) {
            (Text("НЕ").foregroundColor(p.titleRed) + Text("РОДНАЯ РЕЧЬ").foregroundColor(p.word)).font(.display(26)).padding(.bottom, 6)
            Text(t("Версия %s", version) + " · iPhone")
            Text(t("Автор: Константин Хоренко"))
            Link(t("Лицензии и источники"), destination: URL(string: Telemetry.licenses)!)
                .foregroundStyle(p.accent).padding(.top, 8)
        }
        .font(.ui(14)).foregroundStyle(p.secondary)
        .padding(18).frame(maxWidth: .infinity, alignment: .leading)
    }

    // UI tests find a row by its title, or by an id that stays the same in every interface language.
    private func navRow(_ title: String, _ summary: String?, id: String? = nil, action: @escaping () -> Void) -> some View {
        let p = model.palette
        return Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.ui(16)).foregroundStyle(p.text)
                    if let summary { Text(summary).font(.ui(13)).foregroundStyle(p.secondary) }
                }
                .multilineTextAlignment(.leading)
                Spacer()
                Image(systemName: "chevron.right").font(.ui(14)).foregroundStyle(p.faint)
            }
            .padding(.horizontal, 18).padding(.vertical, 13)
            .contentShape(Rectangle())
        }
        .accessibilityIdentifier(id ?? title)
    }

    private func toggle(_ title: String, _ summary: String?, _ on: Bool, _ set: @escaping (Bool) -> Void) -> some View {
        let p = model.palette
        return Toggle(isOn: Binding(get: { on }, set: set)) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.ui(16)).foregroundStyle(p.text)
                if let summary { Text(summary).font(.ui(13)).foregroundStyle(p.secondary) }
            }
        }
        .tint(p.accent)
        .padding(.horizontal, 18).padding(.vertical, 10)
        .accessibilityIdentifier(title)
    }

    // A menu rather than segments: a list of languages does not fit across a phone.
    private func menuChoice(_ title: String, _ options: [String], _ selected: Int, _ onSelect: @escaping (Int) -> Void) -> some View {
        let p = model.palette
        return HStack {
            Text(title).font(.ui(16)).foregroundStyle(p.text)
            Spacer()
            Picker(title, selection: Binding(get: { selected }, set: onSelect)) {
                ForEach(options.indices, id: \.self) { i in Text(options[i]).tag(i) }
            }
            .pickerStyle(.menu)
            .tint(p.accent)
            .accessibilityIdentifier(title)
        }
        .padding(.leading, 18).padding(.trailing, 8).padding(.vertical, 6)
    }

    private func choice(_ title: String, _ options: [String], _ selected: Int, _ onSelect: @escaping (Int) -> Void) -> some View {
        let p = model.palette
        return VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.ui(16)).foregroundStyle(p.text)
            Picker(title, selection: Binding(get: { selected }, set: onSelect)) {
                ForEach(options.indices, id: \.self) { i in Text(options[i]).tag(i) }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier(title)
        }
        .padding(.horizontal, 18).padding(.vertical, 12)
    }
}

struct DotLabel: LabelStyle {
    let color: Color
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 8, height: 8)
            configuration.title
        }
    }
}
