# English side of the cards (decks sr-en and ru-en)

The decks for English speakers use the cards of sr-ru (`data/words/`) and ru-sr
(`data/ru-sr/words/`) as they are: the word, its forms, the example sentences, synonyms. This
directory holds only the English side, one JSON object per card:

```
{"key":"бити","meanings":["to be"],"forms":"сам, си, је, ...; fut. бићу",
 "examples":[{"sr":"Ја [сам] студент из Новог Сада.","en":"I'm a student from Novi Sad."}, ...],
 "related":[{"sr":"би́ће","en":"being, creature"}, ...],"note":"","tips":["..."]}
```

- `sr/` holds the Serbian cards, `ru/` the Russian ones; the files are only cut by rank.
- `examples` and `related` keep the target text beside the English, so `build_json.py --deck
  sr-en` / `ru-en` notices a card changed after its translation and leaves it out with a message.
- `forms` copies every Serbian or Russian form and has English labels: gen. dat. acc. instr. loc.
  (Serbian) prep. (Russian) voc. sg. pl. m. f. n. impf. pf. comp. superl. past fut. short:
  indecl. coll. 3rd pers.; a phrase has `literally: ...` instead.
- `gram` is not stored: the build maps the Russian or Serbian label to the English one.
- `note` and `tips` are written for an English speaker: what concerns only a speaker of the other
  Slavic language (false friends, "unlike in Russian") is left out. Words of the language learned
  stay in Cyrillic; the app shows them in Latin when the Latin setting is on.
- A note (`pos` `note`) has its English title in `title`.
- `{"key":"...","skip":"reason"}` leaves out an article that makes sense only for a speaker of the
  other Slavic language.

A new or changed card of sr-ru or ru-sr needs its English side here, or the English deck leaves it
out (`build_json.py --deck sr-en` says how many and why).
