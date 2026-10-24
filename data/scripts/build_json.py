#!/usr/bin/env python3
"""Merge data/words/*.jsonl into the app's assets/words.json, validating entries.

Levels are assigned here from the frequency order of the accepted words, so the
generated entries only carry the frequency rank of their source candidate.
"""
import difflib
import json
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "data" / "words"
OUT = ROOT / "app" / "app" / "src" / "main" / "assets" / "words.json"

TOPICS = {
    "basic", "people", "family", "body", "health", "home", "food", "clothes", "city",
    "transport", "travel", "nature", "weather", "animals", "time", "numbers", "colors",
    "work", "education", "money", "communication", "emotions", "character", "leisure",
    "culture", "tech", "society", "actions", "qualities", "abstract",
}
POS = {"noun", "verb", "adj", "adv", "pron", "prep", "conj", "num", "part", "interj", "phrase", "compare", "note"}
# Upper bound (by position in frequency order) of each level A1..B2; the rest is C1.
LEVEL_BOUNDS = (500, 1100, 2000, 3000)
LATIN = re.compile(r"[A-Za-zčćžšđČĆŽŠĐ]")
CYR_RU_ONLY = re.compile(r"[ыэъёщЫЭЪЁЩ]")
FIELDS = ("tips", "cyr", "acc", "pos", "gram", "ruGram", "ruNoun", "forms", "meanings", "level", "topic", "rank",
          "examples", "syn", "ant", "related", "note")
SR_GENDER = {"м. р.": "masc", "ж. р.": "femn", "ср. р.": "neut"}
RU_GENDER = {"masc": "м. р.", "femn": "ж. р.", "neut": "ср. р."}
WORD = re.compile(r"[а-яё-]+", re.I)

try:
    import pymorphy3
    MORPH = pymorphy3.MorphAnalyzer()
except ImportError:
    MORPH = None


def sr_gender(gram):
    for label, g in SR_GENDER.items():
        if gram.startswith(label):
            return g
    return None


def ru_noun(meaning):
    """First noun in nominative in a Russian meaning and its gender, e.g. "железная дорога"."""
    for token in WORD.findall(meaning.split("(")[0]):
        for parse in MORPH.parse(token):
            if parse.tag.POS == "NOUN" and parse.tag.case == "nomn":
                return token.lower(), parse.tag.gender if parse.tag.number == "sing" else None
    return None, None


SR_TO_RU = str.maketrans({"ј": "й", "ћ": "ч", "ђ": "дж", "џ": "дж", "љ": "ль", "њ": "нь"})


def looks_alike(sr, ru):
    """True for cognates such as бол/боль, проблем/проблема, кафа/кофе."""
    a = sr.lower().translate(SR_TO_RU)
    if difflib.SequenceMatcher(None, a, ru).ratio() >= 0.55 or (len(a) >= 4 and ru.startswith(a[:4])):
        return True
    skeleton = lambda w: re.sub(r"[аеиоуыэюяьйъ]", "", w)
    return len(skeleton(a)) >= 2 and skeleton(a) == skeleton(ru)


SR_LETTERS = set("абвгдђежзијклљмнњопрстћуфхцчџш")
RU_LETTERS = set("абвгдеёжзийклмнопрстуфхцчшщъыьэюя")


def stray(text, allowed):
    return sorted({ch for ch in text if ch.isalpha() and ch.lower() not in allowed})


ARTICLES = {"compare", "note"}


