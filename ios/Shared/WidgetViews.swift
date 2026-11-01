import AppIntents
import SwiftUI
import WidgetKit

// The widget views, shared with the app, which draws them on its lock screen preview for the
// screenshots.
struct CardEntry: TimelineEntry {
    let date: Date
    let title: String?
    let subtitle: String
    let recall: Bool
    var example: String? = nil
}

struct CardWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: CardEntry
    // The app cannot set the widget family of the environment, so its preview says which it draws.
    var shownAs: WidgetFamily? = nil

    var body: some View {
        switch shownAs ?? family {
        case .accessoryInline:
            Text(entry.title.map { "\($0) · \(entry.subtitle)" } ?? entry.subtitle)
        case .systemMedium:
            VStack(alignment: .leading, spacing: 6) {
                card
                Spacer(minLength: 0)
                HStack {
                    Button(intent: AnswerIntent(know: false)) { Label("Не знаю", systemImage: "xmark") }.tint(.red)
                    Button(intent: AnswerIntent(know: true)) { Label("Знаю", systemImage: "checkmark") }.tint(.green)
                }
                .font(.system(size: 14, weight: .semibold))
            }
        default:
            card
        }
    }

    // With the example when it fits, without it otherwise.
    private var card: some View {
        ViewThatFits(in: .vertical) {
            lines(example: true)
            lines(example: false)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .widgetAccentable()
    }

    private func lines(example: Bool) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            if let title = entry.title {
                Text(title).font(.system(size: entry.recall ? 15 : 20, weight: .semibold)).lineLimit(example ? 1 : 2).minimumScaleFactor(0.6)
            }
            Text(entry.subtitle).font(.system(size: 13)).lineLimit(example ? 1 : 2).opacity(0.8)
            if example, let ex = entry.example {
                Text(ex).font(.system(size: 12).italic()).lineLimit(2).opacity(0.7)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct AnswerButtonView: View {
    let know: Bool
    var body: some View {
        Button(intent: AnswerIntent(know: know)) {
            ZStack {
                AccessoryWidgetBackground()
                VStack(spacing: 0) {
                    Image(systemName: know ? "checkmark" : "xmark").font(.system(size: 18, weight: .bold))
                    Text(know ? "Знаю" : "Не знаю").font(.system(size: 9, weight: .semibold)).lineLimit(1).minimumScaleFactor(0.7)
                }
            }
        }
        .buttonStyle(.plain)
        .widgetAccentable()
    }
}
