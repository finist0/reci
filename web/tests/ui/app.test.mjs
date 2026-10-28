// UI tests of the web app in a phone-sized headless Chrome.
// Usage: node --test "web/tests/ui/*.test.mjs"   (CHROME=/path/to/chrome if it is not google-chrome)

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { serve, launch } from "./chrome.mjs";

let server;
before(async () => (server = await serve()));
after(() => server.close());

// A fresh browser profile per test: no progress, no settings, no cache.
async function app(t) {
    const page = await launch();
    t.after(() => page.close());
    await page.open(server.url);
    return page;
}

const currentKey = page => page.eval("import('./js/repo.js').then(R => R.current()?.key)");
const deckCount = page => page.eval("document.querySelector('.deck-count').textContent");
const known = async page => Number((await deckCount(page)).split("/")[0]);

test("opens on the most frequent word of the default levels", async t => {
    const page = await app(t);
    const expected = await page.eval("import('./js/repo.js').then(R => R.words.find(w => w.level === 3 || w.level === 4).key)");
    assert.equal(await currentKey(page), expected);
    assert.equal(await page.eval("document.title"), "НЕРодная речь");
    assert.deepEqual(page.errors, []);
});

test("a long press on Know shows a toast and moves on; back and Don't know undo it", async t => {
    const page = await app(t);
    const first = await currentKey(page);
    await page.longPress(".mark", "Знаю");
    assert.match(await page.eval("document.querySelector('.toast')?.textContent ?? ''"), /Слово отмечено выученным/);
    assert.notEqual(await currentKey(page), first);
    assert.equal(await known(page), 1);
    // The tap right after a long press must not be lost.
    await page.tap('[data-act="prev"]');
    assert.equal(await currentKey(page), first);
    assert.equal(await page.eval("document.querySelector('.mark.on')?.textContent.trim()"), "Знаю");
    await page.tap(".mark", "Не знаю");
    assert.equal(await known(page), 0);
    await page.sleep(2600);
    assert.equal(await page.eval("!!document.querySelector('.toast')"), false);
});

test("the tap after a long press is not lost when the browser sends no click", async t => {
    const page = await app(t);
    const first = await currentKey(page);
    // Chrome on Android ends a long press without a click; headless Chrome sends one.
    await page.eval(`(() => {
        const el = document.querySelector('.mark[data-long]');
        const r = el.getBoundingClientRect();
        const o = { bubbles: true, clientX: r.x + 5, clientY: r.y + 5, pointerId: 7, pointerType: "touch" };
        el.dispatchEvent(new PointerEvent("pointerdown", o));
        return new Promise(ok => setTimeout(() => { el.dispatchEvent(new PointerEvent("pointerup", o)); ok(); }, 700));
    })()`);
    assert.notEqual(await currentKey(page), first);
    await page.tap('[data-act="prev"]');
    assert.equal(await currentKey(page), first);
});

test("an answer can be changed with plain taps", async t => {
    const page = await app(t);
    await page.tap(".mark", "Знаю");
    assert.equal(await page.eval("document.querySelector('.mark.on')?.textContent.trim()"), "Знаю");
    await page.tap(".mark", "Не знаю");
    assert.equal(await page.eval("document.querySelector('.mark.on')?.textContent.trim()"), "Не знаю");
});

test("the long-press hint comes after Know on a new word and stays away once closed", async t => {
    const page = await app(t);
    await page.tap(".mark", "Знаю");
    assert.match(await page.eval("document.querySelector('.tip')?.textContent ?? ''"), /Удерживайте «Знаю»/);
    await page.tap('[data-act="hintclose"]');
    assert.equal(await page.eval("!!document.querySelector('.tip')"), false);
    for (let i = 0; i < 12; i++) {
        await page.tap('[data-act="next"]');
        const word = await page.eval("!!document.querySelector('.headword')");
        if (word) await page.tap(".mark", "Знаю");
        assert.equal(await page.eval("!!document.querySelector('.tip')"), false, `hint came back on card ${i + 2}`);
    }
});

test("search keeps the keyboard focus and finds by Latin spelling and by translation", async t => {
    const page = await app(t);
    await page.tap('[data-act="search"]');
    await page.waitFor("document.activeElement?.dataset?.input === 'search'");
    await page.type("kuć");
    assert.equal(await page.eval("document.activeElement?.dataset?.input"), "search");
    assert.ok((await page.eval("[...document.querySelectorAll('.row-word')].map(e => e.textContent)")).includes("кућа"));
    await page.tap('[data-act="bymeaning"]', "Перевод");
    await page.eval("(() => { const i = document.querySelector('[data-input=search]'); i.focus(); i.select(); })()");
    await page.type("дом");
    const words = await page.eval("[...document.querySelectorAll('.row-word')].map(e => e.textContent)");
    assert.ok(words.includes("кућа"), `no кућа among ${words.slice(0, 5)}`);
});

