// The statistics notice and switch, reporting a card and writing to the developer, against a fake
// server. Usage: node --test "web/tests/ui/*.test.mjs"   (CHROME=/path/to/chrome if needed)

import { test, before, after, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { serve, launch } from "./chrome.mjs";

let server;
before(async () => (server = await serve()));
after(() => server.close());
beforeEach(() => {
    server.posts.length = 0;
    Object.assign(server.status, { ping: 204, feedback: 204 });
});

async function app(t, opts) {
    const page = await launch(opts);
    t.after(() => page.close());
    await page.open(server.url);
    return page;
}

const posts = kind => server.posts.filter(p => p.kind === kind).map(p => p.body);
const has = (page, sel) => page.eval(`!!document.querySelector(${JSON.stringify(sel)})`);
const currentKey = page => page.eval("import('./js/repo.js').then(R => R.current()?.key)");
const telemetry = page => page.eval("JSON.parse(localStorage.getItem('reci.telemetry'))");

async function waitPosts(page, kind, n = 1) {
    for (let i = 0; i < 100 && posts(kind).length < n; i++) await page.sleep(50);
    assert.equal(posts(kind).length, n, `${kind} posts`);
}

test("the first start tells about statistics and where to turn them off", async t => {
    const page = await app(t, { telemetry: {} });
    assert.match(await page.text(), /анонимную статистику[\s\S]*Настройки → «Анонимная статистика»/);
    await waitPosts(page, "ping");
    const p = posts("ping")[0];
    assert.ok(["web-ios", "web-android", "web-desktop"].includes(p.platform), p.platform);
    assert.equal(p.id, (await telemetry(page)).id);
    await page.tap('[data-act="closenotice"]');
    assert.ok(!(await has(page, ".notice")));
    await page.reload();
    assert.ok(!(await has(page, ".notice")));
    assert.equal(posts("ping").length, 1);
});

test("with statistics off nothing is sent", async t => {
    const page = await app(t, { telemetry: { noticeShown: true, noticeClosed: true, enabled: false } });
    await page.tap('[data-act="next"]');
    await page.sleep(500);
    assert.deepEqual(server.posts, []);
});

test("the statistics switch and what is sent", async t => {
    const page = await app(t);
    await waitPosts(page, "ping");
    await page.tap('[data-act="settings"]');
    assert.match(await page.text(), /Анонимная статистика[\s\S]*Последний раз: \d{4}-\d\d-\d\d/);
    await page.tap('[data-act="whatsent"]');
    const packet = await page.eval("document.querySelector('.packet').textContent");
    assert.equal(JSON.parse(packet).id, (await telemetry(page)).id);
    assert.ok(await has(page, 'a.policy[href="privacy.html"]'));
    await page.tap('[data-act="closeform"]');
    await page.tap('[data-act="stats"]');
    assert.equal((await telemetry(page)).enabled, false);
    assert.ok(!(await has(page, '[data-act="stats"] .switch.on')));
    await page.tap('[data-act="stats"]');
    assert.equal((await telemetry(page)).enabled, true);
});

test("a card is reported from the card itself, and the email is remembered", async t => {
    const page = await app(t);
    const key = await currentKey(page);
    await page.tap('[data-act="report"]');
    assert.match(await page.text(), /Что не так с карточкой\?/);
    assert.ok(await page.eval("document.querySelector('[data-act=sendreport]').disabled"));
    await page.tap('[data-act="problem"][data-arg="stress"]');
    await page.tap('[data-input="reporttext"]');
    await page.type("ударение на втором слоге");
    await page.tap('[data-input="reportemail"]');
    await page.type("me@example.org");
    await page.tap('[data-act="sendreport"]');
    await waitPosts(page, "feedback");
    const m = posts("feedback")[0];
    assert.deepEqual([m.kind, m.card.key, m.card.problem, m.text, m.email], ["card", key, "stress", "ударение на втором слоге", "me@example.org"]);
    assert.notEqual(m.id, (await telemetry(page)).id);
    await page.waitFor("document.querySelector('.toast')?.textContent.includes('Спасибо')");
    await page.tap('[data-act="report"]');
    assert.equal(await page.eval("document.querySelector('[data-input=reportemail]').value"), "me@example.org");
});

test("a mistyped email is caught before sending", async t => {
    const page = await app(t);
    await page.tap('[data-act="report"]');
    await page.tap('[data-act="problem"][data-arg="translation"]');
    await page.tap('[data-input="reportemail"]');
    await page.type("me@example");
    await page.tap('[data-act="sendreport"]');
    assert.match(await page.text(), /Проверьте адрес почты/);
    await page.sleep(300);
    assert.deepEqual(posts("feedback"), []);
});

test("an article offers the kinds of errors that fit it", async t => {
    const page = await app(t);
    await page.tap('[data-act="settings"]');
    await page.tap('[data-act="categories"]');
    for (const id of ["L3", "L4", "X:phrase", "X:note"]) await page.tap(`[data-act="toggle"][data-arg="${id}"]`);
    await page.tap('[data-act="learn"]');
    await page.tap('[data-act="back"]');
    assert.match(await currentKey(page), /^cmp:/);
    await page.tap('[data-act="report"]');
    const kinds = await page.eval("[...document.querySelectorAll('[data-act=problem]')].map(b => b.dataset.arg)");
    assert.deepEqual(kinds, ["text", "other"]);
});

test("a word found by search is reported from its screen", async t => {
    const page = await app(t);
    await page.tap('[data-act="search"]');
    await page.waitFor("document.activeElement?.dataset?.input === 'search'");
    await page.type("кућа");
    await page.tap(".row-word", "кућа");
    await page.waitFor("document.querySelector('.detail')");
    await page.tap('[data-act="report"]');
    await page.tap('[data-act="problem"][data-arg="example"]');
    await page.tap('[data-act="sendreport"]');
    await waitPosts(page, "feedback");
    assert.equal(posts("feedback")[0].card.word, "кућа");
});

test("without network a message waits and goes out when the network is back", async t => {
    const page = await app(t);
    await page.offline(true);
    await page.tap('[data-act="report"]');
    await page.tap('[data-act="problem"][data-arg="other"]');
    await page.tap('[data-act="sendreport"]');
    await page.waitFor("document.querySelector('.toast')?.textContent.includes('интернет')");
    await page.tap('[data-act="settings"]');
    assert.match(await page.text(), /Ждут отправки: 1/);
    await page.offline(false);
    await page.eval("window.dispatchEvent(new Event('online'))");
    await waitPosts(page, "feedback");
    await page.waitFor("JSON.parse(localStorage.getItem('reci.telemetry')).queue.length === 0");
});

test("writing to the developer from the settings", async t => {
    const page = await app(t);
    await page.tap('[data-act="settings"]');
    await page.tap('[data-act="write"]');
    await page.tap('[data-act="sendwrite"]');
    assert.match(await page.text(), /Напишите сообщение/);
    await page.tap('[data-input="writetext"]');
    await page.type("Добавьте, пожалуйста, глаголы движения");
    await page.tap('[data-act="sendwrite"]');
    await waitPosts(page, "feedback");
    const m = posts("feedback")[0];
    assert.deepEqual([m.kind, m.text, m.card, m.email], ["app", "Добавьте, пожалуйста, глаголы движения", undefined, undefined]);
});

test("the privacy policy opens without network", async t => {
    const page = await app(t);
    await page.waitFor("caches.open('reci').then(c => c.match('privacy.html'))");
});

test("statistics count shown cards and answers", async t => {
    const page = await app(t, { telemetry: { noticeShown: true, noticeClosed: true, sentDay: "2000-01-01", triedAt: 0 } });
    await waitPosts(page, "ping");
    await page.tap(".mark", "Знаю");
    await page.tap(".mark", "Знаю");
    await page.tap('[data-act="next"]');
    await page.tap(".mark", "Не знаю");
    await page.tap('[data-act="search"]');
    const counts = (await telemetry(page)).counts;
    assert.deepEqual(counts.answers, { know: 1, dontknow: 1 });
    assert.equal(counts.cards, 1);
    assert.deepEqual(counts.opened, { search: 1 });
});

test("help the author: share, rate and star", async t => {
    const page = await app(t);
    await page.tap('[data-act="settings"]');
    await page.tap('[data-act="help"]');
    assert.match(await page.text(), /Помочь автору[\s\S]*Поделиться с другом[\s\S]*Оценить приложение[\s\S]*Поставить звезду на GitHub/);
    assert.equal(await page.eval("document.querySelector('a[data-act=rate]').href"), "https://play.google.com/store/apps/details?id=org.kostja.reci");
    assert.equal(await page.eval("document.querySelector('a[data-act=star]').href"), "https://github.com/finist0/reci");
    await page.eval("navigator.share = d => { window.shared = d; return Promise.resolve(); }");
    await page.tap('[data-act="share"]');
    const text = await page.eval("window.shared.text");
    assert.match(text, /play\.google\.com\/store\/apps\/details\?id=org\.kostja\.reci/);
    assert.match(text, /finist0\.github\.io\/reci\/web\//);
    assert.deepEqual((await telemetry(page)).counts.opened.share, 1);
});

test("help the author: without a share sheet the text is copied", async t => {
    const page = await app(t);
    await page.send("Browser.grantPermissions", { permissions: ["clipboardReadWrite", "clipboardSanitizedWrite"] });
    await page.tap('[data-act="settings"]');
    await page.tap('[data-act="help"]');
    await page.eval("delete navigator.share; Navigator.prototype.share = undefined");
    await page.tap('[data-act="share"]');
    await page.waitFor("document.querySelector('.toast')?.textContent.includes('скопирован')");
    assert.match(await page.eval("navigator.clipboard.readText()"), /НЕРодная речь/);
});
