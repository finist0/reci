import ReciCore
import SwiftUI

@main
struct ReciApp: App {
    @StateObject private var model = AppModel()
    @StateObject private var router = Router()
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            if let i = CommandLine.arguments.firstIndex(of: "-uitest-lockpreview"), i + 1 < CommandLine.arguments.count {
                LockPreview(wallpaper: CommandLine.arguments[i + 1] == "wallpaper")
                    .environmentObject(model)
                    .onAppear { model.syncShowing() }
            } else {
                main
            }
        }
        .environment(\.locale, Locale(identifier: "ru_RU"))
        .onChange(of: phase) { _, now in
            if now == .active { model.reload() }
        }
    }

    private var main: some View {
        NavigationStack(path: $router.path) {
            CardScreen()
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: Route.self) { route in
                    Group {
                        switch route {
                        case .detail(let key): DetailScreen(key: key)
                        case .topic(let id): TopicScreen(id: id)
                        case .grammar: GrammarScreen()
                        case .categories: CategoriesScreen()
                        case .search: SearchScreen()
                        case .settings: SettingsScreen()
                        case .list(let id): ListScreen(id: id)
                        }
                    }
                    .toolbar(.hidden, for: .navigationBar)
                }
        }
        // On an iPad the phone layout stays a column of reading width in the middle: stretched to
        // the full width, lines grow too long and the buttons drift apart.
        .frame(maxWidth: 680)
        .frame(maxWidth: .infinity)
        .background(model.palette.bg.ignoresSafeArea())
        .environmentObject(model)
        .environmentObject(router)
        .onAppear { router.onGo = { [weak model] r in model?.telemetry.count("opened", r.statName) } }
        .preferredColorScheme(model.palette.dark ? .dark : .light)
        .alert("Приложение закрылось с ошибкой", isPresented: .constant(model.crashOffer != nil)) {
            Button("Не отправлять", role: .cancel) { model.answerCrash(send: false) }
            Button("Отправить") { model.answerCrash(send: true) }
        } message: {
            Text("В прошлый раз приложение неожиданно закрылось. Отправить разработчику отчёт, чтобы он мог это исправить? " +
                 "В отчёте место в коде, где случилась ошибка, модель телефона, версия iOS и настройки приложения. " +
                 "Слова и прогресс не отправляются.")
        }
    }
}

// The header of the inner screens, with a back button.
struct Header: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let title: String

    var body: some View {
        HStack(spacing: 12) {
            Button { dismiss() } label: {
                Image(systemName: "arrow.left").font(.system(size: 18, weight: .semibold)).foregroundStyle(model.palette.text)
                    .frame(width: 44, height: 44).background(Circle().fill(model.palette.card))
            }
            .accessibilityLabel("Назад")
            Text(title).font(.serif(24, .semibold)).foregroundStyle(model.palette.word).lineLimit(1)
            Spacer()
        }
        .padding(.horizontal, 14).padding(.vertical, 8)
    }
}

struct GroupTitle: View {
    @EnvironmentObject var model: AppModel
    let text: String
    var body: some View {
        Text(text.uppercased()).font(.system(size: 12, weight: .semibold)).tracking(1).foregroundStyle(model.palette.accent)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 26).padding(.top, 22).padding(.bottom, 8)
    }
}

struct Panel<Content: View>: View {
    @EnvironmentObject var model: AppModel
    @ViewBuilder let content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .background(RoundedRectangle(cornerRadius: 20).fill(model.palette.card))
            .padding(.horizontal, 14)
    }
}

struct Line: View {
    @EnvironmentObject var model: AppModel
    var body: some View { Divider().overlay(model.palette.divider).padding(.leading, 18) }
}
