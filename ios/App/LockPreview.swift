import ReciCore
import SwiftUI

// A stand-in for the lock screen with the real widgets or the real wallpaper drawn on it, for the
// screenshots: UI tests cannot put widgets on the simulator's own lock screen. Shown only when
// the app is started with "-uitest-lockpreview widgets" or "-uitest-lockpreview wallpaper".
struct LockPreview: View {
    @EnvironmentObject var model: AppModel
    let wallpaper: Bool

    var body: some View {
        let card = LockCard.of(model.repo)
        ZStack(alignment: .top) {
            if wallpaper {
                let w = model.repo.current()
                let screen = UIScreen.main.bounds.size
                WallpaperCard(w: w, prefs: model.prefs, recall: w.map { model.repo.asksRecall($0) } ?? false, palette: model.palette, screen: screen)
                    .frame(width: WallpaperCard.width, height: WallpaperCard.height(screen))
                    .scaleEffect(screen.width / WallpaperCard.width)
                    .frame(width: screen.width, height: screen.height)
            } else {
                LinearGradient(colors: [Color(hex: 0x1F3A4D), Color(hex: 0x0E1626)], startPoint: .top, endPoint: .bottom)
            }
            VStack(spacing: 8) {
                Text(Date.now, format: .dateTime.weekday(.wide).day().month(.wide)).font(.system(size: 20, weight: .semibold))
                Text("9:41").font(.system(size: 92, weight: .bold, design: .rounded))
                HStack(spacing: 10) {
                    if !wallpaper {
                        CardWidgetView(entry: CardEntry(date: .now, title: card.title, subtitle: card.subtitle, recall: card.recall, example: card.example),
                                       shownAs: .accessoryRectangular)
                            .padding(10)
                            .frame(width: 170, height: 72)
                            .background(RoundedRectangle(cornerRadius: 18).fill(.white.opacity(0.18)))
                    }
                    ForEach([false, true], id: \.self) { know in
                        AnswerButtonView(know: know)
                            .frame(width: 72, height: 72)
                            .background(Circle().fill(.white.opacity(0.18)))
                    }
                }
            }
            // On the phone the clock colour is picked in the lock screen editor; dark reads on a light card.
            .foregroundStyle(wallpaper && !model.palette.dark ? model.palette.word : .white)
            .padding(.top, 70)
        }
        .ignoresSafeArea()
        .accessibilityIdentifier("lockPreview")
    }
}
