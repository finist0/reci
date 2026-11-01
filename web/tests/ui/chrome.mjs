// A phone-sized headless Chrome driven over the DevTools protocol, and a static server for the
// repository, so the UI tests need nothing but Node and Chrome. CHROME overrides the binary.

import { spawn } from "node:child_process";
import { createServer } from "node:http";
import { mkdtempSync, readFileSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { extname, join, normalize, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(fileURLToPath(import.meta.url), "../../../..");
const TYPES = {
    ".html": "text/html", ".js": "text/javascript", ".mjs": "text/javascript", ".css": "text/css",
    ".json": "application/json", ".webmanifest": "application/manifest+json", ".png": "image/png",
    ".svg": "image/svg+xml", ".ttf": "font/ttf", ".kts": "text/plain",
};

// The statistics server is faked at /srv: it keeps what it gets and answers with `status`.
export function serve() {
    const posts = [];
    const status = { ping: 204, feedback: 204 };
    const server = createServer((req, res) => {
        const srv = req.url.match(/^\/srv\/(ping|feedback)$/);
        if (srv && req.method === "POST") {
            let body = "";
            req.on("data", c => (body += c));
            req.on("end", () => {
                posts.push({ kind: srv[1], body: JSON.parse(body) });
                res.writeHead(status[srv[1]]);
                res.end();
            });
            return;
        }
        let path = normalize(decodeURIComponent(new URL(req.url, "http://x").pathname));
        if (path.endsWith("/")) path += "index.html";
        const file = join(ROOT, path);
        try {
            if (!file.startsWith(ROOT) || !statSync(file).isFile()) throw new Error();
            res.writeHead(200, { "content-type": TYPES[extname(file)] ?? "application/octet-stream" });
            res.end(readFileSync(file));
        } catch {
            res.writeHead(404);
            res.end();
        }
    });
    return new Promise(ok => server.listen(0, "127.0.0.1", () => ok({
        url: `http://127.0.0.1:${server.address().port}/web/`,
        posts,
        status,
        close: () => server.close(),
    })));
}

const sleep = ms => new Promise(r => setTimeout(r, ms));

// `telemetry` is the stored statistics state the page starts with; by default the notice about
// statistics was already shown and closed, so that it does not get in the way of other tests.
export async function launch({ telemetry = { noticeShown: true, noticeClosed: true } } = {}) {
    const profile = mkdtempSync(join(tmpdir(), "reci-ui-"));
    const chrome = spawn(process.env.CHROME ?? "google-chrome", ["--headless=new", "--disable-gpu", "--no-first-run",
        "--no-default-browser-check", "--remote-debugging-port=0", `--user-data-dir=${profile}`, "--lang=ru", "about:blank"],
    { stdio: "ignore", detached: true });
    let port;
    for (let i = 0; i < 100 && !port; i++) {
        await sleep(100);
        try {
            port = readFileSync(join(profile, "DevToolsActivePort"), "utf-8").split("\n")[0];
        } catch {}
    }
    if (!port) throw new Error("Chrome did not start; set CHROME to its binary");
    const tabs = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
    const ws = new WebSocket(tabs.find(t => t.type === "page").webSocketDebuggerUrl);
    await new Promise(r => ws.addEventListener("open", r, { once: true }));

    let id = 0;
    const waiting = new Map();
    const errors = [];
    ws.addEventListener("message", ev => {
        const m = JSON.parse(ev.data);
        if (m.id && waiting.has(m.id)) {
            waiting.get(m.id)(m);
            waiting.delete(m.id);
        } else if (m.method === "Runtime.exceptionThrown") {
            errors.push(m.params.exceptionDetails.exception?.description ?? m.params.exceptionDetails.text);
        } else if (m.method === "Runtime.consoleAPICalled" && m.params.type === "error") {
            errors.push(m.params.args.map(a => a.value ?? a.description).join(" "));
        }
    });
    const send = (method, params = {}) => new Promise((ok, fail) => {
        if (ws.readyState !== WebSocket.OPEN) return fail(new Error(`${method}: the browser is closed`));
        const n = ++id;
        waiting.set(n, m => (m.error ? fail(new Error(`${method}: ${m.error.message}`)) : ok(m.result)));
        ws.send(JSON.stringify({ id: n, method, params }));
    });
    await send("Runtime.enable");
    await send("Page.enable");
    await send("Network.enable");
    await send("Emulation.setDeviceMetricsOverride", { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
    await send("Emulation.setTouchEmulationEnabled", { enabled: true, maxTouchPoints: 5 });
    await send("Page.addScriptToEvaluateOnNewDocument", { source: `
        window.reciServer = location.origin + "/srv";
        try {
            if (!localStorage.getItem("reci.telemetry")) localStorage.setItem("reci.telemetry", ${JSON.stringify(JSON.stringify(telemetry))});
        } catch {}` });

    const page = {
        errors,
        sleep,
        send,
        async eval(expr) {
            const r = await send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true });
            if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description ?? r.exceptionDetails.text);
            return r.result.value;
        },
        async open(url) {
            await send("Page.navigate", { url });
            await page.waitFor("document.querySelector('.screen')");
        },
        async reload() {
            await send("Page.reload", {});
            await sleep(300);
            await page.waitFor("document.querySelector('.screen')");
        },
        async waitFor(expr, ms = 10000) {
            for (let t = 0; t < ms; t += 50) {
                if (await page.eval(`(async () => !!(await (${expr})))()`).catch(() => false)) return;
                await sleep(50);
            }
            throw new Error(`timed out waiting for ${expr}`);
        },
        // Centre of the first element matching the selector whose text includes `text`.
        async point(sel, text = "") {
            const p = await page.eval(`(() => { const el = [...document.querySelectorAll(${JSON.stringify(sel)})]
                .find(e => e.textContent.includes(${JSON.stringify(text)}));
                if (!el) return null; el.scrollIntoView({ block: "nearest" }); const r = el.getBoundingClientRect();
                return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; })()`);
            if (!p) throw new Error(`no ${sel} "${text}" on screen`);
            return p;
        },
        // A real touch, so the page sees the same events as from a finger.
        async tap(sel, text = "", hold = 0) {
            const { x, y } = await page.point(sel, text);
            await send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x, y }] });
            await sleep(hold || 40);
            await send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
            await sleep(200);
        },
        longPress: (sel, text = "") => page.tap(sel, text, 800),
        async type(text) {
            await send("Input.insertText", { text });
            await sleep(150);
        },
        async offline(on) {
            await send("Network.emulateNetworkConditions", { offline: on, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });
        },
        text: () => page.eval("document.getElementById('app').innerText"),
        async close() {
            ws.close();
            // Chrome's helper processes keep writing the profile after the browser exits: stop the
            // whole process group, and never let a leftover temporary profile fail a test.
            const alive = () => {
                try {
                    process.kill(-chrome.pid, 0);
                    return true;
                } catch {
                    return false;
                }
            };
            try {
                process.kill(-chrome.pid, "SIGTERM");
            } catch {}
            for (let i = 0; i < 50 && alive(); i++) await sleep(100);
            if (alive()) process.kill(-chrome.pid, "SIGKILL");
            try {
                rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
            } catch {}
        },
    };
    return page;
}
