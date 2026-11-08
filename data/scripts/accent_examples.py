#!/usr/bin/env python3
"""Put stress marks on the bracketed words of the examples, from the Wiktionary (kaikki.org) dump.

Usage: accent_examples.py <sh.jsonl> [--dry-run]

A bracketed word gets marks only when the source leaves no doubt where the stress falls:
- the word is the card's own headword, or a word a comparison or a note discusses, which takes
  the form its card has in accents.tsv;
- the inflection tables of the card's word (or, for phrases, comparisons and notes, of the words
  they mention) give the form with one stressed syllable;
- otherwise any entry of the dump that lists the form, again with one stressed syllable.
Sources that disagree, even on the kind of stress or a length mark only, leave the word bare.
Words that are often clitics (је, се, ти, да, не...) stay bare even on their own cards, as do
words already marked by hand. A bracket is marked whole or not at all,
so a phrase never shows the stress on some of its words only. Changed files are rewritten in place;
the counts go to stderr.
"""
import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
POS = {"noun": "noun", "verb": "verb", "adj": "adj", "adv": "adv", "pron": "pron", "prep": "prep",
       "conj": "conj", "num": "num", "part": "particle", "interj": "intj"}
STRESS = "̏̑̀́"
MARKS = STRESS + "̄"
CLITICS = {"je", "se", "si", "sam", "smo", "ste", "su", "ću", "ćeš", "će", "ćemo", "ćete", "li",
           "bi", "bih", "bismo", "biste", "me", "te", "ga", "ju", "nas", "vas", "ih", "mi",
           "ti", "mu", "joj", "nam", "vam", "im", "i", "a", "u", "na", "o", "s", "sa", "k", "ka",
           "za", "od", "do", "iz", "po", "pod", "nad", "pred", "kroz", "bez", "uz", "da", "ne",
           "ni", "pa"}
CYR = dict(zip("абвгдђежзијклљмнњопрстћуфхцчџш",
               ["a", "b", "v", "g", "d", "đ", "e", "ž", "z", "i", "j", "k", "l", "lj", "m", "n",
                "nj", "o", "p", "r", "s", "t", "ć", "u", "f", "h", "c", "č", "dž", "š"]))
WORD = re.compile(r"(?:[^\W\d_][\u0300-\u036f]*)+")


def nfd(s):
    return unicodedata.normalize("NFD", s)


def to_lat(s):
    return "".join(CYR.get(c, c) for c in nfd(s.lower()))


def letters(form):
    """[(Latin letter, its stress marks)] of a form. Stress marks sit on vowels and r only, so the
    acute of ć stays part of the letter."""
    out = []
    for c in nfd(to_lat(form)):
        if unicodedata.category(c) != "Mn":
            out.append([c, ""])
        elif out and c in MARKS and out[-1][0] in "aeiour":
            out[-1][1] += c
        elif out:
            out[-1][0] += c
    return [(unicodedata.normalize("NFC", l), m) for l, m in out]


def key(s):
    """Lower-case Latin without stress marks: what forms are looked up by."""
    return "".join(l for l, _ in letters(s))


def stress_at(form):
    """Positions (in letters) of the stress marks of an accented form."""
    return tuple(i for i, (_, m) in enumerate(letters(form)) if any(c in STRESS for c in m))


def index(dump):
    """(lemma key, pos) -> {form key: [accented Latin forms]}, and form key -> [those forms]."""
    by_lemma = defaultdict(lambda: defaultdict(list))
    by_form = defaultdict(list)
    skip = {"table-tags", "romanization", "inflection-template", "class"}
    for line in open(dump, encoding="utf-8"):
        e = json.loads(line)
        word = e.get("word", "")
        if not word:
            continue
        d = by_lemma[(key(word), e.get("pos"))]
        for f in e.get("forms", []):
            form = f["form"]
            if set(f.get("tags", [])) & skip or " " in form or len(stress_at(form)) != 1:
                continue
            k = key(form)
            if form not in d[k]:
                d[k].append(form)
            if form not in by_form[k]:
                by_form[k].append(form)
    return by_lemma, by_form


