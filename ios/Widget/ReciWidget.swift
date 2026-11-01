import AppIntents
import ReciCore
import SwiftUI
import WidgetKit

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
        return CardEntry(date: Date(), title: card.title, subtitle: card.subtitle, recall: card.recall, example: card.example)
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
