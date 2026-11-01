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

struct FlagButton: View {
    @EnvironmentObject var model: AppModel
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: "flag").font(.system(size: 18)).foregroundStyle(model.palette.faint).frame(width: 40, height: 40)
        }
        .accessibilityLabel("Сообщить об ошибке")
    }
}

struct SentToast: View {
    @EnvironmentObject var model: AppModel
    let text: String
    var body: some View {
        Label(text, systemImage: "checkmark")
            .font(.system(size: 14, weight: .semibold)).foregroundStyle(.white)
            .padding(.horizontal, 16).padding(.vertical, 9)
            .background(Capsule().fill(model.palette.known))
            // One element read as its text; left alone, VoiceOver and UI tests get the icon's name.
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(text)
            .accessibilityIdentifier("sentToast")
    }
}

private let wordProblems = [("translation", "Перевод"), ("stress", "Ударение"), ("example", "Пример"), ("forms", "Род или формы"), ("other", "Другое")]
private let articleProblems = [("text", "Ошибка в тексте"), ("other", "Другое")]

// The layout of the forms below: a title, the fields, Cancel and Send.
private struct FormFrame<Content: View>: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let title: String
    let sendTitle: String
    let canSend: Bool
    let onSend: () -> Bool
    @ViewBuilder let content: Content

    var body: some View {
        let p = model.palette
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) { content }.padding(20)
            }
            .background(p.card.ignoresSafeArea())
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(sendTitle) { if onSend() { dismiss() } }.disabled(!canSend).accessibilityIdentifier("send")
                }
            }
        }
        .tint(p.accent)
    }
}

private struct Field: View {
    @EnvironmentObject var model: AppModel
    let placeholder: String
    @Binding var text: String
    var lines = 1
    var email = false
    // The server takes no longer text; the web and Android forms have the same limits.
    var limit = 4000

    var body: some View {
        let p = model.palette
        Group {
            if lines > 1 {
                TextField(placeholder, text: $text, axis: .vertical).lineLimit(lines...max(lines, 8))
            } else {
                TextField(placeholder, text: $text)
            }
        }
            .keyboardType(email ? .emailAddress : .default)
            .textInputAutocapitalization(email ? .never : .sentences)
            .autocorrectionDisabled(email)
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 12).fill(p.bg))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(p.divider))
            .accessibilityIdentifier(email ? "email" : "message")
            .onChange(of: text) { _, now in
                if now.count > limit { text = String(now.prefix(limit)) }
            }
    }
}

struct ReportSheet: View {
    @EnvironmentObject var model: AppModel
    let w: Word
    @State private var problem: String?
    @State private var text = ""
    @State private var email = ""
    @State private var error = ""
    @State private var started = false

    var body: some View {
        let p = model.palette
        FormFrame(title: "Что не так с карточкой?", sendTitle: "Отправить", canSend: problem != nil, onSend: send) {
            Text(model.prefs.latinFirst ? Translit.toLatin(w.cyr) : w.cyr).foregroundStyle(p.secondary)
            FlowLayout(spacing: 8) {
                ForEach(w.isArticle ? articleProblems : wordProblems, id: \.0) { k, title in
                    Button { problem = k } label: {
                        Text(title).font(.system(size: 14, weight: .medium)).foregroundStyle(problem == k ? .white : p.text)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(Capsule().fill(problem == k ? p.accent : p.card))
                            .overlay(Capsule().stroke(problem == k ? p.accent : p.divider))
                    }
                    .accessibilityIdentifier("problem:\(k)")
                }
            }
            Field(placeholder: "Что именно не так, если хотите", text: $text, lines: 3, limit: 2000)
            Field(placeholder: "Почта для ответа, если нужен ответ", text: $email, email: true)
            if !error.isEmpty { Text(error).foregroundStyle(markDontKnow).font(.system(size: 14)) }
        }
        .onAppear {
            if !started {
                started = true
                email = model.telemetry.state.email
            }
        }
    }

    private func send() -> Bool {
        let mail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let problem else { return false }
        if !mail.isEmpty && !Telemetry.isEmail(mail) {
            error = "Проверьте адрес почты"
            return false
        }
        let card = CardInfo(key: String(w.key.prefix(120)), word: String(w.cyr.prefix(120)), problem: problem,
                            latin: model.prefs.latinFirst, stress: model.prefs.accentMode)
        model.sendFeedback(kind: "card", text: text.trimmingCharacters(in: .whitespacesAndNewlines), card: card, email: mail)
        return true
    }
}

struct WriteSheet: View {
    @EnvironmentObject var model: AppModel
    @State private var text = ""
    @State private var email = ""
    @State private var error = ""
    @State private var started = false

    var body: some View {
        FormFrame(title: "Написать разработчику", sendTitle: "Отправить", canSend: true, onSend: send) {
            Text("Идея, ошибка, вопрос — что угодно.").foregroundStyle(model.palette.secondary)
            Field(placeholder: "Сообщение", text: $text, lines: 5)
            Field(placeholder: "Почта для ответа, если нужен ответ", text: $email, email: true)
            if !error.isEmpty { Text(error).foregroundStyle(markDontKnow).font(.system(size: 14)) }
        }
        .onAppear {
            if !started {
                started = true
                email = model.telemetry.state.email
            }
        }
    }

    private func send() -> Bool {
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let mail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        if body.isEmpty {
            error = "Напишите сообщение"
            return false
        }
        if !mail.isEmpty && !Telemetry.isEmail(mail) {
            error = "Проверьте адрес почты"
            return false
        }
        model.sendFeedback(kind: "app", text: body, card: nil, email: mail)
        return true
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

let shareText = "НЕРодная речь — сербские слова для тех, кто говорит по-русски: карточки с ударениями, примерами и грамматикой. Бесплатно и без рекламы.\nAndroid: https://play.google.com/store/apps/details?id=org.kostja.reci\niPhone и компьютер: https://finist0.github.io/reci/web/"

// No App Store page yet, so there is nothing to rate on the iPhone.
struct HelpSheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    var body: some View {
        let p = model.palette
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Text("Приложение бесплатное и без рекламы. Вот чем можно помочь:").foregroundStyle(p.secondary)
                VStack(alignment: .leading, spacing: 0) {
                    ShareLink(item: shareText) {
                        row("Поделиться с другом", "Ссылки на Android, iPhone и компьютер")
                    }
                    .simultaneousGesture(TapGesture().onEnded { model.telemetry.count("opened", "share") })
                    .accessibilityIdentifier("share")
                    Line()
                    Button {
                        model.telemetry.count("opened", "star")
                        openURL(URL(string: "https://github.com/finist0/reci")!)
                    } label: {
                        row("Поставить звезду на GitHub", "Там лежит код приложения")
                    }
                    .accessibilityIdentifier("star")
                }
                .background(RoundedRectangle(cornerRadius: 20).fill(p.bg))
                Spacer()
            }
            .padding(20)
            .background(p.card.ignoresSafeArea())
            .navigationTitle("Помочь автору")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Закрыть") { dismiss() } } }
        }
        .tint(p.accent)
    }

    private func row(_ title: String, _ summary: String) -> some View {
        let p = model.palette
        return HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 16)).foregroundStyle(p.text)
                Text(summary).font(.system(size: 13)).foregroundStyle(p.secondary)
            }
            .multilineTextAlignment(.leading)
            Spacer()
            Image(systemName: "chevron.right").font(.system(size: 14)).foregroundStyle(p.faint)
        }
        .padding(.horizontal, 18).padding(.vertical, 13)
        .contentShape(Rectangle())
    }
}
