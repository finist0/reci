// Labels and text helpers, the same as in the Android app (data/Models.kt, Translit.kt, Accents.kt).
// The labels are Russian; t() turns them into the user's language when they are read.

import { t } from "./decks.js";

const levelsRu = [
    [1, "A1 · Начальный"],
    [2, "A2 · Элементарный"],
    [3, "B1 · Средний"],
    [4, "B2 · Выше среднего"],
    [5, "C1 · Продвинутый"],
];

const topicsRu = [
    ["basic", "Базовые слова"],
    ["people", "Люди"],
    ["family", "Семья"],
    ["body", "Тело"],
    ["health", "Здоровье"],
    ["home", "Дом"],
    ["food", "Еда и напитки"],
    ["clothes", "Одежда"],
    ["city", "Город"],
    ["transport", "Транспорт"],
    ["travel", "Путешествия"],
    ["nature", "Природа"],
    ["weather", "Погода"],
    ["animals", "Животные"],
    ["time", "Время"],
    ["numbers", "Числа и количество"],
    ["colors", "Цвета и формы"],
    ["work", "Работа"],
    ["education", "Учёба"],
    ["money", "Деньги и покупки"],
    ["communication", "Общение"],
    ["emotions", "Чувства"],
    ["character", "Характер"],
    ["leisure", "Отдых и спорт"],
    ["culture", "Культура"],
    ["tech", "Техника"],
    ["society", "Общество"],
    ["actions", "Действия"],
    ["qualities", "Качества"],
    ["abstract", "Абстрактные понятия"],
];

const sectionsRu = [["X:phrase", "Выражения"], ["X:compare", "Похожие слова"], ["X:note", "Заметки"]];

const listsRu = [["M:dontknow", "Не знаю"], ["M:known", "Знаю"], ["M:fav", "Избранное"]];

const ordersRu = ["По частоте", "Вперемешку", "По алфавиту"];

const translated = list => list.map(([id, name]) => [id, t(name)]);

export const levels = () => translated(levelsRu);
export const topics = () => translated(topicsRu);
export const sections = () => translated(sectionsRu);
export const lists = () => translated(listsRu);
export const orders = () => ordersRu.map(o => t(o));

const levelNames = new Map(levelsRu);
const topicNames = new Map(topicsRu);
const sectionNames = new Map(sectionsRu);
const listNames = new Map(listsRu);

export function category(id) {
    const name = id.startsWith("L") ? levelNames.get(Number(id.slice(1)))
        : id.startsWith("T:") ? topicNames.get(id.slice(2)) : id.startsWith("X:") ? sectionNames.get(id) : listNames.get(id);
    return name ? t(name) : id;
}

export function levelShort(level) {
    return (levelNames.get(level) ?? "").split(" ")[0];
}

const posNames = {
    noun: "сущ.", verb: "гл.", adj: "прил.", adv: "нар.", pron: "мест.", prep: "предл.",
    conj: "союз", num: "числ.", part: "част.", interj: "межд.", phrase: "выражение",
    compare: "похожие слова", note: "заметка",
};

export const pos = code => (posNames[code] ? t(posNames[code]) : code);

const latin = {
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "ђ": "đ", "е": "e", "ж": "ž", "з": "z",
    "и": "i", "ј": "j", "к": "k", "л": "l", "љ": "lj", "м": "m", "н": "n", "њ": "nj", "о": "o",
    "п": "p", "р": "r", "с": "s", "т": "t", "ћ": "ć", "у": "u", "ф": "f", "х": "h", "ц": "c",
    "ч": "č", "џ": "dž", "ш": "š",
};

const isUpper = ch => ch !== undefined && ch !== ch.toLowerCase();
const isLetter = ch => ch !== undefined && ch.toLowerCase() !== ch.toUpperCase();

// Stress marks are combining characters and pass through: ку̏ћа -> kȕća.
export function toLatin(text) {
    const s = [...text.normalize("NFD")];
    let out = "";
    s.forEach((ch, i) => {
        const lat = latin[ch.toLowerCase()];
        if (lat === undefined) {
            out += ch;
        } else if (!isUpper(ch)) {
            out += lat;
        } else {
            // Љ, Њ, Џ become "LJ" inside an all-caps word and "Lj" otherwise.
            const next = s[i + 1], prev = s[i - 1];
            const allCaps = isUpper(next) || ((next === undefined || !isLetter(next)) && isUpper(prev));
            out += allCaps ? lat.toUpperCase() : lat[0].toUpperCase() + lat.slice(1);
        }
    });
    return out.normalize("NFC");
}

const STRESS = new Set(["̏", "̑", "̀", "́"]);

// Keeps where the stress falls and drops its type and the length marks.
export function placeOnly(acc) {
    let out = "";
    for (const ch of acc.normalize("NFD")) {
        if (STRESS.has(ch)) out += "́";
        else if (ch !== "̄") out += ch;
    }
    return out.normalize("NFC");
}

// Drops the marks of Cyrillic vowels and р only: the acute of ć or of Montenegrin с́ is part of the letter.
export function strip(text) {
    const s = text.normalize("NFD");
    let out = "", vowel = false;
    for (const ch of s) {
        const mark = STRESS.has(ch) || ch === "̄";
        if (!mark || !vowel) out += ch;
        if (!/\p{Mn}/u.test(ch)) vowel = "аеиоурАЕИОУРяюыэёЯЮЫЭЁ".includes(ch);
    }
    return out.length === s.length ? text : out.normalize("NFC");
}

// Text with stress marks as the accent setting wants it: 0 as is, 1 the place only, 2 none.
export const showAccents = (text, mode) => (mode === 0 ? text : mode === 1 ? placeOnly(text) : strip(text));

export function esc(s) {
    return String(s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}
