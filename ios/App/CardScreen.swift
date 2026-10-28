import ReciCore
import SwiftUI

struct CardScreen: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router

    var body: some View {
        let p = model.palette
        VStack(spacing: 0) {
            TopBar()
            ZStack(alignment: .topTrailing) {
                RoundedRectangle(cornerRadius: 28).fill(p.card).shadow(color: .black.opacity(0.08), radius: 4, y: 2)
                if let s = model.showing, let w = model.repo.word(s.key) {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 6) {
                            if let stage = s.stage {
                                Text(stage).font(.system(size: 12, weight: .semibold)).foregroundStyle(p.accent)
                                    .accessibilityIdentifier("stage")
                            }
                            WordBody(w: w, covered: s.covered, hideWord: s.recall && !s.revealed,
                                     onUncover: { model.uncover() }, onReveal: { model.reveal() })
                        }
                        .padding(.horizontal, 24).padding(.vertical, 22)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .id(s.id)
                    Star(w: w).padding(10)
                    if model.learnedToast {
                        LearnedToast().frame(maxHeight: .infinity, alignment: .bottom).frame(maxWidth: .infinity).padding(.bottom, 14)
                    }
                } else if model.repo.exhausted {
                    Empty().frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 28))
            .padding(.horizontal, 14).padding(.vertical, 8)
            if model.prefs.useMarks, let s = model.showing, let w = model.repo.word(s.key) {
                if s.hint { LongPressHint() }
                MarksRow(answered: s.answered, article: w.isArticle) { a, learned in model.answer(a, learned: learned) }
                    .padding(.horizontal, 14).padding(.vertical, 4)
            }
            BottomNav()
        }
        .background(p.bg.ignoresSafeArea())
        .onAppear { model.syncShowing() }
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
                    Image(systemName: item.0).font(.system(size: 19)).foregroundStyle(p.text).frame(width: 40, height: 40)
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
                Text(title).font(.system(size: 15, weight: .semibold)).foregroundStyle(p.text)
                Text("\(k)/\(t)").font(.system(size: 13)).foregroundStyle(p.secondary).accessibilityIdentifier("deckCount")
                Image(systemName: "chevron.down").font(.system(size: 12)).foregroundStyle(p.secondary)
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
            Image(systemName: fav ? "star.fill" : "star").font(.system(size: 20))
                .foregroundStyle(fav ? markUnsure : model.palette.faint).frame(width: 40, height: 40)
        }
        .accessibilityLabel("Избранное")
    }
}

struct LearnedToast: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        Label("Слово отмечено выученным", systemImage: "checkmark")
            .font(.system(size: 14, weight: .semibold)).foregroundStyle(.white)
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
            Text("Слово уже знакомо? Удерживайте «Знаю» — оно сразу станет выученным.").font(.system(size: 14)).foregroundStyle(p.text)
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
            Text(title).font(.system(size: 16, weight: .semibold)).foregroundStyle(on ? .white : model.palette.text)
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
                Image(systemName: "chevron.left").font(.system(size: 20, weight: .semibold)).foregroundStyle(p.text)
                    .frame(width: 54, height: 54).background(Circle().fill(p.card))
            }
            .accessibilityLabel("Предыдущее слово")
            Button { model.step(1) } label: {
                HStack {
                    Text("Дальше").font(.system(size: 17, weight: .semibold))
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
