#!/usr/bin/env python3
"""Stress marks for the Serbian words that still lack them: headwords, bracketed example words,
related words, synonyms and antonyms.

Usage:
  accent_rest.py collect <sh.jsonl> <dir> [chunk size]
      Marks what the Wiktionary (kaikki.org) dump agrees on, in place, and writes the rest to
      <dir>/todo-NNN.jsonl for a person or a model to accent.
  accent_rest.py check <dir> <NNN>
      Lists the answers in <dir>/done-NNN.jsonl that apply would reject, and the missing ids.
  accent_rest.py apply <dir> <sh.jsonl>
      Reads <dir>/done-NNN.jsonl ({"id", "acc"} per line), restresses every word the dump
      knows, checks every answer and writes the good ones: headwords to data/accents.tsv, the
      rest into the cards.
"""
import json
import re
import sys
import unicodedata
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from accent_examples import CLITICS, MARKS, STRESS, WORD, index, key, letters, nfd, pick, apply  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
WORDS = ROOT / "data" / "words"
ACCENTS = ROOT / "data" / "accents.tsv"
VOWELS = set("aeiour")


def bare(s):
    return unicodedata.normalize("NFC", "".join(c for c in nfd(s) if c not in MARKS))


def marked(s):
    return any(c in MARKS for c in nfd(s))


def words_ok(text):
    """Every word that is not a clitic carries one stress mark, where Serbian allows it: a falling
    accent only on the first syllable, a rising one never on the last, so a word of one syllable
    has a falling one."""
    for m in WORD.finditer(text):
        token = m.group(0)
        lets = letters(token)
        syl = [i for i, (l, _) in enumerate(lets) if l in VOWELS and not (l == "r" and _between_vowels(lets, i))]
        stressed = [(i, s) for i, (_, ms) in enumerate(lets) for s in ms if s in STRESS]
        if not stressed:
            if key(token) in CLITICS or not syl:
                continue
            return False
        if len(stressed) != 1:
            return False
        i, s = stressed[0]
        if i not in syl:
            return False
        n = syl.index(i)
        if s in "̏̑" and n != 0:
            return False
        if s in "̀́" and n == len(syl) - 1:
            return False
    return True


def _between_vowels(lets, i):
    near = [lets[j][0] for j in (i - 1, i + 1) if 0 <= j < len(lets)]
    return any(c in "aeiou" for c in near)


# The cards whose stress this script fills; other files may still be in the making.
ONLY = None


def load_cards():
    files = {}
    for path in sorted(WORDS.glob("*.jsonl")):
        if ONLY is not None and path.name not in ONLY:
            continue
        files[path.name] = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    return files


def save_cards(files):
    for name, cards in files.items():
        (WORDS / name).write_text("".join(json.dumps(c, ensure_ascii=False, separators=(",", ":")) + "\n"
                                          for c in cards), encoding="utf-8")


def load_accents():
    acc = {}
    for line in ACCENTS.read_text(encoding="utf-8").splitlines():
        k, a, src = line.split("\t")
        acc[k] = (a, src)
    return acc


def headword(card):
    """The Serbian text a card's headword shows: the words a comparison sets side by side."""
    return card["cyr"]


def lookups(dump, files, acc):
    """Accented forms the dump agrees on: of a lemma (stressed as on its own card first), and of
    any inflected form."""
    by_lemma, by_form = index(dump)
    heads = {}
    for cards in files.values():
        for c in cards:
            if c["key"] in acc and " " not in c["cyr"] and c["pos"] not in ("phrase", "compare", "note"):
                heads.setdefault(key(c["cyr"]), set()).add(acc[c["key"]][0])
    cites = {}
    for (lem, _), d in by_lemma.items():
        cites.setdefault(lem, []).extend(d.get(lem, []))

    def lemma_form(k):
        own = heads.get(k)
        if own and len(own) == 1:
            return next(iter(own))
        return pick(cites.get(k, [])) or pick(by_form.get(k, []))

    def any_form(k):
        return pick(by_form.get(k, []))
    return lemma_form, any_form


