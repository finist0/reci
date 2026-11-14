#!/usr/bin/env python3
"""Turn the Russian subtitle word-form frequency list into a lemma frequency list.

Usage: lemmatize_ru.py <ru_50k.txt> <out.tsv> [max_forms]
Output columns: rank, lemma, POS, summed frequency, most frequent forms.
"""
import collections
import re
import sys

import pymorphy3

WORD = re.compile(r"^[а-яё]+(-[а-яё]+)?$")
# Names, places and abbreviations are not words to learn.
SKIP_TAGS = {"Name", "Surn", "Patr", "Geox", "Orgn", "Trad", "Abbr", "Init"}


def main():
    src, out = sys.argv[1], sys.argv[2]
    limit = int(sys.argv[3]) if len(sys.argv) > 3 else 30000
    morph = pymorphy3.MorphAnalyzer()
    freq = collections.Counter()
    forms = collections.defaultdict(collections.Counter)
    n = 0
    for line in open(src, encoding="utf-8"):
        w, c = line.split()
        if not WORD.match(w):
            continue
        n += 1
        if n > limit:
            break
        p = morph.parse(w)[0]
        if p.tag.grammemes & SKIP_TAGS or p.tag.POS is None:
            continue
        pos = {"ADJS": "ADJF", "PRTF": "VERB", "PRTS": "VERB", "GRND": "VERB", "INFN": "VERB",
               "COMP": "ADJF"}.get(p.tag.POS, p.tag.POS)
        lemma = p.normal_form if pos != "VERB" or p.tag.POS == "INFN" else p.inflect({"INFN"}).word if p.inflect({"INFN"}) else p.normal_form
        freq[(lemma, pos)] += int(c)
        forms[(lemma, pos)][w] += int(c)
    with open(out, "w", encoding="utf-8") as f:
        for rank, ((lemma, pos), c) in enumerate(freq.most_common(), 1):
            sample = ",".join(w for w, _ in forms[(lemma, pos)].most_common(4))
            f.write(f"{rank}\t{lemma}\t{pos}\t{c}\t{sample}\n")


if __name__ == "__main__":
    main()
