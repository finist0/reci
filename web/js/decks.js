// Language pairs and the interface language, the counterpart of data/Deck.kt. A deck has the
// language learned (target) and the user's own (native), which the interface speaks unless the
// user picked another one (prefs.uiLang). Decks are listed in assets/decks.json, so a new pair
// needs data only.

import { prefs, deck, useDeck, hasProgress, FIRST } from "./store.js";
import { toLatin } from "./data.js";

export let all = [{ id: FIRST, target: "sr", native: "ru", words: "words.json", grammar: "grammar.json" }];

// Russian text -> language -> translation. Russian is the key, so code reads as before.
let table = {};

// A first start in a browser set to Serbian or a close language opens Russian for Serbs.
export function guess(langs = navigator.languages ?? [navigator.language]) {
    const lang = String(langs[0] ?? "").toLowerCase().split(/[-_]/)[0];
    return ["sr", "hr", "bs", "sh", "cnr", "mk", "sl"].includes(lang) && all.some(d => d.id === "ru-sr") ? "ru-sr" : FIRST;
}

// Earlier users learned the first deck before there was a choice.
export function init(decks, ui) {
    if (decks?.length) all = decks;
    table = ui ?? {};
    if (!deck()) useDeck(prefs.currentKey || hasProgress() ? FIRST : guess());
}

export const current = () => all.find(d => d.id === deck()) ?? all[0];

// Tests set it to see a language without switching decks.
export const i18n = { override: null };

export const lang = () => i18n.override ?? (prefs.uiLang || current().native);

// For dates and language names: Serbian in the script the user reads.
export const locale = () => (lang() === "sr" && prefs.latinFirst ? "sr-Latn" : lang());

export function tIn(l, ru) {
    if (l === "ru") return ru;
    const s = table[ru]?.[l] ?? ru;
    return l === "sr" && prefs.latinFirst ? toLatin(s) : s;
}

// %s and %d take the arguments in turn.
const format = (s, args) => {
    let i = 0;
    return args.length ? s.replace(/%[sd]/g, () => String(args[i++])) : s;
};

// t("Учить %d %s", n, what) in the user's language.
export const t = (ru, ...args) => format(tIn(lang(), ru), args);

// Russian and Serbian have three plural forms, English two: the forms are given in Russian.
export function plural(n, one, few, many) {
    if (lang() === "en") return t(n === 1 ? one : many);
    const m10 = n % 10, m100 = n % 100;
    if (m10 === 1 && m100 !== 11) return t(one);
    if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return t(few);
    return t(many);
}

// Text in the language learned: Serbian in the script the user reads first.
export const primary = s => (prefs.latinFirst && current().target === "sr" ? toLatin(s) : s);

// Text in the user's own language: a translation, a note, a grammar label.
export const native = s => (prefs.latinFirst && current().native === "sr" ? toLatin(s) : s);

// The script setting matters wherever Serbian is shown.
export const hasSerbian = () => current().target === "sr" || current().native === "sr" || lang() === "sr";

const langName = (code, l) => tIn(l, code === "sr" ? "сербский" : code === "ru" ? "русский" : code === "en" ? "английский" : code);

// A language as an option of the settings, named in the interface language.
export const name = code => {
    const s = langName(code, lang());
    return s[0].toUpperCase() + s.slice(1);
};

// The settings choose the language learned and the language of the translations, not a deck.
export const targets = () => [...new Set(all.map(d => d.target))];
export const natives = target => all.filter(d => d.target === target).map(d => d.native);

// The deck of the two languages, else the first one of the language learned: a change of the
// language learned keeps the translations where a deck has them.
export const pick = (target, native) =>
    all.find(d => d.target === target && d.native === native) ?? all.find(d => d.target === target);

const target = () => current().target;

export const howToSay = () =>
    (target() === "ru" ? t("Как сказать по-русски") : target() === "en" ? t("Как сказать по-английски") : t("Как сказать по-сербски"));

// A word to show the font size and the stress marks on.
export const sample = () => (target() === "sr" ? "реч" : target() === "ru" ? "сло́во" : "word");

// How the stress setting looks: Serbian marks four accents, Russian only the place.
export const accentSamples = () => (target() === "sr" ? ["ку̏ћа", "ку́ћа", "кућа"] : ["доро́га", "дорога"]);

// Search: a word in the user's language, or in the language learned.
export const meaningHint = () =>
    (current().native === "sr" ? t("слово по-сербски") : current().native === "en" ? t("слово по-английски") : t("слово по-русски"));
export const wordHint = () => (target() === "sr" ? "реч, reč" : target() === "ru" ? "слово" : "word");
export const wordChip = () =>
    (target() === "ru" ? t("Русское слово") : target() === "en" ? t("Английское слово") : t("Сербское слово"));

export const noVoice = () => (target() === "ru" ? t("Нет русского голоса") : target() === "en" ? t("Нет английского голоса")
    : t("Нет сербского, хорватского или боснийского голоса"));

// What the voice test says.
export const greeting = () => (target() === "ru" ? "Добрый день" : target() === "en" ? "Good afternoon" : "Добар дан");

export const shareText = () => (target() === "ru"
    ? t("НЕРодная речь — русские слова для тех, кто говорит по-сербски: карточки с ударениями и примерами. Бесплатно.")
    : t("НЕРодная речь — сербские слова для тех, кто говорит по-русски: карточки с ударениями, примерами и грамматикой. Бесплатно."));
