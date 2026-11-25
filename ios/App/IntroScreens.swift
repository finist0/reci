import ReciCore
import SwiftUI

// A word to show each language learned by, so that Serbian is not taken for Russian.
private let samples = ["sr": "ку́ћа · kuća", "ru": "доро́га"]

// The first start: which language to learn and the language of the translations, preset by the
// phone's language.
struct PairScreen: View {
    @EnvironmentObject var model: AppModel
    @State private var target = ""
    @State private var native = ""
    // The cards of the pair chosen are loading: another choice now would race them.
    @State private var loading = false

    var body: some View {
        let p = model.palette
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text("НЕРодная речь".uppercased()).font(.ui(12, .bold)).tracking(1.2).foregroundStyle(p.accent).padding(.top, 24)
                    Text(t("Что будем учить?")).font(.serif(30, .semibold)).foregroundStyle(p.word).padding(.top, 6).padding(.bottom, 18)
                    VStack(spacing: 10) {
                        ForEach(Decks.targets, id: \.self) { code in option(code) }
                    }
                    Text(t("Перевод на")).font(.serif(22, .semibold)).foregroundStyle(p.word).padding(.top, 28).padding(.bottom, 12)
                    HStack(spacing: 8) {
                        ForEach(Decks.natives(target), id: \.self) { code in
                            let on = code == native
                            Button { native = code } label: {
                                Text(Decks.name(code)).font(.ui(16, on ? .semibold : .regular)).foregroundStyle(on ? p.accent : p.text)
                                    .padding(.horizontal, 16).padding(.vertical, 10)
                                    .background(Capsule().fill(p.card))
                                    .overlay(Capsule().stroke(on ? p.accent : .clear, lineWidth: 2))
                            }
                            .accessibilityIdentifier("native:\(code)")
                            .accessibilityAddTraits(on ? .isSelected : [])
                        }
                    }
                    Text(t("Выбрано по языку телефона. Всё можно поменять потом в настройках."))
                        .font(.ui(14)).foregroundStyle(p.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16).background(RoundedRectangle(cornerRadius: 18).fill(p.card))
                        .padding(.top, 24)
                }
                .padding(.horizontal, 18)
            }
            PrimaryButton(title: t("Начать")) {
                loading = true
                model.choosePair(Decks.pick(target, native).id)
            }
            .accessibilityIdentifier("pairStart")
            .padding(18)
        }
        .disabled(loading)
        .opacity(loading ? 0.6 : 1)
        .background(p.bg.ignoresSafeArea())
        .accessibilityIdentifier("pair")
        .onAppear {
            target = model.deck.target
            native = model.deck.native
        }
    }

    private func option(_ code: String) -> some View {
        let p = model.palette
        let on = code == target
        return Button {
            target = code
            native = Decks.pick(code, native).native
        } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(Decks.name(code)).font(.ui(19, .semibold)).foregroundStyle(p.word)
                    if let s = samples[code] { Text(s).font(.serif(15)).foregroundStyle(p.secondary) }
                }
                Spacer()
                Circle().stroke(on ? p.accent : p.faint, lineWidth: 2).frame(width: 24, height: 24)
                    .overlay(Circle().fill(on ? p.accent : .clear).padding(6))
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .background(RoundedRectangle(cornerRadius: 20).fill(on ? p.highlight : p.card))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(on ? p.accent : .clear, lineWidth: 2))
        }
        .accessibilityIdentifier("target:\(code)")
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

struct PrimaryButton: View {
    @EnvironmentObject var model: AppModel
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title).font(.ui(17, .semibold)).foregroundStyle(.white)
                .frame(maxWidth: .infinity).frame(height: 54)
                .background(Capsule().fill(model.palette.accent))
        }
    }
}

// The offer to put the card on the lock screen: the two ways iOS allows, each with what it is
// good and bad at and a look at it, then "Set up" or "Later".
struct LockOfferScreen: View {
    @EnvironmentObject var model: AppModel
    let onLater: () -> Void
    let onSetUp: (Int) -> Void
    @State private var mode = 0
    @State private var preview: Int?

