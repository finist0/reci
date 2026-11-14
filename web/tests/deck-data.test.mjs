// Every card of every shipped deck: the side learned in the language learned, the translations in
// the deck's native language, the grammar labels of that language.

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ASSETS = resolve(dirname(fileURLToPath(import.meta.url)), "../../app/app/src/main/assets");
const read = name => JSON.parse(readFileSync(resolve(ASSETS, name), "utf-8"));
const decks = read("decks.json");

const SERBIAN = new Set("абвгдђежзијклљмнњопрстћуфхцчџш");
const RUSSIAN = new Set("абвгдеёжзийклмнопрстуфхцчшщъыьэюя");
const LATIN = /\p{Script=Latin}/u;
const alphabet = {
    sr: ch => SERBIAN.has(ch),
    ru: ch => RUSSIAN.has(ch),
    en: ch => LATIN.test(ch),
};

const GRAM = {
    ru: ["м. р.", "ж. р.", "ср. р.", "мн.", "м. р. мн.", "ж. р. мн.", "ср. р. мн.", "несов.", "сов.", "двувид."],
    sr: ["м. р.", "ж. р.", "с. р.", "мн.", "несвр.", "свр.", "двовид."],
    en: ["m.", "f.", "n.", "pl.", "m. pl.", "f. pl.", "n. pl.", "impf.", "pf.", "impf./pf."],
};

// Serbian names and dishes that the Russian translation keeps for want of a Russian word.
const KEPT = {
    "sr-ru": ["бурегџиница", "ћевабџиница", "печењара", "прозвати", "phr:једно возило у", "phr:срећан васкрс",
        "phr:пијан као земља"],
};

// Sayings that read the same in both languages.
const SAME = { "sr-ru": ["мутити"], "ru-sr": ["око"] };

// Notes on the origin of words and on the alphabet quote other scripts.
const QUOTES = { "note:bre-origin": "βρε", "note:vuk-reform": "ѣ" };

// Stress marks of both languages; ё and й keep theirs, as they are letters of their own.
const STRESS = /[\u0300\u0301\u0304\u030f\u0311]/g;
const letters = s => [...s.normalize("NFD").replace(STRESS, "").normalize("NFC").toLowerCase()].filter(ch => /\p{L}/u.test(ch));
const bare = s => letters(s).join("");
const foreign = (s, ok) => [...new Set(letters(s).filter(ch => !ok(ch)))].join("");

// A translation may quote words of the language learned; a meaning may start with one: "љутити се - сердиться".
const unquoted = (s, native, field) =>
    (native === "en" ? s.replace(/«[^»]*»/g, "") : s.replace(/«[^»]*»|\([^)]*\)/g, "").replace(field === "meanings" ? /^.* - / : /^$/, ""));

function problemsOf({ id, target: t, native: n }, cards) {
    const problems = [];
    const report = (c, field, text, what) => problems.push(`${c.key} ${field}: ${what} in ${JSON.stringify(text)}`);
    const learned = (c, field, s) => {
        const bad = foreign(s, alphabet[t]);
        if (bad) report(c, field, s, bad);
    };
    // Notes, tips and articles talk about the language learned, in any script.
    const translated = (c, field, s, free) => {
        let bad = free || KEPT[id]?.includes(c.key) ? "" : foreign(unquoted(s, n, field), alphabet[n]);
        bad ||= foreign(s, ch => alphabet[n](ch) || alphabet[t](ch) || (free && (LATIN.test(ch) || QUOTES[c.key]?.includes(ch))));
        if (bad) report(c, field, s, bad);
    };
    for (const c of cards) {
        const article = c.pos === "compare" || c.pos === "note";
        if (c.pos === "note") translated(c, "title", c.cyr, true);
        else learned(c, "cyr", c.cyr);
        if (c.acc) learned(c, "acc", c.acc);
        for (const f of ["syn", "ant", "synAcc", "antAcc"]) for (const s of c[f] ?? []) learned(c, f, s);
        if (c.pos !== "note" && !c.meanings.length) report(c, "meanings", c.meanings, "none");
        for (const m of c.meanings) translated(c, "meanings", m, article);
        for (const e of c.examples) {
            if (Object.keys(e).sort().join() !== [t, n].sort().join() || !e[t].trim() || !e[n].trim()) report(c, "examples", e, "not two sides");
            else {
                learned(c, `examples.${t}`, e[t]);
                translated(c, `examples.${n}`, e[n], article);
                if (bare(e[t]) === bare(e[n]) && !SAME[id]?.includes(c.key)) report(c, "examples", e, "not translated");
            }
        }
        for (const r of c.related) {
            if (Object.keys(r).filter(k => k !== "acc").sort().join() !== [t, n].sort().join() || !r[t] || !r[n]) report(c, "related", r, "not two sides");
            else {
                learned(c, `related.${t}`, r[t]);
                if (r.acc) learned(c, "related.acc", r.acc);
                translated(c, `related.${n}`, r[n], article);
            }
        }
        translated(c, "note", c.note, true);
        for (const s of c.tips ?? []) translated(c, "tips", s, true);
        if (c.gram && !GRAM[n].includes(c.gram)) report(c, "gram", c.gram, `not a label of ${n}`);
        if (c.ruGram && !GRAM.ru.includes(c.ruGram)) report(c, "ruGram", c.ruGram, "not a Russian label");
        if (c.ruNoun && foreign(c.ruNoun, alphabet.ru)) report(c, "ruNoun", c.ruNoun, foreign(c.ruNoun, alphabet.ru));
        // Forms are words of the language learned with labels in the native one.
        const bad = foreign(c.forms, ch => alphabet[t](ch) || alphabet[n](ch));
        if (bad) report(c, "forms", c.forms, bad);
    }
    return problems;
}

