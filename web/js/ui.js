// The screens, ported from the Compose UI (ui/*.kt). Each screen renders to HTML; taps are
// dispatched through data-act attributes.

import * as D from "./data.js";
import * as R from "./repo.js";
import { prefs, setPref, setPrefs, onChange, exportData, importData, NEW, DONT_KNOW, UNSURE, KNOWN } from "./store.js";
import { speak, voiceName, onVoices, canSpeak } from "./speech.js";

const esc = D.esc;

// name, bg, card, accent, word, highlight, dark: the palettes of ui/Theme.kt.
const palettes = [
    ["Кајмак", "#F5EFE6", "#FFFCF7", "#C2502D", "#2B2D42", "#F6DDD2", false],
    ["Дунав", "#EAF1F2", "#FFFFFF", "#1F7A8C", "#103B52", "#D3E9EE", false],
    ["Липа", "#EEF2E6", "#FFFFFB", "#4F7F34", "#263D1A", "#DCE9CF", false],
    ["Шљива", "#F3EDF2", "#FFFCFE", "#8A3D6E", "#3E1834", "#EFDCE9", false],
    ["Сунцокрет", "#FAF4E3", "#FFFDF6", "#B87A00", "#4A3400", "#F6E6B8", false],
    ["Ноћ", "#14161B", "#1D2027", "#E59A55", "#F3E7D9", "#3A2C20", true],
    ["Мастило", "#0E1626", "#172238", "#6CB4D4", "#E2EEF7", "#1E3A4D", true],
    ["Шума", "#111612", "#1A211B", "#8DC26F", "#E6F0E0", "#26382A", true],
];

// The Know mark is always green: the accent where it is green itself, the Lipa green elsewhere.
function known(accent, dark) {
    const [r, g, b] = [1, 3, 5].map(i => parseInt(accent.slice(i, i + 2), 16));
    if (g > r && g > b) return accent;
    return dark ? "#5B9440" : "#4F7F34";
}

function applyTheme() {
    const [, bg, card, accent, wordColor, hl, dark] = palettes[prefs.theme] ?? palettes[2];
    const vars = {
        bg, card, accent, word: wordColor, hl, known: known(accent, dark),
        text: dark ? "#F1EFEC" : "#1E1F24",
        secondary: dark ? "#A3A7AF" : "#6E6A64",
        faint: dark ? "#6F737B" : "#A8A29A",
        divider: dark ? "#2B2E35" : "#EAE3D8",
        panel: dark ? "#262A32" : "#F3EDE3",
        title: dark ? "#E5574A" : "#B8322A",
        "word-size": prefs.wordSize + "px",
    };
    const s = document.documentElement.style;
    for (const [k, v] of Object.entries(vars)) s.setProperty("--" + k, v);
    s.colorScheme = dark ? "dark" : "light";
    document.querySelector('meta[name="theme-color"]')?.setAttribute("content", bg);
}

const ICONS = {
    arrow_forward: "M12,4l-1.41,1.41L16.17,11H4v2h12.17l-5.58,5.59L12,20l8,-8z",
    back: "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z",
    book: "M21,5c-1.11,-0.35 -2.33,-0.5 -3.5,-0.5c-1.95,0 -4.05,0.4 -5.5,1.5c-1.45,-1.1 -3.55,-1.5 -5.5,-1.5S2.45,4.9 1,6v14.65c0,0.25 0.25,0.5 0.5,0.5c0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20c1.95,0 4.05,0.4 5.5,1.5c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.65,0 3.35,0.3 4.75,1.05c0.1,0.05 0.15,0.05 0.25,0.05c0.25,0 0.5,-0.25 0.5,-0.5V6C22.4,5.55 21.75,5.25 21,5zM21,18.5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5c-1.7,0 -4.15,0.65 -5.5,1.5V8c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.2,0 2.4,0.15 3.5,0.5V18.5z",
    check: "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z",
    chevron_left: "M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z",
    chevron_right: "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z",
    expand_more: "M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z",
    search: "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z",
    settings: "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z",
    star: "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z",
    star_border: "M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24zM12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z",
    volume: "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z",
};

const icon = (name, cls = "") => `<svg class="ic ${cls}" viewBox="0 0 24 24" aria-hidden="true"><path d="${ICONS[name]}"/></svg>`;

// An element that runs action `act` with `arg` when tapped.
const act = (a, arg = "") => `data-act="${a}" data-arg="${esc(arg)}"`;

// --- Text helpers (Prefs.primary, headword, secondary) ---

const primary = s => (prefs.latinFirst ? D.toLatin(s) : s);

function headword(w) {
    // A note's title is Russian; transliterating it would garble it.
    if (w.pos === "note") return w.cyr;
    const shown = !w.acc || prefs.accentMode === 2 ? w.cyr : prefs.accentMode === 1 ? D.placeOnly(w.acc) : w.acc;
    return primary(shown);
}

const secondary = cyr => (prefs.showBothScripts ? (prefs.latinFirst ? cyr : D.toLatin(cyr)) : null);

// "[x]" marks the headword inside an example sentence.
const highlighted = text => esc(text).replace(/\[([^\]]*)\]/g, '<b class="hl">$1</b>');
const plain = text => text.replace(/[[\]]/g, "");

// {сербский текст} is shown in the chosen script and in the word color, the part after
// "|" inside braces is an ending and gets the accent color; **x** is bold.
function grammarText(s) {
    let out = "", bold = false, i = 0;
    while (i < s.length) {
        if (s.startsWith("**", i)) {
            out += bold ? "</b>" : "<b>";
            bold = !bold;
            i += 2;
        } else if (s[i] === "{") {
            let end = s.indexOf("}", i);
            if (end < 0) end = s.length;
            const [stem, ending = ""] = s.slice(i + 1, end).split("|");
            out += `<span class="sr">${esc(primary(stem))}</span>`;
            if (ending) out += `<span class="ending">${esc(primary(ending))}</span>`;
            i = end + 1;
        } else {
            const cands = [s.indexOf("**", i), s.indexOf("{", i)].filter(x => x >= 0);
            const next = cands.length ? Math.min(...cands) : s.length;
            out += esc(s.slice(i, next));
            i = next;
        }
    }
    return bold ? out + "</b>" : out;
}

