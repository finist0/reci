// The daily statistics and the message queue of web/js/telemetry.js, with fetch faked.
// Usage: node --test web/tests/*.test.mjs

import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const WEB = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const fields = JSON.parse(readFileSync(resolve(WEB, "../tests/telemetry.json"), "utf-8"));

const storage = new Map();
globalThis.localStorage = {
    getItem: k => storage.get(k) ?? null,
    setItem: (k, v) => storage.set(k, String(v)),
    removeItem: k => storage.delete(k),
};
globalThis.document = { addEventListener() {}, visibilityState: "visible" };
const ANDROID_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36";
Object.defineProperty(globalThis, "navigator", {
    configurable: true,
    value: { userAgent: ANDROID_UA, language: "ru-RS", maxTouchPoints: 5 },
});

const S = await import("../js/store.js");
const T = await import("../js/telemetry.js");

let requests, reply;
globalThis.fetch = async (url, init) => {
    requests.push({ url, body: JSON.parse(init.body) });
    const r = await reply(url);
    if (r instanceof Error) throw r;
    return { ok: r >= 200 && r < 300, status: r };
};

const NOON = new Date(2026, 10, 2, 12).getTime();
const HOUR = 60 * 60 * 1000;

beforeEach(() => {
    requests = [];
    reply = () => 204;
    Object.assign(T.state, {
        enabled: true, noticeShown: true, noticeClosed: true, sentDay: "", triedAt: 0, counts: {}, last: null,
        email: "", queue: [],
    });
});

const allowed = (obj, list) => Object.keys(obj).filter(k => !list.includes(k));

