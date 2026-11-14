#!/usr/bin/env python3
"""Compare the stress marks of the Russian deck with Wiktionary (the kaikki.org Russian dump).

Usage: check_ru_stress.py <ru.jsonl> [--fix] [cards.jsonl ...]
Lists every Russian word whose stress the dump gives otherwise. With --fix, a word whose forms
in the dump all agree on one stress is set to it; the rest stay listed for a person to decide.
Without files, all of data/ru-sr/words is checked.
"""
import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "data" / "ru-sr" / "words"
ACUTE = "́"
VOWELS = set("аеёиоуыэюя")
WORD = re.compile(r"[А-Яа-яЁё́]+")
# Serbian labels in the forms of a card: кратко (short forms), ген., мн., свр.
LABELS = {"кратко", "ген", "мн", "комп", "свр", "несвр", "нескл", "двовид", "инстр", "дат", "вин", "лок"}


def nfc(s):
    return unicodedata.normalize("NFC", s)


def bare(w):
    return w.replace(ACUTE, "").lower()


def stressed(w):
    """The word lower-cased with its stress: the position of the acute, or of ё."""
    w = w.lower()
    if ACUTE in w:
        return w
    return w if "ё" in w else None


def texts(card):
    """(field, text) of the Russian parts of a card that carry stress marks."""
    yield "acc", card.get("acc", "")
    yield "forms", card.get("forms", "")
    for i, ex in enumerate(card.get("examples", [])):
        b = re.search(r"\[([^\]]*)\]", ex.get("ru", ""))
        if b:
            yield f"ex{i}", b.group(1)
    for f in ("syn", "ant"):
        for i, w in enumerate(card.get(f, [])):
            yield f"{f}{i}", w
    for i, r in enumerate(card.get("related", [])):
        yield f"rel{i}", r.get("ru", "")


def main():
    dump = sys.argv[1]
    fix = "--fix" in sys.argv
    paths = [Path(a) for a in sys.argv[2:] if a != "--fix"] or sorted(SRC.glob("*.jsonl"))
    files = {p: [json.loads(l) for l in p.read_text(encoding="utf-8").splitlines() if l.strip()] for p in paths}
    need = {bare(w) for cards in files.values() for c in cards for _, t in texts(c)
            for w in WORD.findall(nfc(t)) if sum(ch in VOWELS for ch in w.lower()) > 1}
    known = defaultdict(set)
    for line in open(dump, encoding="utf-8"):
        e = json.loads(line)
        for f in e.get("forms", []) + [{"form": e.get("word", "")}]:
            form = nfc(f.get("form", ""))
            if " " in form or "romanization" in f.get("tags", []):
                continue
            k = bare(form)
            if k in need and (s := stressed(form)):
                known[k].add(s)
    stat = Counter()
    for path, cards in files.items():
        changed = False
        for c in cards:
            for field, t in texts(c):
                for w in WORD.findall(nfc(t)):
                    k = bare(w)
                    if field == "forms" and k in LABELS:
                        continue
                    if k not in known or sum(ch in VOWELS for ch in k) < 2:
                        continue
                    if w.lower() in known[k]:
                        stat["agrees"] += 1
                        continue
                    if fix and len(known[k]) == 1:
                        right = next(iter(known[k]))
                        new = "".join(r.upper() if o.isupper() else r for o, r in zip(w.replace(ACUTE, ""), right.replace(ACUTE, "")))
                        pos = right.find(ACUTE)
                        if pos > 0:
                            new = new[:pos] + ACUTE + new[pos:]
                        _replace(c, field, w, new)
                        changed = True
                        stat["fixed"] += 1
                        print(f"fixed\t{c['key']}\t{field}\t{w}\t{new}")
                    else:
                        stat["differs"] += 1
                        print(f"differs\t{c['key']}\t{field}\t{w}\t{' / '.join(sorted(known[k]))}")
        if changed:
            path.write_text("".join(json.dumps(c, ensure_ascii=False, separators=(",", ":")) + "\n" for c in cards),
                            encoding="utf-8")
    print(dict(stat), file=sys.stderr)


def _replace(card, field, old, new):
    if field == "acc":
        card["acc"] = card["acc"].replace(old, new, 1)
    elif field == "forms":
        card["forms"] = card["forms"].replace(old, new, 1)
    elif field.startswith("ex"):
        ex = card["examples"][int(field[2:])]
        b = re.search(r"\[([^\]]*)\]", ex["ru"])
        ex["ru"] = ex["ru"][:b.start(1)] + b.group(1).replace(old, new, 1) + ex["ru"][b.end(1):]
    elif field.startswith("rel"):
        r = card["related"][int(field[3:])]
        r["ru"] = r["ru"].replace(old, new, 1)
    else:
        f, i = field[:3], int(field[3:])
        card[f][i] = card[f][i].replace(old, new, 1)


if __name__ == "__main__":
    main()
