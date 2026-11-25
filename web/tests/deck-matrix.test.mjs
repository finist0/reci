// The learning steps over the real cards of every deck, in each interface language: the cards come
// from the deck, the answers move them on, and the interface language picks none of them.

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const WEB = resolve(dirname(fileURLToPath(import.meta.url)), "..");

const storage = new Map();
globalThis.localStorage = {
    getItem: k => storage.get(k) ?? null,
    setItem: (k, v) => storage.set(k, String(v)),
    removeItem: k => storage.delete(k),
};
globalThis.document = { addEventListener() {}, visibilityState: "visible" };
Object.defineProperty(globalThis, "navigator", { configurable: true, value: { language: "ru-RU", languages: ["ru-RU"] } });
globalThis.fetch = async url => {
    const text = readFileSync(resolve(WEB, url), "utf-8");
    return { ok: true, json: async () => JSON.parse(text), text: async () => text };
};

const R = await import("../js/repo.js");
const S = await import("../js/store.js");
const Decks = await import("../js/decks.js");
const D = await import("../js/data.js");
await R.load();

const UI = { ru: ["Настройки", "A1 · Начальный"], sr: ["Подешавања", "A1 · Почетни"], en: ["Settings", "A1 · Beginner"] };

// Letters only one of the two Cyrillic alphabets has; a side in English has Latin ones.
const ONLY = { sr: /[ђјљњћџ]/i, ru: /[ыэъьяюёйщ]/i, en: /[a-z]/i };
const ALPHABET = { sr: /^[абвгдђежзијклљмнњопрстћуфхцчџш]+$/, ru: /^[абвгдеёжзийклмнопрстуфхцчшщъыьэюя]+$/ };
const STRESS = /[̀́̄̏̑]/g;
const bare = s => s.normalize("NFD").replace(STRESS, "").normalize("NFC").toLowerCase().replace(/[^\p{L}]/gu, "");

const levels = cards => [...new Set(cards.filter(w => w.level > 0).map(w => w.level))].sort();

// A run of cards answered in turn the way a person might, with the checks of each answer.
function run(deck) {
    let clock = Date.UTC(2026, 0, 1);
    R.clock.now = () => clock;
    R.resetAll();
    S.setPrefs({ activeSize: 20, reviewKnown: true, cardMode: 0, cardCount: 0 });
    R.setCategories(["L1", "L2", "X:phrase", "X:compare", "X:note"], 1);
    S.setPref("shuffleSeed", 12345);

    const shown = [];
    let dontKnow = null;
    for (let i = 0; i < 80; i++) {
        clock += 1000;
        R.step(1);
        const w = R.current();
        assert.ok(w, `${deck.id}: no card after ${i} steps`);
        R.markShown(w);
        shown.push(w);
        if (R.isArticle(w)) {
            R.answer(w, i % 2 ? S.KNOWN : S.DONT_KNOW);
            assert.equal(R.progress(w).phase, i % 2 ? R.LEARNED : 0, `${deck.id} ${w.key}`);
            continue;
        }
        assert.ok(w.level === 1 || w.level === 2 || w.pos === "phrase", `${deck.id} ${w.key}: level ${w.level}`);
        const before = R.progress(w);
        switch (i % 4) {
            case 1:
                R.answer(w, S.DONT_KNOW);
                assert.equal(R.progress(w).state, S.DONT_KNOW, `${deck.id} ${w.key}`);
                if (!dontKnow && before.phase === R.INTRO) dontKnow = { key: w.key, at: i };
                break;
            case 2:
                R.answer(w, S.KNOWN, true);
                assert.equal(R.progress(w).phase, R.LEARNED, `${deck.id} ${w.key}`);
                break;
            default:
                R.answer(w, S.KNOWN);
                if (before.phase === R.INTRO) assert.equal(R.progress(w).phase, R.RECALL, `${deck.id} ${w.key}`);
        }
    }
    assert.ok(dontKnow, `${deck.id}: no new word was answered «Не знаю»`);
    assert.ok(shown.slice(dontKnow.at + 1).some(w => w.key === dontKnow.key), `${deck.id}: ${dontKnow.key} did not come back`);
    return shown;
}

for (const deck of Decks.all) {
    test(`${deck.id}: the learning steps run over its cards the same in every interface language`, async () => {
        await R.switchDeck(deck.id);
        assert.equal(Decks.current().id, deck.id);
        assert.ok(R.words.length > 1000);
        const runs = {};
        try {
            for (const [lang, [settings, level]] of Object.entries(UI)) {
                S.setPref("uiLang", lang);
                assert.equal(Decks.t("Настройки"), settings);
                assert.equal(D.category("L1"), level);
                assert.equal(Decks.current().id, deck.id, "the interface language keeps the deck");
                runs[lang] = run(deck);
                runs[lang].counts = JSON.stringify([...R.counts()].sort());
            }
        } finally {
            S.setPref("uiLang", "ru");
        }

        const shown = runs.ru;
        const words = shown.filter(w => !R.isArticle(w));
        for (const w of words) {
            assert.match(bare(w.cyr), ALPHABET[deck.target], `${w.key}: not in ${deck.target}`);
            assert.ok(w.meanings.length, `${w.key}: no translation`);
            for (const e of w.examples) assert.ok(e.text && e.tr, `${w.key}: an example without two sides`);
        }
        assert.match(words.map(w => w.meanings.join(" ")).join(" "), ONLY[deck.native], `no translations into ${deck.native}`);
        assert.ok(shown.some(w => w.pos === "compare") && shown.some(w => w.pos === "note"), "no articles came up");
        assert.equal(shown.some(w => w.pos === "phrase"), deck.target === "sr", "phrases are in the Serbian decks only");

        for (const lang of ["sr", "en"]) {
            assert.deepEqual(runs[lang].map(w => w.key), shown.map(w => w.key), `the ${lang} interface picked other cards`);
            assert.equal(runs[lang].counts, runs.ru.counts, `the ${lang} interface counted other cards`);
        }
    });

    test(`${deck.id}: each level and section starts with a card of its own`, async () => {
        await R.switchDeck(deck.id);
        R.resetAll();
        S.setPrefs({ activeSize: 20, cardCount: 0 });
        const counts = R.counts();
        const ids = [...levels(R.words).map(l => `L${l}`), ...["X:phrase", "X:compare", "X:note"].filter(id => counts.has(id))];
        assert.deepEqual(levels(R.words), [1, 2, 3, 4, 5]);
        for (const id of ids) {
            assert.ok(counts.get(id)[0] > 0, id);
            R.setCategories([id], 0);
            R.step(1);
            const w = R.current();
            assert.ok(w, `${deck.id} ${id}: no first card`);
            assert.ok(R.inCategory(w, id), `${deck.id} ${id}: ${w.key}`);
        }
        R.setCategories(["L1"], 0);
        R.step(1);
        assert.equal(R.current().key, R.words.find(w => w.level === 1).key, "by frequency the first card of the level comes first");
    });
}
