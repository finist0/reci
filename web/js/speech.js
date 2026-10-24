// Speech through the browser's voices; the counterpart of data/Speaker.kt.

import { toLatin } from "./data.js";

const synth = window.speechSynthesis;
let voice = null;
let latinOnly = false;

// Croatian and Bosnian voices read Serbian Latin text well enough.
function pick() {
    if (!synth) return null;
    const voices = synth.getVoices();
    for (const [tag, latin] of [["sr", false], ["hr", true], ["bs", true]]) {
        const v = voices.find(v => v.lang.toLowerCase().replace("_", "-").split("-")[0] === tag);
        if (v) {
            voice = v;
            latinOnly = latin;
            return v;
        }
    }
    return null;
}

const listeners = new Set();
export const onVoices = f => listeners.add(f);

if (synth) {
    pick();
    synth.addEventListener?.("voiceschanged", () => {
        pick();
        listeners.forEach(f => f());
    });
}

// The language of the voice in use, in Russian, or null when there is none.
export function voiceName() {
    const v = voice ?? pick();
    if (!v) return null;
    try {
        return new Intl.DisplayNames(["ru"], { type: "language" }).of(v.lang.replace("_", "-"));
    } catch {
        return v.lang;
    }
}

export const canSpeak = () => !!synth;

// Must run inside a tap handler: iOS refuses speech that no gesture started.
export function speak(cyr) {
    if (!synth) return;
    const v = voice ?? pick();
    const u = new SpeechSynthesisUtterance(latinOnly ? toLatin(cyr) : cyr);
    if (v) {
        u.voice = v;
        u.lang = v.lang;
    } else {
        u.lang = "sr-RS";
    }
    synth.cancel();
    synth.speak(u);
}
