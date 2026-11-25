// Every deck in every interface language, chosen with the language menus of the settings: the card
// is in the languages of the deck, the interface in the language chosen.
// Usage: node --test "web/tests/ui/*.test.mjs"   (CHROME=/path/to/chrome if it is not google-chrome)

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { serve, launch } from "./chrome.mjs";

const ASSETS = new URL("../../../app/app/src/main/assets/", import.meta.url);
const read = name => JSON.parse(readFileSync(new URL(name, ASSETS), "utf-8"));
const decks = read("decks.json");
const cards = Object.fromEntries(decks.map(d => [d.id, new Map(read(d.words).map(c => [c.key, c]))]));

// What the interface shows in each language: the settings title, the next button, the menu titles
// and the names of the languages.
const UI = {
    ru: { settings: "Настройки", next: "Дальше", menus: ["Учу", "Перевод на", "Язык интерфейса"],
        names: { sr: "Сербский", ru: "Русский", en: "Английский" } },
    sr: { settings: "Подешавања", next: "Даље", menus: ["Учим", "Превод на", "Језик интерфејса"],
        names: { sr: "Српски", ru: "Руски", en: "Енглески" } },
    en: { settings: "Settings", next: "Next", menus: ["Learning", "Translated into", "Interface language"],
        names: { sr: "Serbian", ru: "Russian", en: "English" } },
};

const LETTERS = { sr: /^[абвгдђежзијклљмнњопрстћуфхцчџш]+$/, ru: /^[абвгдеёжзийклмнопрстуфхцчшщъыьэюя]+$/ };
const STRESS = /[̀́̄̏̑]/g;
const bare = s => s.normalize("NFD").replace(STRESS, "").normalize("NFC").toLowerCase().replace(/[^\p{L}]/gu, "");

let server;
before(async () => (server = await serve()));
after(() => server.close());

const deck = page => page.eval("import('./js/decks.js').then(D => D.current().id)");
const textOf = (page, sel) => page.eval(`document.querySelector(${JSON.stringify(sel)})?.textContent.trim() ?? null`);
const menu = name => `document.querySelector('[data-menu="${name}"]')`;
const options = (page, name) => page.eval(`[...${menu(name)}.options].map(o => o.textContent)`);
const chosen = (page, name) => page.eval(`${menu(name)}.closest(".menu").querySelector(".setting-value").textContent`);
const titles = page => page.eval("[...document.querySelectorAll('.menu .setting-title')].map(e => e.textContent)");
const choose = (page, name, value) =>
    page.eval(`(() => { const s = ${menu(name)}; s.value = ${JSON.stringify(value)}; s.dispatchEvent(new Event("change", { bubbles: true })) })()`);

// Picks a deck as a person would: the language learned, then the translations if still needed.
async function pick(page, d) {
    await choose(page, "target", d.target);
    await page.waitFor(`import('./js/decks.js').then(D => D.current().target === "${d.target}")`);
    if ((await deck(page)) !== d.id) await choose(page, "native", d.native);
    await page.waitFor(`import('./js/decks.js').then(D => D.current().id === "${d.id}")`);
}

// Each interface language starts from a browser in another one, so that the menu has work to do.
const BROWSER = { ru: "en-US", sr: "ru-RU", en: "sr-RS" };

for (const [lang, ui] of Object.entries(UI)) {
    test(`the ${lang} interface over each deck, chosen in the settings`, async t => {
        const page = await launch({ lang: BROWSER[lang] });
        t.after(() => page.close());
        await page.open(server.url);
        await page.tap('[data-act="settings"]');
        await choose(page, "uilang", lang);
        await page.waitFor(`document.querySelector('h1')?.textContent === ${JSON.stringify(ui.settings)}`);

        for (const d of decks) {
            const what = `${d.id} in ${lang}`;
            await pick(page, d);
            assert.equal(await textOf(page, "h1"), ui.settings, what);
            assert.deepEqual(await titles(page), ui.menus, what);
            assert.equal(await chosen(page, "target"), ui.names[d.target], what);
            assert.equal(await chosen(page, "native"), ui.names[d.native], what);
            assert.deepEqual(await options(page, "target"), [ui.names.sr, ui.names.ru], what);
            assert.deepEqual(await options(page, "native"), decks.filter(x => x.target === d.target).map(x => ui.names[x.native]), what);
            assert.equal(await chosen(page, "uilang"), { ru: "Русский", sr: "Српски", en: "English" }[lang], what);
            await page.tap('[data-act="back"]');

            // The card on screen: the word in the language learned, the translation of this deck.
            await page.waitFor("document.querySelector('.headword')");
            const key = await page.eval("import('./js/repo.js').then(R => R.current()?.key)");
            const card = cards[d.id].get(key);
            assert.ok(card, `${what}: ${key} is not a card of the deck`);
            assert.match(bare(await textOf(page, ".headword")), LETTERS[d.target], what);
            assert.equal(await page.eval("document.querySelector('.meaning span:last-child').textContent"), card.meanings[0].replace(/[{}]/g, ""), what);
            assert.equal(await textOf(page, ".next-label"), ui.next, what);
            assert.equal(await page.eval(`document.querySelector('[data-act="settings"]').getAttribute("aria-label")`), ui.settings, what);
            assert.equal(await page.eval("document.documentElement.lang"), lang, what);
            await page.tap('[data-act="settings"]');
        }
        assert.deepEqual(page.errors, []);
    });
}
