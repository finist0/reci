import ReciCore
import SwiftUI

struct CardScreen: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router
    @State private var reporting: Word?

    var body: some View {
        let p = model.palette
        VStack(spacing: 0) {
            TopBar()
            ZStack(alignment: .topTrailing) {
                RoundedRectangle(cornerRadius: 28).fill(p.card).shadow(color: .black.opacity(0.08), radius: 4, y: 2)
                if let s = model.showing, let w = model.repo.word(s.key) {
                    CardText(w: w, top: 22, onReport: { reporting = w }) {
                        VStack(alignment: .leading, spacing: 6) {
                            if let stage = s.stage {
                                Text(stage).font(.ui(12, .semibold)).foregroundStyle(p.accent)
                                    .accessibilityIdentifier("stage")
                            }
                            WordBody(w: w, covered: s.covered, hideWord: s.recall && !s.revealed,
                                     onUncover: { model.uncover() }, onReveal: { model.reveal() })
                        }
                    }
                    .id(s.id)
                    if model.learnedToast {
                        LearnedToast().frame(maxHeight: .infinity, alignment: .bottom).frame(maxWidth: .infinity).padding(.bottom, 14)
                    } else if let t = model.sentToast {
                        SentToast(text: t).frame(maxHeight: .infinity, alignment: .bottom).frame(maxWidth: .infinity).padding(.bottom, 14)
                    }
                } else if model.repo.exhausted {
                    Empty().frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 28))
            .padding(.horizontal, 14).padding(.vertical, 8)
            if model.showing != nil && !model.telemetry.state.noticeClosed { StatsNotice() }
            if model.prefs.useMarks, let s = model.showing, let w = model.repo.word(s.key) {
                if s.hint { LongPressHint() }
                MarksRow(answered: s.answered, article: w.isArticle) { a, learned in model.answer(a, learned: learned) }
                    .padding(.horizontal, 14).padding(.vertical, 4)
            }
            BottomNav()
        }
        .background(p.bg.ignoresSafeArea())
        .onAppear { model.syncShowing() }
        .sheet(item: $reporting) { w in ReportSheet(w: w).environmentObject(model) }
    }
}

// The scrolling text of a card. The star, the flag and the school button scroll with it; the
// flag and the school button come after the text, in the bottom corner of a short card and at
// the end of a long one.
struct CardText<Content: View>: View {
    let w: Word
    let top: CGFloat
    let onReport: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        GeometryReader { box in
            ScrollView {
                VStack(spacing: 0) {
                    content()
                        .padding(.horizontal, 24).padding(.top, top)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .overlay(alignment: .topTrailing) { Star(w: w).padding(10) }
                    Spacer(minLength: 4)
                    HStack(spacing: 0) {
                        Spacer()
                        SchoolsButton()
                        FlagButton(action: onReport)
                    }
                    .padding(8)
                }
                .frame(minHeight: box.size.height)
            }
        }
    }
}

struct TopBar: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router

    var body: some View {
        let p = model.palette
        HStack {
            DeckChip()
            Spacer()
            ForEach([("magnifyingglass", "Поиск", Route.search), ("book", "Грамматика", Route.grammar), ("gearshape", "Настройки", Route.settings)],
                    id: \.1) { item in
                Button { router.go(item.2) } label: {
                    Image(systemName: item.0).font(.ui(19)).foregroundStyle(p.text).frame(width: 40, height: 40)
                }
                .accessibilityLabel(item.1)
            }
        }
        .padding(.horizontal, 14).padding(.top, 4)
    }
}

struct DeckChip: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router

    var body: some View {
        let p = model.palette
        let cats = model.prefs.categories
        let first = cats.sorted().first
        let title = (first.map { $0.hasPrefix("L") ? Labels.category($0).components(separatedBy: " ")[0] : Labels.category($0) } ?? "—")
            + (cats.count > 1 ? " +\(cats.count - 1)" : "")
        let (k, t) = counts(cats)
        Button { router.go(.categories) } label: {
            HStack(spacing: 6) {
                Text(title).font(.ui(15, .semibold)).foregroundStyle(p.text)
                Text("\(k)/\(t)").font(.ui(13)).foregroundStyle(p.secondary).accessibilityIdentifier("deckCount")
                Image(systemName: "chevron.down").font(.ui(12)).foregroundStyle(p.secondary)
            }
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(Capsule().fill(p.card))
        }
    }

    // Known words out of all words in the chosen sections, the marked ones included.
    private func counts(_ cats: [String]) -> (Int, Int) {
        var k = 0, t = 0
        for w in model.repo.words where cats.contains(where: { model.repo.inCategory(w, $0) }) {
            t += 1
            if model.repo.progress(w).state == Mark.known { k += 1 }
        }
        return (k, t)
    }
}

