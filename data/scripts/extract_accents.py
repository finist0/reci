#!/usr/bin/env python3
"""Take accented headwords for our cards from the Wiktionary (kaikki.org) Serbo-Croatian dump.

Usage: extract_accents.py <sh.jsonl> <accents.tsv>
Appends lines (key, accented form, source) for cards that have none yet; cards whose word is not
found are listed on stderr. Phrases, comparisons and notes are skipped.
"""
import json
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
POS = {"noun": "noun", "verb": "verb", "adj": "adj", "adv": "adv", "pron": "pron", "prep": "prep",
       "conj": "conj", "num": "num", "particle": "part", "intj": "interj", "phrase": "phrase"}


def bare(s):
    return unicodedata.normalize("NFC", "".join(
        ch for ch in unicodedata.normalize("NFD", s) if unicodedata.category(ch) != "Mn"))


def main():
    found = {}
    for line in open(sys.argv[1], encoding="utf-8"):
        if '"canonical"' not in line:
            continue
        e = json.loads(line)
        word = e.get("word", "")
        if not word or not ("а" <= word[0].lower() <= "ш" or word[0].lower() in "ђјљњћџ"):
            continue
        for f in e.get("forms", []):
            if "canonical" in f.get("tags", []) and bare(f["form"]) == word:
                found.setdefault(word, []).append((POS.get(e.get("pos"), e.get("pos")), f["form"]))
                break

    cards = []
    for path in sorted((ROOT / "data" / "words").glob("*.jsonl")):
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                c = json.loads(line)
                if c["pos"] not in ("phrase", "compare", "note"):
                    cards.append((c["key"], c["cyr"], c["pos"]))
    out_path = Path(sys.argv[2])
    done = set()
    if out_path.exists():
        done = {line.split("\t", 1)[0] for line in out_path.read_text(encoding="utf-8").splitlines() if line}

    hit = miss = 0
    with open(out_path, "a", encoding="utf-8") as out:
        for key, cyr, pos in cards:
            if key in done:
                continue
            # Reflexive verbs are cards like "смејати се"; Wiktionary has the bare verb.
            head, se = (cyr[:-3], " се") if cyr.endswith(" се") else (cyr, "")
            cands = found.get(head, [])
            acc = next((a for p, a in cands if p == pos), cands[0][1] if cands else None)
            if acc:
                out.write(f"{key}\t{acc}{se}\twiktionary\n")
                hit += 1
            else:
                print(f"{key}\t{cyr}\t{pos}", file=sys.stderr)
                miss += 1
    print(f"accented {hit}, missing {miss}", file=sys.stderr)


if __name__ == "__main__":
    main()
