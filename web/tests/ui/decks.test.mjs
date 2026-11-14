// Language pairs in the web app: choosing the languages of the deck and of the interface in the
// settings, the first start by the browser's language.
// Usage: node --test "web/tests/ui/*.test.mjs"   (CHROME=/path/to/chrome if it is not google-chrome)

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
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

const current = page => page.eval("import('./js/repo.js').then(R => { const w = R.current(); return w && { key: w.key, cyr: w.cyr } })");
const deck = page => page.eval("import('./js/decks.js').then(D => D.current().id)");
const textOf = (page, sel) => page.eval(`document.querySelector(${JSON.stringify(sel)})?.textContent.trim() ?? null`);
const has = (page, sel) => page.eval(`!!document.querySelector(${JSON.stringify(sel)})`);
const known = async page => Number((await textOf(page, ".deck-count")).split("/")[0]);

// A language menu of the settings: its options, the value its row shows, and a pick as the
// native menu makes it.
const menu = name => `document.querySelector('[data-menu="${name}"]')`;
const options = (page, name) => page.eval(`[...${menu(name)}.options].map(o => o.textContent)`);
const chosen = (page, name) => page.eval(`${menu(name)}.closest(".menu").querySelector(".setting-value").textContent`);
const choose = (page, name, value) =>
    page.eval(`(() => { const s = ${menu(name)}; s.value = ${JSON.stringify(value)}; s.dispatchEvent(new Event("change", { bubbles: true })) })()`);

// Picks the language learned of the deck in the settings.
async function switchTo(page, id) {
    await page.tap('[data-act="settings"]');
    await choose(page, "target", id.split("-")[0]);
    await page.waitFor(`import('./js/decks.js').then(D => D.current().id === "${id}")`);
    await page.tap('[data-act="back"]');
}

test("the language learned chosen in the settings brings its cards; the progress of each deck stays", async t => {
    const page = await app(t);
    assert.equal(await deck(page), "sr-ru");
    const sr = await current(page);
    await page.longPress(".mark", "Знаю");
    assert.equal(await known(page), 1);
    const srNext = await current(page);

    await page.tap('[data-act="settings"]');
    assert.deepEqual(await options(page, "target"), ["Сербский", "Русский"]);
    assert.equal(await chosen(page, "target"), "Сербский");
    // A tap anywhere on the row opens the menu.
    assert.equal(await page.eval(`(() => { const r = ${menu("target")}.closest(".menu").getBoundingClientRect();
        return document.elementFromPoint(r.left + 20, r.top + r.height / 2) === ${menu("target")} })()`), true);
    // One deck learns Serbian: the menu of its translations has one option.
    assert.deepEqual(await options(page, "native"), ["Русский"]);
    assert.equal(await chosen(page, "native"), "Русский");
    await choose(page, "target", "ru");
    await page.waitFor(`import('./js/decks.js').then(D => D.current().id === "ru-sr")`);
    assert.equal(await textOf(page, "h1"), "Настройки", "the interface is not the deck's");
    assert.equal(await chosen(page, "target"), "Русский");
    assert.deepEqual(await options(page, "native"), ["Сербский"]);
    assert.equal(await has(page, '[data-act="bothscripts"]'), false);
    assert.equal(await has(page, '.setting[data-act="schools"]'), false);
    assert.equal(await page.eval(`document.querySelectorAll('[data-act="stressplace"]').length`), 2);
    await page.tap('[data-act="back"]');

    const ru = await current(page);
    assert.notEqual(ru.key, sr.key);
    assert.equal(await page.eval("import('./js/repo.js').then(R => !!R.word('хотеть'))"), true);
    assert.equal(await known(page), 0);
    assert.equal(await page.eval("document.documentElement.lang"), "ru");
    await page.tap(".mark", "Не знаю");

    await switchTo(page, "sr-ru");
    assert.equal(await known(page), 1);
    assert.deepEqual(await current(page), srNext);
    assert.match(await page.text(), /Дальше/);

    // Each deck comes back where it was left, also after a reload.
    await switchTo(page, "ru-sr");
    assert.equal((await current(page)).key, ru.key);
    assert.equal(await page.eval("document.querySelector('.mark.on')?.textContent.trim()"), "Не знаю");
    await page.reload();
    assert.equal(await deck(page), "ru-sr");
    assert.equal((await current(page)).key, ru.key);
    assert.deepEqual(page.errors, []);
});

test("a browser in Serbian starts on Russian for Serbs, in the Serbian interface", async t => {
    const page = await app(t, { lang: "sr-RS" });
    assert.equal(await deck(page), "ru-sr");
    assert.equal(await page.eval(`document.querySelector('[data-act="settings"]').getAttribute("aria-label")`), "Подешавања");
    assert.match(await page.text(), /Даље/);

    await page.tap('[data-act="settings"]');
    await page.tap('[data-act="alphabet"][data-arg="1"]');
    assert.equal(await textOf(page, "h1"), "Podešavanja");
    await page.tap('[data-act="back"]');
    // The Russian word stays Cyrillic; its Serbian translation follows the script setting.
    assert.match(await textOf(page, ".headword"), /[а-яё]/);
    assert.doesNotMatch(await textOf(page, ".meaning"), /[Ѐ-ӿ]/);
    assert.equal(await textOf(page, ".next-label"), "Dalje");
});

