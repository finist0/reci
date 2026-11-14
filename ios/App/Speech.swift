import AVFoundation
import ReciCore

// Speech through the system voices; the counterpart of data/Speaker.kt and web/js/speech.js.
final class Speech {
    private let synth = AVSpeechSynthesizer()
    private var voice: AVSpeechSynthesisVoice?
    private var latinOnly = false
    private var picked = false
    // The voice speaks the language the deck teaches.
    var deck = Decks.deck(nil) {
        didSet {
            if deck != oldValue {
                picked = false
                voice = nil
                latinOnly = false
            }
        }
    }

    private func pick() {
        if picked { return }
        picked = true
        let voices = AVSpeechSynthesisVoice.speechVoices()
        for (tag, latin) in deck.voices {
            if let v = voices.first(where: { $0.language.lowercased().hasPrefix(tag) }) {
                voice = v
                latinOnly = latin
                return
            }
        }
    }

    // The language of the voice in use, in the language of the interface, or nil when there is none.
    var voiceName: String? {
        pick()
        guard let v = voice else { return nil }
        let lang = deck.native == "sr" && I18n.latin ? "sr_Latn" : deck.native
        return Locale(identifier: lang).localizedString(forIdentifier: v.language) ?? v.language
    }

    func speak(_ cyr: String) {
        pick()
        let u = AVSpeechUtterance(string: latinOnly ? Translit.toLatin(cyr) : cyr)
        u.voice = voice ?? AVSpeechSynthesisVoice(language: deck.target == "ru" ? "ru-RU" : deck.target == "en" ? "en-US" : "sr-RS")
        synth.stopSpeaking(at: .immediate)
        synth.speak(u)
    }
}
