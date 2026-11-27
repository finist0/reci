#!/usr/bin/env python3
"""Check that every interface string has its translations in assets/ui.json.

The apps use the Russian text as the key: t("Настройки"), plural(n, "слово", "слова", "слов"),
and the Russian names of levels, topics and parts of speech. This script collects those keys
from the Android, iOS and web sources and lists the ones that miss a language.

Usage: ui_strings.py [--missing]   (exit status 1 when something is missing)
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TABLE = ROOT / "app" / "app" / "src" / "main" / "assets" / "ui.json"
LANGS = ("sr", "en")
SOURCES = [
    (ROOT / "app" / "app" / "src" / "main" / "java", "*.kt"),
    (ROOT / "ios" / "App", "*.swift"),
    (ROOT / "ios" / "ReciCore" / "Sources", "*.swift"),
    (ROOT / "ios" / "Widget", "*.swift"),
    (ROOT / "web" / "js", "*.js"),
]
LIT = r'"((?:[^"\\\n]|\\.)*)"'
CALL = re.compile(r'\b(?:t|tIn\([^,()]+,)\s*\(?\s*' + LIT)
PLURAL = re.compile(r'\bplural\([^,()]+,\s*' + LIT + r',\s*' + LIT + r',\s*' + LIT)
# Russian values of the label tables: "basic" to "Базовые слова", "basic": "Базовые слова".
LABEL = re.compile(r'(?:"[\w:-]+"|\b\d+|\b[A-Z_]+\()\s*(?:to|:|->|=)?\s*' + LIT)
CYR = re.compile(r"[А-Яа-яЁё]")
# The interface language choice names each language in itself, in any interface.
OWN_NAMES = {"Русский", "Српски"}


def unescape(s):
    return s.replace('\\"', '"').replace("\\n", "\n").replace("\\u00A0", " ").replace("\\u{00A0}", " ")


def keys():
    found = {}
    for base, pattern in SOURCES:
        for f in sorted(base.rglob(pattern)):
            if ".build" in f.parts:
                continue
            text = f.read_text(encoding="utf-8")
            rel = f.relative_to(ROOT)
            for m in CALL.finditer(text):
                found.setdefault(unescape(m.group(1)), rel)
            for m in PLURAL.finditer(text):
                for g in m.groups():
                    found.setdefault(unescape(g), rel)
            if f.name in ("Models.kt", "Models.swift", "data.js", "Deck.kt", "Decks.swift", "decks.js"):
                for m in LABEL.finditer(text):
                    if CYR.search(m.group(1)):
                        found.setdefault(unescape(m.group(1)), rel)
    # The newest "what's new" entry is shown through t() as well.
    news = json.loads((TABLE.parent / "whatsnew.json").read_text(encoding="utf-8"))[0]
    for item in news["items"]:
        found.setdefault(item["text"], TABLE.parent.relative_to(ROOT) / "whatsnew.json")
    # Sample words with stress marks are shown as they are.
    return {k: v for k, v in found.items() if CYR.search(k) and "\u0301" not in k and k not in OWN_NAMES}


def main():
    table = json.loads(TABLE.read_text(encoding="utf-8")) if TABLE.exists() else {}
    missing = [(k, f, [l for l in LANGS if not table.get(k, {}).get(l)]) for k, f in keys().items()]
    missing = [m for m in missing if m[2]]
    for k, f, langs in missing:
        print(f"{f}: {','.join(langs)}: {json.dumps(k, ensure_ascii=False)}")
    print(f"{len(missing)} strings miss a translation", file=sys.stderr)
    sys.exit(1 if missing else 0)


if __name__ == "__main__":
    main()
