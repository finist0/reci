#!/usr/bin/env python3
"""Merge data/words/*.jsonl into the app's assets/words.json, validating entries.

With --deck ru-sr it builds the deck for Serbian speakers learning Russian instead, from
data/ru-sr/words/ into assets/words-ru-sr.json. The English decks sr-en and ru-en take the cards
of sr-ru and ru-sr and the English side of each card from data/en/sr/ and data/en/ru/.

Levels are assigned here from the frequency order of the accepted words, so the
generated entries only carry the frequency rank of their source candidate.
"""
import difflib
import json
import re
import sys
import unicodedata
from pathlib import Path

import build_patches
import mixed_text

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "app" / "src" / "main" / "assets"
# What one learns -> from which native language: sources and the asset the apps load.
DECKS = {
    "sr-ru": {"src": ROOT / "data" / "words", "out": ASSETS / "words.json", "target": "sr", "native": "ru"},
    "ru-sr": {"src": ROOT / "data" / "ru-sr" / "words", "out": ASSETS / "words-ru-sr.json", "target": "ru", "native": "sr"},
    "sr-en": {"src": ROOT / "data" / "words", "en": ROOT / "data" / "en" / "sr", "out": ASSETS / "words-sr-en.json",
              "target": "sr", "native": "en"},
    "ru-en": {"src": ROOT / "data" / "ru-sr" / "words", "en": ROOT / "data" / "en" / "ru", "out": ASSETS / "words-ru-en.json",
              "target": "ru", "native": "en"},
}
DECK = DECKS[sys.argv[sys.argv.index("--deck") + 1] if "--deck" in sys.argv else "sr-ru"]
SRC = DECK["src"]
OUT = DECK["out"]

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
# Also not Serbian, but the forms of a Serbian word note things in Russian ("не склоняется").
CYR_RU_ONLY_WORD = re.compile(r"[ыэъёщяюйьЫЭЪЁЩЯЮЙЬ]")
FIELDS = ("tips", "cyr", "acc", "pos", "gram", "ruGram", "ruNoun", "forms", "meanings", "level", "topic", "rank",
          "examples", "syn", "synAcc", "ant", "antAcc", "related", "note")
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


STRESS_SET = set("\u030f\u0311\u0300\u0301\u0304")


def unstressed(text):
    """The text without stress marks, which examples and notes may carry."""
    d = unicodedata.normalize("NFD", text)
    out, vowel = [], False
    for ch in d:
        if ch not in STRESS_SET or not vowel:
            out.append(ch)
        if unicodedata.category(ch) != "Mn":
            vowel = ch in "аеиоуряюыэёАЕИОУРЯЮЫЭЁ"
    out = "".join(out)
    return text if out == d else unicodedata.normalize("NFC", out)


def stray(text, allowed):
    return sorted({ch for ch in text if ch.isalpha() and ch.lower() not in allowed})


ARTICLES = {"compare", "note"}


def check(e):
    if e.get("pos") in ARTICLES:
        # Articles: a Russian title or a list of words, an explanation and the words discussed.
        errs = [f"missing {f}" for f in ("key", "cyr", "note", "rank") if not e.get(f)]
        for r in e.get("related", []):
            if bad := stray(unstressed(r.get("sr", "")), SR_LETTERS | RU_LETTERS):
                errs.append(f"foreign letters {bad} in {r.get('sr')!r}")
        return errs
    errs = []
    words = [unstressed(w) for w in e.get("syn", []) + e.get("ant", []) + [r.get("sr", "") for r in e.get("related", [])]]
    sr_fields = [e.get("cyr", "")] + ([] if e.get("pos") == "phrase" else [e.get("forms", "")]) + words
    # Stress marks are fine on the bracketed words of an example and nowhere else in it.
    for x in e.get("examples", []):
        outside = re.sub(r"\[[^\]]*\]", "", x.get("sr", ""))
        if unstressed(outside) != outside:
            errs.append(f"stress mark outside the brackets in {x['sr']!r}")
        sr_fields.append(unstressed(x.get("sr", "")))
    ru_fields = list(e.get("meanings", [])) + [r.get("ru", "") for r in e.get("related", [])]
    ru_fields += [x.get("ru", "") for x in e.get("examples", [])]
    # Serbian glosses inside Russian text and Russian labels inside forms are fine;
    # look-alike letters from other scripts and stress marks are not.
    cyr = SR_LETTERS | RU_LETTERS
    for t in sr_fields + ru_fields + [e.get("gram", "")]:
        if bad := stray(t, cyr):
            errs.append(f"foreign letters {bad} in {t!r}")
    note = unstressed(e.get("note", ""))
    if bad := stray(note, cyr | set("abcdefghijklmnopqrstuvwxyz")):
        errs.append(f"foreign letters {bad} in note {e.get('note')!r}")
    for t in sr_fields + ru_fields + [note]:
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
    sr = [e.get("cyr", "")] + ([] if e.get("pos") == "phrase" else [e.get("forms", "")]) + words
    sr += [unstressed(x.get("sr", "")) for x in e.get("examples", [])]
    for t in sr:
        if LATIN.search(t):
            errs.append(f"latin letters in Serbian text {t!r}")
        if (CYR_RU_ONLY if t == e.get("forms") else CYR_RU_ONLY_WORD).search(t):
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


