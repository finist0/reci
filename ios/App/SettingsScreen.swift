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
    @State private var exportFile: ProgressFile?
    @State private var exporting = false
    @State private var importing = false
    @State private var message: String?
    @State private var confirmReset = false
    @State private var confirmArticles: String?

    private let sizes = [10, 15, 20, 30]

    var body: some View {
        let p = model.palette
        let prefs = model.prefs
        VStack(spacing: 0) {
            Header(title: "Настройки")
            ScrollView {
                VStack(spacing: 0) {
                    progressCard
                    Group {
                        GroupTitle(text: "Учёба")
                        Panel {
                            let cats = prefs.categories
                            navRow("Что учим", (cats.count == 1 ? Labels.category(cats[0]) : "Разделов: \(cats.count)") + " · " + Labels.orders[prefs.order]) {
                                router.go(.categories)
                            }
                            Line()
                            choice("Карточки", ["Обучение", "Перевод", "Слово"], prefs.cardMode) { i in model.set { $0.cardMode = i } }
                            Text(modeText).font(.system(size: 13)).foregroundStyle(p.secondary).padding(.horizontal, 18).padding(.bottom, 12)
                            Line()
                            choice("Слов в работе одновременно", sizes.map(String.init), max(0, sizes.firstIndex(of: prefs.activeSize) ?? 0)) { i in
                                model.set { $0.activeSize = sizes[i] }
                            }
                            Line()
                            toggle("Кнопки «Не знаю / Знаю»", nil, prefs.useMarks) { v in model.set { $0.useMarks = v } }
                            Line()
                            toggle("Проверять выученные слова", "Через неделю, потом через 3 недели, 2 месяца, полгода и год", prefs.reviewKnown) { v in
                                model.set { $0.reviewKnown = v }
                            }
                        }
                    }
                    Group {
                        GroupTitle(text: "Экран блокировки")
                        Panel {
                            choice("Карточка на экране блокировки", ["Виджеты", "Обои"], prefs.lockMode) { i in model.set { $0.lockMode = i } }
                            LockSetup(mode: prefs.lockMode).padding(.horizontal, 18).padding(.bottom, 14)
                        }
                    }
                    Group {
                        GroupTitle(text: "Оформление")
                        Panel {
                            themes
                            Line()
                            choice("Алфавит", ["Кириллица", "Latinica"], prefs.latinFirst ? 1 : 0) { i in model.set { $0.latinFirst = i == 1 } }
                            Line()
                            toggle("Подпись другим алфавитом", nil, prefs.showBothScripts) { v in model.set { $0.showBothScripts = v } }
                            Line()
                            choice("Ударения", ["ку̏ћа", "ку́ћа", "кућа"], prefs.accentMode) { i in model.set { $0.accentMode = i } }
                            Line()
                            fontSize
                        }
                    }
                    Group {
                        GroupTitle(text: "Звук")
                        Panel {
                            toggle("Произносить слово сразу", nil, prefs.autoSpeak) { v in model.set { $0.autoSpeak = v } }
                            Line()
                            navRow("Голос", model.speech.voiceName.map { $0.prefix(1).uppercased() + $0.dropFirst() + " · нажмите, чтобы послушать" }
                                ?? "Нет сербского, хорватского или боснийского голоса") { model.speech.speak("Добар дан") }
                        }
                    }
                    Group {
                        GroupTitle(text: "Данные")
                        Panel {
                            navRow("Сохранить прогресс в файл", "Чтобы перенести его на другое устройство") {
                                if let data = try? Backup.export(model.repo) {
                                    exportFile = ProgressFile(data: data)
                                    exporting = true
                                }
                            }
                            Line()
                            navRow("Загрузить прогресс из файла", "Заменит прогресс на этом устройстве") { importing = true }
                            Line()
                            ForEach([("compare", "Снова показывать разборы"), ("note", "Снова показывать заметки")], id: \.0) { pos, title in
                                let hidden = model.repo.hiddenArticles(pos)
                                navRow(title, hidden > 0 ? "Скрыто: \(hidden)" : "Скрытых нет") { if hidden > 0 { confirmArticles = pos } }
                                Line()
                            }
                            Button { confirmReset = true } label: {
                                Text("Сбросить прогресс").font(.system(size: 16)).foregroundStyle(markDontKnow)
                                    .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 18).padding(.vertical, 15)
                            }
                            if let message {
                                Text(message).font(.system(size: 14)).foregroundStyle(p.secondary).padding(.horizontal, 18).padding(.bottom, 12)
                                    .accessibilityIdentifier("dataMessage")
                            }
                        }
                    }
                    Group {
                        GroupTitle(text: "О приложении")
                        Panel { about }
                    }
                    Spacer(minLength: 28)
                }
            }
        }
        .background(p.bg.ignoresSafeArea())
        .fileExporter(isPresented: $exporting, document: exportFile, contentType: .json, defaultFilename: "reci-progress-\(today()).json") { _ in }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .data, .plainText]) { result in
            guard case .success(let url) = result else { return }
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            do {
                let n = try Backup.import(Data(contentsOf: url), into: model.repo)
                message = "Загружен прогресс по \(n) \(plural(n, "слову", "словам", "словам"))."
            } catch {
                message = "Это не файл с прогрессом."
            }
            model.syncShowing()
            model.changed()
        }
        .alert("Сбросить прогресс?", isPresented: $confirmReset) {
            Button("Отмена", role: .cancel) {}
            Button("Сбросить", role: .destructive) {
                model.repo.resetAll()
                model.repo.resetPosition()
                model.syncShowing()
                message = "Прогресс сброшен."
                model.changed()
            }
        } message: {
            Text("Все слова снова станут новыми, избранное очистится.")
        }
        .alert(confirmArticles == "compare" ? "Снова показывать разборы похожих слов?" : "Снова показывать заметки?",
               isPresented: Binding(get: { confirmArticles != nil }, set: { if !$0 { confirmArticles = nil } })) {
            Button("Отмена", role: .cancel) { confirmArticles = nil }
            Button("Показывать") {
                if let pos = confirmArticles { model.repo.showArticlesAgain(pos) }
                confirmArticles = nil
                model.changed()
            }
        } message: {
            Text("Скрытые кнопкой «Не показывать» станут новыми и снова будут приходить на карточках.")
        }
    }

    private var modeText: String {
        switch model.prefs.cardMode {
        case 1: return "Видно только перевод: вспомните слово и откройте его кнопкой или ответом."
        case 2: return "Видно только слово: перевод открывается по нажатию."
        default:
            return "Слово учится по шагам: сначала показано целиком, после «Знаю» надо вспомнить его по переводу, после трёх «Знаю» подряд оно выучено. «Не знаю» вернёт слово через пять карточек. Долгое нажатие на «Знаю» — слово уже знакомо."
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
                Text("из \(all) слов знаю").font(.system(size: 15)).foregroundStyle(p.secondary)
            }
            ProgressView(value: all > 0 ? Double(known) / Double(all) : 0).tint(p.known)
            HStack(spacing: 16) {
                Label("\(dontKnow) не знаю", systemImage: "circle.fill").labelStyle(DotLabel(color: markDontKnow))
                Label("\(model.repo.todayCount) сегодня", systemImage: "circle.fill").labelStyle(DotLabel(color: p.accent))
            }
            .font(.system(size: 13)).foregroundStyle(p.secondary)
        }
        .padding(18)
        .background(RoundedRectangle(cornerRadius: 20).fill(p.card))
        .padding(.horizontal, 14).padding(.top, 8)
    }

    private var themes: some View {
        let p = model.palette
        return VStack(alignment: .leading, spacing: 10) {
            Text("Тема").font(.system(size: 16)).foregroundStyle(p.text).padding(.horizontal, 18)
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
                                Text(pal.name).font(.system(size: 12)).foregroundStyle(on ? p.accent : p.secondary)
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
                Text("Размер шрифта: \(model.prefs.wordSize)").font(.system(size: 16)).foregroundStyle(p.text)
                Spacer()
                Text("реч").font(.serif(CGFloat(model.prefs.wordSize) * 0.6)).foregroundStyle(p.word)
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
            Text("Версия \(version) · iPhone")
            Text("Автор: Константин Хоренко")
            Text("Лицензия: GNU GPL v3 или новее")
            Text("Частотность слов: FrequencyWords (OpenSubtitles), CC BY-SA 4.0.\nУдарения: Викисловарь (kaikki.org), CC BY-SA.\nЧасть карточек и подсказок составлена по материалам из открытых источников.\nШрифты Literata и Ruslan Display: SIL Open Font License.")
                .font(.system(size: 13)).padding(.top, 10)
        }
        .font(.system(size: 14)).foregroundStyle(p.secondary)
        .padding(18).frame(maxWidth: .infinity, alignment: .leading)
    }

    private func navRow(_ title: String, _ summary: String?, action: @escaping () -> Void) -> some View {
        let p = model.palette
        return Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.system(size: 16)).foregroundStyle(p.text)
                    if let summary { Text(summary).font(.system(size: 13)).foregroundStyle(p.secondary) }
                }
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 14)).foregroundStyle(p.faint)
            }
            .padding(.horizontal, 18).padding(.vertical, 13)
            .contentShape(Rectangle())
        }
        .accessibilityIdentifier(title)
    }

    private func toggle(_ title: String, _ summary: String?, _ on: Bool, _ set: @escaping (Bool) -> Void) -> some View {
        let p = model.palette
        return Toggle(isOn: Binding(get: { on }, set: set)) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 16)).foregroundStyle(p.text)
                if let summary { Text(summary).font(.system(size: 13)).foregroundStyle(p.secondary) }
            }
        }
        .tint(p.accent)
        .padding(.horizontal, 18).padding(.vertical, 10)
        .accessibilityIdentifier(title)
    }

    private func choice(_ title: String, _ options: [String], _ selected: Int, _ onSelect: @escaping (Int) -> Void) -> some View {
        let p = model.palette
        return VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.system(size: 16)).foregroundStyle(p.text)
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
