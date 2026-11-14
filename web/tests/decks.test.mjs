// Language pairs: each deck its own cards and progress, the interface in the deck's native
// language, cards read by the deck's languages. The counterpart of DeckTest.kt.

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
globalThis.fetch = async url => {
    const text = readFileSync(resolve(WEB, url), "utf-8");
    return { ok: true, json: async () => JSON.parse(text), text: async () => text };
};

const R = await import("../js/repo.js");
const S = await import("../js/store.js");
const Decks = await import("../js/decks.js");
const D = await import("../js/data.js");
const { t, plural } = Decks;
await R.load();

const srRu = Decks.all.find(d => d.id === "sr-ru");
const ruSr = Decks.all.find(d => d.id === "ru-sr");

function inLanguage(l, f) {
    Decks.i18n.override = l;
    try {
        return f();
    } finally {
        Decks.i18n.override = null;
    }
}

test("a first start learns Serbian, unless the browser speaks Serbian or a close language", () => {
    assert.equal(Decks.current().id, "sr-ru");
    for (const l of ["sr-RS", "sr-Latn", "hr", "bs-BA", "mk", "sl", "cnr"]) assert.equal(Decks.guess([l]), "ru-sr", l);
    for (const l of ["ru-RU", "en-US", "de", ""]) assert.equal(Decks.guess([l]), "sr-ru", l);
});

test("switching decks brings the other cards and keeps the progress of each apart", async () => {
    R.resetAll();
    R.step(1);
    const sr = R.current();
    R.answer(sr, S.KNOWN);
    assert.equal(R.progress(sr).phase, R.RECALL);

    await R.switchDeck("ru-sr");
    assert.equal(Decks.current(), ruSr);
    assert.equal(S.prefs.currentKey, null);
    assert.equal(S.hasProgress(), false);
    assert.ok(R.word("хотеть"), "no Russian card");
    assert.equal(R.word("бити"), undefined);
    R.step(1);
    const ru = R.current();
    R.answer(ru, S.DONT_KNOW);
    assert.equal(R.progress(ru).state, S.DONT_KNOW);

    await R.switchDeck("sr-ru");
    assert.equal(S.prefs.currentKey, sr.key);
    assert.equal(R.current().cyr, sr.cyr);
    assert.equal(R.progress(sr).phase, R.RECALL);
    assert.notEqual(S.getProgress(ru.key)?.state, S.DONT_KNOW);

    // The first deck keeps the names it had before there were others.
    assert.ok(JSON.parse(storage.get("reci.progress"))[sr.key]);
    assert.ok(JSON.parse(storage.get("ru-sr.reci.progress"))[ru.key]);
    const prefs = JSON.parse(storage.get("reci.prefs"));
    assert.equal(prefs.currentKey, sr.key);
    assert.equal(prefs["ru-sr.currentKey"], ru.key);
    assert.equal(prefs.deck, "sr-ru");
});

test("cards read examples and related words by the deck's languages", () => {
    const card = {
        key: "хотеть", cyr: "хотеть", acc: "хоте́ть", pos: "verb", meanings: ["хтети"],
        examples: [{ ru: "Я [хочу́] пить.", sr: "Хоћу да пијем." }],
        syn: ["желать"], synAcc: ["жела́ть"], ant: ["мочь"],
        related: [{ ru: "хотеться", sr: "хтети се", acc: "хоте́ться" }, { ru: "охота", sr: "жеља" }],
    };
    const w = R.parse(card, ruSr);
    assert.deepEqual(w.examples, [{ text: "Я [хочу́] пить.", tr: "Хоћу да пијем." }]);
    assert.deepEqual(w.related, [{ word: "хотеться", tr: "хтети се", acc: "хоте́ться" }, { word: "охота", tr: "жеља", acc: "" }]);
    assert.deepEqual(w.synAcc, ["жела́ть"]);
    assert.deepEqual(w.antAcc, []);
    assert.equal(w.note, "");

    const back = R.parse(card, srRu);
    assert.deepEqual(back.examples, [{ text: "Хоћу да пијем.", tr: "Я [хочу́] пить." }]);
    assert.equal(back.related[0].word, "хтети се");

    // The built-in Serbian dictionary reads the same way.
    const biti = R.word("бити");
    assert.ok(biti.examples.every(e => e.text && e.tr));
    assert.ok(biti.related.some(r => r.acc));
});