SR_ONLY = re.compile(r"[ђјљњћџЂЈЉЊЋЏ]")
RU_VOWELS = set("аеёиоуыэюя")
RU_GRAM = {"noun": ("м. р.", "ж. р.", "с. р.", "мн."), "verb": ("несвр.", "свр.", "двовид.")}


def ru_stress_ok(text):
    """Every Russian word of two or more syllables carries one acute after a vowel; ё is
    stressed itself and takes none. In a hyphenated word (когда-нибудь, давным-давно) each
    part takes at most one mark, and the word at least one."""
    for w in re.findall(r"[а-яё\u0301-]+", unicodedata.normalize("NFC", text).lower()):
        for part in w.split("-"):
            marks = part.count("\u0301")
            if marks > 1 or ("ё" in part and marks):
                return False
            if any(part[i - 1] not in RU_VOWELS for i, c in enumerate(part) if c == "\u0301"):
                return False
        # из-за, из-под: parts of one syllable each are read as one unstressed word.
        if "\u0301" not in w and "ё" not in w and max(sum(c in RU_VOWELS for c in p) for p in w.split("-")) > 1:
            return False
    return True


def check_ru_article(e):
    """A comparison of Russian words or a note, explained in Serbian."""
    errs = [f"missing {f}" for f in ("key", "cyr", "note", "rank") if not e.get(f)]
    prefix = {"compare": "cmp:", "note": "note:"}[e["pos"]]
    if not e.get("key", "").startswith(prefix):
        errs.append(f"key does not start with {prefix}")
    if e.get("topic") not in TOPICS:
        errs.append(f"bad topic {e.get('topic')!r}")
    if e["pos"] == "compare" and (not e.get("meanings") or len(e.get("related", [])) < 2):
        errs.append("a comparison needs its question and two words at least")
    if LATIN.search(e.get("note", "")) or LATIN.search(e.get("cyr", "")):
        errs.append("latin letters")
    for r in e.get("related", []):
        if not r.get("ru") or not r.get("sr"):
            errs.append("related without ru or sr")
        elif not ru_stress_ok(r["ru"]):
            errs.append(f"stress missing or misplaced in {r['ru']!r}")
    for x in e.get("examples", []):
        b = re.search(r"\[([^\]]+)\]", x.get("ru", ""))
        if not x.get("sr") or not b:
            errs.append(f"example without sr or a marked word: {x.get('ru')!r}")
        elif not ru_stress_ok(b.group(1)):
            errs.append(f"stress missing or misplaced in [{b.group(1)}]")
    if e["pos"] == "compare" and len(e.get("examples", [])) < len(e.get("related", [])):
        errs.append("fewer examples than compared words")
    return errs


def check_ru(e):
    """A card of the deck for Serbian speakers learning Russian."""
    if e.get("pos") in ARTICLES:
        return check_ru_article(e)
    errs = [f"missing {f}" for f in ("key", "cyr", "acc", "pos", "meanings", "topic", "rank") if not e.get(f)]
    if e.get("pos") not in POS:
        errs.append(f"bad pos {e.get('pos')!r}")
    if e.get("topic") not in TOPICS:
        errs.append(f"bad topic {e.get('topic')!r}")
    if e.get("pos") in RU_GRAM and e.get("gram") not in RU_GRAM[e["pos"]]:
        errs.append(f"bad gram {e.get('gram')!r}")
    if unstressed(e.get("acc", "")) != e.get("cyr"):
        errs.append(f"acc {e.get('acc')!r} does not match cyr {e.get('cyr')!r}")
    ru = [e.get("acc", ""), e.get("forms", "")] + e.get("syn", []) + e.get("ant", [])
    ru += [r.get("ru", "") for r in e.get("related", [])]
    # A note may quote Russian words, so only Latin letters are wrong in it.
    sr = list(e.get("meanings", [])) + [r.get("sr", "") for r in e.get("related", [])] + [e.get("gram", "")]
    if LATIN.search(e.get("note", "")):
        errs.append(f"latin letters in note {e['note']!r}")
    for x in e.get("examples", []):
        if not x.get("ru") or not x.get("sr"):
            errs.append("example without ru or sr")
            continue
        b = re.search(r"\[([^\]]+)\]", x["ru"])
        if not b:
            errs.append(f"headword not marked in {x['ru']!r}")
        elif not ru_stress_ok(b.group(1)):
            errs.append(f"stress missing or misplaced in [{b.group(1)}]")
        outside = re.sub(r"\[[^\]]*\]", "", x["ru"])
        if unstressed(outside) != outside:
            errs.append(f"stress mark outside the brackets in {x['ru']!r}")
        ru.append(x["ru"])
        sr.append(x["sr"])
    for t in [e.get("acc", "")] + e.get("syn", []) + e.get("ant", []) + [r.get("ru", "") for r in e.get("related", [])]:
        if not ru_stress_ok(t):
            errs.append(f"stress missing or misplaced in {t!r}")
    for t in ru:
        if LATIN.search(t) or SR_ONLY.search(t):
            errs.append(f"non-Russian letters in {t!r}")
    for t in sr:
        if LATIN.search(t) or CYR_RU_ONLY_WORD.search(t):
            errs.append(f"non-Serbian letters in {t!r}")
    if not e.get("examples"):
        errs.append("no examples")
    for r in e.get("related", []):
        if not r.get("ru") or not r.get("sr"):
            errs.append("related without ru or sr")
    return errs


