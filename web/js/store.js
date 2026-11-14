// Settings and progress kept in the browser, the counterpart of Prefs and ProgressDb.

const PREFS = "reci.prefs";
const PROGRESS = "reci.progress";

// The deck learned before there were others (decks.js).
export const FIRST = "sr-ru";

// States in the same order as the Android enum, so exported progress means the same there.
export const NEW = 0, DONT_KNOW = 1, UNSURE = 2, KNOWN = 3;

const defaults = {
    // The interface language: "ru", "sr" or "en"; "" speaks the native language of the deck.
    uiLang: "",
    latinFirst: false,
    showBothScripts: true,
    autoSpeak: false,
    cardMode: 0,
    activeSize: 20,
    cardCount: 0,
    useMarks: true,
    reviewKnown: true,
    accentMode: 1,
    theme: 2,
    wordSize: 42,
    order: 0,
    shuffleSeed: 1,
    currentKey: null,
    // B1, B2 and the sections of data.js, which imports this module through decks.js.
    categories: ["L3", "L4", "X:phrase", "X:compare", "X:note"],
    todayDate: 0,
    todayCount: 0,
    hintDone: false,
    newKnownCount: 0,
    lastShown: "",
    pendingAnswer: null,
};

function read(key) {
    try {
        return JSON.parse(localStorage.getItem(key)) ?? {};
    } catch {
        return {};
    }
}

function write(key, value) {
    try {
        localStorage.setItem(key, JSON.stringify(value));
    } catch {
        // Storage full or blocked: the app keeps working for this session.
    }
}

const listeners = new Set();
export const onChange = f => listeners.add(f);
const changed = () => listeners.forEach(f => f());

const stored = read(PREFS);

// The deck in use; null until decks.js picks one on a first start.
export const deck = () => stored.deck ?? null;

// Storage names of what belongs to one deck: the first deck keeps the old names.
const scoped = (name, id = deck()) => (!id || id === FIRST ? name : `${id}.${name}`);

const PER_DECK = new Set(["cardCount", "shuffleSeed", "currentKey", "categories", "lastShown", "pendingAnswer"]);

// The settings under their plain names, those of one deck read from the deck in use.
export const prefs = {};
for (const name of Object.keys(defaults)) {
    const key = () => (PER_DECK.has(name) ? scoped(name) : name);
    Object.defineProperty(prefs, name, {
        enumerable: true,
        get: () => stored[key()] ?? defaults[name],
        set: v => (stored[key()] = v),
    });
}

export function setPref(name, value) {
    prefs[name] = value;
    write(PREFS, stored);
    changed();
}

export function setPrefs(values) {
    for (const [k, v] of Object.entries(values)) if (k in defaults) prefs[k] = v;
    write(PREFS, stored);
    changed();
}

// key -> {state, fav, seen, last, due, step, phase, streak}; only words that were touched. Each
// deck keeps its own: Russian вода and Serbian вода are different cards.
const progress = new Map(Object.entries(read(scoped(PROGRESS))));

// showAt: the card number from which a word answered «Не знаю» is shown again; 0 when none.
export const emptyProgress = { state: NEW, fav: false, seen: 0, last: 0, due: 0, step: 0, phase: 0, streak: 0, showAt: 0 };

export const getProgress = key => progress.get(key);

// Nothing touched yet: a fresh start rather than an update.
export const hasProgress = () => progress.size > 0;

let saveTimer = 0;

function saveProgress() {
    clearTimeout(saveTimer);
    saveTimer = 0;
    write(scoped(PROGRESS), Object.fromEntries(progress));
}

// Switches the settings of the cards and the progress to another deck.
export function useDeck(id) {
    if (saveTimer) saveProgress();
    stored.deck = id;
    write(PREFS, stored);
    progress.clear();
    for (const [k, v] of Object.entries(read(scoped(PROGRESS)))) progress.set(k, v);
    changed();
}

export function putProgress(key, p) {
    progress.set(key, p);
    // A burst of updates (a new active set) is written once.
    if (!saveTimer) saveTimer = setTimeout(saveProgress, 300);
    changed();
}

export function clearProgress() {
    progress.clear();
    saveProgress();
    changed();
}

// iOS may stop the page at any moment once it is hidden.
document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden" && saveTimer) saveProgress();
});

export function exportData() {
    if (saveTimer) saveProgress();
    return { app: "reci", format: 1, exported: new Date().toISOString(), prefs, progress: Object.fromEntries(progress) };
}

export function importData(data) {
    if (data?.app !== "reci" || typeof data.progress !== "object") throw new Error("not a progress file");
    progress.clear();
    for (const [k, v] of Object.entries(data.progress)) progress.set(k, { ...emptyProgress, ...v });
    saveProgress();
    if (data.prefs) setPrefs({ ...defaults, ...data.prefs, currentKey: null, lastShown: "", pendingAnswer: null });
    else changed();
    return progress.size;
}