test("grammar tables never break a word", async t => {
    const page = await app(t);
    await page.tap('[data-act="ref"]');
    await page.tap(".tile", "Падежи: обзор");
    await page.waitFor("document.querySelector('.g-table td')");
    const broken = await page.eval(`(() => {
        const out = [];
        for (const td of document.querySelectorAll('.g-table td, .g-table th')) {
            const walker = document.createTreeWalker(td, NodeFilter.SHOW_TEXT);
            for (let n; (n = walker.nextNode());) {
                for (const m of n.textContent.matchAll(/\\S+/g)) {
                    const r = document.createRange();
                    r.setStart(n, m.index);
                    r.setEnd(n, m.index + m[0].length);
                    const lines = new Set([...r.getClientRects()].map(x => Math.round(x.top)));
                    if (lines.size > 1) out.push(m[0]);
                }
            }
        }
        return out;
    })()`);
    assert.deepEqual(broken, []);
});

test("progress survives a reload and a file export and import", async t => {
    const page = await app(t);
    await page.longPress(".mark", "Знаю");
    await page.reload();
    assert.equal(await known(page), 1);

    const dir = mkdtempSync(join(tmpdir(), "reci-export-"));
    t.after(() => rmSync(dir, { recursive: true, force: true }));
    const file = join(dir, "progress.json");
    writeFileSync(file, await page.eval("import('./js/store.js').then(S => JSON.stringify(S.exportData()))"));

    await page.tap('[data-act="settings"]');
    await page.tap('[data-act="reset"]');
    await page.tap('[data-act="doreset"]');
    assert.match(await page.text(), /\n0\s*\nиз \d+ слов знаю/);

    await page.eval("document.querySelector('[data-input=importfile]').id = 'importfile'");
    const { root } = await page.send("DOM.getDocument");
    const { nodeId } = await page.send("DOM.querySelector", { nodeId: root.nodeId, selector: "#importfile" });
    await page.send("DOM.setFileInputFiles", { nodeId, files: [file] });
    await page.waitFor("/Загружен прогресс/.test(document.getElementById('app').innerText)");
    assert.match(await page.text(), /\n1\s*\nиз \d+ слов знаю/);
});

test("a reload does not count the card on screen again", async t => {
    const page = await app(t);
    const shown = () => page.eval("import('./js/repo.js').then(R => [R.current().key, R.progress(R.current()).seen, R.todayCount()])");
    const before = await shown();
    await page.reload();
    assert.deepEqual(await shown(), before);
});

test("an answer survives a reload and can still be changed", async t => {
    const page = await app(t);
    await page.tap(".mark", "Знаю");
    await page.reload();
    assert.equal(await page.eval("document.querySelector('.mark.on')?.textContent.trim()"), "Знаю");
    await page.tap(".mark", "Не знаю");
    assert.equal(await page.eval("import('./js/repo.js').then(R => R.progress(R.current()).phase)"), 1);
});

test("article cards ask whether to show them again", async t => {
    const page = await app(t);
    for (let i = 0; i < 6 && !(await page.eval("!!document.querySelector('.article-title')")); i++) {
        await page.tap('[data-act="next"]');
    }
    const buttons = await page.eval("[...document.querySelectorAll('.mark')].map(e => e.textContent.trim())");
    assert.deepEqual(buttons, ["Перечитать позже", "Не показывать"]);
    assert.equal(await page.eval("document.querySelectorAll('.mark[data-long]').length"), 0);
});

test("a dark theme recolours the page", async t => {
    const page = await app(t);
    await page.tap('[data-act="settings"]');
    await page.tap('[data-act="theme"]', "Ноћ");
    assert.equal(await page.eval("getComputedStyle(document.documentElement).getPropertyValue('--bg').trim()"), "#14161B");
    assert.equal(await page.eval("document.querySelector('meta[name=theme-color]').content"), "#14161B");
});

test("the app starts offline from the service worker cache", async t => {
    const page = await app(t);
    await page.waitFor("navigator.serviceWorker.controller || false", 15000).catch(async () => {
        await page.reload();
        await page.waitFor("navigator.serviceWorker.controller", 15000);
    });
    await page.waitFor("caches.open('reci').then(c => c.keys()).then(k => k.length >= 15)", 15000);
    await page.offline(true);
    await page.reload();
    assert.ok(await page.eval("!!document.querySelector('.headword, .article-title')"));
});
