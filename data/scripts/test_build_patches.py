#!/usr/bin/env python3
"""Tests of build_patches.py. Run: python3 -m unittest discover -s data/scripts -p 'test_*.py'"""
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

import build_patches as bp

FIXTURE = json.loads((Path(__file__).resolve().parents[2] / "tests" / "dict-patches.json").read_text())


def card(key, rank, meanings):
    return {"key": key, "cyr": key, "pos": "noun", "meanings": meanings, "level": 1, "topic": "basic", "rank": rank}


class Shared(unittest.TestCase):
    def test_the_shared_cases(self):
        for c in FIXTURE["cases"]:
            with self.subTest(c["name"]):
                words, grammar = bp.apply(FIXTURE["base"], FIXTURE["grammar"], c["patch"])
                self.assertEqual([w["key"] for w in words], c["keys"])
                for k, meanings in c["changed"].items():
                    self.assertEqual(next(w for w in words if w["key"] == k)["meanings"], meanings)
                self.assertEqual(grammar[0]["blocks"][0]["text"], c["grammar"])


class Patch(unittest.TestCase):
    def test_a_patch_holds_only_what_changed(self):
        base = [card("а", 1, ["a"]), card("б", 2, ["b"]), card("в", 3, ["c"])]
        now = [card("а", 1, ["a"]), card("б", 2, ["bb"]), card("г", 4, ["d"])]
        p = bp.patch(base, [], now, [])
        self.assertEqual([w["key"] for w in p["words"]], ["б", "г"])
        self.assertEqual(p["removed"], ["в"])
        self.assertIsNone(p["grammar"])
        self.assertEqual(bp.apply(base, [], p), (now, []))

    def test_an_unchanged_dictionary_gives_an_empty_patch(self):
        base = [card("а", 1, ["a"])]
        p = bp.patch(base, [{"id": "x"}], base, [{"id": "x"}])
        self.assertEqual((p["words"], p["removed"], p["grammar"]), ([], [], None))

    def test_the_shared_base_id(self):
        b = FIXTURE["baseId"]
        self.assertEqual(bp.base_id(b["words"].encode(), b["grammar"].encode()), b["id"])

    def test_the_base_id_covers_both_files(self):
        self.assertNotEqual(bp.base_id(b"[1]", b"[]"), bp.base_id(b"[1]", b"[2]"))
        self.assertEqual(len(bp.base_id(b"", b"")), 32)