const markColor = state => (state === KNOWN ? "var(--known)" : state === UNSURE ? "var(--unsure)" : "var(--red)");
const dot = state => (state === NEW ? "" : `<span class="dot" style="background:${markColor(state)}"></span>`);

// --- Navigation ---

const stack = [{ name: "card" }];
const top = () => stack[stack.length - 1];

function saveScroll(entry) {
    entry.scroll = {};
    for (const el of document.querySelectorAll("[data-scroll]")) entry.scroll[el.dataset.scroll] = el.scrollTop;
}

function go(name, arg) {
    saveScroll(top());
    stack.push({ name, arg });
    history.pushState({ depth: stack.length }, "");
    render();
}

function back() {
    if (stack.length > 1) history.back();
}

window.addEventListener("popstate", e => {
    const depth = Math.max(1, e.state?.depth ?? 1);
    if (depth >= stack.length) return;
    stack.length = depth;
    render();
});

// Grammar topics, comparison cards ("cmp:") and word cards ("w:") share one link format.
function openRef(id) {
    if (!id) go("grammar");
    else if (id.startsWith("w:")) go("detail", id.slice(2));
    else if (id.startsWith("cmp:")) go("detail", id);
    else go("topic", id);
}

// A Serbian word mentioned on a card: its own card if the dictionary has one, else its sound.
function openWord(sr, from) {
    const target = R.wordByCyr(sr);
    if (target && target.key !== from) go("detail", target.key);
    else speak(sr);
}

// --- Shared pieces ---

const header = title => `
    <header class="head">
        <button class="round" ${act("back")} aria-label="Назад">${icon("back")}</button>
        <h1>${esc(title)}</h1>
    </header>`;

const groupTitle = t => `<div class="group">${esc(t)}</div>`;
const panel = inner => `<div class="panel">${inner}</div>`;
const line = '<div class="line"></div>';

