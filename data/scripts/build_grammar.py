#!/usr/bin/env python3
"""Validate data/grammar/grammar.json and copy it into the app's assets.

Usage: build_grammar.py [--check]
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "data" / "grammar" / "grammar.json"
OUT = ROOT / "app" / "app" / "src" / "main" / "assets" / "grammar.json"

IDS = [
    "alphabet", "gender", "cases", "noun-m", "noun-n", "noun-f-a", "noun-f-cons", "prepositions",
    "pronouns", "possessive", "clitics", "biti", "present", "perfect", "future", "aspect",
    "da-present", "modal", "imperative", "conditional", "questions", "negation", "adjectives",
    "comparison", "numbers", "false-friends",
]
BLOCKS = {"p": ("text",), "tip": ("text",), "list": ("items",), "table": ("head", "rows"), "ex": ("sr", "ru")}
SERBIAN = re.compile(r"\{([^{}]*)\}")
LATIN = re.compile(r"[A-Za-zčćžšđČĆŽŠĐ]")
RU_ONLY = re.compile(r"[ыэъёщЫЭЪЁЩ]")


def texts(block):
    t = block["type"]
    if t in ("p", "tip"):
        yield block["text"]
    elif t == "list":
        yield from block["items"]
    elif t == "table":
        yield from block["head"]
        for row in block["rows"]:
            yield from row
    elif t == "ex":
        yield block["ru"]


def check_serbian(s, where, errs):
    if LATIN.search(s.replace("|", "")):
        errs.append(f"{where}: Latin letters in Serbian {s!r}")
    if RU_ONLY.search(s):
        errs.append(f"{where}: Russian-only letters in Serbian {s!r}")


def main():
    topics = json.loads(SRC.read_text(encoding="utf-8"))
    errs = []
    ids = [t.get("id") for t in topics]
    extra = SRC.parent / "extra.json"
    if extra.exists():
        topics += json.loads(extra.read_text(encoding="utf-8"))
    for missing in [i for i in IDS if i not in ids]:
        errs.append(f"missing topic {missing}")
    for unknown in [i for i in ids if i not in IDS]:
        errs.append(f"unknown topic {unknown}")
    for t in topics:
        tid = t.get("id")
        for f in ("id", "group", "title", "blocks"):
            if not t.get(f):
                errs.append(f"{tid}: missing {f}")
        for n, b in enumerate(t.get("blocks", [])):
            where = f"{tid}#{n}"
            if b.get("type") not in BLOCKS:
                errs.append(f"{where}: bad block type {b.get('type')!r}")
                continue
            for f in BLOCKS[b["type"]]:
                if f not in b:
                    errs.append(f"{where}: missing {f}")
            if b["type"] == "table":
                width = len(b.get("head", []))
                for r in b.get("rows", []):
                    if len(r) != width:
                        errs.append(f"{where}: row width {len(r)} != head width {width}: {r}")
            if b["type"] == "ex":
                check_serbian(b.get("sr", ""), where, errs)
            for s in texts(b):
                if s.count("{") != s.count("}"):
                    errs.append(f"{where}: unbalanced braces in {s!r}")
                for m in SERBIAN.findall(s):
                    check_serbian(m, where, errs)
    for e in errs:
        print(e, file=sys.stderr)
    if not errs and "--check" not in sys.argv:
        OUT.write_text(json.dumps(topics, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"{len(topics)} topics, {len(errs)} errors")
    sys.exit(1 if errs else 0)


if __name__ == "__main__":
    main()
