import ReciCore
import SwiftUI

enum Route: Hashable {
    case detail(String)
    case topic(String)
    case grammar
    case categories
    case search
    case settings
    case list(String)
}

final class Router: ObservableObject {
    @Published var path: [Route] = []

    func go(_ r: Route) { path.append(r) }

    // Grammar topics, comparison cards ("cmp:") and word cards ("w:") share one link format.
    func openRef(_ id: String?) {
        guard let id else { return go(.grammar) }
        if id.hasPrefix("w:") { go(.detail(String(id.dropFirst(2)))) } else if id.hasPrefix("cmp:") { go(.detail(id)) } else { go(.topic(id)) }
    }
}

// "[x]" marks the headword inside an example sentence.
func highlighted(_ text: String, _ accent: Color) -> AttributedString {
    var out = AttributedString()
    var rest = Substring(text)
    while let open = rest.firstIndex(of: "[") {
        out += AttributedString(String(rest[..<open]))
        let afterOpen = rest.index(after: open)
        guard let close = rest[afterOpen...].firstIndex(of: "]") else {
            rest = rest[afterOpen...]
            break
        }
        var hl = AttributedString(String(rest[afterOpen..<close]))
        hl.foregroundColor = accent
        hl.font = .serif(17, .semibold)
        out += hl
        rest = rest[rest.index(after: close)...]
    }
    out += AttributedString(String(rest))
    return out
}

func plain(_ text: String) -> String { text.replacingOccurrences(of: "[", with: "").replacingOccurrences(of: "]", with: "") }

struct SectionLabel: View {
    @EnvironmentObject var model: AppModel
    let text: String
    var body: some View {
        Text(text.uppercased()).font(.system(size: 12, weight: .semibold)).tracking(1).foregroundStyle(model.palette.secondary)
            .padding(.bottom, 6)
    }
}

struct WordBody: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router
    let w: Word
    var covered = false
    var hideWord = false
    var onUncover: () -> Void = {}
    var onReveal: () -> Void = {}

    var body: some View {
        if w.isArticle {
            ArticleBody(w: w)
        } else if hideWord {
            RecallBody(w: w, onReveal: onReveal)
        } else {
            wordView
        }
    }

    private var p: Palette { model.palette }
    private var prefs: Prefs { model.prefs }

    private var wordView: some View {
        // Phrases are several words long and would wrap badly at the headword size.
        let size = CGFloat(w.pos == "phrase" ? Double(prefs.wordSize) * 0.72 : Double(prefs.wordSize))
        return VStack(alignment: .leading, spacing: 0) {
            if let sec = prefs.secondary(w.cyr) {
                Text(sec).font(.system(size: 15)).foregroundStyle(p.secondary).padding(.trailing, 40)
            }
            Text(prefs.headword(w)).font(.serif(size, .semibold)).foregroundStyle(p.word).padding(.trailing, 40)
                .accessibilityIdentifier("headword")
            HStack(spacing: 12) {
                Button { model.speech.speak(w.cyr) } label: {
                    Image(systemName: "speaker.wave.2.fill").foregroundStyle(p.accent).frame(width: 38, height: 38)
                        .background(Circle().fill(p.highlight))
                }
                .accessibilityLabel("Произнести")
                VStack(alignment: .leading, spacing: 2) {
                    GramLine(w: w)
                    if !w.forms.isEmpty { Text(prefs.primary(w.forms)).font(.system(size: 14)).foregroundStyle(p.secondary) }
                }
            }
            .padding(.top, 10)
            GrammarLinks(w: w)
            ZStack {
                Meanings(w: w)
                if covered {
                    Button(action: onUncover) {
                        Text("Показать перевод").font(.system(size: 15, weight: .semibold)).foregroundStyle(p.accent)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(RoundedRectangle(cornerRadius: 14).fill(p.highlight))
                    }
                }
            }
            .padding(.top, 18)
            Details(w: w)
        }
    }
}

struct GramLine: View {
    @EnvironmentObject var model: AppModel
    let w: Word
    var body: some View {
        Text(line()).font(.system(size: 14)).foregroundStyle(model.palette.secondary)
    }