def check(e):
    if e.get("pos") in ARTICLES:
        # Articles: a Russian title or a list of words, an explanation and the words discussed.
        errs = [f"missing {f}" for f in ("key", "cyr", "note", "rank") if not e.get(f)]
        for r in e.get("related", []):
            if bad := stray(r.get("sr", ""), SR_LETTERS | RU_LETTERS):
                errs.append(f"foreign letters {bad} in {r.get('sr')!r}")
        return errs
    errs = []
    sr_fields = [e.get("cyr", "")] + ([] if e.get("pos") == "phrase" else [e.get("forms", "")]) + e.get("syn", []) + e.get("ant", [])
    sr_fields += [r.get("sr", "") for r in e.get("related", [])] + [x.get("sr", "") for x in e.get("examples", [])]
    ru_fields = list(e.get("meanings", [])) + [r.get("ru", "") for r in e.get("related", [])]
    ru_fields += [x.get("ru", "") for x in e.get("examples", [])]
    # Serbian glosses inside Russian text and Russian labels inside forms are fine;
    # look-alike letters from other scripts and stress marks are not.
    cyr = SR_LETTERS | RU_LETTERS
    for t in sr_fields + ru_fields + [e.get("gram", "")]:
        if bad := stray(t, cyr):
            errs.append(f"foreign letters {bad} in {t!r}")
    if bad := stray(e.get("note", ""), cyr | set("abcdefghijklmnopqrstuvwxyz")):
        errs.append(f"foreign letters {bad} in note {e.get('note')!r}")
    for t in sr_fields + ru_fields + [e.get("note", "")]:
        if any(unicodedata.category(ch) == "Mn" for ch in t):
            errs.append(f"combining mark in {t!r}")
    for f in ("key", "cyr", "pos", "meanings", "topic", "rank"):
        if not e.get(f):
            errs.append(f"missing {f}")
    if e.get("pos") not in POS:
        errs.append(f"bad pos {e.get('pos')!r}")
    if e.get("topic") not in TOPICS:
        errs.append(f"bad topic {e.get('topic')!r}")
    if not isinstance(e.get("meanings"), list) or not all(isinstance(m, str) and m for m in e["meanings"]):
        errs.append("meanings must be a list of strings")
    if e.get("pos") == "noun" and not (sr_gender(e.get("gram", "")) or e.get("gram", "").startswith("мн.")):
        errs.append(f"noun without gender in gram {e.get('gram')!r}")
    # A phrase card keeps its literal Russian translation in forms.
    sr = [e.get("cyr", "")] + ([] if e.get("pos") == "phrase" else [e.get("forms", "")]) + e.get("syn", []) + e.get("ant", [])
    sr += [r.get("sr", "") for r in e.get("related", [])]
    sr += [x.get("sr", "") for x in e.get("examples", [])]
    for t in sr:
        if LATIN.search(t):
            errs.append(f"latin letters in Serbian text {t!r}")
        if CYR_RU_ONLY.search(t):
            errs.append(f"Russian-only letters in Serbian text {t!r}")
    if not e.get("examples"):
        errs.append("no examples")
    for x in e.get("examples", []):
        if not x.get("sr") or not x.get("ru"):
            errs.append("example without sr or ru")
        elif not re.search(r"\[[^\]]+\]", x["sr"]):
            errs.append(f"headword not marked in {x['sr']!r}")
    for r in e.get("related", []):
        if not r.get("sr") or not r.get("ru"):
            errs.append("related without sr or ru")
    return errs


def main():
    words = {}
    bad = 0
    for path in sorted(SRC.glob("*.jsonl")):
        for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if not line.strip():
                continue
            try:
                e = json.loads(line)
            except json.JSONDecodeError as err:
                print(f"{path.name}:{n}: bad json: {err}", file=sys.stderr)
                bad += 1
                continue
            errs = check(e)
            for err in errs:
                print(f"{path.name}:{n}: {e.get('key')}: {err}", file=sys.stderr)
            if errs:
                bad += 1
                continue
            if e["key"] in words:
                print(f"{path.name}:{n}: duplicate key {e['key']}", file=sys.stderr)
                continue
            words[e["key"]] = e
    accents = ROOT / "data" / "accents.tsv"
    if accents.exists():
        for n, line in enumerate(accents.read_text(encoding="utf-8").splitlines(), 1):
            key, acc, _src = line.split("\t")
            e = words.get(key)
            bare = unicodedata.normalize("NFC", "".join(
                ch for ch in unicodedata.normalize("NFD", acc) if unicodedata.category(ch) != "Mn"))
            if e is None:
                print(f"accents.tsv:{n}: no card {key!r}", file=sys.stderr)
            elif bare != e["cyr"]:
                print(f"accents.tsv:{n}: {acc!r} does not match {e['cyr']!r}", file=sys.stderr)
            elif acc != e["cyr"]:
                e["acc"] = unicodedata.normalize("NFC", acc)
    tips = ROOT / "data" / "tips.jsonl"
    if tips.exists():
        for line in tips.read_text(encoding="utf-8").splitlines():
            t = json.loads(line)
            if t["key"] in words:
                words[t["key"]].setdefault("tips", []).append(t["tip"])
    out = sorted(words.values(), key=lambda e: (e["rank"], e["key"]))
    mismatches = 0
    # Phrases live in their own section and do not take slots in the frequency levels.
    i = 0
    for e in out:
        if e["pos"] in ("phrase", "compare", "note"):
            e["level"] = 0
            continue
        e["level"] = next((lvl for lvl, b in enumerate(LEVEL_BOUNDS, 1) if i < b), 5)
        i += 1
    for e in out:
        # Flag nouns whose gender differs from the gender of the main Russian translation.
        if MORPH and e["pos"] == "noun":
            noun, rg = ru_noun(e["meanings"][0])
            sg = sr_gender(e["gram"])
            if sg and rg and sg != rg:
                e["ruGram"] = RU_GENDER[rg]
                if looks_alike(e["cyr"], noun):
                    e["ruNoun"] = noun
                mismatches += 1
    if MORPH is None and "--check" not in sys.argv:
        print("pymorphy3 missing (see data/requirements.txt): gender mismatches are not flagged", file=sys.stderr)
    out = [{"key": e["key"], **{f: e[f] for f in FIELDS if f in e}} for e in out]
    if "--check" not in sys.argv:
        OUT.parent.mkdir(parents=True, exist_ok=True)
        OUT.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"{len(out)} words ok, {bad} rejected, {mismatches} nouns differ in gender from Russian")


if __name__ == "__main__":
    main()
