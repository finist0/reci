// Offline copy of the app and its data. Every request is answered from the cache at once and
// refreshed in the background, so an update shows up on the next start.

const CACHE = "reci";
const ASSETS = "../app/app/src/main/";
const CORE = [
    "./", "index.html", "style.css", "manifest.webmanifest", "icon-180.png", "icon-192.png",
    "js/app.js", "js/data.js", "js/repo.js", "js/speech.js", "js/store.js", "js/ui.js",
    ASSETS + "assets/words.json", ASSETS + "assets/grammar.json",
    ASSETS + "res/font/literata.ttf", ASSETS + "res/font/ruslan_display.ttf",
    "../app/app/build.gradle.kts",
];

self.addEventListener("install", e => {
    e.waitUntil(caches.open(CACHE).then(c => c.addAll(CORE)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", e => e.waitUntil(self.clients.claim()));

self.addEventListener("fetch", e => {
    const req = e.request;
    if (req.method !== "GET" || new URL(req.url).origin !== location.origin) return;
    e.respondWith(caches.open(CACHE).then(async cache => {
        const cached = await cache.match(req, { ignoreSearch: true });
        const fresh = fetch(req).then(res => {
            if (res.ok) cache.put(req, res.clone());
            return res;
        });
        if (cached) {
            e.waitUntil(fresh.catch(() => {}));
            return cached;
        }
        return fresh;
    }));
});