class Releases(unittest.TestCase):
    """Against a throwaway repository with releases of different ages."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.old = (bp.ROOT, bp.OUT)
        bp.ROOT, bp.OUT = self.root, self.root / "dict"
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.email", "t@example.org")
        self.git("config", "user.name", "T")

    def tearDown(self):
        bp.ROOT, bp.OUT = self.old
        self.tmp.cleanup()

    def git(self, *args, date=None):
        env = {**os.environ}
        if date:
            env.update(GIT_AUTHOR_DATE=date, GIT_COMMITTER_DATE=date)
        subprocess.run(["git", "-C", str(self.root), *args], check=True, capture_output=True, env=env)

    def commit(self, words, date, updater=True, grammar=None, strip=False):
        assets = self.root / bp.ASSETS
        assets.mkdir(parents=True, exist_ok=True)
        (assets / "words.json").write_text(json.dumps(words, ensure_ascii=False))
        (assets / "grammar.json").write_text(json.dumps(grammar or []))
        upd = self.root / bp.UPDATER
        if updater:
            upd.parent.mkdir(parents=True, exist_ok=True)
            upd.write_text("// updater")
        elif upd.exists():
            upd.unlink()
        acc = self.root / bp.ACCENTS
        acc.parent.mkdir(parents=True, exist_ok=True)
        acc.write_text("fun strip(text: String)" if strip else "fun placeOnly(acc: String)")
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "c", date=date)

    def tag(self, name, date):
        self.git("tag", "-a", name, "-m", name, date=date)

    def test_recent_releases_with_the_updater_get_patches(self):
        self.commit([card("а", 1, ["a"])], "2026-01-01T20:00:00+01:00", updater=False)
        self.tag("v0.9", "2026-01-01T20:00:00+01:00")
        self.commit([card("а", 1, ["a1"])], "2026-02-01T20:00:00+01:00")
        self.tag("v1.0", "2026-02-01T20:00:00+01:00")
        self.commit([card("а", 1, ["a2"])], "2026-07-01T20:00:00+01:00")
        self.tag("v1.1", "2026-07-01T20:00:00+01:00")
        self.commit([card("а", 1, ["a3"])], "2026-09-01T20:00:00+01:00")
        self.tag("v1.2", "2026-09-01T20:00:00+01:00")
        self.commit([card("а", 1, ["a4"])], "2026-10-01T20:00:00+01:00")
        # v0.9 cannot update, v1.0 is older than six months before HEAD.
        self.assertEqual([t for t, _ in bp.releases()], ["v1.2", "v1.1"])
        files = bp.build()
        manifest = json.loads(files["manifest.json"])
        self.assertEqual(sorted(e["release"] for e in manifest["patches"].values()), ["v1.1", "v1.2"])
        for bid, e in manifest["patches"].items():
            p = json.loads(files[e["file"]])
            self.assertEqual(p["base"], bid)
            self.assertEqual(p["words"][0]["meanings"], ["a4"])

    def test_the_newest_release_always_gets_a_patch(self):
        self.commit([card("а", 1, ["a"])], "2025-01-01T20:00:00+01:00")
        self.tag("v1.0", "2025-01-01T20:00:00+01:00")
        self.commit([card("а", 1, ["b"])], "2026-10-01T20:00:00+01:00")
        self.assertEqual([t for t, _ in bp.releases()], ["v1.0"])

    def test_a_release_with_the_current_dictionary_gets_no_patch(self):
        self.commit([card("а", 1, ["a"])], "2026-09-01T20:00:00+01:00")
        self.tag("v1.0", "2026-09-01T20:00:00+01:00")
        self.commit([card("а", 1, ["b"])], "2026-10-01T20:00:00+01:00")
        self.tag("v1.1", "2026-10-01T20:00:00+01:00")
        # Right after tagging, dict/ written before the tag is still up to date.
        manifest = json.loads(bp.build()["manifest.json"])
        self.assertEqual([e["release"] for e in manifest["patches"].values()], ["v1.0"])

    def test_no_release_with_the_updater_gives_an_empty_manifest(self):
        self.commit([card("а", 1, ["a"])], "2026-10-01T20:00:00+01:00", updater=False)
        self.tag("v1.0", "2026-10-01T20:00:00+01:00")
        self.assertEqual(json.loads(bp.build()["manifest.json"]), {"format": 1, "patches": {}})

    def test_the_base_id_is_the_one_of_the_release_files(self):
        self.commit([card("а", 1, ["a"])], "2026-10-01T20:00:00+01:00", grammar=[{"id": "g"}])
        self.tag("v1.0", "2026-10-01T20:00:00+01:00")
        w = (self.root / bp.ASSETS / "words.json").read_bytes()
        g = (self.root / bp.ASSETS / "grammar.json").read_bytes()
        self.commit([card("а", 1, ["b"])], "2026-10-02T20:00:00+01:00", grammar=[{"id": "g2"}])
        manifest = json.loads(bp.build()["manifest.json"])
        self.assertEqual(list(manifest["patches"]), [bp.base_id(w, g)])
        p = json.loads(bp.build()[manifest["patches"][bp.base_id(w, g)]["file"]])
        self.assertEqual(p["grammar"], [{"id": "g2"}])

    def test_only_releases_that_hide_marks_get_them_in_examples_and_notes(self):
        marked = {**card("а", 1, ["a"]), "note": "ку̏ћа", "examples": [{"sr": "[ку̏ћа̄] у сѐлу", "ru": "дом"}]}
        self.commit([{**marked, "note": "кућа", "examples": [{"sr": "[кућа]", "ru": "дом"}]}],
                    "2026-10-01T20:00:00+01:00")
        self.tag("v1.0", "2026-10-01T20:00:00+01:00")
        self.commit([{**marked, "note": "кућа", "examples": [{"sr": "[кућа]", "ru": "дом"}], "meanings": ["c"]}],
                    "2026-10-02T20:00:00+01:00", strip=True)
        self.tag("v1.1", "2026-10-02T20:00:00+01:00")
        self.commit([{**marked, "meanings": ["b"]}], "2026-10-03T20:00:00+01:00", strip=True)
        files = bp.build()
        got = {e["release"]: json.loads(files[e["file"]])["words"][0]
               for e in json.loads(files["manifest.json"])["patches"].values()}
        self.assertEqual((got["v1.0"]["note"], got["v1.0"]["examples"][0]["sr"]), ("кућа", "[кућа] у селу"))
        self.assertEqual((got["v1.1"]["note"], got["v1.1"]["examples"][0]["sr"]), ("ку̏ћа", "[ку̏ћа̄] у сѐлу"))

    def test_a_shared_dictionary_gets_the_marks_only_when_every_release_hides_them(self):
        base = {**card("а", 1, ["a"]), "note": "кућа", "examples": []}
        self.commit([base], "2026-10-01T20:00:00+01:00")
        self.tag("v1.0", "2026-10-01T20:00:00+01:00")
        self.commit([base], "2026-10-02T20:00:00+01:00", strip=True)
        self.tag("v1.1", "2026-10-02T20:00:00+01:00")
        self.commit([{**base, "note": "ку̏ћа", "meanings": ["b"]}], "2026-10-03T20:00:00+01:00", strip=True)
        files = bp.build()
        [e] = json.loads(files["manifest.json"])["patches"].values()
        self.assertEqual(json.loads(files[e["file"]])["words"][0]["note"], "кућа")

    def test_latin_letters_keep_their_marks(self):
        self.assertEqual(bp.bare("ćevapi, с́, ку̏ћа, ср̏це"), "ćevapi, с́, кућа, срце")

    def test_check_finds_a_stale_dict_and_writing_fixes_it(self):
        self.commit([card("а", 1, ["a"])], "2026-10-01T20:00:00+01:00")
        self.tag("v1.0", "2026-10-01T20:00:00+01:00")
        self.commit([card("а", 1, ["b"])], "2026-10-02T20:00:00+01:00")
        run = lambda *a: subprocess.run(["python3", bp.__file__, *a], capture_output=True, text=True,
                                        env={**os.environ, "RECI_ROOT": str(self.root)})
        self.assertNotEqual(run("--check").returncode, 0)
        self.assertEqual(run().returncode, 0)
        self.assertEqual(run("--check").returncode, 0)
        (self.root / "dict" / "patch-stale.json").write_text("{}")
        self.assertNotEqual(run("--check").returncode, 0)
        run()
        self.assertFalse((self.root / "dict" / "patch-stale.json").exists())


if __name__ == "__main__":
    unittest.main()