def pick(forms):
    """The form, when all the sources agree on it: the same syllable and the same kind of stress."""
    return forms[0] if forms and len({tuple(letters(f)) for f in forms}) == 1 else None


def apply(token, accented):
    """The marks of an accented form put on the same letters of a (Cyrillic) token."""
    owner = [i for i, c in enumerate(token) for _ in CYR.get(c.lower(), c.lower())]
    acc = letters(accented)
    if len(acc) != len(owner):
        return None
    marks = defaultdict(str)
    for i, (_, m) in enumerate(acc):
        marks[owner[i]] += m
    return unicodedata.normalize("NFC", "".join(nfd(c) + marks[i] for i, c in enumerate(token)))


def lemmas(card):
    """Lemma keys whose tables are searched first for the card's examples."""
    if card["pos"] in ("compare", "note", "phrase"):
        words = [w for r in card["related"] for w in WORD.findall(r["sr"])]
        if card["pos"] == "phrase":
            words += WORD.findall(card["cyr"])
        return [(key(w), None) for w in words]
    head = card["cyr"][:-3] if card["cyr"].endswith(" се") else card["cyr"]
    return [(key(head), POS.get(card["pos"]))]


def main():
    dump = sys.argv[1]
    dry = "--dry-run" in sys.argv
    acc = {}
    for line in (ROOT / "data" / "accents.tsv").read_text(encoding="utf-8").splitlines():
        k, a, _ = line.split("\t")
        acc[k] = a
    by_lemma, by_form = index(dump)
    by_key = defaultdict(list)
    for (lem, pos), d in by_lemma.items():
        by_key[lem].append((pos, d))
    cards = [json.loads(line) for path in sorted((ROOT / "data" / "words").glob("*.jsonl"))
             for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    heads = {}
    for c in cards:
        if c["key"] in acc:
            heads.setdefault(key(c["cyr"]), acc[c["key"]])

    stat = Counter()
    for path in sorted((ROOT / "data" / "words").glob("*.jsonl")):
        lines = path.read_text(encoding="utf-8").splitlines()
        changed = False
        for n, line in enumerate(lines):
            if not line.strip():
                continue
            card = json.loads(line)
            own = [d for lem, pos in lemmas(card) for p, d in by_key.get(lem, []) if pos in (None, p)]
            if card["pos"] in ("compare", "note"):
                # A word the article discusses is stressed as on its own card.
                talked = {key(r["sr"]): heads[key(r["sr"])] for r in card["related"] if key(r["sr"]) in heads}
            else:
                talked = {key(card["cyr"].removesuffix(" се")): acc[card["key"]]} if card["key"] in acc else {}

            def mark(m):
                token = m.group(0)
                k = key(token)
                if any(c in MARKS for c in nfd(token)):
                    stat["already"] += 1
                    return token
                if k in CLITICS:
                    stat["clitic"] += 1
                    return token
                form = talked.get(k)
                if form:
                    stat["headword"] += 1
                if not form:
                    form = pick([f for d in own for f in d.get(k, [])])
                    if form:
                        stat["own"] += 1
                if not form:
                    form = pick(by_form.get(k, []))
                    if form:
                        stat["any"] += 1
                out = form and apply(token, form)
                if not out:
                    stat["missing"] += 1
                    return token
                return out

            def bracket(b):
                miss = stat["missing"]
                out = WORD.sub(mark, b.group(1))
                if stat["missing"] != miss:
                    stat["partial"] += 1
                    return b.group(0)
                stat["done"] += 1
                return "[" + out + "]"

            for ex in card["examples"]:
                new = re.sub(r"\[([^\]]*)\]", bracket, ex["sr"])
                if new != ex["sr"]:
                    ex["sr"] = new
                    changed = True
            lines[n] = json.dumps(card, ensure_ascii=False, separators=(",", ":"))
        if changed and not dry:
            path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(dict(stat), file=sys.stderr)


if __name__ == "__main__":
    main()
