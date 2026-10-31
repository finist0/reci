import ReciCore
import SwiftUI

struct StatsNotice: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        let p = model.palette
        HStack {
            Text("Приложение раз в день отправляет анонимную статистику: сколько карточек показано, какие разделы и настройки выбраны. Отключить: Настройки → «Анонимная статистика».")
                .font(.system(size: 14)).foregroundStyle(p.text)
                .accessibilityIdentifier("statsNotice")
            Button("✕") { model.closeNotice() }.foregroundStyle(p.secondary).frame(width: 40, height: 40)
                .accessibilityLabel("Понятно")
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 14).fill(p.highlight))
        .padding(.horizontal, 14)
        .onAppear { model.noticeShown() }
    }
}

struct WhatSentSheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let p = model.palette
        let last = model.telemetry.state.last
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Раз в день уходит: случайный номер этой установки, платформа, версии приложения и системы, язык, сколько карточек показано и ответов дано, сколько слов выучено, выбранные разделы и настройки, какой способ показа карточки на экране блокировки выбран, какие экраны открывали.\n\nНе уходит: слова и ответы по отдельным словам, имя, почта, точное местоположение. Адрес IP сервер не сохраняет.")
                        .foregroundStyle(p.secondary)
                    Text(last.map { "Последний пакет, \($0.day)" } ?? "Пока ничего не отправлялось").foregroundStyle(p.text)
                    if let last {
                        Text(packetText(last)).font(.system(size: 12, design: .monospaced))
                            .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                            .background(RoundedRectangle(cornerRadius: 12).fill(p.panel))
                            .accessibilityIdentifier("packet")
                    }
                    Link("Политика конфиденциальности", destination: URL(string: Telemetry.privacy)!)
                }
                .padding(20)
            }
            .background(p.card.ignoresSafeArea())
            .navigationTitle("Анонимная статистика")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Закрыть") { dismiss() } } }
        }
        .tint(p.accent)
    }

    private func packetText(_ ping: Ping) -> String {
        let enc = JSONEncoder()
        enc.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        return (try? enc.encode(ping)).map { String(decoding: $0, as: UTF8.self) } ?? ""
    }
}
