# Serbian word cards: generation spec

Audience: Russian speakers learning Serbian. All Serbian text is standard Serbian,
**ekavian**, in **Cyrillic** (the app transliterates to Latin itself). All explanations and
translations are Russian. Everything is written from scratch; do not copy any dictionary or
app content.

## Input

`data/raw/candidates.tsv`: `rank<TAB>lemma(latin)<TAB>upos<TAB>freq<TAB>sample forms`.
The list comes from a subtitle corpus lemmatized automatically, so it is noisy.

For each candidate, decide:
- **Skip**:
  - personal and place names, brand names, foreign words (`henri`, `džono`, `piter`);
  - lemmatizer garbage, onomatopoeia and filler interjections (`ha`, `eh`, `hm`, `oh`);
  - strong profanity and slurs.
- **Normalize** a Croatian, Bosnian or ijekavian lemma to standard Serbian ekavian
  (`tko` -> `ко`, `nevjerovatno` -> `невероватно`, `kćerka` -> `ћерка`). Skip it if that
  standard form is already a candidate elsewhere in the list.
- **Fix** a wrong lemma or part of speech (`reći/ADJ` is garbage, skip it).
- **Keep** function words (`бити`, `да`, `се`, `и`, `у`, `на`). They need good cards too.

Output one card per kept word. Several candidates that normalize to the same Serbian word
produce one card, with the smallest rank.

## Card format (one JSON object per line, UTF-8, no pretty-printing)

| field | content |
|---|---|
| `key` | the Cyrillic lemma; for homonyms add `#2` (`кључ`, `кључ#2`) |
| `cyr` | the Cyrillic lemma as shown |
| `pos` | `noun verb adj adv pron prep conj num part interj phrase` |
| `gram` | noun: `м. р.` / `ж. р.` / `ср. р.` (`мн.` for plural-only); verb: `несов.` / `сов.` / `двувид.`; empty otherwise |
| `forms` | short Serbian forms that a learner needs, see below; may be empty |
| `meanings` | 1-4 Russian equivalents, most common first, each a few words |
| `topic` | one of: basic people family body health home food clothes city transport travel nature weather animals time numbers colors work education money communication emotions character leisure culture tech society actions qualities abstract |
| `rank` | the candidate's rank (integer) |
| `examples` | 2 objects `{"sr", "ru"}` (3 for the most frequent 300 words when the word has distinct senses) |
| `syn` | 0-3 Serbian synonyms, only real ones |
| `ant` | 0-3 Serbian antonyms, only real ones |
| `related` | 0-3 objects `{"sr", "ru"}`: common words of the same root (`радити` -> `рад`, `радник`) |
| `note` | optional short Russian note, usually empty (see below) |

Forms:
- noun: genitive singular when it is not obvious, plural when irregular: `ген. човека; мн. људи`
- verb: present 1st and 2nd person singular, and the aspect pair: `радим, радиш; сов. урадити`
- adjective: feminine and neuter, comparative when irregular: `добра, добро; сравн. бољи`
- pronoun, numeral: key case forms if useful

Examples:
- natural everyday Serbian, 4-12 words, ekavian, Cyrillic, correct grammar and punctuation;
- the headword's form in the sentence is wrapped in square brackets: `Наша [кућа] је близу мора.`
  Mark exactly one occurrence, the inflected form as it appears;
- each example shows a different meaning or typical use where possible;
- the Russian translation is natural Russian, not word-for-word.

`note` is for things a Russian speaker gets wrong:
- false friends: `позориште` is theatre, not «позорище»; `право` also means «прямо»;
- case government: `+ ген.`;
- register: `разг.`, `книжн.`;
- a stress or meaning trap.

Keep it under 80 characters, and leave it empty when there is nothing important to say.

Topic: the main area of meaning. Use `basic` for function words and very general words,
`actions` for general verbs that fit no specific topic, and `qualities` for general
adjectives.

## Gold examples

```
{"key":"кућа","cyr":"кућа","pos":"noun","gram":"ж. р.","forms":"мн. куће","meanings":["дом","здание"],"topic":"home","rank":120,"examples":[{"sr":"Наша [кућа] је близу мора.","ru":"Наш дом недалеко от моря."},{"sr":"Идем [кући], уморан сам.","ru":"Я иду домой, я устал."}],"syn":["дом"],"ant":[],"related":[{"sr":"кућни","ru":"домашний"},{"sr":"домаћица","ru":"домохозяйка"}],"note":"«идем кући» - иду домой"}
{"key":"радити","cyr":"радити","pos":"verb","gram":"несов.","forms":"радим, радиш; сов. урадити","meanings":["работать","делать"],"topic":"work","rank":45,"examples":[{"sr":"Где [радиш] сада?","ru":"Где ты сейчас работаешь?"},{"sr":"Шта [радите] вечерас?","ru":"Что вы делаете сегодня вечером?"}],"syn":[],"ant":["одмарати се"],"related":[{"sr":"рад","ru":"работа, труд"},{"sr":"радник","ru":"рабочий"}],"note":""}
{"key":"добар","cyr":"добар","pos":"adj","gram":"","forms":"добра, добро; сравн. бољи","meanings":["хороший","добрый"],"topic":"qualities","rank":88,"examples":[{"sr":"Он је [добар] човек.","ru":"Он хороший человек."},{"sr":"[Добар] дан, како сте?","ru":"Добрый день, как поживаете?"}],"syn":["ваљан"],"ant":["лош","зао"],"related":[{"sr":"доброта","ru":"доброта"}],"note":""}
{"key":"врло","cyr":"врло","pos":"adv","gram":"","forms":"","meanings":["очень"],"topic":"basic","rank":300,"examples":[{"sr":"Ово је [врло] важно.","ru":"Это очень важно."},{"sr":"Била је [врло] љубазна.","ru":"Она была очень любезна."}],"syn":["веома","јако"],"ant":[],"related":[],"note":""}
{"key":"позориште","cyr":"позориште","pos":"noun","gram":"ср. р.","forms":"мн. позоришта","meanings":["театр"],"topic":"culture","rank":1900,"examples":[{"sr":"Вечерас идемо у [позориште].","ru":"Сегодня вечером мы идём в театр."},{"sr":"Народно [позориште] је у центру града.","ru":"Народный театр находится в центре города."}],"syn":["театар"],"ant":[],"related":[{"sr":"позоришни","ru":"театральный"}],"note":"ложный друг: не «позорище», а «театр»"}
```

## Validation

`python3 -I data/scripts/build_json.py --check` checks every file in `data/words/`. It
reports Latin letters in Serbian text, Russian-only letters (ы э ъ ё щ), a missing
`[headword]` mark, bad pos or topic values, and invalid JSON.
