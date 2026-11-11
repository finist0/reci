// The window with the news of an update, on the first start of a new version.
// Usage: node --test "web/tests/ui/*.test.mjs"   (CHROME=/path/to/chrome if it is not google-chrome)

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { serve, launch } from "./chrome.mjs";

const NEWS = [
    { version: "9.9", items: [{ text: "Новое в 9.9" }, { text: "Только для iPhone", only: ["ios"] }] },
    { version: "9.8", items: [{ text: "Новое в 9.8", only: ["web", "android"] }] },
];

let server;
before(async () => (server = await serve({ files: {
    "/app/app/build.gradle.kts": 'versionName = "9.9"\n',
    "/app/app/src/main/assets/whatsnew.json": JSON.stringify(NEWS),
} })));
after(() => server.close());

async function app(t, storage) {
    const page = await launch({ storage });
    t.after(() => page.close());
    await page.open(server.url);
    await page.waitFor("localStorage.getItem('reci.whatsNewSeen') === '9.9'");
    return page;
}

const news = page => page.eval("document.querySelector('.news')?.innerText ?? null");
const progress = JSON.stringify({ "x": { state: 3 } });

test("an update shows its news once", async t => {
    const page = await app(t, { "reci.whatsNewSeen": "9.8" });
    await page.waitFor("document.querySelector('.news')");
    const text = await news(page);
    assert.match(text, /Что нового в версии 9\.9/);
    assert.match(text, /Новое в 9\.9/);
    assert.doesNotMatch(text, /Только для iPhone|Новое в 9\.8/);
    await page.tap(".news button", "Понятно");
    assert.equal(await news(page), null);
    await page.reload();
    await page.sleep(500);
    assert.equal(await news(page), null);
});

test("an update from before the window shows every version since, under its number", async t => {
    const page = await app(t, { "reci.progress": progress });
    await page.waitFor("document.querySelector('.news')");
    const text = await news(page);
    assert.match(text, /^Что нового\n/);
    assert.match(text, /Версия 9\.9[\s\S]*Новое в 9\.9[\s\S]*Версия 9\.8[\s\S]*Новое в 9\.8/);
});

test("a first visit is not told about changes", async t => {
    const page = await app(t, {});
    await page.sleep(500);
    assert.equal(await news(page), null);
});
