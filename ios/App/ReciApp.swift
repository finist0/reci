import ReciCore
import SwiftUI
import WidgetKit

@main
struct ReciApp: App {
    @StateObject private var model = AppModel()
    @StateObject private var router = Router()
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
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
            .environmentObject(model)
            .environmentObject(router)
            .preferredColorScheme(model.palette.dark ? .dark : .light)
        }
        .onChange(of: phase) { _, now in
            if now == .active { model.reload() }
            // Widgets are seen only outside the app; iOS limits how often an app may refresh them.
            if now == .background { WidgetCenter.shared.reloadAllTimelines() }
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
