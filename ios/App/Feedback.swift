import ReciCore
import SwiftUI

struct StatsNotice: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        let p = model.palette
        HStack {
            Text(t("Приложение раз в день отправляет анонимную статистику: сколько карточек показано, какие ") +
                 t("разделы и настройки выбраны. Отключить: Настройки → «Анонимная статистика»."))
                .font(.ui(14)).foregroundStyle(p.text)
                .accessibilityIdentifier("statsNotice")
            Button("✕") { model.closeNotice() }.foregroundStyle(p.secondary).frame(width: 40, height: 40)
                .accessibilityLabel(t("Понятно"))
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 14).fill(p.highlight))
        .padding(.horizontal, 14)
        .onAppear { model.noticeShown() }
    }
}

// Opens "Куда пойти учиться?"; sits next to the flag at the bottom of a card.
struct SchoolsButton: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var router: Router
    var body: some View {
        Button { router.go(.schools) } label: {
            Image(systemName: "studentdesk").font(.ui(18)).foregroundStyle(model.palette.faint).frame(width: 40, height: 40)
        }
        .accessibilityLabel(t("Куда пойти учиться"))
    }
}

// A place for Serbian language schools to tell about themselves; empty for now.
struct SchoolsScreen: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        VStack(spacing: 0) {
            Header(title: t("Куда пойти учиться?"))
            Text(t("Здесь могла быть реклама вашей школы сербского языка"))
                .font(.ui(17)).foregroundStyle(model.palette.secondary).multilineTextAlignment(.center)
                .padding(32).frame(maxWidth: .infinity, maxHeight: .infinity)
                .accessibilityIdentifier("schoolsEmpty")
        }
        .background(model.palette.bg.ignoresSafeArea())
    }
}

struct FlagButton: View {
    @EnvironmentObject var model: AppModel
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: "flag").font(.ui(18)).foregroundStyle(model.palette.faint).frame(width: 40, height: 40)
        }
        .accessibilityLabel(t("Сообщить об ошибке"))
    }
}

struct SentToast: View {
    @EnvironmentObject var model: AppModel
    let text: String
    var body: some View {
        Label(text, systemImage: "checkmark")
            .font(.ui(14, .semibold)).foregroundStyle(.white)
            .padding(.horizontal, 16).padding(.vertical, 9)
            .background(Capsule().fill(model.palette.known))
            // One element read as its text; left alone, VoiceOver and UI tests get the icon's name.
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(text)
            .accessibilityIdentifier("sentToast")
    }
}

private var wordProblems: [(String, String)] {
    [("translation", t("Перевод")), ("stress", t("Ударение")), ("example", t("Пример")), ("forms", t("Род или формы")), ("other", t("Другое"))]
}
private var articleProblems: [(String, String)] { [("text", t("Ошибка в тексте")), ("other", t("Другое"))] }

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
                ToolbarItem(placement: .cancellationAction) { Button(t("Отмена")) { dismiss() } }
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
                TextField(placeholder, text: $text, axis: .vertical).lineLimit(lines...lines + 6)
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
        FormFrame(title: t("Что не так?"), sendTitle: t("Отправить"), canSend: problem != nil, onSend: send) {
            Text(w.pos == "note" ? model.prefs.native(w.cyr) : model.prefs.primary(w.cyr)).foregroundStyle(p.secondary)
            FlowLayout(spacing: 8) {
                ForEach(w.isArticle ? articleProblems : wordProblems, id: \.0) { k, title in
                    Button { problem = k } label: {
                        Text(title).font(.ui(14, .medium)).foregroundStyle(problem == k ? .white : p.text)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(Capsule().fill(problem == k ? p.accent : p.card))
                            .overlay(Capsule().stroke(problem == k ? p.accent : p.divider))
                    }
                    .accessibilityIdentifier("problem:\(k)")
                }
            }
            Field(placeholder: t("Что именно не так"), text: $text, lines: 4, limit: 2000)
            Field(placeholder: t("Почта для ответа, если нужен ответ"), text: $email, email: true)
            if !error.isEmpty { Text(error).foregroundStyle(markDontKnow).font(.ui(14)) }
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
        guard let problem else { return false }
        if body.isEmpty {
            error = t("Напишите, что не так")
            return false
        }
        if !mail.isEmpty && !Telemetry.isEmail(mail) {
            error = t("Проверьте адрес почты")
            return false
        }
        let card = CardInfo(key: String(w.key.prefix(120)), word: String(w.cyr.prefix(120)), problem: problem,
                            latin: model.prefs.latinFirst, stress: model.prefs.accentMode,
                            deck: model.prefs.deckId)
        model.sendFeedback(kind: "card", text: body, card: card, email: mail)
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
        FormFrame(title: t("Сообщение"), sendTitle: t("Отправить"), canSend: true, onSend: send) {
            Text(t("Идея, ошибка, вопрос — что угодно.")).foregroundStyle(model.palette.secondary)
            Field(placeholder: t("Сообщение"), text: $text, lines: 6)
            Field(placeholder: t("Почта для ответа, если нужен ответ"), text: $email, email: true)
            if !error.isEmpty { Text(error).foregroundStyle(markDontKnow).font(.ui(14)) }
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
            error = t("Напишите сообщение")
            return false
        }
        if !mail.isEmpty && !Telemetry.isEmail(mail) {
            error = t("Проверьте адрес почты")
            return false
        }
        model.sendFeedback(kind: "app", text: body, card: nil, email: mail)
        return true
    }
}