struct Star: View {
    @EnvironmentObject var model: AppModel
    let w: Word
    var body: some View {
        let fav = model.repo.progress(w).fav
        Button { model.toggleFav(w) } label: {
            Image(systemName: fav ? "star.fill" : "star").font(.ui(20))
                .foregroundStyle(fav ? markUnsure : model.palette.faint).frame(width: 40, height: 40)
        }
        .accessibilityLabel("Избранное")
    }
}

struct LearnedToast: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        Label("Слово отмечено выученным", systemImage: "checkmark")
            .font(.ui(14, .semibold)).foregroundStyle(.white)
            .padding(.horizontal, 16).padding(.vertical, 9)
            .background(Capsule().fill(model.palette.known))
            .accessibilityIdentifier("learnedToast")
    }
}

struct LongPressHint: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        let p = model.palette
        HStack {
            Text("Слово уже знакомо? Удерживайте «Знаю» — оно сразу станет выученным.").font(.ui(14)).foregroundStyle(p.text)
            Button("✕") { model.closeHint() }.foregroundStyle(p.secondary).frame(width: 40, height: 40)
                .accessibilityLabel("Закрыть")
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 14).fill(p.highlight))
        .padding(.horizontal, 14)
    }
}

// Long-press on «Знаю» marks a word the user already knows as learned at once. Articles are
// read, not learned, so their buttons only say whether to show them again.
struct MarksRow: View {
    @EnvironmentObject var model: AppModel
    let answered: Int?
    let article: Bool
    let onAnswer: (Int, Bool) -> Void

    var body: some View {
        let p = model.palette
        HStack(spacing: 4) {
            mark(article ? "Перечитать позже" : "Не знаю", id: "markDontKnow", on: answered == Mark.dontKnow, color: article ? p.secondary : markDontKnow)
                .onTapGesture { onAnswer(Mark.dontKnow, false) }
            mark(article ? "Не показывать" : "Знаю", id: "markKnow", on: answered == Mark.known, color: p.known)
                .gesture(
                    LongPressGesture(minimumDuration: 0.5)
                        .onEnded { _ in if !article { onAnswer(Mark.known, true) } }
                        .exclusively(before: TapGesture().onEnded { onAnswer(Mark.known, false) })
                )
        }
        .padding(4)
        .background(Capsule().fill(p.panel))
    }

    private func mark(_ title: String, id: String, on: Bool, color: Color) -> some View {
        HStack(spacing: 8) {
            Circle().fill(on ? .white : color).frame(width: 8, height: 8)
            Text(title).font(.ui(16, .semibold)).foregroundStyle(on ? .white : model.palette.text)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 12)
        .background(Capsule().fill(on ? color : .clear))
        .contentShape(Capsule())
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityAddTraits(on ? .isSelected : [])
        .accessibilityIdentifier(id)
    }
}

struct BottomNav: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        let p = model.palette
        HStack(spacing: 10) {
            Button { model.step(-1) } label: {
                Image(systemName: "chevron.left").font(.ui(20, .semibold)).foregroundStyle(p.text)
                    .frame(width: 54, height: 54).background(Circle().fill(p.card))
            }
            .accessibilityLabel("Предыдущее слово")
            Button { model.step(1) } label: {
                HStack {
                    Text("Дальше").font(.ui(17, .semibold))
                    Image(systemName: "arrow.right")
                }
                .foregroundStyle(.white).frame(maxWidth: .infinity).frame(height: 54)
                .background(Capsule().fill(p.accent))
            }
            .accessibilityIdentifier("next")
        }
        .padding(.horizontal, 14).padding(.top, 6).padding(.bottom, 12)
    }
}

struct Empty: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router
    var body: some View {
        let p = model.palette
        VStack(spacing: 10) {
            Text("Здесь пока пусто").font(.serif(22, .semibold)).foregroundStyle(p.word)
            Text("В выбранных разделах не осталось слов").foregroundStyle(p.secondary)
            Button("Выбрать слова") { router.go(.categories) }.buttonStyle(.borderedProminent).tint(p.accent)
        }
    }
}
