#!/usr/bin/env python3
"""Write dict/: a patch from the dictionary built into each recent release to the current one.

The apps download the patch for the dictionary they carry and apply it on top of it, so card
fixes reach them without a new release. A patch holds the changed and added cards whole, the
removed keys, and the whole grammar reference when it changed.

Releases are the v* tags whose tree has the apps' updater; of them, those tagged in the last six
months and always the newest one. Six months count back from the date of HEAD, so the output does
not change from one day to the next. A release that carries the current dictionary needs no patch,
so a new tag changes nothing here until the dictionary does.

build_json.py and build_grammar.py call write() after they change the assets, so a card change
always comes with its patches. Running this by hand is needed only after a release leaves the
six months.

Usage: build_patches.py           write dict/
       build_patches.py --check   exit 1 when dict/ is not what this would write
"""
import datetime
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path

# RECI_ROOT points the script at another checkout; the tests use it.
ROOT = Path(os.environ.get("RECI_ROOT") or Path(__file__).resolve().parents[2])
ASSETS = "app/app/src/main/assets"
OUT = ROOT / "dict"
UPDATER = "app/app/src/main/java/org/kostja/reci/data/DictUpdate.kt"
KEEP = datetime.timedelta(days=183)


def git(*args, binary=False):
    out = subprocess.run(["git", "-C", str(ROOT), *args], check=True, capture_output=True)
    return out.stdout if binary else out.stdout.decode()


def base_id(words: bytes, grammar: bytes) -> str:
    """The id the apps compute from their own words.json and grammar.json."""
    return hashlib.sha256(words + b"\n" + grammar).hexdigest()[:32]


def releases():
    """(tag, date) of the releases to write patches for, newest first."""
    tags = []
    for line in git("tag", "-l", "v*", "--format=%(refname:short) %(creatordate:iso-strict)").splitlines():
        name, date = line.split(" ", 1)
        if subprocess.run(["git", "-C", str(ROOT), "cat-file", "-e", f"{name}:{UPDATER}"],
                          capture_output=True).returncode == 0:
            tags.append((name, datetime.datetime.fromisoformat(date)))
    tags.sort(key=lambda t: t[1], reverse=True)
    if not tags:
        return []
    head = datetime.datetime.fromisoformat(git("log", "-1", "--format=%cI", "HEAD").strip())
    return [tags[0]] + [t for t in tags[1:] if head - t[1] <= KEEP]


def patch(base_words, base_grammar, words, grammar):
    """What turns the base dictionary into the current one."""
    old = {w["key"]: w for w in base_words}
    keys = {w["key"] for w in words}
    return {
        "format": 1,
        "words": [w for w in words if old.get(w["key"]) != w],
        "removed": sorted(k for k in old if k not in keys),
        "grammar": grammar if grammar != base_grammar else None,
    }


def apply(base_words, base_grammar, p):
    """The apps' side, used here to check every patch before it is written."""
    by_key = {w["key"]: w for w in base_words}
    for k in p["removed"]:
        by_key.pop(k, None)
    for w in p["words"]:
        by_key[w["key"]] = w
    words = sorted(by_key.values(), key=lambda w: (w["rank"], w["key"]))
    return words, p["grammar"] if p["grammar"] is not None else base_grammar


def dump(obj) -> bytes:
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode()


def build():
    """{file name: bytes} of everything dict/ should hold."""
    words_bytes = (ROOT / ASSETS / "words.json").read_bytes()
    grammar_bytes = (ROOT / ASSETS / "grammar.json").read_bytes()
    words, grammar = json.loads(words_bytes), json.loads(grammar_bytes)
    files, entries = {}, {}
    for tag, date in releases():
        bw = git("show", f"{tag}:{ASSETS}/words.json", binary=True)
        bg = git("show", f"{tag}:{ASSETS}/grammar.json", binary=True)
        bid = base_id(bw, bg)
        if bid == base_id(words_bytes, grammar_bytes):
            continue
        base_words, base_grammar = json.loads(bw), json.loads(bg)
        p = {**patch(base_words, base_grammar, words, grammar), "base": bid}
        if apply(base_words, base_grammar, p) != (words, grammar):
            sys.exit(f"the patch for {tag} does not give the current dictionary")
        data = dump(p)
        name = f"patch-{bid}.json"
        files[name] = data
        entries[bid] = {"release": tag, "file": name, "sha256": hashlib.sha256(data).hexdigest(), "size": len(data)}
    files["manifest.json"] = json.dumps({"format": 1, "patches": entries}, ensure_ascii=False, indent=1).encode() + b"\n"
    return files


def current():
    return {p.name: p.read_bytes() for p in OUT.glob("*.json")} if OUT.exists() else {}


def write():
    files, old = build(), current()
    OUT.mkdir(exist_ok=True)
    for name in set(old) - set(files):
        (OUT / name).unlink()
    for name, data in files.items():
        (OUT / name).write_bytes(data)
    print(f"dict/: {len(files) - 1} patches")


def main():
    if "--check" not in sys.argv:
        write()
        return
    files, old = build(), current()
    if old != files:
        stale = sorted(set(old) ^ set(files) | {n for n in files if old.get(n) != files[n]})
        sys.exit("dict/ is out of date, run data/scripts/build_patches.py: " + ", ".join(stale))
    print(f"dict/ is up to date: {len(files) - 1} patches")


if __name__ == "__main__":
    main()