    var body: some View {
        let p = model.palette
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(t("Главное в приложении").uppercased()).font(.ui(12, .bold)).tracking(1.2).foregroundStyle(p.accent).padding(.top, 20)
                    Text(t("Слово на экране блокировки")).font(.serif(24, .semibold)).foregroundStyle(p.word).padding(.top, 4)
                    Text(t("Берёте телефон — и видите слово. iPhone умеет это двумя способами:")).font(.ui(14)).foregroundStyle(p.secondary)
                        .padding(.top, 4).padding(.bottom, 12)
                    way(0, t("Виджеты"), tag: t("проще"),
                        pros: [t("Ответ сразу показывает следующее слово"), t("Настройка за минуту")],
                        cons: [t("Мелко: только слово и перевод")],
                        how: t("Как: удерживать экран блокировки → Customize → три виджета под часами."))
                    way(1, t("Обои"), tag: nil,
                        pros: [t("Вся карточка крупно: ударение, примеры")],
                        cons: [t("Новое слово не сразу, а когда сработает автоматизация"), t("Настройка минут пять, через Shortcuts")],
                        how: t("Как: команда Card Wallpaper и две автоматизации, приложение проведёт по шагам."))
                        .padding(.top, 10)
                }
                .padding(.horizontal, 18)
            }
            VStack(spacing: 2) {
                PrimaryButton(title: mode == 0 ? t("Настроить виджеты") : t("Настроить обои")) { onSetUp(mode) }
                    .accessibilityIdentifier("lockSetUp")
                Button(action: onLater) {
                    Text(t("Позже")).font(.ui(17, .semibold)).foregroundStyle(p.word).frame(maxWidth: .infinity).frame(height: 44)
                }
                .accessibilityIdentifier("lockLater")
            }
            .padding(.horizontal, 18).padding(.vertical, 10)
        }
        .background(p.bg.ignoresSafeArea())
        .accessibilityIdentifier("lockOffer")
        .fullScreenCover(item: Binding(get: { preview.map(Look.init) }, set: { preview = $0?.mode })) { look in
            LookScreen(mode: look.mode, onBack: { preview = nil }, onSetUp: { onSetUp(look.mode) })
                .environmentObject(model)
        }
    }

    private func way(_ m: Int, _ title: String, tag: String?, pros: [String], cons: [String], how: String) -> some View {
        let p = model.palette
        let on = m == mode
        return HStack(alignment: .top, spacing: 12) {
            Thumbnail(wallpaper: m == 1)
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 8) {
                    Text(title).font(.ui(18, .bold)).foregroundStyle(p.word)
                    if let tag {
                        Text(tag).font(.ui(11, .bold)).foregroundStyle(p.accent)
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(RoundedRectangle(cornerRadius: 6).fill(p.highlight))
                    }
                }
                ForEach(pros, id: \.self) { mark("+", p.known, $0) }
                ForEach(cons, id: \.self) { mark("−", markDontKnow, $0) }
                Text(how).font(.ui(12)).foregroundStyle(p.secondary).padding(.top, 3)
                Button { preview = m } label: {
                    Text(t("Посмотреть, как выглядит") + " ›").font(.ui(14, .semibold)).foregroundStyle(p.accent)
                }
                .accessibilityIdentifier(m == 0 ? "lookWidgets" : "lookWallpaper")
                .padding(.top, 5)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 20).fill(on ? p.highlight : p.card))
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(on ? p.accent : .clear, lineWidth: 2))
        .contentShape(Rectangle())
        .onTapGesture { mode = m }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(m == 0 ? "wayWidgets" : "wayWallpaper")
    }

    private func mark(_ sign: String, _ color: Color, _ text: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Text(sign).font(.ui(14, .bold)).foregroundStyle(color).frame(width: 12)
            Text(text).font(.ui(13)).foregroundStyle(model.palette.text)
        }
    }
}

private struct Look: Identifiable {
    let mode: Int
    var id: Int { mode }
}

// The lock screen with the card the way chosen, as LockPreview draws it for the screenshots.
private struct Thumbnail: View {
    let wallpaper: Bool
    private let width: CGFloat = 74

    var body: some View {
        let screen = UIScreen.main.bounds.size
        LockPreview(wallpaper: wallpaper)
            .frame(width: screen.width, height: screen.height)
            .scaleEffect(width / screen.width, anchor: .topLeading)
            .frame(width: width, height: width * screen.height / screen.width, alignment: .topLeading)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(hex: 0x2A2A28), lineWidth: 3))
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

private struct LookScreen: View {
    @EnvironmentObject var model: AppModel
    let mode: Int
    let onBack: () -> Void
    let onSetUp: () -> Void

    var body: some View {
        let p = model.palette
        ZStack(alignment: .bottom) {
            // Its widgets are the real ones: a tap would answer the card.
            LockPreview(wallpaper: mode == 1).allowsHitTesting(false).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(t("Так будет выглядеть").uppercased()).font(.ui(12, .bold)).tracking(1.2).foregroundStyle(p.accent)
                Text(mode == 0 ? t("Под часами — слово с переводом и кнопки «Не знаю» и «Знаю». Нажали — сразу следующее слово.")
                     : t("Вся карточка — обои экрана блокировки. Отвечать круглыми виджетами под часами."))
                    .font(.ui(15)).foregroundStyle(p.text)
                HStack(spacing: 10) {
                    Button(action: onBack) {
                        Text(t("Назад")).font(.ui(16, .semibold)).foregroundStyle(p.word)
                            .frame(maxWidth: .infinity).frame(height: 46).background(Capsule().fill(p.panel))
                    }
                    .accessibilityIdentifier("lookBack")
                    Button(action: onSetUp) {
                        Text(t("Настроить так")).font(.ui(16, .semibold)).foregroundStyle(.white)
                            .frame(maxWidth: .infinity).frame(height: 46).background(Capsule().fill(p.accent))
                    }
                    .accessibilityIdentifier("lookSetUp")
                }
                .padding(.top, 8)
            }
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 22).fill(p.card.opacity(0.97)))
            .padding(.horizontal, 14).padding(.bottom, 30)
        }
        .ignoresSafeArea()
    }
}
