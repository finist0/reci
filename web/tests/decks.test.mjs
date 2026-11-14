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
const browser = { language: "ru-RU", languages: ["ru-RU"] };
Object.defineProperty(globalThis, "navigator", { configurable: true, value: browser });
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
        assert.equal(Decks.name("ru"), "Руски");
        S.setPref("latinFirst", true);
        assert.equal(t("Настройки"), "Podešavanja");
        assert.equal(Decks.name("ru"), "Ruski");
        S.setPref("latinFirst", false);
    });
    assert.equal(Decks.name("sr"), "Сербский");
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

test("a first start takes the interface language from the browser; an earlier user keeps the deck's", () => {
    assert.equal(Decks.guessUi(["ru-RU"]), "ru");
    assert.equal(Decks.guessUi(["sr-Latn-RS"]), "sr");
    assert.equal(Decks.guessUi(["hr-HR"]), "sr");
    assert.equal(Decks.guessUi(["de-DE"]), "en");

    const ui = JSON.parse(readFileSync(resolve(WEB, R.ASSETS, "ui.json"), "utf-8"));
    const start = (lang, stored = "") => {
        browser.languages = [lang];
        S.setPref("uiLang", stored);
        Decks.init(Decks.all, ui);
        return S.prefs.uiLang;
    };
    try {
        R.resetAll();
        S.setPref("currentKey", null);
        assert.equal(start("ru-RU"), "ru");
        assert.equal(start("sr-RS"), "sr");
        assert.equal(start("hr-HR"), "sr");
        assert.equal(start("de-DE"), "en");
        assert.equal(t("Настройки"), "Settings");
        assert.equal(start("ru-RU", "en"), "en", "the stored language stays");
        S.setPref("currentKey", "бити");
        assert.equal(start("en-US"), "ru", "an earlier user keeps the interface of the deck");
    } finally {
        browser.languages = ["ru-RU"];
        S.setPref("currentKey", null);
        S.setPref("uiLang", "ru");
    }
});

test("the interface language is chosen apart from the deck and stays over a deck switch", async () => {
    assert.equal(S.prefs.uiLang, "ru");
    S.setPref("uiLang", "en");
    assert.equal(t("Настройки"), "Settings");
    assert.equal(plural(5, "карточку", "карточки", "карточек"), "cards");
    // The cards stay in the language of the deck: the search of translations asks for Russian.
    assert.equal(Decks.meaningHint(), "word in Russian");

    await R.switchDeck("ru-sr");
    assert.equal(t("Настройки"), "Settings");
    assert.equal(S.prefs.uiLang, "en");
    assert.equal(Decks.meaningHint(), "word in Serbian");
    await R.switchDeck("sr-ru");

    S.setPref("uiLang", "sr");
    assert.equal(inLanguage("en", () => t("Настройки")), "Settings");
    assert.equal(t("Настройки"), "Подешавања");
    assert.equal(Decks.lang(), "sr");
    S.setPref("uiLang", "ru");
});

// What the settings do on a tap of the language learned.
const learn = target => R.switchDeck(Decks.pick(target, Decks.current().native).id);

test("the language learned picks the deck, and each deck keeps its progress", async () => {
    assert.deepEqual(Decks.targets(), ["sr", "ru"]);
    assert.deepEqual(Decks.natives("sr"), ["ru"]);
    assert.deepEqual(Decks.natives("ru"), ["sr"]);
    R.resetAll();
    R.step(1);
    const sr = R.current();
    R.answer(sr, S.KNOWN);

    await learn("ru");
    assert.equal(Decks.current(), ruSr);
    R.step(1);
    const ru = R.current();
    R.answer(ru, S.DONT_KNOW);

    await learn("sr");
    assert.equal(Decks.current(), srRu);
    assert.equal(R.word("хотеть"), undefined);
    assert.equal(R.progress(sr).phase, R.RECALL);
    await learn("ru");
    assert.equal(R.progress(ru).state, S.DONT_KNOW);
    await learn("sr");
});

test("the translations follow the language learned and stay where a deck has them", () => {
    const decks = Decks.all;
    const ui = JSON.parse(readFileSync(resolve(WEB, R.ASSETS, "ui.json"), "utf-8"));
    const srEn = { id: "sr-en", target: "sr", native: "en", words: "words.json", grammar: "" };
    Decks.init([srRu, ruSr, srEn], ui);
    try {
        assert.deepEqual(Decks.targets(), ["sr", "ru"]);
        assert.deepEqual(Decks.natives("sr"), ["ru", "en"]);
        assert.deepEqual(Decks.natives("ru"), ["sr"]);
        assert.equal(Decks.pick("sr", "en"), srEn, "English translations stay");
        assert.equal(Decks.pick("sr", "ru"), srRu);
        assert.equal(Decks.pick("sr", "sr"), srRu, "no Serbian deck translates into Serbian");
        assert.equal(Decks.pick("ru", "en"), ruSr, "no Russian deck translates into English");
        assert.equal(inLanguage("en", () => Decks.name("en")), "English");
    } finally {
        Decks.init(decks, ui);
    }
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
    assert.equal(inLanguage("sr", Decks.howToSay), "Kako se kaže na ruskom");
    await R.switchDeck("sr-ru");
    S.setPref("latinFirst", false);
});
