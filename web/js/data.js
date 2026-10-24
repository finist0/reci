// Labels and text helpers, the same as in the Android app (data/Models.kt, Translit.kt, Accents.kt).

export const levels = [
    [1, "A1 · Начальный"],
    [2, "A2 · Элементарный"],
    [3, "B1 · Средний"],
    [4, "B2 · Выше среднего"],
    [5, "C1 · Продвинутый"],
];

export const topics = [
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

export const sections = [["X:phrase", "Выражения"], ["X:compare", "Похожие слова"], ["X:note", "Заметки"]];

export const lists = [["M:dontknow", "Не знаю"], ["M:known", "Знаю"], ["M:fav", "Избранное"]];

export const orders = ["По частоте", "Вперемешку", "По алфавиту"];

const levelNames = new Map(levels);
const topicNames = new Map(topics);
const sectionNames = new Map(sections);
const listNames = new Map(lists);

export function category(id) {
    if (id.startsWith("L")) return levelNames.get(Number(id.slice(1))) ?? id;
    if (id.startsWith("T:")) return topicNames.get(id.slice(2)) ?? id;
    if (id.startsWith("X:")) return sectionNames.get(id) ?? id;
    return listNames.get(id) ?? id;
}

export function levelShort(level) {
    return (levelNames.get(level) ?? "").split(" ")[0];
}

const posNames = {
    noun: "сущ.", verb: "гл.", adj: "прил.", adv: "нар.", pron: "мест.", prep: "предл.",
    conj: "союз", num: "числ.", part: "част.", interj: "межд.", phrase: "выражение",
    compare: "похожие слова", note: "заметка",
};

export const pos = code => posNames[code] ?? code;

export function plural(n, one, few, many) {
    const m10 = n % 10, m100 = n % 100;
    if (m10 === 1 && m100 !== 11) return one;
    if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
    return many;
}

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

export function esc(s) {
    return String(s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}
