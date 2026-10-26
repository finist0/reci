// The dictionary and the learning rules, ported from data/Repo.kt; keep the two in step.

import { lists } from "./data.js";
import { prefs, setPref, setPrefs, getProgress, putProgress, clearProgress, emptyProgress, NEW, DONT_KNOW, UNSURE, KNOWN } from "./store.js";

export const ASSETS = "../app/app/src/main/assets/";

export let words = [];
let byKey = new Map();
export let topics = [];

export async function load() {
    const [w, g] = await Promise.all([
        fetch(ASSETS + "words.json").then(r => r.json()),
        fetch(ASSETS + "grammar.json").then(r => r.json()).catch(() => []),
    ]);
    words = w.map(x => ({
        acc: "", gram: "", ruGram: "", ruNoun: "", forms: "", meanings: [], level: 1, topic: "basic", rank: 0,
        examples: [], syn: [], ant: [], related: [], note: "", tips: [], ...x,
    }));
    byKey = new Map(words.map(x => [x.key, x]));
    topics = g;
}

export const word = key => byKey.get(key);
export const topic = id => topics.find(t => t.id === id);

let byCyr = null;

// The card for a word mentioned elsewhere (a synonym, an antonym), if the dictionary has one.
export function wordByCyr(cyr) {
    if (!byCyr) {
        byCyr = new Map();
        for (const w of words) if ((w.level > 0 || w.pos === "phrase") && !byCyr.has(w.cyr)) byCyr.set(w.cyr, w);
    }
    return byCyr.get(cyr);
}

let compareIndex = null;

// Comparison cards that discuss this word, for the links under its grammar line.
export function comparesFor(w) {
    if (w.pos === "compare") return [];
    if (!compareIndex) {
        compareIndex = new Map();
        for (const c of words) {
            if (c.pos !== "compare") continue;
            for (const r of c.related) {
                if (!compareIndex.has(r.sr)) compareIndex.set(r.sr, []);
                compareIndex.get(r.sr).push(c);
            }
        }
    }
    return (compareIndex.get(w.cyr) ?? []).slice(0, 2);
}

// Topics worth opening from a word card, most specific first.
export function linksFor(w) {
    let ids = [];
    switch (w.pos) {
        case "noun": {
            const g = w.gram;
            const own = g.startsWith("м") ? "noun-m" : g.startsWith("ср") ? "noun-n"
                : g.startsWith("ж") && w.cyr.endsWith("а") ? "noun-f-a" : g.startsWith("ж") ? "noun-f-cons" : "cases";
            ids = [own, "prepositions"];
            break;
        }
        case "verb": ids = w.key === "бити" ? ["biti", "perfect"] : ["present", "aspect"]; break;
        case "prep": ids = ["prepositions", "cases"]; break;
        case "pron": ids = ["pronouns", "clitics"]; break;
        case "adj": ids = ["adjectives", "comparison"]; break;
        case "num": ids = ["numbers"]; break;
        case "part": case "conj": ids = ["clitics", "questions"]; break;
    }
    return ids.filter(id => topic(id));
}

export const progress = w => getProgress(w.key) ?? emptyProgress;

function update(w, f) {
    putProgress(w.key, f({ ...progress(w) }));
}

const DAY = 24 * 60 * 60 * 1000;

// A word goes: not started -> INTRO (word and translation shown) -> RECALL (translation
// only, the user names the word) -> LEARNED, then comes back for checks.
export const INTRO = 1, RECALL = 2, LEARNED = 3;
const INTRO_STREAK = 1, RECALL_STREAK = 3;

// A word answered «Не знаю» comes back this many cards later, while it is still fresh.
const RETRY_AFTER = 5;

const reviewIntervals = [7 * DAY, 21 * DAY, 60 * DAY, 180 * DAY, 365 * DAY];

const reviewDue = step => Date.now() + reviewIntervals[Math.min(step, reviewIntervals.length - 1)];
const learned = p => ({ ...p, phase: LEARNED, streak: 0, state: KNOWN, step: 0, due: reviewDue(0) });
const passedCheck = p => ({ ...p, step: p.step + 1, due: reviewDue(p.step + 1) });

export const isArticle = w => w.pos === "compare" || w.pos === "note";

export const isDue = p => !!p && p.phase === LEARNED && prefs.reviewKnown && p.due >= 1 && p.due <= Date.now();

// Whether the card should hide the Serbian side so the user recalls it.
export function asksRecall(w) {
    if (isArticle(w)) return false;
    if (prefs.cardMode === 1) return true;
    if (prefs.cardMode === 2) return false;
    const p = shownProgress(w);
    return p.phase === RECALL || isDue(p);
}

// Answers to recently shown cards with the progress they were given on, so an answer can
// be changed, also after going back to the card.
const answers = new Map();

export const answerOf = w => answers.get(w.key)?.state ?? null;

// The progress the card was shown with, before it was answered.
export const shownProgress = w => answers.get(w.key)?.before ?? progress(w);

