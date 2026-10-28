import ReciCore
import SwiftUI

struct DetailScreen: View {
    @EnvironmentObject var model: AppModel
    let key: String
    @State private var answered: Int?
    @State private var learned = false
    @State private var started = false

    var body: some View {
        let p = model.palette
        VStack(spacing: 0) {
            Header(title: "")
            if let w = model.repo.word(key) {
                ZStack(alignment: .topTrailing) {
                    ScrollView {
                        WordBody(w: w).padding(24).frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .background(RoundedRectangle(cornerRadius: 28).fill(p.card))
                    Star(w: w).padding(10)
                }
                .padding(.horizontal, 14)
                if learned { LearnedToast().padding(.top, 10) }
                MarksRow(answered: answered, article: w.isArticle) { a, l in
                    model.repo.answer(w, a, learned: l)
                    answered = a
                    learned = l
                    model.changed()
                }
                .padding(.horizontal, 14).padding(.vertical, 12)
                .onAppear {
                    if !started {
                        started = true
                        answered = model.repo.answerOf(w)
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .background(p.bg.ignoresSafeArea())
    }
}

struct CategoriesScreen: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var router: Router
    @State private var sel: Set<String> = []
    @State private var order = 0
    @State private var loaded = false

    var body: some View {
        let p = model.palette
        let counts = model.repo.counts()
        let count = { (id: String) in counts[id]?.all ?? 0 }
        let withKnown = sel.contains("M:known")
        let total = model.repo.words.filter { w in
            sel.contains { model.repo.inCategory(w, $0) } && (withKnown || model.repo.progress(w).state != Mark.known)
        }.count
        VStack(spacing: 0) {
            Header(title: "Что учим")
            ScrollView {
                VStack(spacing: 0) {
                    Group {
                        GroupTitle(text: "Уровни")
                        Panel {
                            ForEach(Array(Labels.levels.enumerated()), id: \.offset) { i, item in
                                if i > 0 { Line() }
                                levelRow(item.0, item.1, known: counts["L\(item.0)"]?.known ?? 0, all: count("L\(item.0)"))
                            }
                        }
                    }
                    Group {
                        GroupTitle(text: "Разделы")
                        Text("Долгое нажатие на раздел, тему или отметку открывает список слов")
                            .font(.system(size: 12)).foregroundStyle(p.secondary).padding(.horizontal, 26).padding(.bottom, 8)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        chips(Labels.sections.filter { count($0.0) > 0 }.map { ($0.0, "\($0.1) · \(count($0.0))") })
                    }
                    Group {
                        GroupTitle(text: "Мои отметки")
                        chips(Labels.lists.map { ($0.0, "\($0.1) · \(count($0.0))") })
                    }
                    Group {
                        GroupTitle(text: "Темы")
                        chips(Labels.topics.filter { count("T:" + $0.0) > 0 }.map { ("T:" + $0.0, "\($0.1) · \(count("T:" + $0.0))") })
                    }
                    Group {
                        GroupTitle(text: "Порядок")
                        FlowLayout(spacing: 8) {
                            ForEach(Array(Labels.orders.enumerated()), id: \.offset) { i, name in
                                chip(name, on: order == i).onTapGesture { order = i }
                            }
                        }
                        .padding(.horizontal, 14)
                    }
                    Spacer(minLength: 16)
                }
            }
            Button {
                guard !sel.isEmpty else { return }
                model.repo.setCategories(Array(sel).sorted(), order: order)
                model.syncShowing()
                model.changed()
                dismiss()
            } label: {
                Text("Учить \(total) \(plural(total, "карточку", "карточки", "карточек"))")
                    .font(.system(size: 17, weight: .semibold)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity).frame(height: 54)
                    .background(Capsule().fill(sel.isEmpty ? p.faint : p.accent))
            }
            .accessibilityIdentifier("learn")
            .padding(14)
        }
        .background(p.bg.ignoresSafeArea())
        .onAppear {
            if !loaded {
                loaded = true
                sel = Set(model.prefs.categories)
                order = model.prefs.order
            }
        }
    }

    private func toggle(_ id: String) {
        if sel.contains(id) { sel.remove(id) } else { sel.insert(id) }
    }

    private func levelRow(_ level: Int, _ name: String, known: Int, all: Int) -> some View {
        let p = model.palette
        let id = "L\(level)"
        let on = sel.contains(id)
        return HStack(spacing: 14) {
            Text(name.components(separatedBy: " ")[0]).font(.serif(17, .bold)).foregroundStyle(on ? .white : p.accent)
                .frame(width: 44, height: 44).background(RoundedRectangle(cornerRadius: 12).fill(on ? p.accent : p.highlight))
            VStack(alignment: .leading, spacing: 6) {
                Text(name.components(separatedBy: "· ").last ?? name).font(.system(size: 16, weight: .medium)).foregroundStyle(p.text)
                ProgressView(value: all > 0 ? Double(known) / Double(all) : 0).tint(p.known)
                Text("знаю \(known) из \(all)").font(.system(size: 12)).foregroundStyle(p.secondary)
            }
            Button { router.go(.list(id)) } label: {
                Image(systemName: "chevron.right").foregroundStyle(p.secondary).frame(width: 32, height: 32)
            }
            .accessibilityLabel("Список слов")
            Image(systemName: on ? "checkmark.circle.fill" : "circle").foregroundStyle(on ? p.accent : p.faint)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .contentShape(Rectangle())
        .onTapGesture { toggle(id) }
        .accessibilityIdentifier(id)
    }

    private func chips(_ items: [(String, String)]) -> some View {
        FlowLayout(spacing: 8) {
            ForEach(items, id: \.0) { id, title in
                chip(title, on: sel.contains(id))
                    .gesture(
                        LongPressGesture(minimumDuration: 0.5).onEnded { _ in router.go(.list(id)) }
                            .exclusively(before: TapGesture().onEnded { toggle(id) })
                    )
                    .accessibilityIdentifier(id)
            }
        }
        .padding(.horizontal, 14)
    }

    private func chip(_ title: String, on: Bool) -> some View {
        let p = model.palette
        return Text(title).font(.system(size: 14, weight: .medium)).foregroundStyle(on ? .white : p.text)
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(Capsule().fill(on ? p.accent : p.card))
            .overlay(Capsule().stroke(on ? p.accent : p.divider))
            .contentShape(Capsule())
            .accessibilityAddTraits(.isButton)
    }
}

struct SearchScreen: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router
    @State private var q = ""
    @State private var byMeaning = false
    @FocusState private var focused: Bool

    var body: some View {
        let p = model.palette
        VStack(spacing: 0) {
            Header(title: "Поиск")
            TextField(byMeaning ? "слово по-русски" : "реч, reč", text: $q)
                .textInputAutocapitalization(.never).autocorrectionDisabled()
                .focused($focused)
                .padding(12).background(RoundedRectangle(cornerRadius: 14).fill(p.card))
                .padding(.horizontal, 14)
                .accessibilityIdentifier("searchField")
            Picker("", selection: $byMeaning) {
                Text("Сербское слово").tag(false)
                Text("Перевод").tag(true)
            }
            .pickerStyle(.segmented).padding(14)
            List(results(), id: \.key) { w in
                Button { router.go(.detail(w.key)) } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text(model.prefs.primary(w.cyr)).font(.serif(18, .semibold)).foregroundStyle(p.word)
                            Spacer()
                            Text(Labels.levelShort(w.level)).font(.system(size: 12)).foregroundStyle(p.secondary)
                        }
                        Text(w.meanings.joined(separator: " · ")).font(.system(size: 14)).foregroundStyle(p.secondary)
                    }
                }
                .listRowBackground(p.card)
            }
            .scrollContentBackground(.hidden)
        }
        .background(p.bg.ignoresSafeArea())
        .onAppear { focused = true }
    }

    private func results() -> [Word] {
        let q = q.trimmingCharacters(in: .whitespaces).lowercased()
        if q.isEmpty { return [] }
        return Array(model.repo.words.filter { w in
            byMeaning ? w.meanings.contains { $0.lowercased().contains(q) }
                : w.cyr.lowercased().hasPrefix(q) || Translit.toLatin(w.cyr).lowercased().hasPrefix(q)
        }.prefix(200))
    }
}

struct ListScreen: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router
    let id: String
    @State private var q = ""

