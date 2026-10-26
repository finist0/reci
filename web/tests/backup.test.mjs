// Loads tests/progress-sample.json, the file BackupTest.kt loads on Android, so a progress file
// saved by either version opens in the other.

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const WEB = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const sample = JSON.parse(readFileSync(resolve(WEB, "../tests/progress-sample.json"), "utf-8"));

const storage = new Map();
globalThis.localStorage = {
    getItem: k => storage.get(k) ?? null,
    setItem: (k, v) => storage.set(k, String(v)),
    removeItem: k => storage.delete(k),
};
globalThis.document = { addEventListener() {}, visibilityState: "visible" };

const S = await import("../js/store.js");

// Settings that belong to one device or one session are not carried over by Android.
const shared = Object.keys(sample.prefs).filter(k => !["currentKey", "todayDate", "todayCount", "shuffleSeed"].includes(k));

test("a progress file loads with every word and setting", () => {
    S.clearProgress();
    assert.equal(S.importData(structuredClone(sample)), Object.keys(sample.progress).length);
    for (const [key, p] of Object.entries(sample.progress)) assert.deepEqual(S.getProgress(key), p, key);
    for (const k of shared) assert.deepEqual(S.prefs[k], sample.prefs[k], k);
});

test("a saved file has the same fields as the sample", () => {
    S.importData(structuredClone(sample));
    const out = JSON.parse(JSON.stringify(S.exportData()));
    assert.equal(out.app, "reci");
    assert.equal(out.format, sample.format);
    assert.deepEqual(out.progress, sample.progress);
    for (const k of shared) assert.deepEqual(out.prefs[k], sample.prefs[k], k);
});

test("a file of another kind is refused and changes nothing", () => {
    S.importData(structuredClone(sample));
    assert.throws(() => S.importData({ hello: 1 }));
    assert.deepEqual(S.getProgress("да"), sample.progress["да"]);
});