test("the Serbian interface, in either script", () => {
    inLanguage("sr", () => {
        assert.equal(t("Настройки"), "Подешавања");
        assert.equal(t("Учить %d %s", 3, plural(3, "карточку", "карточки", "карточек")), "Учи 3 картице");
        assert.equal(D.category("L1"), "A1 · Почетни");
        assert.equal(D.pos("noun"), "имен.");
        assert.equal(D.orders()[0], t("По частоте"));
        S.setPref("latinFirst", true);
        assert.equal(t("Настройки"), "Podešavanja");
        S.setPref("latinFirst", false);
    });
    assert.equal(Decks.title(ruSr), "Учим руски · превод на српски");
    assert.equal(Decks.title(srRu), "Учу сербский · перевод на русский");
});

test("the English interface", () => {
    inLanguage("en", () => {
        assert.equal(t("Настройки"), "Settings");
        assert.equal(t("Загружен прогресс по %d %s.", 1, plural(1, "слову", "словам", "словам")), "Loaded progress for 1 word.");
        assert.equal(D.category("M:known"), "Know");
    });
    assert.equal(t("Настройки"), "Настройки");
    // A string the table lacks shows as it is.
    assert.equal(inLanguage("en", () => t("Нет такой строки %d", 5)), "Нет такой строки 5");
});

test("the interface language is chosen apart from the deck and stays over a deck switch", async () => {
    assert.equal(S.prefs.uiLang, "");
    S.setPref("uiLang", "en");
    assert.equal(t("Настройки"), "Settings");
    assert.equal(plural(5, "карточку", "карточки", "карточек"), "cards");
    // The cards stay in the language of the deck: the search of translations asks for Russian.
    assert.equal(Decks.meaningHint(), "word in Russian");

    await R.switchDeck("ru-sr");
    assert.equal(t("Настройки"), "Settings");
    assert.equal(Decks.meaningHint(), "word in Serbian");
    S.setPref("uiLang", "");
    assert.equal(t("Настройки"), "Подешавања");
    await R.switchDeck("sr-ru");
    assert.equal(t("Настройки"), "Настройки");

    S.setPref("uiLang", "sr");
    assert.equal(inLanguage("en", () => t("Настройки")), "Settings");
    assert.equal(t("Настройки"), "Подешавања");
    assert.equal(Decks.lang(), "sr");
    S.setPref("uiLang", "");
});

test("plural forms in Russian, Serbian and English", () => {
    const forms = l => inLanguage(l, () => [1, 2, 5, 11, 12, 21, 22, 25, 111, 112].map(n => plural(n, "карточку", "карточки", "карточек")));
    assert.deepEqual(forms("ru"), ["карточку", "карточки", "карточек", "карточек", "карточек", "карточку", "карточки", "карточек", "карточек", "карточек"]);
    assert.deepEqual(forms("sr"), ["картицу", "картице", "картица", "картица", "картица", "картицу", "картице", "картица", "картица", "картица"]);
    assert.deepEqual(forms("en"), ["card", "cards", "cards", "cards", "cards", "cards", "cards", "cards", "cards", "cards"]);
});

test("the learned language and the native one each follow the script setting where they are Serbian", async () => {
    S.setPref("latinFirst", true);
    assert.equal(Decks.primary("реч"), "reč");
    assert.equal(Decks.native("реч"), "реч");
    await R.switchDeck("ru-sr");
    assert.equal(Decks.primary("слово"), "слово");
    assert.equal(Decks.native("реч"), "reč");
    assert.equal(Decks.howToSay(), "Kako se kaže na ruskom");
    await R.switchDeck("sr-ru");
    S.setPref("latinFirst", false);
});
