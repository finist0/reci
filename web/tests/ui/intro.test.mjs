// The first start asks which language to learn and the language of the translations.
// Usage: node --test "web/tests/ui/*.test.mjs"   (CHROME=/path/to/chrome if it is not google-chrome)

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { serve, launch } from "./chrome.mjs";

let server;
before(async () => (server = await serve()));
after(() => server.close());

async function app(t, opts) {
    const page = await launch({ intro: true, ...opts });
    t.after(() => page.close());
    await page.open(server.url);
    return page;
}

const has = (page, sel) => page.eval(`!!document.querySelector(${JSON.stringify(sel)})`);
const deck = page => page.eval("import('./js/decks.js').then(D => D.current().id)");
const on = (page, sel) => page.eval(`[...document.querySelectorAll(${JSON.stringify(sel + ".on")})].map(b => b.dataset.code)`);
const codes = (page, sel) => page.eval(`[...document.querySelectorAll(${JSON.stringify(sel)})].map(b => b.dataset.code)`);

test("a first visit chooses the languages, preset by the browser's, before the cards", async t => {
    const page = await app(t, { lang: "en-US" });
    assert.ok(await has(page, ".screen.pair"));
    assert.ok(!(await has(page, ".card-wrap")), "no cards behind the choice");
    assert.deepEqual(await on(page, ".pair-option"), ["sr"]);
    assert.deepEqual(await on(page, ".pair-chip"), ["en"]);

    await page.tap('.pair-option[data-code="ru"]');
    assert.deepEqual(await codes(page, ".pair-chip"), ["sr", "en"], "Russian is translated into Serbian or English");
    assert.deepEqual(await on(page, ".pair-chip"), ["en"], "the translations stay where a deck has them");
    await page.tap('.pair-option[data-code="sr"]');
    await page.tap('.pair-chip[data-code="ru"]');
    assert.deepEqual(await on(page, ".pair-ui"), ["en"], "the translations leave the interface alone");
    await page.tap('.pair-ui[data-code="sr"]');
    assert.equal(await page.eval("document.querySelector('[data-act=pairstart]').textContent.trim()"), "Почни");
    await page.tap('[data-act="pairstart"]');
    await page.waitFor("document.querySelector('.card-wrap')");
    assert.equal(await deck(page), "sr-ru", "the cards come once the pair chosen is in");
    assert.equal(await page.eval("localStorage.getItem('reci.intro')"), "done");

    await page.reload();
    assert.ok(!(await has(page, ".screen.pair")), "asked once");
    assert.equal(await deck(page), "sr-ru");
});

test("start keeps the guess as it is", async t => {
    const page = await app(t, { lang: "sr-RS" });
    assert.deepEqual(await on(page, ".pair-option"), ["ru"]);
    await page.tap('[data-act="pairstart"]');
    await page.waitFor("document.querySelector('.card-wrap')");
    assert.equal(await deck(page), "ru-sr");
});

test("a pair whose cards fail to load keeps the choice on screen", async t => {
    const broken = await serve({ files: { "/app/app/src/main/assets/words-ru-en.json": "not json" } });
    t.after(() => broken.close());
    const page = await launch({ intro: true, lang: "en-US" });
    t.after(() => page.close());
    await page.open(broken.url);
    await page.tap('.pair-option[data-code="ru"]');
    await page.tap('[data-act="pairstart"]');
    await page.waitFor("document.querySelector('.toast')");
    assert.ok(await has(page, ".screen.pair"));
    assert.equal(await page.eval("localStorage.getItem('reci.intro')"), "pair", "still to be asked");
    assert.equal(await page.eval("document.querySelector('[data-act=\"pairstart\"]').disabled"), false, "and open to another try");
});

test("earlier users are not asked", async t => {
    const progress = await app(t, { storage: { "reci.progress": JSON.stringify({ x: { state: 3 } }) } });
    assert.ok(!(await has(progress, ".screen.pair")), "a user with progress");
    const chosen = await app(t, { storage: { "reci.prefs": JSON.stringify({ deck: "sr-en" }) } });
    assert.ok(!(await has(chosen, ".screen.pair")), "a user with a deck");
    assert.equal(await deck(chosen), "sr-en");
});
