// Which news the first start after an update shows.
// Usage: node --test web/tests/

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parse, pick, compare, BEFORE } from "../js/whatsnew.js";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

const entries = parse([
    { version: "2.0", items: [{ text: "future" }] },
    { version: "1.10", items: [{ text: "ten" }, { text: "ten, Android only", only: ["android"] }] },
    { version: "1.8", items: [{ text: "eight, iPhone only", only: ["ios"] }] },
    { version: "1.7", items: [{ text: "seven" }, { text: "seven, web", only: ["ios", "web"] }] },
], "web");
const versions = (seen, version, fresh = false) => pick(entries, version, seen, fresh).map(e => e.version);

test("only the platform's items are kept", () => {
    assert.deepEqual(entries.map(e => e.version), ["2.0", "1.10", "1.7"]);
    assert.deepEqual(entries[1].items, ["ten"]);
    assert.deepEqual(entries[2].items, ["seven", "seven, web"]);
});

test("an update shows every version since the last seen, newest first", () => {
    assert.deepEqual(versions("1.6", "1.7"), ["1.7"]);
    assert.deepEqual(versions("1.6", "1.10"), ["1.10", "1.7"]);
    assert.deepEqual(versions("1.7", "1.10"), ["1.10"]);
});

test("the same version, an older one or a version without news show nothing", () => {
    assert.deepEqual(versions("1.7", "1.7"), []);
    assert.deepEqual(versions("1.10", "1.7"), []);
    assert.deepEqual(versions("1.7", "1.8"), []);
});

test("a fresh start shows nothing, but an update from before the window does", () => {
    assert.deepEqual(versions(null, "1.10", true), []);
    assert.deepEqual(versions(null, "1.10"), ["1.10", "1.7"]);
});

test("the shared file has news for the web", () => {
    const all = JSON.parse(readFileSync(resolve(ROOT, "app/app/src/main/assets/whatsnew.json"), "utf-8"));
    const web = parse(all, "web");
    assert.ok(web.length > 0);
    for (const e of web) assert.ok(compare(e.version, BEFORE) > 0, e.version);
});
