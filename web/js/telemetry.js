// Anonymous daily statistics and messages to the developer, sent to the project's server.
// Kept apart from the settings, so that a progress file never carries the install id.

import { prefs } from "./store.js";

const KEY = "reci.telemetry";
const SERVER = "https://reci.finist.workers.dev";
const RETRY = 60 * 60 * 1000;

const server = () => globalThis.reciServer ?? SERVER;

// randomUUID is missing before Safari 15.4.
function uuid() {
    if (crypto.randomUUID) return crypto.randomUUID();
    const b = crypto.getRandomValues(new Uint8Array(16));
    b[6] = (b[6] & 0x0f) | 0x40;
    b[8] = (b[8] & 0x3f) | 0x80;
    const h = [...b].map(x => x.toString(16).padStart(2, "0")).join("");
    return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

const timeout = ms => (AbortSignal.timeout ? AbortSignal.timeout(ms) : undefined);

const fresh = () => ({
    id: uuid(), feedbackId: uuid(), enabled: true, noticeShown: false,
    noticeClosed: false, sentDay: "", triedAt: 0, counts: {}, last: null, email: "", queue: [],
});

function read() {
    try {
        return { ...fresh(), ...JSON.parse(localStorage.getItem(KEY)) };
    } catch {
        return fresh();
    }
}

export const state = read();

// Another tab of the app wrote first: its switch, sums and queue are the current ones.
globalThis.addEventListener?.("storage", e => {
    if (e.key === KEY) Object.assign(state, read());
});

function save() {
    try {
        localStorage.setItem(KEY, JSON.stringify(state));
    } catch {
        // Storage full or blocked: statistics for this session are lost, nothing else.
    }
}
save();

// Local date: the day the person lives in, as the server expects.
export function localDay(d = new Date()) {
    const p = n => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

// --- What the app does, summed since the last ping ---

export function count(name, sub) {
    if (!state.enabled) return;
    const c = state.counts;
    if (sub) {
        c[name] ??= {};
        c[name][sub] = (c[name][sub] ?? 0) + 1;
    } else {
        c[name] = (c[name] ?? 0) + 1;
    }
    save();
}

// What was counted while a ping was on its way stays for the next one.
function minus(now, sent) {
    const out = {};
    for (const [k, v] of Object.entries(now)) {
        if (typeof v === "number") {
            if (v - (sent[k] ?? 0) > 0) out[k] = v - (sent[k] ?? 0);
            continue;
        }
        const o = {};
        for (const [sub, n] of Object.entries(v)) if (n - (sent[k]?.[sub] ?? 0) > 0) o[sub] = n - (sent[k]?.[sub] ?? 0);
        if (Object.keys(o).length) out[k] = o;
    }
    return out;
}

// --- Where the app runs ---

export function platform(ua = navigator.userAgent, touch = navigator.maxTouchPoints) {
    if (/iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && touch > 1)) return "web-ios";
    if (/Android/.test(ua)) return "web-android";
    return "web-desktop";
}

// Chrome on Android reports Android 10 in the user agent whatever the version, so the real one
// comes from the client hints where the browser has them.
export async function osName(ua = navigator.userAgent, hints = navigator.userAgentData) {
    if (/Android/.test(ua) && hints?.getHighEntropyValues) {
        try {
            const v = (await hints.getHighEntropyValues(["platformVersion"])).platformVersion;
            if (v) return "Android " + v.split(".")[0];
        } catch {}
    }
    const ios = ua.match(/OS (\d+)_(\d+)/);
    if (/iPhone|iPad|iPod/.test(ua) && ios) return `iOS ${ios[1]}.${ios[2]}`;
    const android = ua.match(/Android (\d+)/);
    if (android) return "Android " + android[1];
    for (const [re, name] of [[/Windows/, "Windows"], [/CrOS/, "ChromeOS"], [/Macintosh/, "macOS"], [/Linux/, "Linux"]]) {
        if (re.test(ua)) return name;
    }
    return "other";
}

// The browser and its version, from the user agent; enough to tell which engine drew a card.
export function browser(ua = navigator.userAgent) {
    for (const [re, name] of [[/EdgA?\/(\d+)/, "Edge"], [/FxiOS\/(\d+)/, "Firefox"], [/Firefox\/(\d+)/, "Firefox"],
                              [/CriOS\/(\d+)/, "Chrome"], [/SamsungBrowser\/(\d+)/, "Samsung Internet"],
                              [/Chrome\/(\d+)/, "Chrome"], [/Version\/(\d+).*Safari/, "Safari"]]) {
        const m = ua.match(re);
        if (m) return `${name} ${m[1]}`;
    }
    return "other";
}

// Where the app runs, sent with every message and crash report so that a card can be seen as
// the user saw it. No words, answers or progress.
export function environment() {
    const media = q => globalThis.matchMedia?.(q).matches ?? false;
    return {
        browser: browser(),
        screen: `${globalThis.innerWidth}x${globalThis.innerHeight} css px`,
        ratio: globalThis.devicePixelRatio ?? 1,
        touch: navigator.maxTouchPoints > 0,
        installed: media("(display-mode: standalone)") || navigator.standalone === true,
        dark: media("(prefers-color-scheme: dark)"),
        locale: String(navigator.language).slice(0, 120),
        mode: prefs.cardMode, theme: prefs.theme, latin: prefs.latinFirst, both: prefs.showBothScripts,
        stress: prefs.accentMode, size: prefs.wordSize, marks: prefs.useMarks, review: prefs.reviewKnown,
        speak: prefs.autoSpeak, active: prefs.activeSize, order: prefs.order,
    };
}

// --- Errors ---

const CRASH = "reci.crash";

// An error the page did not handle is kept, the first one only, and offered to the developer
// at the next start. Errors of browser extensions and other sites are not ours to report.
export function watchErrors() {
    const keep = text => {
        try {
            if (!localStorage.getItem(CRASH)) localStorage.setItem(CRASH, `${new Date().toISOString()}\n${text}`.slice(0, 11000));
        } catch {}
    };
    globalThis.addEventListener("error", e => {
        if (!e.filename?.startsWith(globalThis.location.origin)) return;
        keep(`${e.error?.stack ?? e.message}\n    at ${e.filename}:${e.lineno}:${e.colno}`);
    });
    globalThis.addEventListener("unhandledrejection", e => {
        const stack = e.reason?.stack ?? String(e.reason);
        if (stack.includes(globalThis.location.origin)) keep(`Unhandled rejection: ${stack}`);
    });
}

export const pendingCrash = () => {
    try {
        return localStorage.getItem(CRASH);
    } catch {
        return null;
    }
};

export function clearCrash() {
    try {
        localStorage.removeItem(CRASH);
    } catch {}
}

// --- The daily ping ---

export async function ping(app, learned, day = localDay(), c = state.counts) {
    const p = {
        id: state.id, day, platform: platform(), app, os: await osName(),
        locale: /^[\w-]{1,40}$/.test(navigator.language) ? navigator.language : "other",
        cats: prefs.categories.filter(c => /^[\w:.-]{1,32}$/.test(c)).slice(0, 100),
        mode: prefs.cardMode, theme: prefs.theme, latin: prefs.latinFirst, learned,
    };
    if (c.cards) p.cards = c.cards;
    if (c.answers) p.answers = c.answers;
    if (c.opened) p.opened = c.opened;
    return p;
}

let sending = false;

// Sends today's ping once: when statistics are on, the notice about them has been shown and
// nothing went out today. A failed attempt is retried an hour later at the earliest.
export async function sendPing(app, learned, now = Date.now()) {
    const day = localDay(new Date(now));
    if (sending || !state.enabled || !state.noticeShown || !app || state.sentDay === day) return false;
    if (now - state.triedAt < RETRY) return false;
    sending = true;
    state.triedAt = now;
    save();
    try {
        const sent = structuredClone(state.counts);
        const p = await ping(app, learned, day, sent);
        const res = await fetch(server() + "/ping", {
            method: "POST", body: JSON.stringify(p), keepalive: true, signal: timeout(5000),
        });
        // A rejected ping would be rejected again: drop it with the sums, as if it was sent.
        if (res.ok || res.status === 400 || res.status === 413) {
            Object.assign(state, { sentDay: day, counts: minus(state.counts, sent), last: res.ok ? p : state.last });
            save();
            return res.ok;
        }
    } catch {
        // No network or no answer within 5 seconds: try another time.
    } finally {
        sending = false;
    }
    return false;
}

export function setEnabled(on) {
    state.enabled = on;
    // Sums collected while statistics were off are never sent.
    if (!on) state.counts = {};
    save();
}

export function noticeShown() {
    if (state.noticeShown) return;
    state.noticeShown = true;
    save();
}

export function closeNotice() {
    state.noticeClosed = true;
    save();
}

// --- Messages to the developer ---

let flushing = null;

// Sends the waiting messages in order. One that the server rejects is dropped; one that hit a
// limit or no network waits for the next try. A call during a running flush waits for it.
export function flush() {
    flushing ??= drain().finally(() => (flushing = null));
    return flushing;
}

async function drain() {
    while (state.queue.length) {
        let res;
        try {
            res = await fetch(server() + "/feedback", {
                method: "POST", body: JSON.stringify(state.queue[0]), keepalive: true, signal: timeout(10000),
            });
        } catch {
            return;
        }
        if (res.status === 429 || res.status >= 500) return;
        state.queue.shift();
        save();
    }
}

// The server's check, so that a mistyped address is caught before the message is queued.
export const isEmail = s => /^[^\s@<>"]{1,64}@[^\s@<>"]{1,128}\.[^\s@<>"]{2,32}$/.test(s);

// Queues a message ({kind, text, card}) and tries to send it; true when it went out at once.
// It carries its own id, so that it cannot be tied to the statistics of the same install. A
// crash report carries no email and leaves the remembered one alone.
export async function sendFeedback(message, email, app) {
    if (message.kind !== "crash") state.email = email;
    const m = { id: state.feedbackId, platform: platform(), app: app || "unknown", os: await osName(), ...message,
                env: environment() };
    // The server refuses control characters; pasted text may carry some.
    m.text = (m.text ?? "").replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, "");
    if (!m.text) delete m.text;
    if (email && message.kind !== "crash") m.email = email;
    state.queue.push(m);
    save();
    // A flush already running may have passed the end of the queue before this message came.
    // Compared as text: another tab may have reloaded the queue meanwhile.
    const waiting = () => state.queue.some(q => JSON.stringify(q) === JSON.stringify(m));
    const running = flushing !== null;
    await flush();
    if (running && waiting()) await flush();
    return !waiting();
}
