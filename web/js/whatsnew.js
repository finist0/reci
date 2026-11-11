// What changed in each version, from whatsnew.json (shared with Android and the iPhone app),
// shown once on the first start after an update. The counterpart of data/WhatsNew.kt.

// The last version without this window: an update from it has no version seen yet.
export const BEFORE = "1.6";
const SEEN = "reci.whatsNewSeen";

export function parse(all, platform) {
    return all.map(e => ({ version: e.version, items: e.items.filter(i => !i.only || i.only.includes(platform)).map(i => i.text) }))
        .filter(e => e.items.length > 0);
}

export function compare(a, b) {
    const x = a.split(".").map(n => parseInt(n, 10) || 0), y = b.split(".").map(n => parseInt(n, 10) || 0);
    for (let i = 0; i < Math.max(x.length, y.length); i++) {
        const d = (x[i] ?? 0) - (y[i] ?? 0);
        if (d) return Math.sign(d);
    }
    return 0;
}

// The entries after the version seen last, up to the running one, newest first. A fresh start
// has nothing to compare with, so it is told nothing.
export function pick(entries, version, seen, fresh) {
    const from = seen ?? (fresh ? version : BEFORE);
    return entries.filter(e => compare(e.version, from) > 0 && compare(e.version, version) <= 0)
        .sort((a, b) => compare(b.version, a.version));
}

// The news for this start; the running version counts as seen from now on.
export function check(all, version, fresh) {
    let seen = null;
    try {
        seen = localStorage.getItem(SEEN);
        localStorage.setItem(SEEN, version);
    } catch {
        return [];
    }
    return pick(parse(all, "web"), version, seen, fresh);
}
