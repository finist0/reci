"""Marks the words of the other language inside a card's text: {...} around them.

A Serbian note for Russian learners quotes Russian words, a Russian gloss in the forms of a
Serbian word explains them. The apps turn Serbian Cyrillic into Latin when the user reads
Latin, and must leave the Russian words as they are, so the build tells them which is which.
The braces hold the deck's other language: the language learned inside a translation or a
note, the user's own language inside the forms of the word learned.

The language of a word is decided, in this order, by:
- letters only one of the languages has (ы, я, й... against ђ, ј, љ...);
- stress marks: in the deck for Serbs they mark Russian words only;
- the words of both decks: a word found in the text of one language only;
- its neighbours in the same clause, for a word both languages have (на, и, живот).
"""
import collections
import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCES = {"sr-ru": ROOT / "data" / "words", "ru-sr": ROOT / "data" / "ru-sr" / "words"}
UI = ROOT / "app" / "app" / "src" / "main" / "assets" / "ui.json"

RU_ONLY = set("ыэъьяюёйщ")
SR_ONLY = set("ђјљњћџ")
# Serbian has four accents and length; Russian marks the stress with the acute only.
SR_ACCENTS = set("\u030f\u0311\u0300\u0304")
ACUTE = "\u0301"
LETTER = r"[^\W\d_][\u0300-\u036f]*"
WORD = re.compile(rf"(?:{LETTER})+(?:-(?:{LETTER})+)*")
# Punctuation that ends a clause: the words on its two sides are not read together.
BREAK = re.compile(r"[.,:;!?()\[\]«»„“”\"=+/•\n—–]|\s-\s|^-|\d")
LABEL = re.compile(r"[.:]")
# Letter pairs Serbian spells with ј or џ: Russian -ие, -ее and дж.
RU_SPELLING = re.compile(r"[ие]е|дж")
NAMED = {"српск": "sr", "руск": "ru"}


def bare(word):
    d = unicodedata.normalize("NFD", word.lower())
    return unicodedata.normalize("NFC", "".join(ch for ch in d if unicodedata.category(ch) != "Mn"))


def letters(word):
    """The word's letters with й and ё kept whole: NFD would make them и and е."""
    return set(unicodedata.normalize("NFC", word.lower()))


def words_of(text):
    return [bare(m.group()) for m in WORD.finditer(text.replace("[", "").replace("]", ""))]


class Lexicon:
    """How often each word is written in each language anywhere in the two decks."""

    # A word both languages write tells which it is when one writes it this much more often.
    RATIO = 20

    def __init__(self):
        self.count = {"ru": collections.Counter(), "sr": collections.Counter()}

    @classmethod
    def load(cls, sources=SOURCES, ui=UI):
        lex = cls()
        for deck, path in sources.items():
            target, native = deck.split("-")
            for f in sorted(path.glob("*.jsonl")):
                for line in f.read_text(encoding="utf-8").splitlines():
                    if line.strip():
                        lex.add_card(json.loads(line), target, native)
        if ui.exists():
            for ru, tr in json.loads(ui.read_text(encoding="utf-8")).items():
                lex.add("ru", ru)
                lex.add("sr", tr.get("sr", ""))
        return lex

    def add(self, lang, text):
        # A text with letters of the other language mixes the two and teaches nothing.
        if not text or letters(text) & (SR_ONLY if lang == "ru" else RU_ONLY):
            return
        self.count[lang].update(words_of(text))

    def add_card(self, e, target, native):
        article = e.get("pos") in ("compare", "note")
        for x in e.get("examples", []):
            self.add(target, x.get(target, ""))
            self.add(native, x.get(native, ""))
        for r in e.get("related", []):
            self.add(target, r.get(target, ""))
            if not article:
                self.add(native, r.get(native, ""))
        if not article:
            self.add(target, e.get("cyr", ""))
            for m in e.get("meanings", []):
                self.add(native, m)
        for w in e.get("syn", []) + e.get("ant", []):
            self.add(target, w)
        self.add(native, e.get("gram", ""))

    def lang(self, word):
        ru, sr = self.count["ru"][word], self.count["sr"][word]
        if not ru and not sr or min(ru, sr) == 0 and max(ru, sr) < 2:
            return None
        if not hasattr(self, "total"):
            self.total = {k: sum(c.values()) for k, c in self.count.items()}
        ratio = (sr / self.total["sr"]) / (ru / self.total["ru"]) if ru else float("inf")
        return "sr" if ratio >= self.RATIO else "ru" if ratio <= 1 / self.RATIO else None