const chip = (text, selected, a, arg, opts = {}) => `
    <button class="chip ${selected ? "on" : ""}" ${act(a, arg)} ${opts.long ? `data-long="${opts.long}"` : ""}>
        ${opts.dot ? `<span class="dot" style="background:${selected ? "#fff" : opts.dot}"></span>` : ""}${esc(text)}
    </button>`;

// Articles are read, not learned, so their buttons only say whether to show them again.
function marksRow(answered, article) {
    return `
    <div class="marks">
        <button class="mark ${answered === DONT_KNOW ? "on" : ""}" style="--c:${article ? "var(--secondary)" : "var(--red)"}" ${act("mark", DONT_KNOW)}>
            <span class="dot"></span>${article ? "Перечитать позже" : "Не знаю"}</button>
        <button class="mark ${answered === KNOWN ? "on" : ""}" style="--c:var(--known)" ${act("mark", KNOWN)} ${article ? "" : 'data-long="learned"'}>
            <span class="dot"></span>${article ? "Не показывать" : "Знаю"}</button>
    </div>`;
}

// --- Word card body (ui/WordBody.kt) ---

function gramLine(w) {
    let out = `<span>${esc(D.pos(w.pos))}</span>`;
    if (!w.gram) return out;
    if (!w.ruGram) return out + `<b class="${w.pos === "noun" ? "noun-gram" : ""}">${esc(w.gram)}</b>`;
    const strong = !!w.ruNoun;
    const rest = strong ? `  ≠ рус. «${esc(w.ruNoun)}» ${esc(w.ruGram)}` : `  · в рус. ${esc(w.ruGram)}`;
    return out + `<span class="gender ${strong ? "strong" : ""}"><b>${esc(w.gram)}</b>${rest}</span>`;
}

const meanings = w => `<div class="meanings">${w.meanings.map((m, i) =>
    `<div class="meaning"><span class="num">${i + 1}</span><span>${esc(m)}</span></div>`).join("")}</div>`;

function grammarLinks(w) {
    const compares = R.comparesFor(w), links = R.linksFor(w);
    if (!compares.length && !links.length) return "";
    return `<div class="links">${compares.map(c =>
        `<button class="link cmp" ${act("ref", c.key)}>≈ ${esc(c.meanings[0] || c.cyr)}</button>`).join("")}${links.map(id =>
        `<button class="link" ${act("ref", id)}>§ ${esc(R.topic(id).title)}</button>`).join("")}</div>`;
}

const sectionLabel = t => `<div class="label">${esc(t)}</div>`;
const divider = '<div class="divider"></div>';
const more = sr => (R.wordByCyr(sr) ? '<span class="more"> ›</span>' : "");

function details(w) {
    let out = "";
    if (w.examples.length) {
        out += divider + sectionLabel("Примеры") + '<div class="examples">' + w.examples.map(e => `
            <button class="example" ${act("say", plain(e.sr))}>
                <span class="sr-text">${highlighted(primary(e.sr))}</span><span class="ru">${esc(e.ru)}</span>
            </button>`).join("") + "</div>";
    }
    if (w.syn.length || w.ant.length) {
        out += divider;
        const chips = (items, cls) => `<div class="chips">${items.map(s =>
            `<button class="wchip ${cls}" ${act("word", s)}>${esc(primary(s))}${more(s)}</button>`).join("")}</div>`;
        if (w.syn.length) out += sectionLabel("Синонимы") + chips(w.syn, "syn");
        if (w.syn.length && w.ant.length) out += '<div class="gap"></div>';
        if (w.ant.length) out += sectionLabel("Антонимы") + chips(w.ant, "ant");
    }
    if (w.related.length) {
        out += divider + sectionLabel("Однокоренные") + '<div class="related">' + w.related.map(r => `
            <button class="rel" ${act("word", r.sr)}><b>${esc(primary(r.sr))}</b>${more(r.sr)}<span class="dash">—</span>${esc(r.ru)}</button>`).join("") + "</div>";
    }
    const notes = [w.note, ...w.tips].filter(Boolean);
    if (notes.length) {
        out += `<div class="useful"><div class="useful-title">Полезно знать</div>${notes.map(t =>
            `<div class="bullet"><span>•</span><span>${esc(t)}</span></div>`).join("")}</div>`;
    }
    return out;
}

function wordBody(w, { slide = false, covered = slide, hideWord = false } = {}) {
    if (R.isArticle(w)) return articleBody(w);
    if (hideWord) return recallBody(w);
    const sec = secondary(w.cyr);
    // Phrases are several words long and would wrap badly at the headword size.
    const size = w.pos === "phrase" ? "calc(var(--word-size) * 0.72)" : "var(--word-size)";
    return `
        ${sec !== null ? `<div class="secondary">${esc(sec)}</div>` : ""}
        <div class="headword" style="font-size:${size}">${esc(headword(w))}</div>
        <div class="gramrow">
            <button class="speak" ${act("say", w.cyr)} aria-label="Произнести">${icon("volume")}</button>
            <div><div class="gram">${gramLine(w)}</div>${w.forms ? `<div class="forms">${esc(primary(w.forms))}</div>` : ""}</div>
        </div>
        ${grammarLinks(w)}
        <div class="meanings-box">${meanings(w)}${covered ? `<button class="cover" ${act("uncover")}>Показать перевод</button>` : ""}</div>
        ${details(w)}`;
}

// The translation side only: meanings and grammar, with a button that shows the word.
function recallBody(w) {
    // The Russian side of the examples as a hint; the Serbian sentences would give the word away.
    const hints = w.examples.length
        ? divider + sectionLabel("Как сказать по-сербски") + `<div class="hints">${w.examples.map(e => `<div>«${esc(e.ru)}»</div>`).join("")}</div>`
        : "";
    return `
        <div class="hidden-word">· · ·</div>
        <div class="gram">${gramLine(w)}</div>
        ${meanings(w)}
        ${hints}
        <button class="reveal" ${act("reveal")}>Показать слово</button>`;
}

// Comparisons ("compare") and short notes ("note") share the card but not the word layout.
function articleBody(w) {
    const compare = w.pos === "compare";
    let out = sectionLabel(compare ? "Похожие слова" : "Заметка") +
        `<div class="article-title">${esc(compare ? (w.meanings[0] || primary(w.cyr)) : w.cyr)}</div>`;
    if (compare) {
        out += '<div class="items">' + w.related.map(r => `
            <button class="item" ${act("word", r.sr)}><b>${esc(primary(r.sr))}${more(r.sr)}</b><span>${esc(r.ru)}</span></button>`).join("") + "</div>";
        if (w.note) out += `<p class="article-text">${esc(w.note)}</p>`;
    } else {
        out += `<p class="article-text">${esc(w.note)}</p>`;
        if (w.related.length) {
            out += sectionLabel("Слова") + '<div class="related">' + w.related.map(r => `
                <button class="rel" ${act("word", r.sr)}><b>${esc(primary(r.sr))}</b><span class="dash">—</span>${esc(r.ru)}</button>`).join("") + "</div>";
        }
    }
    if (w.examples.length) {
        out += divider + sectionLabel("Примеры") + '<div class="examples">' + w.examples.map(e => `
            <button class="example plain" ${act("say", plain(e.sr))}>
                <span class="sr-text">${highlighted(primary(e.sr))}</span><span class="ru">${esc(e.ru)}</span>
            </button>`).join("") + "</div>";
    }
    return out;
}

// --- Card screen (ui/Card.kt) ---

// What the user is asked to do with this card, shown above it.
function stageLabel(w) {
    if (R.isArticle(w)) return null;
    const p = R.shownProgress(w);
    const what = prefs.cardMode === 2 ? "перевод" : "слово";
    if (R.isDue(p)) return `Проверка: вспомните ${what}`;
    if (p.phase === R.RECALL) return `Вспомните ${what} · «Знаю» ${p.streak} из 3`;
    if (p.phase === R.LEARNED) return "Выучено";
    if (p.state === DONT_KNOW || p.state === UNSURE) return "Ещё раз: запомните слово";
    return "Новое слово";
}

// Fixed for one showing of a card: answering moves the word to its next stage, but the card on
// screen keeps the layout it was shown with. A later answer replaces the earlier one.
let show = null;

function cardShow(w) {
    const id = w.key + "#" + prefs.cardCount;
    if (show?.id !== id) {
        const answered = R.answerOf(w);
        const p = R.shownProgress(w);
        show = {
            id, key: w.key, recall: R.asksRecall(w), stage: stageLabel(w), answered, revealed: answered !== null,
            covered: prefs.cardMode === 2 && !R.isArticle(w),
            fresh: !R.isArticle(w) && p.phase <= R.INTRO && p.streak === 0, hintCounted: false, hint: false,
        };
        R.markShown(w);
    }
    return show;
}

const clockFmt = new Intl.DateTimeFormat("ru", { hour: "2-digit", minute: "2-digit" });
const dateFmt = new Intl.DateTimeFormat("ru", { weekday: "short", day: "numeric", month: "short" });

function deckChip() {
    const cats = prefs.categories;
    const first = [...cats].sort()[0];
    const title = (first === undefined ? "—" : first.startsWith("L") ? D.category(first).split(" ")[0] : D.category(first)) +
        (cats.length > 1 ? ` +${cats.length - 1}` : "");
    // Known words out of all words in the chosen sections, the marked ones included.
    let k = 0, t = 0;
    for (const w of R.words) {
        if (!cats.some(c => R.inCategory(w, c))) continue;
        t++;
        if (R.progress(w).state === KNOWN) k++;
    }
    return `<button class="deck" ${act("categories")}><span class="deck-title">${esc(title)}</span>
        <span class="deck-count">${k}/${t}</span>${icon("expand_more")}</button>`;
}

function cardScreen() {
    let w = R.current();
    if (!w && !R.exhausted) {
        R.step(1);
        w = R.current();
    }
    const now = new Date();
    const head = `
        <header class="topbar">
            <div class="clock"><div class="time">${clockFmt.format(now)}</div><div class="date">${esc(dateFmt.format(now))}</div></div>
            <div class="bar">
                ${deckChip()}
                <div class="icons">
                    <button ${act("search")} aria-label="Поиск">${icon("search")}</button>
                    <button ${act("ref")} aria-label="Грамматика">${icon("book")}</button>
                    <button ${act("settings")} aria-label="Настройки">${icon("settings")}</button>
                </div>
            </div>
        </header>`;
    if (!w) {
        return head + `
            <div class="card-wrap"><div class="empty">
                <div class="empty-title">Здесь пока пусто</div>
                <div class="empty-text">В выбранных разделах не осталось слов</div>
                <button class="primary" ${act("categories")}>Выбрать слова</button>
            </div></div>` + bottomNav();
    }
    const s = cardShow(w);
    const fav = R.progress(w).fav;
    return head + `
        <div class="card-wrap">
            <div class="card-scroll" data-scroll="card-${esc(s.id)}">
                ${s.stage ? `<div class="stage">${esc(s.stage)}</div>` : ""}
                ${wordBody(w, { slide: prefs.cardMode === 2 && !R.isArticle(w), covered: s.covered, hideWord: s.recall && !s.revealed })}
            </div>
            <button class="star ${fav ? "on" : ""}" ${act("fav", w.key)} aria-label="Избранное">${icon(fav ? "star" : "star_border")}</button>
            ${learnedToast ? toast("Слово отмечено выученным") : ""}
        </div>
        ${prefs.useMarks && s.hint ? longPressHint : ""}
        ${prefs.useMarks ? marksRow(s.answered, R.isArticle(w)) : ""}
        ${bottomNav()}`;
}

const longPressHint = `
    <div class="tip"><span>Слово уже знакомо? Удерживайте «Знаю» — оно сразу станет выученным.</span>
        <button ${act("hintclose")} aria-label="Закрыть">✕</button></div>`;

// The long-press hint is shown on the 1st, 10th and 30th «Знаю» given to a new word, until it is
// closed or the long press is used once.
function hintAfterNewKnown() {
    if (prefs.hintDone) return false;
    const n = prefs.newKnownCount + 1;
    setPrefs({ newKnownCount: n, hintDone: n >= 30 });
    return n === 1 || n === 10 || n === 30;
}

const toast = text => `<div class="toast">${icon("check")}<span>${esc(text)}</span></div>`;

// A long press on «Знаю» moves on at once, so a toast shows that it worked.
let learnedToast = false;
let toastTimer = 0;

function showLearnedToast() {
    learnedToast = true;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
        learnedToast = false;
        schedule();
    }, 2500);
}

const bottomNav = () => `
    <nav class="bottom">
        <button class="circle" ${act("prev")} aria-label="Предыдущее слово">${icon("chevron_left")}</button>
        <button class="next" ${act("next")}>Дальше ${icon("arrow_forward")}</button>
    </nav>`;

// --- Categories (ui/Screens.kt CategoriesScreen) ---

function categoriesScreen(e) {
    e.sel ??= [...prefs.categories];
    e.order ??= prefs.order;
    const sel = new Set(e.sel);
    const withKnown = sel.has("M:known");
    const total = R.words.filter(w => e.sel.some(c => R.inCategory(w, c)) && (withKnown || R.progress(w).state !== KNOWN)).length;
    const counts = R.counts();
    const count = id => counts.get(id)?.[0] ?? 0;
    const knownN = id => counts.get(id)?.[1] ?? 0;
    const dots = { "M:dontknow": "var(--red)", "M:known": "var(--known)", "M:fav": "var(--unsure)" };

    const levels = D.levels.map(([lvl, name], i) => {
        const id = "L" + lvl, on = sel.has(id), n = count(id), k = knownN(id);
        return (i ? line : "") + `
            <div class="level" ${act("toggle", id)}>
                <span class="level-badge ${on ? "on" : ""}">${esc(name.split(" ")[0])}</span>
                <span class="level-main"><span class="level-name">${esc(name.split("· ")[1])}</span>
                    <span class="bar-track"><span class="bar-fill" style="width:${n ? (100 * k) / n : 0}%"></span></span>
                    <span class="level-count">знаю ${k} из ${n}</span></span>
                <button class="round small" ${act("list", id)} aria-label="Список слов">${icon("chevron_right")}</button>
                <span class="check ${on ? "on" : ""}">${on ? icon("check") : ""}</span>
            </div>`;
    }).join("");
    const flow = items => `<div class="flow">${items.join("")}</div>`;
    return `<div class="screen-col">${header("Что учим")}
        <div class="scroll" data-scroll="cats">
            ${groupTitle("Уровни")}${panel(levels)}
            ${groupTitle("Разделы")}
            <div class="hint">Долгое нажатие на раздел, тему или отметку открывает список слов</div>
            ${flow(D.sections.filter(([id]) => count(id) > 0).map(([id, name]) => chip(`${name} · ${count(id)}`, sel.has(id), "toggle", id, { long: "list" })))}
            ${groupTitle("Мои отметки")}
            ${flow(D.lists.map(([id, name]) => chip(`${name} · ${count(id)}`, sel.has(id), "toggle", id, { long: "list", dot: dots[id] })))}
            ${groupTitle("Темы")}
            ${flow(D.topics.filter(([c]) => count("T:" + c) > 0).map(([c, name]) => chip(`${name} · ${count("T:" + c)}`, sel.has("T:" + c), "toggle", "T:" + c, { long: "list" })))}
            ${groupTitle("Порядок")}
            ${flow(D.orders.map((name, i) => chip(name, e.order === i, "order", i)))}
            <div class="spacer"></div>
        </div>
        <button class="primary wide ${sel.size ? "" : "off"}" ${act("learn")}>Учить ${total} ${D.plural(total, "карточку", "карточки", "карточек")}</button>
    </div>`;
}

// --- Search, detail and word lists ---

function searchMatch(w, q, byMeaning) {
    if (byMeaning) return w.meanings.some(m => m.toLowerCase().includes(q));
    return w.cyr.toLowerCase().startsWith(q) || D.toLatin(w.cyr).toLowerCase().startsWith(q);
}

function searchResults(e) {
    const q = (e.q ?? "").trim().toLowerCase();
    if (!q) return "";
    return R.words.filter(w => searchMatch(w, q, e.byMeaning)).slice(0, 200).map(w => {
        const shown = primary(w.cyr);
        const n = !e.byMeaning && shown.toLowerCase().startsWith(q) ? q.length : 0;
        return `<button class="row" ${act("detail", w.key)}>
            <span class="row-top"><span class="row-word"><span class="match">${esc(shown.slice(0, n))}</span>${esc(shown.slice(n))}</span>
            <span class="row-level">${esc(D.levelShort(w.level))}</span></span>
            <span class="row-sub">${esc(w.meanings.join(" · "))}</span></button>`;
    }).join("");
}

function searchScreen(e) {
    return `<div class="screen-col">
        <div class="searchbar">
            <button class="round" ${act("back")} aria-label="Назад">${icon("back")}</button>
            <label class="field">${icon("search")}<input data-input="search" type="search" autocomplete="off" autocapitalize="off"
                spellcheck="false" placeholder="${e.byMeaning ? "слово по-русски" : "реч, reč"}" value="${esc(e.q ?? "")}" autofocus></label>
        </div>
        <div class="flow tabs">${chip("Сербское слово", !e.byMeaning, "bymeaning", "0")}${chip("Перевод", !!e.byMeaning, "bymeaning", "1")}</div>
        <div class="scroll list" data-scroll="search" data-results>${searchResults(e)}</div>
    </div>`;
}

function detailScreen(e) {
    const w = R.word(e.arg);
    if (!w) return `<div class="screen-col">${header("")}</div>`;
    e.answered ??= R.answerOf(w);
    return `<div class="screen-col">${header("")}
        <div class="card-wrap detail"><div class="card-scroll" data-scroll="detail">${wordBody(w)}</div>
            ${e.learned ? toast("Слово отмечено выученным") : ""}</div>
        ${marksRow(e.answered, R.isArticle(w))}
    </div>`;
}

function listResults(e) {
    const q = (e.q ?? "").trim().toLowerCase();
    const all = R.words.filter(w => R.inCategory(w, e.arg));
    const shown = !q ? all : all.filter(w => w.cyr.toLowerCase().includes(q) || D.toLatin(w.cyr).toLowerCase().includes(q) ||
        w.meanings.some(m => m.toLowerCase().includes(q)));
    return shown.map(w => `
        <button class="row" ${act("detail", w.key)}>
            <span class="row-top"><span class="row-word">${esc(w.pos === "compare" ? (w.meanings[0] || w.cyr) : headword(w))}</span>${dot(R.progress(w).state)}</span>
            ${w.pos !== "note" && w.pos !== "compare" ? `<span class="row-sub">${esc(w.meanings.join(" · "))}</span>` : ""}
        </button>`).join("");
}

function listScreen(e) {
    const n = R.words.filter(w => R.inCategory(w, e.arg)).length;
    return `<div class="screen-col">${header(`${D.category(e.arg)} · ${n}`)}
        <label class="field inset">${icon("search")}<input data-input="list" type="search" autocomplete="off" placeholder="найти в списке" value="${esc(e.q ?? "")}"></label>
        <div class="scroll list" data-scroll="list" data-results>${listResults(e)}</div>
    </div>`;
}

// --- Grammar (ui/GrammarScreens.kt) ---

function grammarResults(e) {
    if (!e.tab) {
        // A group's topics are not contiguous in the file, so group them before listing.
        const groups = new Map();
        for (const t of R.topics) {
            if (!groups.has(t.group)) groups.set(t.group, []);
            groups.get(t.group).push(t);
        }
        return [...groups].map(([g, ts]) => groupTitle(g) +
            ts.map(t => `<button class="tile" ${act("ref", t.id)}>${esc(t.title)}</button>`).join("")).join("");
    }
    const q = (e.q ?? "").trim().toLowerCase();
    return R.words.filter(w => w.pos === (e.tab === 1 ? "compare" : "note")).filter(w => !q || w.cyr.toLowerCase().includes(q) ||
        D.toLatin(w.cyr).toLowerCase().includes(q) || w.meanings.some(m => m.toLowerCase().includes(q)) || w.related.some(r => r.sr.includes(q)))
        .map(w => `<button class="tile" ${act("detail", w.key)}><span>${esc(w.pos === "compare" ? (w.meanings[0] || w.cyr) : w.cyr)}</span>${dot(R.progress(w).state)}</button>`).join("");
}

function grammarScreen(e) {
    e.tab ??= 0;
    const n = pos => R.words.filter(w => w.pos === pos).length;
    const tabs = ["Грамматика", `Похожие · ${n("compare")}`, `Заметки · ${n("note")}`];
    return `<div class="screen-col">${header("Справочник")}
        <div class="flow tabs">${tabs.map((t, i) => chip(t, e.tab === i, "tab", i)).join("")}</div>
        ${e.tab ? `<label class="field inset">${icon("search")}<input data-input="grammar" type="search" autocomplete="off" placeholder="найти по слову или названию" value="${esc(e.q ?? "")}"></label>` : ""}
        <div class="scroll tiles" data-scroll="grammar-${e.tab}" data-results>${grammarResults(e)}</div>
    </div>`;
}

function block(b) {
    switch (b.type) {
        case "p": return `<p class="g-p">${grammarText(b.text)}</p>`;
        case "tip": return `<p class="g-tip">${grammarText(b.text)}</p>`;
        case "list": return `<div class="g-list">${b.items.map(it => `<div class="bullet"><span>•</span><span>${grammarText(it)}</span></div>`).join("")}</div>`;
        case "ex": return `<button class="g-ex" ${act("say", plain(b.sr))}><span class="sr-text">${highlighted(primary(b.sr))}</span><span class="ru">${esc(b.ru)}</span></button>`;
        case "table": return `<div class="g-table"><table><thead><tr>${b.head.map(h => `<th>${grammarText(h)}</th>`).join("")}</tr></thead>
            <tbody>${b.rows.map(r => `<tr>${r.map(c => `<td>${grammarText(c)}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`;
        default: return "";
    }
}

function topicScreen(e) {
    const t = R.topic(e.arg);
    if (!t) return `<div class="screen-col">${header("")}</div>`;
    const next = R.topics[R.topics.indexOf(t) + 1];
    return `<div class="screen-col topic">${header(t.title)}
        <div class="scroll" data-scroll="topic">
            ${t.blocks.map(block).join("")}
            ${next ? `<button class="next-topic" ${act("topic", next.id)}>Дальше: ${esc(next.title)} ›</button>` : ""}
        </div>
    </div>`;
}

// --- Settings (ui/SettingsScreen.kt) ---

const toggle = (title, summary, on, a) => `
    <button class="setting" ${act(a)}>
        <span class="setting-main"><span class="setting-title">${esc(title)}</span>${summary ? `<span class="setting-sub">${esc(summary)}</span>` : ""}</span>
        <span class="switch ${on ? "on" : ""}"></span>
    </button>`;

const choice = (title, options, selected, a) => `
    <div class="choice"><div class="setting-title">${esc(title)}</div>
        <div class="segments">${options.map((o, i) => `<button class="${i === selected ? "on" : ""}" ${act(a, i)}>${esc(o)}</button>`).join("")}</div>
    </div>`;

const navRow = (title, summary, a, arg = "") => `
    <button class="setting" ${act(a, arg)}>
        <span class="setting-main"><span class="setting-title">${esc(title)}</span>${summary ? `<span class="setting-sub">${esc(summary)}</span>` : ""}</span>
        ${icon("arrow_forward", "faint")}
    </button>`;

const standalone = () => matchMedia("(display-mode: standalone)").matches || navigator.standalone === true;

let appVersion = "";

function settingsScreen(e) {
    const counts = [0, 0, 0, 0];
    for (const w of R.words) counts[R.progress(w).state]++;
    const all = R.words.length, knownN = counts[KNOWN];
    const cats = prefs.categories;
    const modeText = ["Слово учится по шагам: сначала показано целиком, после «Знаю» надо вспомнить его по переводу, после трёх «Знаю» подряд оно выучено. «Не знаю» вернёт слово через пять карточек. Долгое нажатие на «Знаю» — слово уже знакомо.",
        "Видно только перевод: вспомните слово и откройте его кнопкой или ответом.",
        "Видно только слово: перевод открывается по нажатию."][prefs.cardMode];
    const sizes = [10, 15, 20, 30];
    const voice = voiceName();
    const themes = palettes.map(([name, bg, , accent], i) => `
        <button class="theme ${prefs.theme === i ? "on" : ""}" ${act("theme", i)}>
            <span class="swatch" style="background:${bg}"><span style="background:${accent}"></span></span><span>${esc(name)}</span>
        </button>`).join("");

    const install = standalone() ? "" : groupTitle("Как приложение") + panel(`<div class="note">
        <b>iPhone:</b> откройте эту страницу в Safari, нажмите «Поделиться» и «На экран „Домой“».<br>
        <b>Android:</b> в Chrome меню ⋮ → «Добавить на главный экран» или «Установить приложение».<br>
        <b>Компьютер:</b> в Chrome или Edge значок установки в адресной строке.<br>
        Прогресс хранится в этом браузере на этом устройстве.</div>`);

    return `<div class="screen-col">${header("Настройки")}
        <div class="scroll" data-scroll="settings">
            <div class="progress-card">
                <div class="pc-top"><span class="pc-known">${knownN}</span><span class="pc-of">из ${all} слов знаю</span></div>
                <div class="bar-track big"><span class="bar-fill" style="width:${all ? (100 * knownN) / all : 0}%"></span></div>
                <div class="pc-counts"><span><span class="dot" style="background:var(--red)"></span>${counts[DONT_KNOW] + counts[UNSURE]} не знаю</span>
                    <span><span class="dot" style="background:var(--accent)"></span>${R.todayCount()} сегодня</span></div>
            </div>
            ${install}
            ${groupTitle("Учёба")}
            ${panel(navRow("Что учим", (cats.length === 1 ? D.category(cats[0]) : `Разделов: ${cats.length}`) + " · " + D.orders[prefs.order], "categories") + line +
                choice("Карточки", ["Обучение", "Перевод", "Слово"], prefs.cardMode, "cardmode") + `<div class="note">${esc(modeText)}</div>` + line +
                choice("Слов в работе одновременно", sizes.map(String), Math.max(0, sizes.indexOf(prefs.activeSize)), "activesize") + line +
                toggle("Кнопки «Не знаю / Знаю»", null, prefs.useMarks, "usemarks") + line +
                toggle("Проверять выученные слова", "Через неделю, потом через 3 недели, 2 месяца, полгода и год", prefs.reviewKnown, "reviewknown"))}
            ${groupTitle("Оформление")}
            ${panel(`<div class="choice"><div class="setting-title">Тема</div><div class="themes">${themes}</div></div>` + line +
                choice("Алфавит", ["Кириллица", "Latinica"], prefs.latinFirst ? 1 : 0, "alphabet") + line +
                toggle("Подпись другим алфавитом", null, prefs.showBothScripts, "bothscripts") + line +
                choice("Ударения", ["ку̏ћа", "ку́ћа", "кућа"], prefs.accentMode, "accents") + line +
                `<div class="choice"><div class="size-row"><span class="setting-title" data-size-label>Размер шрифта: ${prefs.wordSize}</span>
                    <span class="size-sample" style="font-size:${prefs.wordSize * 0.6}px">реч</span></div>
                    <input type="range" min="28" max="72" value="${prefs.wordSize}" data-input="wordsize"></div>`)}
            ${groupTitle("Звук")}
            ${panel(toggle("Произносить слово сразу", null, prefs.autoSpeak, "autospeak") + line +
                navRow("Голос", !canSpeak() ? "Браузер не умеет озвучивать" : voice ? voice[0].toUpperCase() + voice.slice(1) + " · нажмите, чтобы послушать"
                    : "Нет сербского, хорватского или боснийского голоса", "testvoice"))}
            ${groupTitle("Данные")}
            ${panel(navRow("Сохранить прогресс в файл", "Чтобы перенести его на другое устройство", "export") + line +
                navRow("Загрузить прогресс из файла", "Заменит прогресс на этом устройстве", "import") + line +
                [["compare", "Снова показывать разборы"], ["note", "Снова показывать заметки"]].map(([pos, title]) => {
                    const hidden = R.hiddenArticles(pos);
                    return navRow(title, hidden ? `Скрыто: ${hidden}` : "Скрытых нет", "articles", pos) + line;
                }).join("") +
                `<button class="setting danger" ${act("reset")}>Сбросить прогресс</button>` +
                (e.msg ? `<div class="note">${esc(e.msg)}</div>` : ""))}
            <input type="file" accept="application/json,.json" data-input="importfile" hidden>
            ${groupTitle("О приложении")}
            ${panel(`<div class="about">
                <div class="app-title"><span>НЕ</span>РОДНАЯ РЕЧЬ</div>
                <div>Версия ${esc(appVersion)} · веб-версия</div>
                <div>Автор: Константин Хоренко</div>
                <div>Лицензия: GNU GPL v3 или новее</div>
                <div class="credits">Частотность слов: FrequencyWords (OpenSubtitles), CC BY-SA 4.0.<br>
                    Ударения: Викисловарь (kaikki.org), CC BY-SA.<br>
                    Часть карточек и подсказок составлена по материалам из открытых источников.<br>
                    Шрифты Literata и Ruslan Display: SIL Open Font License.<br>
                    Иконки: Material Icons, Apache 2.0.</div></div>`)}
            <div class="spacer"></div>
        </div>
        ${e.articles ? `<div class="modal"><div class="dialog">
            <div class="dialog-title">Снова показывать ${e.articles === "compare" ? "разборы похожих слов" : "заметки"}?</div>
            <div class="dialog-text">Скрытые кнопкой «Не показывать» станут новыми и снова будут приходить на карточках.</div>
            <div class="dialog-buttons"><button ${act("cancel")}>Отмена</button><button ${act("showagain")}>Показывать</button></div>
        </div></div>` : ""}
        ${e.confirm ? `<div class="modal"><div class="dialog">
            <div class="dialog-title">Сбросить прогресс?</div>
            <div class="dialog-text">Все слова снова станут новыми, избранное очистится.</div>
            <div class="dialog-buttons"><button ${act("cancel")}>Отмена</button><button ${act("doreset")}>Сбросить</button></div>
        </div></div>` : ""}
    </div>`;
}

const screens = {
    card: cardScreen, categories: categoriesScreen, search: searchScreen, detail: detailScreen, list: listScreen,
    grammar: grammarScreen, topic: topicScreen, settings: settingsScreen,
};

// --- Rendering ---

const root = document.getElementById("app");
let rendered = null;

function render() {
    const e = top();
    // Re-rendering the same screen keeps its scroll; returning to a screen restores it.
    if (rendered === e) saveScroll(e);
    const focused = document.activeElement?.dataset?.input;
    applyTheme();
    root.innerHTML = `<div class="screen ${e.name}">${screens[e.name](e)}</div>`;
    for (const el of root.querySelectorAll("[data-scroll]")) el.scrollTop = e.scroll?.[el.dataset.scroll] ?? 0;
    if (rendered !== e) {
        root.querySelector("[autofocus]")?.focus();
    } else if (focused) {
        const input = root.querySelector(`[data-input="${focused}"]`);
        input?.focus();
        input?.setSelectionRange?.(input.value.length, input.value.length);
    }
    rendered = e;
}

let pending = false;

function schedule() {
    if (pending) return;
    pending = true;
    queueMicrotask(() => {
        pending = false;
        render();
    });
}

onChange(schedule);
onVoices(() => top().name === "settings" && schedule());

// The clock on the card ticks once a minute while the page is visible.
setInterval(() => {
    if (document.visibilityState !== "visible") return;
    const now = new Date();
    const t = root.querySelector(".time"), d = root.querySelector(".date");
    if (t) t.textContent = clockFmt.format(now);
    if (d) d.textContent = dateFmt.format(now);
}, 15000);

// A new day or a due check may be waiting when the app comes back.
document.addEventListener("visibilitychange", () => document.visibilityState === "visible" && schedule());

// --- Actions ---

function exportProgress() {
    const data = JSON.stringify(exportData());
    const name = `reci-progress-${new Date().toISOString().slice(0, 10)}.json`;
    const file = new File([data], name, { type: "application/json" });
    // iOS saves files through the share sheet; elsewhere a download does.
    if (navigator.canShare?.({ files: [file] })) {
        navigator.share({ files: [file] }).catch(() => {});
        return;
    }
    const a = document.createElement("a");
    a.href = URL.createObjectURL(file);
    a.download = name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 10000);
}

const actions = {
    back,
    categories: () => go("categories"),
    search: () => go("search"),
    settings: () => go("settings"),
    ref: arg => openRef(arg),
    topic: arg => {
        stack[stack.length - 1] = { name: "topic", arg };
        history.replaceState({ depth: stack.length }, "");
        render();
    },
    detail: arg => go("detail", arg),
    list: arg => go("list", arg),
    say: arg => speak(arg),
    word: arg => {
        const e = top();
        openWord(arg, e.name === "detail" ? e.arg : R.current()?.key);
    },
    fav: arg => R.toggleFav(R.word(arg)),
    reveal: () => {
        if (show) show.revealed = true;
        schedule();
    },
    uncover: () => {
        if (show) show.covered = false;
        schedule();
    },
    hintclose: () => {
        if (show) show.hint = false;
        setPref("hintDone", true);
    },
    prev: () => R.step(-1),
    next: () => {
        R.step(1);
        const w = R.current();
        // Speech has to start inside the tap.
        if (prefs.autoSpeak && w && !R.isArticle(w) && !R.asksRecall(w)) speak(w.cyr);
    },
    mark: arg => answer(Number(arg), false),
    toggle: arg => {
        const e = top();
        e.sel = e.sel.includes(arg) ? e.sel.filter(x => x !== arg) : [...e.sel, arg];
        schedule();
    },
    order: arg => {
        top().order = Number(arg);
        schedule();
    },
    learn: () => {
        const e = top();
        if (!e.sel.length) return;
        R.setCategories(e.sel, e.order);
        back();
    },
    bymeaning: arg => {
        top().byMeaning = arg === "1";
        schedule();
    },
    tab: arg => {
        Object.assign(top(), { tab: Number(arg), q: "" });
        schedule();
    },
    cardmode: arg => setPref("cardMode", Number(arg)),
    activesize: arg => setPref("activeSize", [10, 15, 20, 30][Number(arg)]),
    usemarks: () => setPref("useMarks", !prefs.useMarks),
    reviewknown: () => setPref("reviewKnown", !prefs.reviewKnown),
    theme: arg => setPref("theme", Number(arg)),
    alphabet: arg => setPref("latinFirst", arg === "1"),
    bothscripts: () => setPref("showBothScripts", !prefs.showBothScripts),
    accents: arg => setPref("accentMode", Number(arg)),
    autospeak: () => setPref("autoSpeak", !prefs.autoSpeak),
    testvoice: () => speak("Добар дан"),
    export: exportProgress,
    import: () => root.querySelector('[data-input="importfile"]')?.click(),
    reset: () => {
        top().confirm = true;
        schedule();
    },
    cancel: () => {
        Object.assign(top(), { confirm: false, articles: null });
        schedule();
    },
    articles: arg => {
        if (!R.hiddenArticles(arg)) return;
        top().articles = arg;
        schedule();
    },
    showagain: () => {
        const e = top();
        R.showArticlesAgain(e.articles);
        e.articles = null;
    },
    doreset: () => {
        Object.assign(top(), { confirm: false, msg: "Прогресс сброшен." });
        R.resetAll();
        R.resetPosition();
    },
};

function answer(a, learnedNow) {
    const e = top();
    if (e.name === "detail") {
        R.answer(R.word(e.arg), a, learnedNow);
        e.answered = a;
        e.learned = learnedNow;
    } else if (show) {
        R.answer(R.word(show.key), a, learnedNow);
        show.answered = a;
        show.revealed = true;
        if (learnedNow) {
            show.hint = false;
            setPref("hintDone", true);
            showLearnedToast();
            R.step(1);
        } else if (a === KNOWN && show.fresh && !show.hintCounted) {
            show.hintCounted = true;
            show.hint = hintAfterNewKnown();
        }
    }
    schedule();
}

const longActions = {
    learned: () => answer(KNOWN, true),
    list: el => go("list", el.dataset.arg),
};

// Long press: a timer started on touch, cancelled when the finger moves or lifts.
let pressTimer = 0;
let longFired = false;

root.addEventListener("pointerdown", ev => {
    // Some browsers send no click after a long press, so the flag must not outlive the gesture.
    longFired = false;
    const el = ev.target.closest("[data-long]");
    if (!el) return;
    const [x, y] = [ev.clientX, ev.clientY];
    pressTimer = setTimeout(() => {
        longFired = true;
        navigator.vibrate?.(20);
        longActions[el.dataset.long]?.(el);
    }, 550);
    const cancel = e2 => {
        if (e2.type === "pointermove" && Math.hypot(e2.clientX - x, e2.clientY - y) < 10) return;
        clearTimeout(pressTimer);
        root.removeEventListener("pointermove", cancel);
    };
    root.addEventListener("pointermove", cancel);
    for (const t of ["pointerup", "pointercancel"]) root.addEventListener(t, cancel, { once: true });
});

root.addEventListener("contextmenu", ev => ev.target.closest("[data-long]") && ev.preventDefault());

root.addEventListener("click", ev => {
    if (longFired) {
        longFired = false;
        ev.preventDefault();
        return;
    }
    const el = ev.target.closest("[data-act]");
    if (!el) return;
    actions[el.dataset.act]?.(el.dataset.arg);
});

const inputs = {
    search: v => (top().q = v),
    list: v => (top().q = v),
    grammar: v => (top().q = v),
};

root.addEventListener("input", ev => {
    const name = ev.target.dataset.input;
    if (name === "wordsize") {
        // Only the label follows the slider; the setting is stored when it is released.
        const v = Number(ev.target.value);
        document.documentElement.style.setProperty("--word-size", v + "px");
        const label = root.querySelector("[data-size-label]");
        if (label) label.textContent = `Размер шрифта: ${v}`;
        const sample = root.querySelector(".size-sample");
        if (sample) sample.style.fontSize = v * 0.6 + "px";
        return;
    }
    if (!inputs[name]) return;
    inputs[name](ev.target.value);
    // Re-render only the results, so the field keeps focus and the keyboard stays up.
    const results = root.querySelector("[data-results]");
    const e = top();
    if (results) results.innerHTML = { search: searchResults, list: listResults, grammar: grammarResults }[name](e);
});

root.addEventListener("change", async ev => {
    const name = ev.target.dataset.input;
    if (name === "wordsize") setPref("wordSize", Number(ev.target.value));
    if (name === "importfile") {
        const file = ev.target.files?.[0];
        if (!file) return;
        try {
            const n = importData(JSON.parse(await file.text()));
            R.resetPosition();
            top().msg = `Загружен прогресс по ${n} ${D.plural(n, "слову", "словам", "словам")}.`;
        } catch {
            top().msg = "Это не файл с прогрессом.";
        }
        schedule();
    }
});

export async function start() {
    history.replaceState({ depth: 1 }, "");
    fetch("../app/app/build.gradle.kts").then(r => r.text()).then(t => {
        appVersion = t.match(/versionName\s*=\s*"([^"]+)"/)?.[1] ?? "";
    }).catch(() => {});
    render();
}