// KNOWN or DONT_KNOW; learnedNow skips the stages for a word the user already knows. A new
// answer to the same card replaces the previous one.
export function answer(w, a, learnedNow = false) {
    const old = answers.get(w.key);
    if (old && old.state === a && old.learned === learnedNow) return;
    update(w, cur => {
        const before = old?.before ?? cur;
        answers.set(w.key, { before, state: a, learned: learnedNow });
        const p = {
            ...cur, state: before.state, phase: before.phase, streak: before.streak, step: before.step,
            due: before.due, showAt: before.showAt ?? 0,
        };
        if (!learnedNow) return advance(w, p, a);
        return p.phase === LEARNED ? p : learned(p);
    });
}

function advance(w, p, a) {
    const n = prefs.cardCount;
    // «Не показывать» hides an article for good; «Перечитать позже» leaves it in the round.
    if (isArticle(w)) {
        return a === KNOWN ? { ...p, state: KNOWN, phase: LEARNED, streak: 0, step: 0, due: 0 }
            : { ...p, state: NEW, phase: 0, streak: 0, due: 0 };
    }
    // A forgotten learned word skips the introduction and is asked again a full round later.
    if (p.phase === LEARNED) {
        return a === KNOWN ? passedCheck(p)
            : { ...p, phase: RECALL, streak: 0, state: a, step: 0, due: 0, showAt: n + prefs.activeSize };
    }
    if (p.phase === RECALL) {
        if (a === KNOWN) return p.streak + 1 >= RECALL_STREAK ? learned(p) : { ...p, streak: p.streak + 1, state: NEW };
        if (a === UNSURE) return { ...p, streak: 0, state: a };
        return { ...p, phase: INTRO, streak: 0, state: DONT_KNOW, showAt: n + RETRY_AFTER };
    }
    if (a === KNOWN) {
        return p.streak + 1 >= INTRO_STREAK ? { ...p, phase: RECALL, streak: 0, state: NEW }
            : { ...p, phase: INTRO, streak: p.streak + 1, state: NEW };
    }
    return { ...p, phase: INTRO, streak: 0, state: a, showAt: n + RETRY_AFTER };
}

export const toggleFav = w => update(w, p => ({ ...p, fav: !p.fav }));

function today() {
    const d = new Date();
    return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate();
}

export const todayCount = () => (prefs.todayDate === today() ? prefs.todayCount : 0);

export function markShown(w) {
    if (revisit) return;
    update(w, p => ({ ...p, seen: p.seen + 1, last: Date.now(), showAt: 0 }));
    const t = today();
    setPrefs({ todayDate: t, todayCount: prefs.todayDate === t ? prefs.todayCount + 1 : 1 });
}

export function resetAll() {
    answers.clear();
    clearProgress();
}

export function inCategory(w, id) {
    const p = getProgress(w.key);
    if (id.startsWith("L")) return Number(id.slice(1)) === w.level;
    if (id.startsWith("T:")) return id.slice(2) === w.topic;
    if (id.startsWith("X:")) return id.slice(2) === w.pos;
    // UNSURE is left over from the removed «Почти» answer.
    if (id === "M:dontknow") return p?.state === DONT_KNOW || p?.state === UNSURE;
    if (id === "M:known") return p?.state === KNOWN;
    if (id === "M:fav") return p?.fav === true;
    return false;
}

// Per category: all words and words marked known, in one pass.
export function counts() {
    const out = new Map();
    const add = (id, known) => {
        const a = out.get(id) ?? [0, 0];
        a[0]++;
        if (known) a[1]++;
        out.set(id, a);
    };
    for (const w of words) {
        const known = getProgress(w.key)?.state === KNOWN;
        if (w.level > 0) add("L" + w.level, known);
        else add("X:" + w.pos, known);
        add("T:" + w.topic, known);
        for (const [id] of lists) if (inCategory(w, id)) add(id, known);
    }
    return out;
}

