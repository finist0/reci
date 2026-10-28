import ReciCore
import SwiftUI

// {сербский текст} is shown in the chosen script and in the word color, the part after "|" inside
// braces is an ending and gets the accent color; **x** is bold.
func grammarText(_ s: String, prefs: Prefs, palette p: Palette) -> AttributedString {
    var out = AttributedString()
    var bold = false
    var i = s.startIndex
    func piece(_ text: String, _ color: Color? = nil, serif: Bool = false) -> AttributedString {
        var a = AttributedString(text)
        if let color { a.foregroundColor = color }
        if serif { a.font = .serif(16, bold ? .bold : .semibold) } else if bold { a.font = .system(size: 16, weight: .bold) }
        return a
    }
    while i < s.endIndex {
        if s[i...].hasPrefix("**") {
            bold.toggle()
            i = s.index(i, offsetBy: 2)
        } else if s[i] == "{" {
            let start = s.index(after: i)
            let end = s[start...].firstIndex(of: "}") ?? s.endIndex
            let parts = s[start..<end].split(separator: "|", maxSplits: 1, omittingEmptySubsequences: false).map(String.init)
            out += piece(prefs.primary(parts[0]), p.word, serif: true)
            if parts.count > 1 && !parts[1].isEmpty { out += piece(prefs.primary(parts[1]), p.accent, serif: true) }
            i = end < s.endIndex ? s.index(after: end) : end
        } else {
            var j = i
            while j < s.endIndex && s[j] != "{" && !s[j...].hasPrefix("**") { j = s.index(after: j) }
            out += piece(String(s[i..<j]))
            i = j
        }
    }
    return out
}

struct GrammarScreen: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router
    @State private var tab = 0
    @State private var q = ""

    var body: some View {
        let p = model.palette
        let n = { (pos: String) in model.repo.words.filter { $0.pos == pos }.count }
        VStack(spacing: 0) {
            Header(title: "Справочник")
            Picker("", selection: $tab) {
                Text("Грамматика").tag(0)
                Text("Похожие · \(n("compare"))").tag(1)
                Text("Заметки · \(n("note"))").tag(2)
            }
            .pickerStyle(.segmented).padding(.horizontal, 14).padding(.bottom, 10)
            if tab > 0 {
                TextField("найти по слову или названию", text: $q).textInputAutocapitalization(.never).autocorrectionDisabled()
                    .padding(12).background(RoundedRectangle(cornerRadius: 14).fill(p.card)).padding(.horizontal, 14)
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    if tab == 0 {
                        ForEach(groups(), id: \.0) { group, topics in
                            GroupTitle(text: group)
                            ForEach(topics) { t in tile(t.title) { router.go(.topic(t.id)) } }
                        }
                    } else {
                        ForEach(articles()) { w in
                            tile(w.pos == "compare" ? (w.meanings.first ?? w.cyr) : w.cyr) { router.go(.detail(w.key)) }
                        }
                    }
                }
                .padding(.bottom, 20)
            }
        }
        .background(p.bg.ignoresSafeArea())
    }

    // A group's topics are not contiguous in the file, so group them before listing.
    private func groups() -> [(String, [GrammarTopic])] {
        var order: [String] = []
        var by: [String: [GrammarTopic]] = [:]
        for t in model.repo.topics {
            if by[t.group] == nil { order.append(t.group) }
            by[t.group, default: []].append(t)
        }
        return order.map { ($0, by[$0]!) }
    }

    private func articles() -> [Word] {
        let q = q.trimmingCharacters(in: .whitespaces).lowercased()
        return model.repo.words.filter { $0.pos == (tab == 1 ? "compare" : "note") }.filter { w in
            q.isEmpty || w.cyr.lowercased().contains(q) || Translit.toLatin(w.cyr).lowercased().contains(q)
                || w.meanings.contains { $0.lowercased().contains(q) } || w.related.contains { $0.sr.contains(q) }
        }
    }

    private func tile(_ title: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.system(size: 16)).foregroundStyle(model.palette.text)
                .frame(maxWidth: .infinity, alignment: .leading).padding(16)
                .background(RoundedRectangle(cornerRadius: 16).fill(model.palette.card))
        }
        .padding(.horizontal, 14)
    }
}

struct TopicScreen: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router
    let id: String

    var body: some View {
        let p = model.palette
        let topics = model.repo.topics
        VStack(spacing: 0) {
            if let t = model.repo.topic(id) {
                Header(title: t.title)
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(Array(t.blocks.enumerated()), id: \.offset) { _, b in block(b) }
                        if let i = topics.firstIndex(where: { $0.id == id }), i + 1 < topics.count {
                            let next = topics[i + 1]
                            Button { router.go(.topic(next.id)) } label: {
                                Text("Дальше: \(next.title) ›").font(.system(size: 16, weight: .semibold)).foregroundStyle(p.accent)
                            }
                            .padding(.top, 12)
                        }
                    }
                    .padding(.horizontal, 20).padding(.bottom, 24)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                Header(title: "")
                Spacer()
            }
        }
        .background(p.bg.ignoresSafeArea())
    }

    @ViewBuilder
    private func block(_ b: GrammarBlock) -> some View {
        let p = model.palette
        let text = { (s: String) in Text(grammarText(s, prefs: model.prefs, palette: p)).foregroundStyle(p.text) }
        switch b.type {
        case "p":
            text(b.text ?? "").font(.system(size: 16))
        case "tip":
            text(b.text ?? "").font(.system(size: 15)).padding(14).frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 14).fill(p.highlight.opacity(0.6)))
        case "list":
            VStack(alignment: .leading, spacing: 6) {
                ForEach(Array((b.items ?? []).enumerated()), id: \.offset) { _, it in
                    HStack(alignment: .top, spacing: 6) { Text("•").foregroundStyle(p.accent); text(it).font(.system(size: 16)) }
                }
            }
        case "ex":
            Button { model.speech.speak(plain(b.sr ?? "")) } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text(highlighted(model.prefs.primary(b.sr ?? ""), p.accent)).font(.serif(17)).foregroundStyle(p.text)
                    Text(b.ru ?? "").font(.system(size: 14)).foregroundStyle(p.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        case "table":
            ScrollView(.horizontal, showsIndicators: false) {
                Grid(alignment: .leading, horizontalSpacing: 14, verticalSpacing: 8) {
                    GridRow {
                        ForEach(Array((b.head ?? []).enumerated()), id: \.offset) { _, h in
                            text(h).font(.system(size: 13, weight: .semibold)).fixedSize()
                        }
                    }
                    ForEach(Array((b.rows ?? []).enumerated()), id: \.offset) { _, row in
                        GridRow {
                            ForEach(Array(row.enumerated()), id: \.offset) { _, c in text(c).font(.system(size: 15)).fixedSize() }
                        }
                    }
                }
                .padding(12)
            }
            .background(RoundedRectangle(cornerRadius: 14).fill(p.card))
        default:
            EmptyView()
        }
    }
}