def collect(dump, out, size):
    files = load_cards()
    acc = load_accents()
    lemma_form, _ = lookups(dump, files, acc)

    stat = Counter()

    def mark_lemma(text):
        """The text with every word stressed from the dump, or None when a word is unknown."""
        def one(m):
            token = m.group(0)
            if marked(token) or key(token) in CLITICS:
                return token
            form = lemma_form(key(token))
            res = form and apply(token, form)
            if not res:
                raise LookupError
            return res
        try:
            return WORD.sub(one, text)
        except LookupError:
            return None

    todo = []
    for name, cards in files.items():
        for n, c in enumerate(cards):
            meaning = "; ".join(c.get("meanings", []))
            ctx = f"{c['cyr']} — {meaning}"
            if c["pos"] != "note" and c["key"] not in acc:
                todo.append({"id": f"{name}:{n}:head", "kind": "head", "text": headword(c), "ctx": meaning})
            for i, ex in enumerate(c.get("examples", [])):
                b = re.search(r"\[([^\]]*)\]", ex["sr"])
                if b and not marked(b.group(1)) and any(key(w) not in CLITICS for w in WORD.findall(b.group(1))):
                    todo.append({"id": f"{name}:{n}:ex:{i}", "kind": "example", "text": b.group(1),
                                 "ctx": ex["sr"]})
            for field in ("syn", "ant"):
                for i, s in enumerate(c.get(field, [])):
                    if marked(s):
                        continue
                    res = mark_lemma(s)
                    if res and res != s:
                        c[field][i] = res
                        stat[field + " dump"] += 1
                    elif not res:
                        todo.append({"id": f"{name}:{n}:{field}:{i}", "kind": "word", "text": s, "ctx": ctx})
            for i, r in enumerate(c.get("related", [])):
                if marked(r["sr"]):
                    continue
                res = mark_lemma(r["sr"])
                if res and res != r["sr"]:
                    r["sr"] = res
                    stat["related dump"] += 1
                elif not res:
                    todo.append({"id": f"{name}:{n}:rel:{i}", "kind": "word", "text": r["sr"],
                                 "ctx": f"{r['ru']} (на карточке {ctx})"})
    save_cards(files)
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    for j in range(0, len(todo), size):
        with open(out / f"todo-{j // size:03d}.jsonl", "w", encoding="utf-8") as f:
            for t in todo[j:j + size]:
                f.write(json.dumps(t, ensure_ascii=False) + "\n")
    stat.update(t["kind"] + " todo" for t in todo)
    print(dict(stat), file=sys.stderr)


def apply_done(out, dump):
    global ONLY
    ONLY = {line.split('"id": "')[1].split(":")[0] for p in Path(out).glob("todo-*.jsonl")
            for line in p.read_text(encoding="utf-8").splitlines()}
    files = load_cards()
    acc = load_accents()
    lemma_form, any_form = lookups(dump, files, acc)

    def from_dump(text, kind):
        """The answer with every word the dump knows stressed as the dump says."""
        def one(m):
            token = m.group(0)
            k = key(token)
            if k in CLITICS:
                return token
            form = any_form(k) if kind == "example" else lemma_form(k)
            res = form and apply(bare(token), form)
            if res and res != token:
                stat["dump"] += 1
            return res or token
        return WORD.sub(one, text)
    todo = {}
    for p in sorted(Path(out).glob("todo-*.jsonl")):
        for line in p.read_text(encoding="utf-8").splitlines():
            t = json.loads(line)
            todo[t["id"]] = t
    stat = Counter()
    bad = []
    for p in sorted(Path(out).glob("done-*.jsonl")):
        for line in p.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            d = json.loads(line)
            t = todo.get(d.get("id"))
            a = unicodedata.normalize("NFC", d.get("acc", ""))
            if not t:
                stat["unknown id"] += 1
                continue
            # The dump's forms of a compound can break the rules; then the answer stays as given.
            if words_ok(d2 := from_dump(a, t["kind"])):
                a = d2
            if bare(a) != bare(t["text"]) or not words_ok(a):
                bad.append((t["id"], t["text"], a))
                continue
            name, n, kind, *rest = t["id"].split(":")
            c = files[name][int(n)]
            if kind == "head":
                acc[c["key"]] = (a, "llm")
            elif kind == "ex":
                ex = c["examples"][int(rest[0])]
                ex["sr"] = ex["sr"].replace("[" + t["text"] + "]", "[" + a + "]", 1)
            elif kind == "rel":
                c["related"][int(rest[0])]["sr"] = a
            else:
                c[kind][int(rest[0])] = a
            stat[kind] += 1
    save_cards(files)
    ACCENTS.write_text("".join(f"{k}\t{a}\t{s}\n" for k, (a, s) in acc.items()), encoding="utf-8")
    with open(Path(out) / "rejected.tsv", "w", encoding="utf-8") as f:
        for row in bad:
            f.write("\t".join(row) + "\n")
    stat["rejected"] = len(bad)
    print(dict(stat), file=sys.stderr)


def check_done(out, num):
    todo = [json.loads(line) for line in (Path(out) / f"todo-{num}.jsonl").read_text(encoding="utf-8").splitlines()]
    done = {}
    path = Path(out) / f"done-{num}.jsonl"
    for line in path.read_text(encoding="utf-8").splitlines() if path.exists() else []:
        if line.strip():
            d = json.loads(line)
            done[d["id"]] = unicodedata.normalize("NFC", d.get("acc", ""))
    bad = 0
    for t in todo:
        a = done.get(t["id"])
        if a is None:
            print(f"missing\t{t['id']}\t{t['text']}")
        elif bare(a) != bare(t["text"]):
            print(f"letters differ\t{t['id']}\t{t['text']}\t{a}")
        elif not words_ok(a):
            print(f"bad stress\t{t['id']}\t{t['text']}\t{a}")
        else:
            continue
        bad += 1
    print(f"{len(todo) - bad} ok, {bad} to fix", file=sys.stderr)


if __name__ == "__main__":
    if sys.argv[1] == "collect":
        collect(sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 400)
    elif sys.argv[1] == "check":
        check_done(sys.argv[2], sys.argv[3])
    elif sys.argv[1] == "apply":
        apply_done(sys.argv[2], sys.argv[3])
    else:
        sys.exit(__doc__)
