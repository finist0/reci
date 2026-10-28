// Runs tests/learning-scenarios.json against web/js/repo.js; LearningScenariosTest.kt runs the
// same file against Repo.kt, so the two implementations cannot drift apart unnoticed.
// Usage: node --test web/tests/

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const WEB = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const scenarios = JSON.parse(readFileSync(resolve(WEB, "../tests/learning-scenarios.json"), "utf-8"));

// The browser pieces the modules touch.
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
await R.load();

const DAY = 24 * 60 * 60 * 1000;
const phases = { new: 0, intro: R.INTRO, recall: R.RECALL, learned: R.LEARNED };
const states = { new: S.NEW, dontknow: S.DONT_KNOW, known: S.KNOWN };

for (const sc of scenarios) {
    test(sc.name, () => run(sc));
}

function run(sc) {
    let clock = Date.UTC(2026, 0, 1);
    R.clock.now = () => clock;
    const vars = {};
    let distance = -1;
    let collected = [];

    R.resetAll();
    const s = sc.settings ?? {};
    S.setPrefs({ activeSize: s.activeSize ?? 20, order: s.order ?? 0, reviewKnown: s.reviewKnown ?? true, cardMode: 0, cardCount: 0 });
    R.setCategories(s.categories ?? ["L3"], s.order ?? 0);

    const current = () => R.current() ?? assert.fail("no card on screen");
    const kind = w => (R.isArticle(w) ? "article" : w.pos === "phrase" ? "phrase" : "word");
    const word = name => R.word(vars[name]?.key) ?? assert.fail(`no word ${name}`);

    // What the card screen does after a step: the card on screen is counted as shown.
    const move = delta => {
        clock += 1000;
        R.step(delta);
        const w = R.current();
        if (w) R.markShown(w);
    };
    const answer = a => {
        const w = current();
        if (a === "know" || a === "hide") R.answer(w, S.KNOWN);
        else if (a === "dontknow" || a === "later") R.answer(w, S.DONT_KNOW);
        else if (a === "long") R.answer(w, S.KNOWN, true);
        else throw new Error(`unknown answer ${a}`);
    };
    // Answers the card on screen the way a skim or an until step asks for, unless it already has
    // an answer: a card is answered once, as a person would.
    const answerAll = step => {
        const w = current();
        if (R.answerOf(w) !== null) return;
        const policy = R.isArticle(w) ? step.articles ?? "none" : step.answer ?? "none";
        if (policy !== "none") answer(policy);
    };

    const steps = {
        next: () => move(1),
        prev: () => move(-1),
        answer: st => answer(st.a),
        remember: st => (vars[st.as] = { key: current().key, n: S.prefs.cardCount }),
        fav: () => R.toggleFav(current()),
        advanceDays: st => (clock += st.days * DAY),
        set: st => S.setPrefs({ activeSize: st.activeSize }),
        showAgain: () => R.markShown(current()),
        showArticlesAgain: st => R.showArticlesAgain(st.pos),
        pick: st => {
            const pool = R.words.filter(w => w.level === st.level);
            const coll = new Intl.Collator("sr");
            const w = st.by === "alpha" ? [...pool].sort((a, b) => coll.compare(a.cyr, b.cyr))[0] : pool[0];
            vars[st.as] = { key: w.key, n: 0 };
        },
        skim: st => {
            const seen = [];
            for (let i = 0; i < st.count; i++) {
                answerAll(st);
                move(1);
                const w = current();
                seen.push([S.prefs.cardCount, w.key, R.isArticle(w) ? w.pos : kind(w)]);
            }
            if (st.collect) collected = seen;
        },
        until: st => {
            const target = vars[st.word];
            for (let i = 0; i < st.max; i++) {
                answerAll(st);
                move(1);
                if (current().key === target.key) {
                    distance = S.prefs.cardCount - target.n;
                    return;
                }
            }
            assert.fail(`${st.word} did not come back within ${st.max} cards`);
        },
        untilArticle: st => {
            for (let i = 0; i < st.max; i++) {
                answerAll(st);
                move(1);
                if (R.isArticle(current())) return;
            }
            assert.fail(`no article within ${st.max} cards`);
        },
    };

    const expect = e => {
        if ("word" in e) {
            const w = word(e.word);
            const p = R.progress(w);
            if ("phase" in e) assert.equal(p.phase, phases[e.phase], `phase of ${w.key}`);
            if ("streak" in e) assert.equal(p.streak, e.streak, `streak of ${w.key}`);
            if ("state" in e) assert.equal(p.state, states[e.state], `state of ${w.key}`);
            if ("fav" in e) assert.equal(p.fav, e.fav, `fav of ${w.key}`);
            if ("seen" in e) assert.equal(p.seen, e.seen, `times ${w.key} was shown`);
            if ("dueInDays" in e) {
                if (e.dueInDays === null) assert.equal(p.due, 0, `no check for ${w.key}`);
                else assert.equal(Math.round((p.due - clock) / DAY), e.dueInDays, `days to check of ${w.key}`);
            }
        }
        if ("current" in e) assert.equal(current().key, vars[e.current]?.key);
        if ("currentNot" in e) for (const n of e.currentNot) assert.notEqual(current().key, vars[n]?.key);
        if ("distance" in e) assert.equal(distance, e.distance, "cards until it came back");
        if ("cardNumber" in e) assert.equal(S.prefs.cardCount, e.cardNumber, "card number");
        if ("activeCount" in e) {
            const n = R.words.filter(w => [R.INTRO, R.RECALL].includes(R.progress(w).phase)).length;
            assert.equal(n, e.activeCount, "active words");
        }
        if ("distinctWords" in e) {
            const n = new Set(collected.filter(c => c[2] !== "compare" && c[2] !== "note").map(c => c[1])).size;
            assert.equal(n, e.distinctWords, "different words");
        }
        if ("hidden" in e) for (const [pos, n] of Object.entries(e.hidden)) assert.equal(R.hiddenArticles(pos), n, `hidden ${pos}`);
        if ("articleSlots" in e) {
            for (const [n, , k] of collected) assert.equal(k === "compare" || k === "note", n % 5 === 4, `card ${n} is an article`);
        }
        if ("articlesAlternate" in e) {
            const kinds = collected.map(c => c[2]).filter(k => k === "compare" || k === "note");
            assert.ok(kinds.length >= 4, `too few articles: ${kinds}`);
            kinds.slice(1).forEach((k, i) => assert.notEqual(k, kinds[i], `articles do not alternate: ${kinds}`));
        }
        if ("kindsAt" in e) {
            for (const [n, k] of Object.entries(e.kindsAt)) assert.equal(collected.find(c => c[0] === Number(n))?.[2], k, `kind of card ${n}`);
        }
        if ("keyAt" in e) {
            for (const [n, v] of Object.entries(e.keyAt)) assert.equal(collected.find(c => c[0] === Number(n))?.[1], vars[v]?.key, `card ${n}`);
        }
        if ("notSeen" in e) assert.ok(!collected.some(c => c[1] === vars[e.notSeen]?.key), `${e.notSeen} came back`);
    };

    sc.steps.forEach((st, i) => {
        try {
            if (st.expect) expect(st.expect);
            else (steps[st.do] ?? (() => { throw new Error(`unknown step ${st.do}`); }))(st);
        } catch (err) {
            err.message = `step ${i + 1} ${JSON.stringify(st)}: ${err.message}`;
            throw err;
        }
    });
}
