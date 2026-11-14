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

    // Answers reload the timeline, so the entry only has to follow day changes. A reload also
    // sends the daily ping when it is due, for someone who only looks at the card.
    func getTimeline(in context: Context, completion: @escaping (Timeline<CardEntry>) -> Void) {
        let repo = LockDeck.repo()
        let timeline = Timeline(entries: [entry(repo)], policy: .after(Date().addingTimeInterval(3600)))
        Task { @MainActor in
            await Stats.fromLockScreen(repo)
            completion(timeline)
        }
    }

    private func entry(_ repo: Repo? = LockDeck.repo()) -> CardEntry {
        let card = LockCard.of(repo)
        return CardEntry(date: Date(), title: card.title, subtitle: card.subtitle, recall: card.recall, example: card.example)
    }
}

struct CardWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "card", provider: CardProvider()) { entry in
            CardWidgetView(entry: entry).containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName(t("Карточка"))
        .description(t("Слово, которое вы сейчас учите."))
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
        .configurationDisplayName(t("Знаю"))
        .description(t("Отмечает слово на карточке и показывает следующее."))
        .supportedFamilies([.accessoryCircular])
    }
}

struct DontKnowWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "dontknow", provider: ButtonProvider()) { _ in
            AnswerButtonView(know: false).containerBackground(.clear, for: .widget)
        }
        .configurationDisplayName(t("Не знаю"))
        .description(t("Вернёт слово через пять карточек и покажет следующее."))
        .supportedFamilies([.accessoryCircular])
    }
}

@main
struct ReciWidgetBundle: WidgetBundle {
    // The widget names and the card speak the language of the deck in use.
    init() {
        LockDeck.loadTables()
        _ = Store(dir: LockDeck.dir)
    }

    var body: some Widget {
        CardWidget()
        KnowWidget()
        DontKnowWidget()
    }
}
