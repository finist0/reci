import { test } from "node:test";
import assert from "node:assert/strict";
import { showAccents, strip, toLatin } from "../js/data.js";

const ex = "Имамо малу [ку̏ћу] на [сѐлу].";

test("an example follows the accent setting", () => {
    assert.equal(showAccents(ex, 0), ex);
    assert.equal(showAccents(ex, 1), "Имамо малу [ку́ћу] на [се́лу].");
    assert.equal(showAccents(ex, 2), "Имамо малу [кућу] на [селу].");
    assert.equal(toLatin(showAccents(ex, 1)), "Imamo malu [kúću] na [sélu].");
});

test("strip leaves other marks alone", () => {
    assert.equal(strip("йё ку̏ћа̄"), "йё кућа");
    assert.equal(strip("кућа"), "кућа");
    assert.equal(strip("ćevapi, kȕća, с́"), "ćevapi, kȕća, с́");
});