    private func line() -> AttributedString {
        var out = AttributedString(Labels.pos(w.pos))
        guard !w.gram.isEmpty else { return out }
        var g = AttributedString("  " + w.gram)
        g.font = .system(size: 14, weight: .semibold)
        if w.ruGram.isEmpty {
            g.foregroundColor = w.pos == "noun" ? model.palette.accent : model.palette.text
            return out + g
        }
        // A different gender than in Russian; the strong mark when the Russian word looks alike.
        g.foregroundColor = w.ruNoun.isEmpty ? markUnsure : markDontKnow
        out += g
        out += AttributedString(w.ruNoun.isEmpty ? "  · в рус. \(w.ruGram)" : "  ≠ рус. «\(w.ruNoun)» \(w.ruGram)")
        return out
    }
}

struct Meanings: View {
    @EnvironmentObject var model: AppModel
    let w: Word
    var body: some View {
        let p = model.palette
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(w.meanings.enumerated()), id: \.offset) { i, m in
                HStack(alignment: .top, spacing: 10) {
                    Text("\(i + 1)").font(.system(size: 12, weight: .bold)).foregroundStyle(p.accent)
                        .frame(width: 22, height: 22).background(Circle().fill(p.highlight))
                    Text(m).font(.serif(19)).foregroundStyle(p.text)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct GrammarLinks: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router
    let w: Word
    var body: some View {
        let compares = model.repo.comparesFor(w), links = model.repo.linksFor(w)
        if !compares.isEmpty || !links.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(compares) { c in
                        link("≈ " + (c.meanings.first ?? c.cyr)) { router.go(.detail(c.key)) }
                    }
                    ForEach(links, id: \.self) { id in
                        link("§ " + (model.repo.topic(id)?.title ?? id)) { router.go(.topic(id)) }
                    }
                }
            }
            .padding(.top, 10)
        }
    }

    private func link(_ title: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.system(size: 13)).foregroundStyle(model.palette.accent)
                .padding(.horizontal, 10).padding(.vertical, 5)
                .background(Capsule().stroke(model.palette.divider))
        }
    }
}

// A Serbian word mentioned on a card: its own card if the dictionary has one, else its sound.
struct WordChip: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router
    let sr: String
    var from: String = ""
    var body: some View {
        let target = model.repo.wordByCyr(sr)
        Button {
            if let target, target.key != from { router.go(.detail(target.key)) } else { model.speech.speak(sr) }
        } label: {
            Text(model.prefs.primary(sr) + (target != nil ? " ›" : "")).font(.system(size: 15)).foregroundStyle(model.palette.text)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(Capsule().fill(model.palette.panel))
        }
    }
}

struct Details: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router
    let w: Word

    var body: some View {
        let p = model.palette
        VStack(alignment: .leading, spacing: 0) {
            if !w.examples.isEmpty {
                Divider().overlay(p.divider).padding(.vertical, 16)
                SectionLabel(text: "Примеры")
                ForEach(Array(w.examples.enumerated()), id: \.offset) { _, e in
                    Button { model.speech.speak(plain(e.sr)) } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(highlighted(model.prefs.primary(e.sr), p.accent)).font(.serif(17)).foregroundStyle(p.text)
                            Text(e.ru).font(.system(size: 14)).foregroundStyle(p.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.bottom, 10)
                }
            }
            if !w.syn.isEmpty || !w.ant.isEmpty {
                Divider().overlay(p.divider).padding(.vertical, 16)
                if !w.syn.isEmpty {
                    SectionLabel(text: "Синонимы")
                    Flow(items: w.syn) { WordChip(sr: $0, from: w.key) }
                }
                if !w.ant.isEmpty {
                    SectionLabel(text: "Антонимы").padding(.top, 10)
                    Flow(items: w.ant) { WordChip(sr: $0, from: w.key) }
                }
            }
            if !w.related.isEmpty {
                Divider().overlay(p.divider).padding(.vertical, 16)
                SectionLabel(text: "Однокоренные")
                RelatedList(items: w.related, from: w.key)
            }
            let notes = ([w.note] + w.tips).filter { !$0.isEmpty }
            if !notes.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Полезно знать").font(.system(size: 13, weight: .semibold)).foregroundStyle(p.accent)
                    ForEach(notes, id: \.self) { t in
                        HStack(alignment: .top, spacing: 6) {
                            Text("•")
                            Text(t)
                        }
                        .font(.system(size: 15)).foregroundStyle(p.text)
                    }
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 14).fill(p.highlight.opacity(0.6)))
                .padding(.top, 18)
            }
        }
    }
}

