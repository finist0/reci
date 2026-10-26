// Every setting of the web app, changed through the settings screen and checked on the card.
// Usage: node --test "web/tests/ui/*.test.mjs"   (CHROME=/path/to/chrome if it is not google-chrome)

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { serve, launch } from "./chrome.mjs";

let server;
before(async () => (server = await serve()));
after(() => server.close());

async function app(t) {
    const page = await launch();
    t.after(() => page.close());
    await page.open(server.url);
    return page;
}

const current = page => page.eval("import('./js/repo.js').then(R => { const w = R.current(); return w && { key: w.key, cyr: w.cyr, acc: w.acc, level: w.level, pos: w.pos }; })");
const prefs = page => page.eval("import('./js/store.js').then(S => ({ ...S.prefs }))");
const has = (page, sel) => page.eval(`!!document.querySelector(${JSON.stringify(sel)})`);
const textOf = (page, sel) => page.eval(`document.querySelector(${JSON.stringify(sel)})?.textContent.trim() ?? null`);

async function settings(page, ...taps) {
    await page.tap('[data-act="settings"]');
    for (const sel of taps) await page.tap(sel);
    await page.tap('[data-act="back"]');
}

test("What to learn: one level in alphabetical order", async t => {
    const page = await app(t);
    await page.tap('[data-act="settings"]');
    await page.tap('[data-act="categories"]');
    for (const id of ["L3", "L4", "X:phrase", "X:compare", "X:note", "L1"]) await page.tap(`[data-act="toggle"][data-arg="${id}"]`);
    await page.tap('[data-act="order"][data-arg="2"]');
    await page.tap('[data-act="learn"]');
    assert.match(await page.text(), /A1 · Начальный · По алфавиту/);
    await page.tap('[data-act="back"]');
    const w = await current(page);
    assert.equal(w.level, 1);
    const first = await page.eval(`import('./js/repo.js').then(R => {
        const coll = new Intl.Collator("sr");
        return R.words.filter(w => w.level === 1 && w.pos !== "phrase").map(w => w.cyr).sort(coll.compare)[0];
    })`);
    assert.equal(w.cyr, first);
});

test("Cards: translation only hides the word, word only covers the translation", async t => {
    const page = await app(t);
    await settings(page, '[data-act="cardmode"][data-arg="1"]');
    assert.equal(await has(page, ".headword"), false);
    await page.tap(".reveal", "Показать слово");
    assert.equal(await has(page, ".headword"), true);

    await settings(page, '[data-act="cardmode"][data-arg="2"]');
    assert.equal(await has(page, ".headword"), true);
    await page.tap(".cover", "Показать перевод");
    assert.equal(await has(page, ".cover"), false);

    await settings(page, '[data-act="cardmode"][data-arg="0"]');
    assert.equal(await has(page, ".headword"), true);
    assert.equal(await has(page, ".cover"), false);
    await page.tap('[data-act="next"]');
    assert.equal(await has(page, ".headword"), true);
});

test("Words at a time: the cards cycle through that many words", async t => {
    const page = await app(t);
    await settings(page, '[data-act="activesize"][data-arg="0"]');
    const seen = new Set();
    for (let i = 0; i < 30; i++) {
        const w = await current(page);
        if (w.pos !== "compare" && w.pos !== "note") seen.add(w.key);
        await page.tap('[data-act="next"]');
    }
    assert.equal(seen.size, 10);
});

test("Know / Don't know buttons can be hidden", async t => {
    const page = await app(t);
    assert.equal(await has(page, ".marks"), true);
    await settings(page, '[data-act="usemarks"]');
    assert.equal(await has(page, ".marks"), false);
    await settings(page, '[data-act="usemarks"]');
    assert.equal(await has(page, ".marks"), true);
});

test("Check learned words can be turned off", async t => {
    const page = await app(t);
    await settings(page, '[data-act="reviewknown"]');
    assert.equal((await prefs(page)).reviewKnown, false);
    await page.reload();
    assert.equal((await prefs(page)).reviewKnown, false);
});

