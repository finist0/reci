import AVFoundation
import ReciCore

// Speech through the system voices; the counterpart of data/Speaker.kt and web/js/speech.js.
final class Speech {
    private let synth = AVSpeechSynthesizer()
    private var voice: AVSpeechSynthesisVoice?
    private var latinOnly = false
    private var picked = false

    // Croatian and Bosnian voices read Serbian Latin text well enough.
    private func pick() {
        if picked { return }
        picked = true
        let voices = AVSpeechSynthesisVoice.speechVoices()
        for (tag, latin) in [("sr", false), ("hr", true), ("bs", true)] {
            if let v = voices.first(where: { $0.language.lowercased().hasPrefix(tag) }) {
                voice = v
                latinOnly = latin
                return
            }
        }
    }

    // The language of the voice in use, in Russian, or nil when there is none.
    var voiceName: String? {
        pick()
        guard let v = voice else { return nil }
        return Locale(identifier: "ru").localizedString(forIdentifier: v.language) ?? v.language
    }

    func speak(_ cyr: String) {
        pick()
        let u = AVSpeechUtterance(string: latinOnly ? Translit.toLatin(cyr) : cyr)
        u.voice = voice ?? AVSpeechSynthesisVoice(language: "sr-RS")
        synth.stopSpeaking(at: .immediate)
        synth.speak(u)
    }
}
