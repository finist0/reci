// Settings and progress kept in the browser, the counterpart of Prefs and ProgressDb.

import { sections } from "./data.js";

const PREFS = "reci.prefs";
const PROGRESS = "reci.progress";

// States in the same order as the Android enum, so exported progress means the same there.
export const NEW = 0, DONT_KNOW = 1, UNSURE = 2, KNOWN = 3;

const defaults = {
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
    categories: ["L3", "L4", ...sections.map(s => s[0])],
    todayDate: 0,
    todayCount: 0,
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

export const prefs = { ...defaults, ...read(PREFS) };

export function setPref(name, value) {
    prefs[name] = value;
    write(PREFS, prefs);
    changed();
}

export function setPrefs(values) {
    Object.assign(prefs, values);
    write(PREFS, prefs);
    changed();
}

// key -> {state, fav, seen, last, due, step, phase, streak}; only words that were touched.
const progress = new Map(Object.entries(read(PROGRESS)));

export const emptyProgress = { state: NEW, fav: false, seen: 0, last: 0, due: 0, step: 0, phase: 0, streak: 0 };

export const getProgress = key => progress.get(key);

let saveTimer = 0;

function saveProgress() {
    clearTimeout(saveTimer);
    saveTimer = 0;
    write(PROGRESS, Object.fromEntries(progress));
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
    if (data.prefs) setPrefs({ ...defaults, ...data.prefs, currentKey: null });
    else changed();
    return progress.size;
}