def split_stress(e):
    """Related words, synonyms and antonyms carry stress marks in the sources. The app gets them
    bare, which it looks cards up by, and the stressed form beside: acc, synAcc, antAcc."""
    t = DECK["target"]
    for f in ("syn", "ant"):
        bare = [unstressed(w) for w in e.get(f, [])]
        if bare != e.get(f, []):
            e[f + "Acc"] = [unicodedata.normalize("NFC", w) for w in e[f]]
            e[f] = bare
    for r in e.get("related", []):
        bare = unstressed(r[t])
        if bare != r[t]:
            r[t], r["acc"] = bare, unicodedata.normalize("NFC", r[t])


def mark_languages(words):
    """Braces around the words of the other language in the texts that mix the two, so the apps
    leave Russian in Cyrillic when they show Serbian in Latin. See mixed_text.py."""
    t, n = DECK["target"], DECK["native"]
    if {t, n} != {"sr", "ru"}:
        return
    # Russian words in the notes of the deck for Serbs carry stress marks, Serbian ones none.
    marker = mixed_text.Marker(mixed_text.Lexicon.load(), stress="ru" if n == "sr" else None)
    for e in words:
        # Forms are in the language learned, their labels in the user's own.
        if e.get("forms"):
            e["forms"] = marker.mark(e["forms"], t, labels=True, lexical=False)
        # Russian notes on Serbian words are left as written: a Russian word taken for Serbian
        # would come out in Latin, which is worse than a Serbian one left in Cyrillic.
        if n != "sr":
            continue
        mark = lambda s: marker.mark(s, n)
        if e["pos"] == "compare" and e["meanings"]:
            # The title asks which of the compared words: they alone are Russian in it.
            cmp = set(mixed_text.words_of(e["cyr"] + " " + " ".join(r[t] for r in e.get("related", []))))
            e["meanings"] = [marker.mark(e["meanings"][0], n, other_words=cmp)] + [mark(m) for m in e["meanings"][1:]]
        else:
            e["meanings"] = [mark(m) for m in e["meanings"]]
        if e["pos"] == "note":
            e["cyr"] = mark(e["cyr"])
        if e.get("note"):
            e["note"] = mark(e["note"])
        if e.get("tips"):
            e["tips"] = [mark(x) for x in e["tips"]]
        for x in e.get("examples", []) + e.get("related", []):
            x[n] = mark(x[n])


# The grammar labels of both source decks in English.
EN_GRAM = {"м. р.": "m.", "ж. р.": "f.", "ср. р.": "n.", "с. р.": "n.", "мн.": "pl.", "м. р. мн.": "m. pl.",
           "ж. р. мн.": "f. pl.", "ср. р. мн.": "n. pl.", "несов.": "impf.", "сов.": "pf.", "несвр.": "impf.",
           "свр.": "pf.", "двувид.": "impf./pf.", "двовид.": "impf./pf.", "сов. и несов.": "impf./pf.",
           "сов./несов.": "impf./pf."}
CYR = re.compile(r"[А-Яа-яЁёЂђЈјЉљЊњЋћЏџ]")


def load_en():
    """English sides of the cards by key; {"skip": reason} for an article left out."""
    en = {}
    for path in sorted(DECK["en"].glob("*.jsonl")):
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                o = json.loads(line)
                en[o["key"]] = o
    return en