struct RelatedList: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router
    let items: [Related]
    var from = ""
    var body: some View {
        let p = model.palette
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(items.enumerated()), id: \.offset) { _, r in
                let target = model.repo.wordByCyr(r.sr)
                Button {
                    if let target, target.key != from { router.go(.detail(target.key)) } else { model.speech.speak(r.sr) }
                } label: {
                    (Text(model.prefs.primary(r.sr) + (target != nil ? " ›" : "")).fontWeight(.semibold).foregroundColor(p.word)
                        + Text("  —  ").foregroundColor(p.faint) + Text(r.ru).foregroundColor(p.text))
                        .font(.system(size: 16))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .multilineTextAlignment(.leading)
                }
            }
        }
    }
}

// The translation side only: meanings and grammar, with a button that shows the word.
struct RecallBody: View {
    @EnvironmentObject var model: AppModel
    let w: Word
    let onReveal: () -> Void
    var body: some View {
        let p = model.palette
        VStack(alignment: .leading, spacing: 12) {
            Text("· · ·").font(.serif(CGFloat(model.prefs.wordSize))).foregroundStyle(p.faint)
            GramLine(w: w)
            Meanings(w: w)
            // The Russian side of the examples as a hint; the Serbian sentences would give the word away.
            if !w.examples.isEmpty {
                Divider().overlay(p.divider)
                SectionLabel(text: "Как сказать по-сербски")
                ForEach(Array(w.examples.enumerated()), id: \.offset) { _, e in
                    Text("«\(e.ru)»").font(.system(size: 16)).foregroundStyle(p.text)
                }
            }
            Button(action: onReveal) {
                Text("Показать слово").font(.system(size: 16, weight: .semibold)).foregroundStyle(p.accent)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(RoundedRectangle(cornerRadius: 14).fill(p.highlight))
            }
            .padding(.top, 8)
        }
    }
}

// Comparisons ("compare") and short notes ("note") share the card but not the word layout.
struct ArticleBody: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router
    let w: Word
    var body: some View {
        let p = model.palette
        let compare = w.pos == "compare"
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: compare ? "Похожие слова" : "Заметка")
            Text(compare ? (w.meanings.first ?? model.prefs.primary(w.cyr)) : w.cyr)
                .font(.serif(26, .semibold)).foregroundStyle(p.word)
                .accessibilityIdentifier("articleTitle")
            if compare {
                RelatedList(items: w.related, from: w.key)
                if !w.note.isEmpty { Text(w.note).font(.system(size: 16)).foregroundStyle(p.text) }
            } else {
                Text(w.note).font(.system(size: 16)).foregroundStyle(p.text)
                if !w.related.isEmpty {
                    SectionLabel(text: "Слова")
                    RelatedList(items: w.related, from: w.key)
                }
            }
            if !w.examples.isEmpty {
                Divider().overlay(p.divider)
                SectionLabel(text: "Примеры")
                ForEach(Array(w.examples.enumerated()), id: \.offset) { _, e in
                    Button { model.speech.speak(plain(e.sr)) } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(highlighted(model.prefs.primary(e.sr), p.accent)).font(.serif(17)).foregroundStyle(p.text)
                            Text(e.ru).font(.system(size: 14)).foregroundStyle(p.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        }
    }
}

// Chips that wrap onto the next line.
struct Flow<Item: Hashable, Content: View>: View {
    let items: [Item]
    let content: (Item) -> Content
    var body: some View {
        FlowLayout(spacing: 8) {
            ForEach(items, id: \.self) { content($0) }
        }
    }
}

struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 300
        var x: CGFloat = 0, y: CGFloat = 0, row: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > width {
                x = 0
                y += row + spacing
                row = 0
            }
            x += size.width + spacing
            row = max(row, size.height)
        }
        return CGSize(width: width, height: y + row)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, row: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX {
                x = bounds.minX
                y += row + spacing
                row = 0
            }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            row = max(row, size.height)
        }
    }
}