class Marker:
    def __init__(self, lexicon, stress=None):
        self.lex = lexicon
        # The language whose words alone carry stress marks in this deck's text, if any.
        self.stress = stress

    def spelled(self, word):
        """The language the word's spelling belongs to, or None."""
        ls = letters(word)
        # A Russian word spelled out in Serbian letters (об-јавить) keeps the letters Latin lacks.
        if ls & RU_ONLY:
            return "ru"
        if ls & SR_ONLY:
            return "sr"
        d = unicodedata.normalize("NFD", word)
        if set(d) & SR_ACCENTS:
            return "sr"
        return self.stress if self.stress and ACUTE in d else None

    def lang(self, word, lexical=True):
        """"ru", "sr" or None when the word alone does not tell."""
        if not lexical:
            return self.spelled(word)
        b = bare(word)
        return self.spelled(word) or ("ru" if RU_SPELLING.search(b) else self.lex.lang(b))

    def langs(self, text, base, other_words=None, lexical=True):
        """(match, language) of each word of text, base where nothing tells."""
        other = "ru" if base == "sr" else "sr"
        found = list(WORD.finditer(text))
        if other_words is not None:
            return [(m, other if bare(m.group()) in other_words else base) for m in found]
        known = [self.lang(m.group(), lexical) for m in found]
        words = [bare(m.group()) for m in found]
        # Words in one clause: no clause break between them.
        clause = [0] * len(found)
        for i in range(1, len(found)):
            gap = text[found[i - 1].end():found[i].start()]
            clause[i] = clause[i - 1] + (1 if BREAK.search(gap) else 0)
            # "српско стол", "руски живот": the text names the language of the word it quotes.
            # "на руском" is "in Russian" and names nothing.
            named = NAMED.get(words[i - 1][:5])
            if named and gap.strip() == "" and self.spelled(found[i].group()) in (None, named) and \
                    not (i > 1 and words[i - 2] in ("на", "у")):
                known[i] = named
        out = []
        for i, m in enumerate(found):
            lang = known[i]
            if lang is None:
                left = next((known[j] for j in range(i - 1, -1, -1) if clause[j] == clause[i] and known[j]), None)
                right = next((known[j] for j in range(i + 1, len(found)) if clause[j] == clause[i] and known[j]), None)
                sides = {left, right} - {None}
                lang = sides.pop() if len(sides) == 1 else base
            out.append((m, lang))
        return out

    def mark(self, text, base, other_words=None, labels=False, lexical=True):
        """text with {} around each run of words not in base. A text with braces is taken as
        marked by hand.

        other_words: the words of the other language, when the text has no others.
        labels: the text is the forms of a word, whose labels (ген., кратко:) and what follows
        "дословно:" are in the other language.
        lexical: whether the words of the decks tell the language of a word spelled alike in
        both; without them only its spelling and its neighbours do."""
        if not text or "{" in text:
            return text
        other = "ru" if base == "sr" else "sr"
        res = self.langs(text, base, other_words, lexical)
        if labels:
            lit = text.find("дословно:")
            res = [(m, other if lit >= 0 and m.start() >= lit or LABEL.match(text, m.end()) else lang)
                   for m, lang in res]
        out, pos, i = [], 0, 0
        while i < len(res):
            if res[i][1] == base:
                i += 1
                continue
            j = i
            # A run goes on over spaces and hyphens, not over punctuation.
            while j + 1 < len(res) and res[j + 1][1] == other and \
                    re.fullmatch(r"[ \u00a0-]*", text[res[j][0].end():res[j + 1][0].start()]):
                j += 1
            start, end = res[i][0].start(), res[j][0].end()
            out.append(text[pos:start] + "{" + text[start:end] + "}")
            pos, i = end, j + 1
        return "".join(out) + text[pos:]
