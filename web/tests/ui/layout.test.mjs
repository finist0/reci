// How the card fits small and ordinary phone screens, and where its star and flag sit.
// A larger text setting in a browser leaves fewer CSS pixels on the screen, so a small viewport
// stands for it too.
// Usage: node --test "web/tests/ui/*.test.mjs"   (CHROME=/path/to/chrome if it is not google-chrome)

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { serve, launch } from "./chrome.mjs";

let server;
before(async () => (server = await serve()));
after(() => server.close());

async function app(t, width, height) {
    const page = await launch();
    t.after(() => page.close());
    await page.send("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: 2, mobile: true });
    await page.open(server.url);
    await page.waitFor("document.fonts.ready.then(() => true)");
    return page;
}

async function articles(page) {
    await page.eval("import('./js/repo.js').then(R => R.setCategories(['X:compare']))");
    await page.waitFor("document.querySelector('.mark[data-brief]')");
}

const currentKey = page => page.eval("import('./js/repo.js').then(R => R.current()?.key)");

const rect = (page, sel) => page.eval(`(() => {
    const r = document.querySelector(${JSON.stringify(sel)}).getBoundingClientRect();
    return { left: r.left, right: r.right, top: r.top, bottom: r.bottom };
})()`);

const shown = (page, sel) => page.eval(`(() => {
    const e = document.querySelector(${JSON.stringify(sel)});
    return !!e && e.getClientRects().length > 0;
})()`);

// Inside the visible part of the card, not scrolled out of it.
const inCard = async (page, sel) => {
    const [c, r] = [await rect(page, ".card-wrap"), await rect(page, sel)];
    return r.top >= c.top - 1 && r.bottom <= c.bottom + 1;
};

const sameRow = (a, b) => a.top < b.bottom && b.top < a.bottom;

test("a small screen with large text keeps the top bar icons and the level title", async t => {
    // 360 CSS pixels with the text at 130%.
    const page = await app(t, 277, 492);
    const width = await page.eval("innerWidth");
    for (const act of ["search", "ref", "settings"]) {
        assert.ok((await rect(page, `[data-act="${act}"]`)).right <= width, `${act} sticks out of the screen`);
    }
    assert.equal(await shown(page, ".deck-count"), false);
    assert.equal(await page.eval("(e => e.scrollWidth <= e.clientWidth)(document.querySelector('.deck-title'))"), true);
    await page.tap('[data-act="settings"]');
    assert.ok(await shown(page, ".screen.settings"));
});

test("a short screen puts the answers and the arrows in one row", async t => {
    const page = await app(t, 320, 568);
    const know = await rect(page, ".mark[data-long]");
    assert.ok(sameRow(know, await rect(page, '[data-act="next"]')));
    assert.ok(sameRow(know, await rect(page, '[data-act="prev"]')));
    assert.equal(await shown(page, ".next-label"), false);
    assert.equal(await page.eval(`document.querySelector('[data-act="next"]').getAttribute("aria-label")`), "Дальше");
    const first = await currentKey(page);
    await page.tap('[data-act="next"]');
    assert.notEqual(await currentKey(page), first);
});

test("article buttons take short labels when the full ones do not fit", async t => {
    const page = await app(t, 320, 568);
    await articles(page);
    const labels = await page.eval(`[...document.querySelectorAll(".mark")].map(e => [e.getAttribute("aria-label"), getComputedStyle(e, "::after").content])`);
    assert.deepEqual(labels, [["Перечитать позже", '"Позже"'], ["Не показывать", '"Скрыть"']]);
    assert.equal(await shown(page, ".mark-text"), false);
    const first = await currentKey(page);
    await page.tap(".mark", "Не показывать");
    await page.tap('[data-act="next"]');
    assert.notEqual(await currentKey(page), first);
});

test("an ordinary screen keeps two rows, the count and the full labels", async t => {
    const page = await app(t, 390, 844);
    assert.ok(await shown(page, ".next-label"));
    assert.ok(!sameRow(await rect(page, ".mark"), await rect(page, '[data-act="next"]')));
    assert.ok(await shown(page, ".deck-count"));
    await articles(page);
    assert.deepEqual(await page.eval(`[...document.querySelectorAll(".mark")].map(e => e.innerText.trim())`),
        ["Перечитать позже", "Не показывать"]);
});

test("a long card shows the flag only at its end, and the star scrolls away", async t => {
    const page = await app(t, 320, 568);
    assert.ok(await inCard(page, ".star"));
    assert.equal(await inCard(page, '[data-act="report"]'), false);
    assert.equal(await inCard(page, '[data-act="schools"]'), false);
    assert.ok(await page.eval("document.querySelector('.card-wrap').classList.contains('more')"), "no fade over the cut text");
    await page.eval("(e => { e.scrollTop = e.scrollHeight; })(document.querySelector('.card-scroll'))");
    await page.waitFor("!document.querySelector('.card-wrap').classList.contains('more')");
    assert.ok(await inCard(page, '[data-act="report"]'));
    assert.equal(await inCard(page, ".star"), false);
    await page.tap('[data-act="report"]');
    assert.ok(await shown(page, ".dialog.form"));
});

test("a short card keeps the flag and the school button in its bottom corner", async t => {
    const page = await app(t, 390, 1600);
    const [card, flag] = [await rect(page, ".card-wrap"), await rect(page, '[data-act="report"]')];
    assert.ok(card.bottom - flag.bottom < 16 && card.right - flag.right < 16, "the flag is not in the corner");
    assert.ok(await inCard(page, '[data-act="schools"]'));
    assert.equal(await page.eval("document.querySelector('.card-wrap').classList.contains('more')"), false);
});
