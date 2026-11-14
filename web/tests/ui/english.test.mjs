// The decks with English translations as the app carries them: a first start in another
// language, the settings and the search. The first cards of each deck are translated in any case.
// Usage: node --test "web/tests/ui/*.test.mjs"   (CHROME=/path/to/chrome if it is not google-chrome)

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { serve, launch } from "./chrome.mjs";

let server;
before(async () => (server = await serve()));
after(() => server.close());

async function app(t, opts) {
    const page = await launch(opts);
    t.after(() => page.close());
    await page.open(server.url);
    return page;
}

const deck = page => page.eval("import('./js/decks.js').then(D => D.current().id)");
const menu = name => `document.querySelector('[data-menu="${name}"]')`;
const options = (page, name) => page.eval(`[...${menu(name)}.options].map(o => o.textContent)`);
const results = page => page.eval("[...document.querySelectorAll('[data-results] [data-act=detail]')].map(e => e.dataset.arg)");

async function search(page, chip, text) {
    await page.tap('[data-act="bymeaning"]', chip);
    await page.eval("(() => { const i = document.querySelector('[data-input=search]'); i.focus(); i.select(); })()");
    await page.type(text);
}

test("a browser in another language starts on Serbian with English and finds words both ways", async t => {
    const page = await app(t, { lang: "de-DE" });
    assert.equal(await deck(page), "sr-en");
    await page.tap('[data-act="settings"]');
    assert.deepEqual(await options(page, "native"), ["Russian", "English"]);
    assert.deepEqual(await options(page, "target"), ["Serbian", "Russian"]);
    await page.tap('[data-act="back"]');

    await page.tap('[data-act="search"]');
    await page.waitFor("document.activeElement?.dataset?.input === 'search'");
    await search(page, "Translation", "to be");
    assert.ok((await results(page)).includes("бити"));
    await search(page, "Serbian word", "biti");
    assert.ok((await results(page)).includes("бити"));
    assert.deepEqual(page.errors, []);
});

test("Russian with English translations, chosen in the settings", async t => {
    const page = await app(t, { lang: "en-US" });
    await page.tap('[data-act="settings"]');
    await page.eval(`(() => { const s = ${menu("target")}; s.value = "ru"; s.dispatchEvent(new Event("change", { bubbles: true })) })()`);
    await page.waitFor(`import('./js/decks.js').then(D => D.current().id === "ru-en")`);
    assert.equal(await page.eval("import('./js/repo.js').then(R => R.word('я').meanings.join())"), "I");
    assert.deepEqual(page.errors, []);
});
