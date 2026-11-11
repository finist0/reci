import ReciCore
import SwiftUI

struct CardScreen: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router
    @State private var reporting: Word?

    var body: some View {
        let p = model.palette
        // On a short screen the bars around the card get tighter, so the card keeps room for its text.
        GeometryReader { screen in
            let short = screen.size.height < 700
            VStack(spacing: 0) {
                TopBar()
                ZStack(alignment: .topTrailing) {
                    RoundedRectangle(cornerRadius: 28).fill(p.card).shadow(color: .black.opacity(0.08), radius: 4, y: 2)
                    if let s = model.showing, let w = model.repo.word(s.key) {
                        CardText(w: w, top: short ? 16 : 22, onReport: { reporting = w }) {
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
                .padding(.horizontal, 14).padding(.vertical, short ? 5 : 8)
                if model.showing != nil && !model.telemetry.state.noticeClosed { StatsNotice() }
                if model.prefs.useMarks, let s = model.showing, let w = model.repo.word(s.key) {
                    if s.hint { LongPressHint() }
                    if short {
                        OneRow(answered: s.answered, article: w.isArticle)
                    } else {
                        MarksRow(answered: s.answered, article: w.isArticle) { a, learned in model.answer(a, learned: learned) }
                            .padding(.horizontal, 14).padding(.vertical, 4)
                        BottomNav()
                    }
                } else {
                    BottomNav()
                }
            }
        }
        .background(p.bg.ignoresSafeArea())
        .onAppear { model.syncShowing() }
        .sheet(item: $reporting) { w in ReportSheet(w: w).environmentObject(model) }
    }
}

// The scrolling text of a card. The star, the flag and the school button scroll with it; the
// flag and the school button come after the text, in the bottom corner of a short card and at
// the end of a long one. Text cut by the card edge fades out instead of ending mid-line.
struct CardText<Content: View>: View {
    @EnvironmentObject var model: AppModel
    let w: Word
    let top: CGFloat
    let onReport: () -> Void
    @ViewBuilder let content: () -> Content
    @State private var more = false

    var body: some View {
        let p = model.palette
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
                .background(GeometryReader { text in
                    Color.clear.preference(key: TextBottom.self, value: text.frame(in: .named("cardText")).maxY)
                })
            }
            .coordinateSpace(.named("cardText"))
            .onPreferenceChange(TextBottom.self) { more = $0 > box.size.height + 1 }
            .overlay(alignment: .bottom) {
                if more {
                    LinearGradient(colors: [p.card.opacity(0), p.card], startPoint: .top, endPoint: .bottom)
                        .frame(height: 16).allowsHitTesting(false)
                }
            }
        }
    }
}

// Where the bottom of a card's text is, in the coordinates of its scroll view.
private struct TextBottom: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

struct TopBar: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router

    var body: some View {
        let p = model.palette
        HStack {
            DeckChip().layoutPriority(1)
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
        let title = first.map { $0.hasPrefix("L") ? Labels.category($0).components(separatedBy: " ")[0] : Labels.category($0) } ?? "—"
        let more = cats.count > 1 ? " +\(cats.count - 1)" : ""
        let (k, t) = counts(cats)
        Button { router.go(.categories) } label: {
            // Short of room, the chip drops the count, then the "+N" and the arrow, and only then
            // cuts the title.
            ViewThatFits(in: .horizontal) {
                chip(title + more, count: "\(k)/\(t)", arrow: true)
                chip(title + more, count: nil, arrow: true)
                chip(title, count: nil, arrow: true)
                chip(title, count: nil, arrow: false)
            }
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(Capsule().fill(p.card))
        }
    }

    private func chip(_ title: String, count: String?, arrow: Bool) -> some View {
        let p = model.palette
        return HStack(spacing: 6) {
            Text(title).font(.ui(15, .semibold)).foregroundStyle(p.text).lineLimit(1)
            if let count { Text(count).font(.ui(13)).foregroundStyle(p.secondary).accessibilityIdentifier("deckCount") }
            if arrow { Image(systemName: "chevron.down").font(.ui(12)).foregroundStyle(p.secondary) }
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
    // Lower buttons, for the one row of a short screen.
    var compact = false
    let onAnswer: (Int, Bool) -> Void

    var body: some View {
        // Short of room, article buttons take short labels, and then the buttons drop their dots.
        ViewThatFits(in: .horizontal) {
            row(brief: false, dots: true)
            if article { row(brief: true, dots: true) }
            row(brief: article, dots: false)
        }
        .padding(4)
        .background(Capsule().fill(model.palette.panel))
    }

    private func row(brief: Bool, dots: Bool) -> some View {
        let p = model.palette
        return HStack(spacing: 4) {
            mark(article ? "Перечитать позже" : "Не знаю", brief: brief ? "Позже" : nil, dot: dots,
                 id: "markDontKnow", on: answered == Mark.dontKnow, color: article ? p.secondary : markDontKnow)
                .onTapGesture { onAnswer(Mark.dontKnow, false) }
            mark(article ? "Не показывать" : "Знаю", brief: brief ? "Скрыть" : nil, dot: dots,
                 id: "markKnow", on: answered == Mark.known, color: p.known)
                .gesture(
                    LongPressGesture(minimumDuration: 0.5)
                        .onEnded { _ in if !article { onAnswer(Mark.known, true) } }
                        .exclusively(before: TapGesture().onEnded { onAnswer(Mark.known, false) })
                )
        }
    }

    private func mark(_ title: String, brief: String?, dot: Bool, id: String, on: Bool, color: Color) -> some View {
        HStack(spacing: 8) {
            if dot { Circle().fill(on ? .white : color).frame(width: 8, height: 8) }
            Text(brief ?? title).font(.ui(16, .semibold)).foregroundStyle(on ? .white : model.palette.text).lineLimit(1)
        }
        .frame(maxWidth: .infinity).padding(.vertical, compact ? 8 : 12)
        .background(Capsule().fill(on ? color : .clear))
        .contentShape(Capsule())
        .accessibilityElement(children: .combine)
        .accessibilityLabel(title)
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

// A short screen puts "back", the answers and a round "next" in one row.
struct OneRow: View {
    @EnvironmentObject var model: AppModel
    let answered: Int?
    let article: Bool

    var body: some View {
        let p = model.palette
        HStack(spacing: 8) {
            Button { model.step(-1) } label: {
                Image(systemName: "chevron.left").font(.ui(18, .semibold)).foregroundStyle(p.text)
                    .frame(width: 46, height: 46).background(Circle().fill(p.card))
            }
            .accessibilityLabel("Предыдущее слово")
            MarksRow(answered: answered, article: article, compact: true) { a, learned in model.answer(a, learned: learned) }
            Button { model.step(1) } label: {
                Image(systemName: "arrow.right").font(.ui(18, .semibold)).foregroundStyle(.white)
                    .frame(width: 46, height: 46).background(Circle().fill(p.accent))
            }
            .accessibilityLabel("Дальше")
            .accessibilityIdentifier("next")
        }
        .padding(.horizontal, 14).padding(.top, 4).padding(.bottom, 8)
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