test("the interface language chosen in the settings changes the interface at once and stays", async t => {
    const page = await app(t);
    await page.tap('[data-act="settings"]');
    assert.deepEqual(await options(page, "uilang"), ["Русский", "Српски", "English"]);
    assert.equal(await chosen(page, "uilang"), "Русский");
    await choose(page, "uilang", "en");
    await page.waitFor("document.querySelector('h1')?.textContent === 'Settings'");
    assert.equal(await page.eval("document.documentElement.lang"), "en");
    assert.equal(await chosen(page, "uilang"), "English");

    await choose(page, "target", "ru");
    await page.waitFor(`import('./js/decks.js').then(D => D.current().id === "ru-sr")`);
    assert.equal(await textOf(page, "h1"), "Settings");
    await page.reload();
    assert.match(await page.text(), /Next/);

    await page.tap('[data-act="settings"]');
    assert.equal(await chosen(page, "uilang"), "English");
    await choose(page, "uilang", "sr");
    await page.waitFor("document.querySelector('h1')?.textContent === 'Подешавања'");
    assert.deepEqual(page.errors, []);
});

test("with a second deck for a language learned, the translations follow the language learned", async t => {
    const decks = JSON.parse(readFileSync(new URL("../../../app/app/src/main/assets/decks.json", import.meta.url), "utf-8"));
    decks.push({ id: "sr-en", target: "sr", native: "en", words: "words.json", grammar: "" });
    const three = await serve({ files: { "/app/app/src/main/assets/decks.json": JSON.stringify(decks) } });
    t.after(() => three.close());
    const page = await launch();
    t.after(() => page.close());
    await page.open(three.url);

    await page.tap('[data-act="settings"]');
    assert.deepEqual(await options(page, "target"), ["Сербский", "Русский"]);
    assert.deepEqual(await options(page, "native"), ["Русский", "Английский"]);
    assert.equal(await chosen(page, "native"), "Русский");
    const waitDeck = id => page.waitFor(`import('./js/decks.js').then(D => D.current().id === "${id}")`);
    await choose(page, "native", "en");
    await waitDeck("sr-en");
    assert.equal(await chosen(page, "native"), "Английский");

    // No deck teaches Russian with English translations: the Serbian one is taken.
    await choose(page, "target", "ru");
    await waitDeck("ru-sr");
    assert.deepEqual(await options(page, "native"), ["Сербский"]);

    // Nor Serbian with Serbian translations: the first deck for Serbian is taken.
    await choose(page, "target", "sr");
    await waitDeck("sr-ru");
    assert.deepEqual(await options(page, "native"), ["Русский", "Английский"]);
    assert.deepEqual(page.errors, []);
});

test("a first start takes the interface language from the browser and keeps it", async t => {
    const ui = page => page.eval("import('./js/store.js').then(S => S.prefs.uiLang)");
    for (const [lang, want, title] of [["ru-RU", "ru", "Настройки"], ["sr-RS", "sr", "Подешавања"],
        ["hr-HR", "sr", "Подешавања"], ["de-DE", "en", "Settings"]]) {
        const page = await app(t, { lang });
        assert.equal(await ui(page), want, lang);
        assert.equal(await page.eval(`document.querySelector('[data-act="settings"]').getAttribute("aria-label")`), title);
    }
    // An earlier user keeps the interface the deck gave, whatever the browser says.
    const earlier = await app(t, { lang: "en-US", storage: { "reci.prefs": JSON.stringify({ currentKey: "бити" }) } });
    assert.equal(await ui(earlier), "ru");
    // A stored choice holds in a browser set to another language, and an empty one counts as none.
    const chosen = await app(t, { lang: "ru-RU", storage: { "reci.prefs": JSON.stringify({ uiLang: "sr" }) } });
    assert.equal(await ui(chosen), "sr");
    const empty = await app(t, { lang: "de-DE", storage: { "reci.prefs": JSON.stringify({ uiLang: "" }) } });
    assert.equal(await ui(empty), "en");
});

test("a browser in Russian starts on Serbian; an earlier user stays on it", async t => {
    const fresh = await app(t, { lang: "ru-RU" });
    assert.equal(await deck(fresh), "sr-ru");
    const earlier = await app(t, { lang: "sr-RS", storage: { "reci.prefs": JSON.stringify({ currentKey: "бити" }) } });
    assert.equal(await deck(earlier), "sr-ru");
    assert.equal((await current(earlier)).key, "бити");
});

test("the Russian deck speaks Russian and searches Russian without ё", async t => {
    const page = await app(t, { lang: "sr" });
    await page.eval("window.spoken = []; speechSynthesis.speak = u => window.spoken.push([u.text, u.lang])");
    await page.tap('[data-act="settings"]');
    await page.tap('[data-act="testvoice"]');
    const [text, lang] = (await page.eval("window.spoken")).at(-1);
    assert.equal(text, "Добрый день");
    assert.match(lang, /^ru/);
    await page.tap('[data-act="back"]');

    await page.tap('[data-act="search"]');
    await page.waitFor("document.activeElement?.dataset?.input === 'search'");
    assert.equal(await page.eval("document.querySelector('[data-input=search]').placeholder"), "слово");
    await page.type("еще");
    assert.ok((await page.eval("[...document.querySelectorAll('.row-word')].map(e => e.textContent)")).includes("ещё"));
    await page.tap('[data-act="bymeaning"]', "Превод");
    await page.eval("(() => { const i = document.querySelector('[data-input=search]'); i.focus(); i.select(); })()");
    await page.type("kuć");
    const rows = await page.eval("[...document.querySelectorAll('.row-sub')].map(e => e.textContent)");
    assert.ok(rows.some(s => s.includes("кућа")), `no кућа among ${rows.slice(0, 5)}`);
});
