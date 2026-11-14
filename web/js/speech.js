// Speech through the browser's voices; the counterpart of data/Speaker.kt.

import { toLatin } from "./data.js";
import { current, locale } from "./decks.js";

const synth = window.speechSynthesis;
let voice = null;
let latinOnly = false;
// The language the voice was picked for: another deck speaks another language.
let voiceFor = null;

// Croatian and Bosnian voices read Serbian Latin text well enough.
const candidates = { ru: [["ru", false]], en: [["en", false]], sr: [["sr", false], ["hr", true], ["bs", true]] };
const fallback = { ru: "ru-RU", en: "en-US", sr: "sr-RS" };

function pick() {
    voice = null;
    voiceFor = current().target;
    if (!synth) return null;
    const voices = synth.getVoices();
    for (const [tag, latin] of candidates[voiceFor] ?? candidates.sr) {
        const v = voices.find(v => v.lang.toLowerCase().replace("_", "-").split("-")[0] === tag);
        if (v) {
            voice = v;
            latinOnly = latin;
            return v;
        }
    }
    return null;
}

const ready = () => (voiceFor === current().target && voice) || pick();

const listeners = new Set();
export const onVoices = f => listeners.add(f);

if (synth) {
    pick();
    synth.addEventListener?.("voiceschanged", () => {
        pick();
        listeners.forEach(f => f());
    });
}

// The language of the voice in use, in the user's language, or null when there is none.
export function voiceName() {
    const v = ready();
    if (!v) return null;
    try {
        return new Intl.DisplayNames([locale()], { type: "language" }).of(v.lang.replace("_", "-"));
    } catch {
        return v.lang;
    }
}

export const canSpeak = () => !!synth;

// Must run inside a tap handler: iOS refuses speech that no gesture started.
export function speak(cyr) {
    if (!synth) return;
    const v = ready();
    const u = new SpeechSynthesisUtterance(latinOnly ? toLatin(cyr) : cyr);
    if (v) {
        u.voice = v;
        u.lang = v.lang;
    } else {
        u.lang = fallback[current().target] ?? fallback.sr;
    }
    synth.cancel();
    synth.speak(u);
}
