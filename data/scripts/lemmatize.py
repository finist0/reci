#!/usr/bin/env python3
"""Turn the subtitle word-form frequency list into a lemma frequency list.

Usage: lemmatize.py <sr_50k.txt> <out.tsv> [max_forms]
Output columns: lemma, upos, summed frequency, most frequent forms.
"""
import collections
import os
import re
import sys

import classla

# classla downloads its models into ~/classla_resources unless CLASSLA_RESOURCES says otherwise
RESOURCES = os.environ.get("CLASSLA_RESOURCES", os.path.expanduser("~/classla_resources"))
WORD = re.compile(r"^[a-zčćžšđ]+$")
SKIP_UPOS = {"PROPN", "PUNCT", "SYM", "X", "NUM"}


def main():
    src, out = sys.argv[1], sys.argv[2]
    limit = int(sys.argv[3]) if len(sys.argv) > 3 else 20000

    forms = []
    for line in open(src, encoding="utf-8"):
        w, n = line.split()
        if WORD.match(w):
            forms.append((w, int(n)))
        if len(forms) >= limit:
            break

    nlp = classla.Pipeline(
        "sr", type="nonstandard", processors="tokenize,pos,lemma",
        tokenize_pretokenized=True, dir=RESOURCES, use_gpu=False,
    )
    freq = collections.Counter()
    seen_forms = collections.defaultdict(list)
    batch = 2000
    for i in range(0, len(forms), batch):
        chunk = forms[i:i + batch]
        doc = nlp([[w] for w, _ in chunk])
        for (w, n), sent in zip(chunk, doc.sentences):
            tok = sent.words[0]
            if tok.upos in SKIP_UPOS:
                continue
            key = (tok.lemma.lower(), tok.upos)
            freq[key] += n
            if len(seen_forms[key]) < 5:
                seen_forms[key].append(w)
        print(f"{min(i + batch, len(forms))}/{len(forms)}", file=sys.stderr, flush=True)

    with open(out, "w", encoding="utf-8") as f:
        for (lemma, upos), n in freq.most_common():
            f.write(f"{lemma}\t{upos}\t{n}\t{','.join(seen_forms[(lemma, upos)])}\n")


if __name__ == "__main__":
    main()