def to_en(e, o):
    """The card with its English side, or None with the reason it cannot have one."""
    t = DECK["target"]
    if o is None:
        return None, "no English side"
    if o.get("skip"):
        return None, None
    same = lambda a, b: unstressed(a) == unstressed(b)
    ex = [x for x in e["examples"]]
    if [x[t] for x in ex] != [x[t] for x in o.get("examples", [])] and not all(
            same(a[t], b[t]) for a, b in zip(ex, o.get("examples", []))):
        return None, "examples changed since the translation"
    rel = e.get("related", [])
    if len(rel) != len(o.get("related", [])) or not all(same(a[t], b[t]) for a, b in zip(rel, o["related"])):
        return None, "related words changed since the translation"
    if e.get("gram") and e["gram"] not in EN_GRAM:
        return None, f"no English for gram {e['gram']!r}"
    article = e["pos"] in ARTICLES
    words = o.get("meanings", []) + [x["en"] for x in o["examples"]] + ([] if article else [r["en"] for r in o["related"]])
    if any(CYR.search(re.sub(r"«[^»]*»", "", w)) for w in words if not (article and w in o.get("meanings", []))):
        return None, "Cyrillic in the English text"
    if not article and not o.get("meanings"):
        return None, "no meanings"
    if e.get("forms") and not o.get("forms"):
        return None, "forms without their English labels"
    e = dict(e, meanings=o.get("meanings", []), gram=EN_GRAM.get(e.get("gram", ""), ""), forms=o.get("forms", ""),
             note=o.get("note", ""), examples=[{t: a[t], "en": b["en"]} for a, b in zip(ex, o["examples"])],
             related=[{t: a[t], "en": b["en"]} for a, b in zip(rel, o["related"])])
    if e["pos"] == "note":
        e["cyr"] = o["title"]
    if o.get("tips"):
        e["tips"] = o["tips"]
    return e, None


def main():
    words = {}
    bad = 0
    en = load_en() if "en" in DECK else {}
    untranslated = {}
    files = [Path(f) for f in sys.argv[sys.argv.index("--files") + 1:]] if "--files" in sys.argv else sorted(SRC.glob("*.jsonl"))
    for path in files:
        for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if not line.strip():
                continue
            try:
                e = json.loads(line)
            except json.JSONDecodeError as err:
                print(f"{path.name}:{n}: bad json: {err}", file=sys.stderr)
                bad += 1
                continue
            errs = check(e) if DECK["target"] == "sr" else check_ru(e)
            for err in errs:
                print(f"{path.name}:{n}: {e.get('key')}: {err}", file=sys.stderr)
            if errs:
                bad += 1
                continue
            if e["key"] in words:
                print(f"{path.name}:{n}: duplicate key {e['key']}", file=sys.stderr)
                continue
            if "en" in DECK:
                e, why = to_en(e, en.get(e["key"]))
                if why:
                    untranslated[why] = untranslated.get(why, 0) + 1
                if e is None:
                    continue
            split_stress(e)
            words[e["key"]] = e
    accents = ROOT / "data" / "accents.tsv"
    if accents.exists() and DECK["target"] == "sr":
        for n, line in enumerate(accents.read_text(encoding="utf-8").splitlines(), 1):
            key, acc, _src = line.split("\t")
            e = words.get(key)
            bare = unicodedata.normalize("NFC", "".join(
                ch for ch in unicodedata.normalize("NFD", acc) if unicodedata.category(ch) != "Mn"))
            if e is None:
                if "en" not in DECK:
                    print(f"accents.tsv:{n}: no card {key!r}", file=sys.stderr)
            elif bare != e["cyr"]:
                print(f"accents.tsv:{n}: {acc!r} does not match {e['cyr']!r}", file=sys.stderr)
            elif acc != e["cyr"]:
                e["acc"] = unicodedata.normalize("NFC", acc)
    tips = ROOT / "data" / "tips.jsonl"
    if tips.exists() and DECK["target"] == "sr" and DECK["native"] == "ru":
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
        if MORPH and e["pos"] == "noun" and DECK["native"] == "ru":
            noun, rg = ru_noun(e["meanings"][0])
            sg = sr_gender(e["gram"])
            if sg and rg and sg != rg:
                e["ruGram"] = RU_GENDER[rg]
                if looks_alike(e["cyr"], noun):
                    e["ruNoun"] = noun
                mismatches += 1
    if MORPH is None and "--check" not in sys.argv:
        print("pymorphy3 missing (see data/requirements.txt): gender mismatches are not flagged", file=sys.stderr)
    mark_languages(out)
    out = [{"key": e["key"], **{f: e[f] for f in FIELDS if f in e}} for e in out]
    if "--check" not in sys.argv:
        OUT.parent.mkdir(parents=True, exist_ok=True)
        OUT.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"{len(out)} words ok, {bad} rejected, {mismatches} nouns differ in gender from Russian")
    for why, n in sorted(untranslated.items()):
        print(f"left out: {n} cards: {why}", file=sys.stderr)
    if "--check" not in sys.argv and DECK["target"] == "sr" and DECK["native"] == "ru":
        build_patches.write()


if __name__ == "__main__":
    main()