    var body: some View {
        let p = model.palette
        let all = model.repo.words.filter { model.repo.inCategory($0, id) }
        let query = q.trimmingCharacters(in: .whitespaces).lowercased()
        let shown = query.isEmpty ? all : all.filter { w in
            w.cyr.lowercased().contains(query) || Translit.toLatin(w.cyr).lowercased().contains(query)
                || w.meanings.contains { $0.lowercased().contains(query) }
        }
        VStack(spacing: 0) {
            Header(title: "\(Labels.category(id)) · \(all.count)")
            TextField("найти в списке", text: $q).textInputAutocapitalization(.never).autocorrectionDisabled()
                .padding(12).background(RoundedRectangle(cornerRadius: 14).fill(p.card)).padding(.horizontal, 14)
            List(shown, id: \.key) { w in
                Button { router.go(.detail(w.key)) } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(w.pos == "compare" ? (w.meanings.first ?? w.cyr) : model.prefs.headword(w))
                            .font(.serif(18, .semibold)).foregroundStyle(p.word)
                        if !w.isArticle {
                            Text(w.meanings.joined(separator: " · ")).font(.system(size: 14)).foregroundStyle(p.secondary)
                        }
                    }
                }
                .listRowBackground(p.card)
            }
            .scrollContentBackground(.hidden)
        }
        .background(p.bg.ignoresSafeArea())
    }
}
