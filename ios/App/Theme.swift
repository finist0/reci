import CoreText
import SwiftUI
import UIKit

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

// The palettes of ui/Theme.kt.
struct Palette {
    let name: String
    let bg, card, accent, word, highlight: Color
    let dark: Bool
    let known: Color

    init(_ name: String, _ bg: UInt32, _ card: UInt32, _ accent: UInt32, _ word: UInt32, _ highlight: UInt32, _ dark: Bool) {
        self.name = name
        self.bg = Color(hex: bg)
        self.card = Color(hex: card)
        self.accent = Color(hex: accent)
        self.word = Color(hex: word)
        self.highlight = Color(hex: highlight)
        self.dark = dark
        // The Know mark is always green: the accent where it is green itself, the Lipa green elsewhere.
        let r = (accent >> 16) & 0xFF, g = (accent >> 8) & 0xFF, b = accent & 0xFF
        known = g > r && g > b ? Color(hex: accent) : Color(hex: dark ? 0x5B9440 : 0x4F7F34)
    }

    var text: Color { Color(hex: dark ? 0xF1EFEC : 0x1E1F24) }
    var secondary: Color { Color(hex: dark ? 0xA3A7AF : 0x6E6A64) }
    var faint: Color { Color(hex: dark ? 0x6F737B : 0xA8A29A) }
    var divider: Color { Color(hex: dark ? 0x2B2E35 : 0xEAE3D8) }
    var panel: Color { Color(hex: dark ? 0x262A32 : 0xF3EDE3) }
    var titleRed: Color { Color(hex: dark ? 0xE5574A : 0xB8322A) }
}

let markDontKnow = Color(hex: 0xD64545)
let markUnsure = Color(hex: 0xE0A526)

let palettes = [
    Palette("Кајмак", 0xF5EFE6, 0xFFFCF7, 0xC2502D, 0x2B2D42, 0xF6DDD2, false),
    Palette("Дунав", 0xEAF1F2, 0xFFFFFF, 0x1F7A8C, 0x103B52, 0xD3E9EE, false),
    Palette("Липа", 0xEEF2E6, 0xFFFFFB, 0x4F7F34, 0x263D1A, 0xDCE9CF, false),
    Palette("Шљива", 0xF3EDF2, 0xFFFCFE, 0x8A3D6E, 0x3E1834, 0xEFDCE9, false),
    Palette("Сунцокрет", 0xFAF4E3, 0xFFFDF6, 0xB87A00, 0x4A3400, 0xF6E6B8, false),
    Palette("Ноћ", 0x14161B, 0x1D2027, 0xE59A55, 0xF3E7D9, 0x3A2C20, true),
    Palette("Мастило", 0x0E1626, 0x172238, 0x6CB4D4, 0xE2EEF7, 0x1E3A4D, true),
    Palette("Шума", 0x111612, 0x1A211B, 0x8DC26F, 0xE6F0E0, 0x26382A, true),
]

extension Font {
    // Literata is a variable font: the weight is a position on its "wght" axis.
    static func serif(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        let wght: Double = weight == .bold ? 700 : weight == .semibold ? 600 : weight == .medium ? 500 : 400
        guard let base = UIFont(name: "Literata-Regular", size: size) else { return .system(size: size, weight: weight, design: .serif) }
        let axis = 0x7767_6874 // "wght"
        let desc = base.fontDescriptor.addingAttributes([UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [axis: wght]])
        return Font(UIFont(descriptor: desc, size: size))
    }

    static func display(_ size: CGFloat) -> Font { .custom("RuslanDisplay", size: size) }
}
