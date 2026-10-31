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
                WallpaperCard(w: w, prefs: model.prefs, recall: w.map { model.repo.asksRecall($0) } ?? false, palette: model.palette)
                    .frame(width: 1290, height: 2796)
                    .scaleEffect(UIScreen.main.bounds.width / 1290)
                    .frame(width: UIScreen.main.bounds.width, height: UIScreen.main.bounds.height)
            } else {
                LinearGradient(colors: [Color(hex: 0x1F3A4D), Color(hex: 0x0E1626)], startPoint: .top, endPoint: .bottom)
            }
            VStack(spacing: 8) {
                Text(Date.now, format: .dateTime.weekday(.wide).day().month(.wide)).font(.system(size: 20, weight: .semibold))
                Text("9:41").font(.system(size: 92, weight: .bold, design: .rounded))
                HStack(spacing: 10) {
                    if !wallpaper {
                        CardWidgetView(entry: CardEntry(date: .now, title: card.title, subtitle: card.subtitle, recall: card.recall))
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
            .foregroundStyle(.white)
            .shadow(color: .black.opacity(wallpaper ? 0.35 : 0), radius: 4)
            .padding(.top, 70)
        }
        .ignoresSafeArea()
        .accessibilityIdentifier("lockPreview")
    }
}
