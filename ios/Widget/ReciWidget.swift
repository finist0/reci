import AppIntents
import ReciCore
import SwiftUI
import WidgetKit

struct CardEntry: TimelineEntry {
    let date: Date
    let title: String?
    let subtitle: String
    let recall: Bool
}

struct CardProvider: TimelineProvider {
    func placeholder(in context: Context) -> CardEntry {
        CardEntry(date: Date(), title: "кућа", subtitle: "дом", recall: false)
    }

    func getSnapshot(in context: Context, completion: @escaping (CardEntry) -> Void) {
        completion(entry())
    }

    // Answers reload the timeline, so the entry only has to follow day changes.
    func getTimeline(in context: Context, completion: @escaping (Timeline<CardEntry>) -> Void) {
        completion(Timeline(entries: [entry()], policy: .after(Date().addingTimeInterval(3600))))
    }

    private func entry() -> CardEntry {
        let card = LockCard.of(LockDeck.repo())
        return CardEntry(date: Date(), title: card.title, subtitle: card.subtitle, recall: card.recall)
    }
}

struct CardWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: CardEntry

    var body: some View {
        switch family {
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

    private var card: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let title = entry.title {
                Text(title).font(.system(size: entry.recall ? 15 : 20, weight: .semibold)).lineLimit(2).minimumScaleFactor(0.6)
            }
            Text(entry.subtitle).font(.system(size: 13)).lineLimit(2).opacity(0.8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .widgetAccentable()
    }
}

struct CardWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "card", provider: CardProvider()) { entry in
            CardWidgetView(entry: entry).containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Карточка")
        .description("Слово, которое вы сейчас учите.")
        .supportedFamilies([.accessoryRectangular, .accessoryInline, .systemSmall, .systemMedium])
    }
}

// The buttons look the same whatever the card, so they need no dictionary to draw.
struct ButtonProvider: TimelineProvider {
    struct Entry: TimelineEntry { let date: Date }

    func placeholder(in context: Context) -> Entry { Entry(date: Date()) }
    func getSnapshot(in context: Context, completion: @escaping (Entry) -> Void) { completion(Entry(date: Date())) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> Void) {
        completion(Timeline(entries: [Entry(date: Date())], policy: .never))
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

struct KnowWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "know", provider: ButtonProvider()) { _ in
            AnswerButtonView(know: true).containerBackground(.clear, for: .widget)
        }
        .configurationDisplayName("Знаю")
        .description("Отмечает слово на карточке и показывает следующее.")
        .supportedFamilies([.accessoryCircular])
    }
}

struct DontKnowWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "dontknow", provider: ButtonProvider()) { _ in
            AnswerButtonView(know: false).containerBackground(.clear, for: .widget)
        }
        .configurationDisplayName("Не знаю")
        .description("Вернёт слово через пять карточек и покажет следующее.")
        .supportedFamilies([.accessoryCircular])
    }
}

@main
struct ReciWidgetBundle: WidgetBundle {
    var body: some Widget {
        CardWidget()
        KnowWidget()
        DontKnowWidget()
    }
}