test("the decks are the four pairs the settings choose from", () => {
    assert.deepEqual(decks.map(d => `${d.id} ${d.target} ${d.native}`), ["sr-ru sr ru", "ru-sr ru sr", "sr-en sr en", "ru-en ru en"]);
});

for (const deck of decks) {
    test(`${deck.id}: every card is in ${deck.target} with translations into ${deck.native}`, () => {
        const cards = read(deck.words);
        assert.ok(cards.length > 1000, `${deck.words} has ${cards.length} cards`);
        assert.deepEqual(problemsOf(deck, cards), []);
    });
}

test("a card in the wrong language is caught", () => {
    const [srRu, ruSr, srEn, ruEn] = decks;
    const card = (over, ex = { sr: "Наша [кућа] је близу мора.", ru: "Наш дом недалеко от моря." }) => ({
        key: "кућа", cyr: "кућа", acc: "ку̏ћа", pos: "noun", gram: "ж. р.", forms: "мн. куће", meanings: ["дом"],
        examples: [ex], syn: [], ant: [], related: [{ sr: "кућни", ru: "домашний", acc: "ку̏ћнӣ" }], note: "", ...over,
    });
    assert.deepEqual(problemsOf(srRu, [card({})]), []);
    const caught = (deck, c) => assert.equal(problemsOf(deck, [c]).length, 1, JSON.stringify(c));
    caught(srRu, card({ cyr: "kuća" }));
    caught(srRu, card({ syn: ["дом (жильё)"] }));
    caught(srRu, card({ meanings: ["кућа"] }));
    caught(srRu, card({}, { sr: "Наша [кућа] је близу мора.", ru: "Наша кућа је велика." }));
    caught(srRu, card({}, { sr: "Боли ме [стомак].", ru: "Боли ме стомак." }));
    caught(srRu, card({}, { sr: "Наша [кућа] је близу мора.", en: "Our house is near the sea." }));
    caught(srRu, card({}, { sr: "Наша [кућа] је близу мора.", ru: "Наш дом недалеко от моря.", en: "Our house." }));
    caught(srRu, card({ gram: "f." }));
    caught(srRu, card({ related: [{ sr: "кућни", ru: "home" }] }));
    assert.deepEqual(problemsOf(srRu, [card({ meanings: ["дом (кућа)", "љутити се - сердиться"], note: "«кућа» - дом, house" })]), []);

    const en = (over, ex = { sr: "Наша [кућа] је близу мора.", en: "Our house is near the sea." }) =>
        card({ gram: "f.", meanings: ["house"], related: [], ...over }, ex);
    assert.deepEqual(problemsOf(srEn, [en({ meanings: ["home (in «идем кући»)"], note: "кућа is a house" })]), []);
    caught(srEn, en({ meanings: ["кућа"] }));
    caught(srEn, en({ note: "a house, «дом» in Russian: дом, жильё" }));
    caught(srEn, en({}, { sr: "Наша [кућа] је близу мора.", en: "Наш дом недалеко от моря." }));
    caught(ruEn, { ...en({ cyr: "дом", acc: "дом", forms: "" }, { ru: "Наш [дом] у моря.", en: "Our house is by the sea." }), note: "like кућа in Serbian" });

    const ru = (over, ex = { ru: "Наш [дом] у моря.", sr: "Наша кућа је близу мора." }) =>
        card({ key: "дом", cyr: "дом", acc: "дом", gram: "м. р.", forms: "мн. дома́", meanings: ["кућа"], related: [], ...over }, ex);
    assert.deepEqual(problemsOf(ruSr, [ru({})]), []);
    caught(ruSr, ru({ meanings: ["синьор"] }));
    caught(ruSr, ru({ cyr: "ђом" }));
    caught(ruSr, ru({}, { ru: "Наш [дом] у моря.", sr: "Не крий од мене истину." }));
});