test("a new install has two different random ids and statistics on", () => {
    const ids = JSON.parse(storage.get("reci.telemetry"));
    assert.match(ids.id, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    assert.notEqual(ids.id, ids.feedbackId);
    assert.equal(ids.enabled, true);
    assert.equal(ids.noticeShown, false);
});

test("the ping carries only the fields the server accepts", async () => {
    T.count("cards");
    T.count("answers", "know");
    T.count("opened", "search");
    assert.equal(await T.sendPing("0.12", 7, NOON), true);
    const p = requests[0].body;
    assert.match(requests[0].url, /\/ping$/);
    assert.deepEqual(allowed(p, [...fields.ping.required, ...fields.ping.optional]), []);
    for (const k of fields.ping.required) assert.ok(p[k], k);
    assert.deepEqual(allowed(p.answers, fields.answers), []);
    assert.equal(p.day, "2026-11-02");
    assert.equal(p.platform, "web-android");
    assert.equal(p.locale, "ru-RS");
    assert.deepEqual([p.cards, p.answers, p.opened, p.learned], [1, { know: 1 }, { search: 1 }, 7]);
    assert.deepEqual(p.cats, S.prefs.categories);
    assert.equal(p.lock, undefined);
});

test("one ping a day, and the sums start again after it", async () => {
    T.count("cards");
    assert.equal(await T.sendPing("0.12", 0, NOON), true);
    assert.deepEqual(T.state.counts, {});
    T.count("cards");
    assert.equal(await T.sendPing("0.12", 0, NOON + 2 * HOUR), false);
    assert.equal(requests.length, 1);
    assert.equal(await T.sendPing("0.12", 0, NOON + 24 * HOUR), true);
    assert.equal(requests[1].body.day, "2026-11-03");
    assert.equal(requests[1].body.cards, 1);
});

test("nothing is sent while statistics are off, and nothing collected then is sent later", async () => {
    T.setEnabled(false);
    T.count("cards");
    assert.equal(await T.sendPing("0.12", 0, NOON), false);
    assert.equal(requests.length, 0);
    T.setEnabled(true);
    await T.sendPing("0.12", 0, NOON);
    assert.equal(requests[0].body.cards, undefined);
});

test("nothing is sent before the notice about statistics was shown", async () => {
    T.state.noticeShown = false;
    assert.equal(await T.sendPing("0.12", 0, NOON), false);
    T.noticeShown();
    assert.equal(await T.sendPing("0.12", 0, NOON), true);
});

test("without a known app version there is no ping", async () => {
    assert.equal(await T.sendPing("", 0, NOON), false);
    assert.equal(requests.length, 0);
});

test("a failed ping keeps the sums and waits an hour", async () => {
    reply = () => new Error("offline");
    T.count("cards");
    assert.equal(await T.sendPing("0.12", 0, NOON), false);
    assert.equal(T.state.counts.cards, 1);
    reply = () => 204;
    assert.equal(await T.sendPing("0.12", 0, NOON + HOUR / 2), false);
    assert.equal(requests.length, 1);
    assert.equal(await T.sendPing("0.12", 0, NOON + HOUR), true);
    assert.equal(requests[1].body.cards, 1);
});

test("counts made while a ping is on its way are kept", async () => {
    T.count("cards");
    reply = () => {
        T.count("cards");
        T.count("answers", "know");
        return 204;
    };
    assert.equal(await T.sendPing("0.12", 0, NOON), true);
    assert.equal(requests[0].body.cards, 1);
    assert.deepEqual(T.state.counts, { cards: 1, answers: { know: 1 } });
});

test("a ping hitting the daily limit is tried again later", async () => {
    reply = () => 503;
    assert.equal(await T.sendPing("0.12", 0, NOON), false);
    assert.equal(T.state.sentDay, "");
});

test("a rejected ping is not sent again that day", async () => {
    reply = () => 400;
    T.count("cards");
    assert.equal(await T.sendPing("0.12", 0, NOON), false);
    assert.equal(T.state.sentDay, "2026-11-02");
    assert.deepEqual(T.state.counts, {});
});

test("the operating system", async () => {
    const hints = v => ({ getHighEntropyValues: async () => ({ platformVersion: v }) });
    assert.equal(await T.osName(ANDROID_UA, hints("15.0.0")), "Android 15");
    assert.equal(await T.osName(ANDROID_UA, undefined), "Android 10");
    assert.equal(await T.osName("Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15", undefined), "iOS 18.6");
    assert.equal(await T.osName("Mozilla/5.0 (Windows NT 10.0; Win64; x64)", undefined), "Windows");
    assert.equal(await T.osName("Mozilla/5.0 (X11; Linux x86_64)", undefined), "Linux");
    assert.equal(await T.osName("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)", undefined), "macOS");
});

test("the platform", () => {
    assert.equal(T.platform("Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X)", 5), "web-ios");
    assert.equal(T.platform("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)", 5), "web-ios");
    assert.equal(T.platform("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)", 0), "web-desktop");
    assert.equal(T.platform(ANDROID_UA, 5), "web-android");
    assert.equal(T.platform("Mozilla/5.0 (Windows NT 10.0; Win64; x64)", 0), "web-desktop");
});

test("a message goes out with its own id and only the accepted fields", async () => {
    const card = { key: "бити", word: "бити", problem: "stress", latin: false, stress: 1 };
    assert.equal(await T.sendFeedback({ kind: "card", text: "", card }, "me@example.org", "0.12"), true);
    const m = requests[0].body;
    assert.match(requests[0].url, /\/feedback$/);
    assert.deepEqual(allowed(m, [...fields.feedback.required, ...fields.feedback.optional]), []);
    assert.deepEqual(allowed(m.card, fields.feedback.card), []);
    assert.equal(m.id, T.state.feedbackId);
    assert.notEqual(m.id, T.state.id);
    assert.equal(m.text, undefined);
    assert.equal(m.email, "me@example.org");
    assert.equal(T.state.email, "me@example.org");
    assert.deepEqual(T.state.queue, []);
});

test("without network a message waits and goes out later", async () => {
    reply = () => new Error("offline");
    assert.equal(await T.sendFeedback({ kind: "app", text: "Привет" }, "", "0.12"), false);
    assert.equal(T.state.queue.length, 1);
    assert.equal(JSON.parse(storage.get("reci.telemetry")).queue.length, 1);
    reply = () => 204;
    await T.flush();
    assert.deepEqual(T.state.queue, []);
    assert.equal(requests.length, 2);
});

test("a message over the limit waits, a rejected one is dropped", async () => {
    reply = () => 429;
    await T.sendFeedback({ kind: "app", text: "один" }, "", "0.12");
    assert.equal(T.state.queue.length, 1);
    reply = () => 400;
    await T.flush();
    assert.deepEqual(T.state.queue, []);
});

test("messages go out in order", async () => {
    reply = () => new Error("offline");
    await T.sendFeedback({ kind: "app", text: "первое" }, "", "0.12");
    await T.sendFeedback({ kind: "app", text: "второе" }, "", "0.12");
    requests = [];
    reply = () => 204;
    await T.flush();
    assert.deepEqual(requests.map(r => r.body.text), ["первое", "второе"]);
});

test("control characters are dropped from a message", async () => {
    await T.sendFeedback({ kind: "app", text: "a\u000cb\nc\u0000" }, "", "0.12");
    assert.equal(requests[0].body.text, "ab\nc");
});

test("a message sent while an earlier one is on its way goes out too", async () => {
    let release;
    const gate = new Promise(r => (release = r));
    reply = () => gate.then(() => 204);
    const first = T.sendFeedback({ kind: "app", text: "первое" }, "", "0.12");
    await new Promise(r => setTimeout(r, 10));
    const second = T.sendFeedback({ kind: "app", text: "второе" }, "", "0.12");
    release();
    assert.deepEqual([await first, await second], [true, true]);
    assert.deepEqual(requests.map(r => r.body.text), ["первое", "второе"]);
});

test("messages are sent with statistics off", async () => {
    T.setEnabled(false);
    assert.equal(await T.sendFeedback({ kind: "app", text: "Привет" }, "", "0.12"), true);
});

test("the email check matches the server", () => {
    for (const ok of ["a@b.cd", "me.name+tag@mail.example.org"]) assert.ok(T.isEmail(ok), ok);
    for (const bad of ["", "a@b", "a b@c.de", "@c.de", "a@.de", "<a>@c.de"]) assert.ok(!T.isEmail(bad), bad);
});

test("the progress file carries no statistics ids", () => {
    const text = JSON.stringify(S.exportData());
    assert.ok(!text.includes(T.state.id));
    assert.ok(!text.includes(T.state.feedbackId));
});