// mulberry32: the browser has no seeded random.
function rng(seed) {
    let a = seed >>> 0;
    return () => {
        a = (a + 0x6D2B79F5) >>> 0;
        let t = a;
        t = Math.imul(t ^ (t >>> 15), t | 1);
        t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

let shuffledList = [];
let shuffledSeed = 0;

// The whole dictionary is shuffled once per seed, so the order stays stable as words move on.
function shuffled(seed) {
    if (!shuffledList.length || seed !== shuffledSeed) {
        const r = rng(seed);
        shuffledList = [...words];
        for (let i = shuffledList.length - 1; i > 0; i--) {
            const j = Math.floor(r() * (i + 1));
            [shuffledList[i], shuffledList[j]] = [shuffledList[j], shuffledList[i]];
        }
        shuffledSeed = seed;
    }
    return shuffledList;
}

// Words and phrases are learned; comparisons and notes are only read and checked.
function learnable(w, cats) {
    if (isArticle(w)) return false;
    if (w.pos === "phrase") return cats.includes("X:phrase") || cats.some(c => c.startsWith("M:") && inCategory(w, c));
    return cats.some(c => !c.startsWith("X:") && inCategory(w, c));
}

let queueKey = "";
let queueCache = [];

// Everything learnable in the chosen order; every fifth item is a phrase when both are chosen.
function queue() {
    const cats = prefs.categories, order = prefs.order, seed = prefs.shuffleSeed;
    const key = JSON.stringify([cats, order, seed, words.length]);
    if (key === queueKey) return queueCache;
    const chosen = (order === 1 ? shuffled(seed) : words).filter(w => learnable(w, cats));
    const phrases = shuffled(seed).filter(w => w.pos === "phrase" && learnable(w, cats));
    let plain = chosen.filter(w => w.pos !== "phrase");
    if (order === 2) {
        const coll = new Intl.Collator("sr");
        plain = [...plain].sort((a, b) => coll.compare(a.cyr, b.cyr));
    }
    const out = [];
    let ph = 0;
    plain.forEach((w, i) => {
        out.push(w);
        if (i % 4 === 3 && ph < phrases.length) out.push(phrases[ph++]);
    });
    out.push(...phrases.slice(ph));
    queueKey = key;
    queueCache = out;
    return out;
}

const phaseOf = w => getProgress(w.key)?.phase ?? 0;
const lastOf = w => getProgress(w.key)?.last ?? 0;
const showAtOf = w => getProgress(w.key)?.showAt ?? 0;

// The words being learned now; topped up from the queue when some were learned.
function activeSet() {
    const q = queue();
    const active = q.filter(w => phaseOf(w) === INTRO || phaseOf(w) === RECALL);
    const missing = prefs.activeSize - active.length;
    if (missing <= 0) return active;
    const fresh = [];
    for (const w of q) {
        if (fresh.length >= missing) break;
        if (phaseOf(w) === 0 && getProgress(w.key)?.state !== KNOWN) fresh.push(w);
    }
    for (const w of fresh) update(w, p => ({ ...p, phase: INTRO, streak: 0 }));
    return [...active, ...fresh];
}

function minBy(list, f) {
    let best = null, bestV = Infinity;
    for (const x of list) {
        const v = f(x);
        if (v < bestV) [best, bestV] = [x, v];
    }
    return best;
}

function articleFor(exclude) {
    const cats = prefs.categories;
    return minBy(words.filter(w => isArticle(w) && cats.includes("X:" + w.pos) && w.key !== exclude && phaseOf(w) !== LEARNED), lastOf);
}

// Articles of one kind ("compare" or "note") hidden with «Не показывать».
export const hiddenArticles = pos => words.filter(w => w.pos === pos && phaseOf(w) === LEARNED).length;

export function showArticlesAgain(pos) {
    for (const w of words) {
        if (w.pos === pos && phaseOf(w) === LEARNED) update(w, p => ({ ...p, state: NEW, phase: 0, step: 0, due: 0, last: 0 }));
    }
}

const dueFor = exclude => minBy(queue().filter(w => w.key !== exclude && isDue(getProgress(w.key))), w => getProgress(w.key).due);

// Next card: a word whose return after «Не знаю» is due, every fifth an article, every fourth
// a check of a learned word when one is due, otherwise the active word shown longest ago.
function pickNext(exclude) {
    const n = prefs.cardCount;
    const active = activeSet();
    const back = minBy(active.filter(w => w.key !== exclude && showAtOf(w) >= 1 && showAtOf(w) <= n), showAtOf);
    if (back) return back;
    if (n % 5 === 4) {
        const a = articleFor(exclude);
        if (a) return a;
    }
    if (n % 4 === 3) {
        const d = dueFor(exclude);
        if (d) return d;
    }
    return minBy(active.filter(w => w.key !== exclude && showAtOf(w) <= n), lastOf) ?? active[0] ?? dueFor(exclude) ?? articleFor(exclude);
}

const history = [];

// Cards left by going back, shown again in the same order by going forward.
const forward = [];

// The card on screen was reached by going back or forward, so it was already counted.
let revisit = false;

// Set when nothing is left to show for the chosen sections.
export let exhausted = false;

export function setCategories(ids, order) {
    setPrefs({ categories: ids, order });
    resetPosition();
}

export function resetPosition() {
    history.length = 0;
    forward.length = 0;
    answers.clear();
    exhausted = false;
    setPrefs({ currentKey: null, shuffleSeed: Math.floor(Math.random() * 2 ** 31) });
}

export const current = () => (prefs.currentKey ? word(prefs.currentKey) ?? null : null);

export function step(delta) {
    const cur = prefs.currentKey;
    if (delta < 0) {
        const prev = history.pop();
        if (prev === undefined) return;
        if (cur) forward.push(cur);
        revisit = true;
        setPref("currentKey", prev);
        return;
    }
    if (cur) {
        history.push(cur);
        if (history.length > 100) history.shift();
    }
    const fwd = forward.pop();
    if (fwd !== undefined) {
        revisit = true;
        setPref("currentKey", fwd);
        return;
    }
    revisit = false;
    prefs.cardCount++;
    const next = pickNext(cur);
    exhausted = !next;
    // A fresh showing of a card takes a fresh answer.
    if (next) answers.delete(next.key);
    const keep = new Set([...history, next?.key]);
    for (const k of [...answers.keys()]) if (!keep.has(k)) answers.delete(k);
    setPrefs({ cardCount: prefs.cardCount, currentKey: next?.key ?? null });
}