struct WhatSentSheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var asking = false
    @State private var result: String?

    var body: some View {
        let p = model.palette
        let last = model.telemetry.state.last
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text(t("Раз в день уходит: случайный номер этой установки, платформа, версии приложения и системы, ") +
                         t("язык, сколько карточек показано и ответов дано, сколько слов выучено, выбранные разделы ") +
                         t("и настройки, какой способ показа карточки на экране блокировки выбран, какие виджеты приложения стоят, давно ли рисовались обои, сколько ответов дано на экране блокировки, какие экраны открывали.\n\n") +
                         t("Не уходит: слова и ответы по отдельным словам, имя, почта, точное местоположение. Адрес IP сервер не сохраняет."))
                        .foregroundStyle(p.secondary)
                    Text(last.map { t("Последний пакет, %s", $0.day) } ?? t("Пока ничего не отправлялось")).foregroundStyle(p.text)
                    if let last {
                        ScrollView(.horizontal) {
                            Text(packetText(last)).font(.ui(12, design: .monospaced)).fixedSize()
                                .padding(10)
                                .accessibilityIdentifier("packet")
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 12).fill(p.panel))
                    }
                    // Quoted when asking about a message or the deletion of the data.
                    VStack(alignment: .leading, spacing: 2) {
                        Text(t("Номер этой установки, для статистики")).font(.ui(13)).foregroundStyle(p.secondary)
                        Text(model.telemetry.state.id).font(.ui(12, design: .monospaced)).foregroundStyle(p.text)
                            .textSelection(.enabled).accessibilityIdentifier("installId")
                        Text(t("Номер для сообщений разработчику")).font(.ui(13)).foregroundStyle(p.secondary).padding(.top, 6)
                        Text(model.telemetry.state.feedbackId).font(.ui(12, design: .monospaced)).foregroundStyle(p.text)
                            .textSelection(.enabled).accessibilityIdentifier("feedbackId")
                    }
                    Link(t("Политика конфиденциальности"), destination: URL(string: Telemetry.privacy)!)
                    Button(t("Удалить мои данные с сервера")) { asking = true }
                        .font(.ui(13)).foregroundStyle(p.secondary)
                    if let result {
                        Text(result).font(.ui(13)).foregroundStyle(p.text).accessibilityIdentifier("forgetResult")
                    }
                }
                .padding(20)
            }
            .alert(t("Удалить данные с сервера?"), isPresented: $asking) {
                Button(t("Отмена"), role: .cancel) {}
                Button(t("Удалить"), role: .destructive) {
                    Task {
                        result = await model.telemetry.forget()
                            ? t("Данные удалены с сервера") : t("Не удалось связаться с сервером. Попробуйте позже")
                    }
                }
            } message: {
                Text(t("С сервера разработчика удалятся статистика и сообщения, отправленные с этого телефона. Прогресс и ") +
                     t("настройки на телефоне останутся. Приложение получит новые номера и дальше будет отправлять ") +
                     t("статистику уже с ними, если она включена."))
            }
            .background(p.card.ignoresSafeArea())
            .navigationTitle(t("Анонимная статистика"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(t("Закрыть")) { dismiss() } } }
        }
        .tint(p.accent)
    }

    private func packetText(_ ping: Ping) -> String {
        let enc = JSONEncoder()
        enc.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        return (try? enc.encode(ping)).map { String(decoding: $0, as: UTF8.self) } ?? ""
    }
}

func shareText(_ deck: Deck) -> String {
    deck.shareText + "\nAndroid: https://play.google.com/store/apps/details?id=org.kostja.reci\n" + t("iPhone и компьютер:") + " https://finist0.github.io/reci/web/"
}

// No App Store page yet, so there is nothing to rate on the iPhone.
struct HelpSheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    var body: some View {
        let p = model.palette
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Text(t("Приложение бесплатное. Вот чем можно помочь:")).foregroundStyle(p.secondary)
                VStack(alignment: .leading, spacing: 0) {
                    ShareLink(item: shareText(model.deck)) {
                        row(t("Поделиться с другом"), t("Ссылки на Android, iPhone и компьютер"))
                    }
                    .simultaneousGesture(TapGesture().onEnded { model.telemetry.count("opened", "share") })
                    .accessibilityIdentifier("share")
                    Line()
                    Button {
                        model.telemetry.count("opened", "star")
                        openURL(URL(string: "https://github.com/finist0/reci")!)
                    } label: {
                        row(t("Поставить звезду на GitHub"), t("Там лежит код приложения"))
                    }
                    .accessibilityIdentifier("star")
                }
                .background(RoundedRectangle(cornerRadius: 20).fill(p.bg))
                Spacer()
            }
            .padding(20)
            .background(p.card.ignoresSafeArea())
            .navigationTitle(t("Помочь автору"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(t("Закрыть")) { dismiss() } } }
        }
        .tint(p.accent)
    }

    private func row(_ title: String, _ summary: String) -> some View {
        let p = model.palette
        return HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.ui(16)).foregroundStyle(p.text)
                Text(summary).font(.ui(13)).foregroundStyle(p.secondary)
            }
            .multilineTextAlignment(.leading)
            Spacer()
            Image(systemName: "chevron.right").font(.ui(14)).foregroundStyle(p.faint)
        }
        .padding(.horizontal, 18).padding(.vertical, 13)
        .contentShape(Rectangle())
    }
}