test("Alphabet and the second script line", async t => {
    const page = await app(t);
    const cyrillic = /[Ѐ-ӿ]/;
    assert.match(await textOf(page, ".headword"), cyrillic);
    assert.doesNotMatch(await textOf(page, ".secondary"), cyrillic);

    await settings(page, '[data-act="alphabet"][data-arg="1"]');
    assert.doesNotMatch(await textOf(page, ".headword"), cyrillic);
    assert.match(await textOf(page, ".secondary"), cyrillic);

    await settings(page, '[data-act="bothscripts"]');
    assert.equal(await has(page, ".secondary"), false);
});

test("Stress marks: full, the stressed vowel only, none", async t => {
    const page = await app(t);
    for (let i = 0; i < 20 && !(await current(page)).acc; i++) await page.tap('[data-act="next"]');
    const w = await current(page);
    assert.ok(w.acc, "no word with stress marks in the first cards");
    const marks = /[̀-ͯ]/g;

    await settings(page, '[data-act="accents"][data-arg="0"]');
    assert.equal(await textOf(page, ".headword"), w.acc);
    await settings(page, '[data-act="accents"][data-arg="1"]');
    const one = await textOf(page, ".headword");
    assert.equal(one.match(marks)?.length, 1);
    assert.equal(one.replace(marks, ""), w.cyr);
    await settings(page, '[data-act="accents"][data-arg="2"]');
    assert.equal(await textOf(page, ".headword"), w.cyr);
});

test("Font size changes the headword", async t => {
    const page = await app(t);
    await page.tap('[data-act="settings"]');
    await page.eval(`(() => {
        const s = document.querySelector('[data-input="wordsize"]');
        s.value = 60;
        s.dispatchEvent(new Event("input", { bubbles: true }));
        s.dispatchEvent(new Event("change", { bubbles: true }));
    })()`);
    assert.match(await page.text(), /Размер шрифта: 60/);
    await page.tap('[data-act="back"]');
    assert.equal(await page.eval("getComputedStyle(document.querySelector('.headword')).fontSize"), "60px");
    assert.equal((await prefs(page)).wordSize, 60);
});

test("Say the word at once speaks a new word but not a word to recall", async t => {
    const page = await app(t);
    await page.eval("window.spoken = []; speechSynthesis.speak = u => window.spoken.push(u.text)");
    const spoken = () => page.eval("window.spoken");

    await page.tap('[data-act="next"]');
    assert.deepEqual(await spoken(), []);

    await settings(page, '[data-act="autospeak"]');
    await page.tap('[data-act="next"]');
    const w = await current(page);
    assert.ok(w.pos !== "compare" && w.pos !== "note");
    assert.deepEqual(await spoken(), [w.cyr]);

    await settings(page, '[data-act="cardmode"][data-arg="1"]');
    await page.tap('[data-act="next"]');
    assert.equal((await spoken()).length, 1);

    await page.tap('[data-act="settings"]');
    await page.tap('[data-act="testvoice"]');
    assert.equal((await spoken()).at(-1), "Добар дан");
});

test("Hidden articles are counted and shown again", async t => {
    const page = await app(t);
    for (let i = 0; i < 6 && !(await has(page, ".article-title")); i++) await page.tap('[data-act="next"]');
    const pos = (await current(page)).pos;
    await page.tap(".mark", "Не показывать");
    const row = pos === "compare" ? "Снова показывать разборы" : "Снова показывать заметки";

    await page.tap('[data-act="settings"]');
    await page.tap(".setting", row);
    await page.waitFor("document.querySelector('.modal')");
    await page.tap(".dialog-buttons button", "Отмена");
    assert.match(await page.text(), new RegExp(`${row}\\s*Скрыто: 1`));

    await page.tap(".setting", row);
    await page.tap(".dialog-buttons button", "Показывать");
    assert.match(await page.text(), new RegExp(`${row}\\s*Скрытых нет`));
});
