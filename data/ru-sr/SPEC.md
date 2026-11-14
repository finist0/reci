# Russian word cards for Serbian speakers: generation spec

Audience: native Serbian speakers learning Russian. The Russian is standard modern Russian;
everything addressed to the learner (meanings, translations, notes, grammar labels) is standard
Serbian, **ekavian**, in **Cyrillic** (the app transliterates it to Latin itself). Everything is
written from scratch; do not copy any dictionary or app content.

These cards are not the Serbian deck turned around. Write each card for what a Serb needs to
learn about this Russian word: the words look close, so the value is in where they differ.

## Input

`candidates-NNN.tsv`: `rank<TAB>lemma<TAB>POS<TAB>freq<TAB>sample forms`, from a subtitle corpus
lemmatized automatically (pymorphy3), so it is noisy.

For each candidate decide:
- **Skip** personal and place names, brands, lemmatizer garbage (`рокк`), filler interjections
  (`ой`, `ах`, `эх`, `хм`), strong profanity and slurs.
- **Fix** a wrong lemma or part of speech: a verb comes as its infinitive, a participle used as an
  adjective is an adjective, `это` is one card.
- **Keep** function words (`быть`, `что`, `в`, `не`, `и`, `же`, `ли`): Serbs need them most.

One card per kept word. Two candidates with the same lemma make one card with the smaller rank.

## Card format (one JSON object per line, UTF-8, no pretty-printing)

| field | content |
|---|---|
| `key` | the Russian lemma without stress marks, with ё where it is written (`ещё`); homonyms get `#2` |
| `cyr` | the same lemma as shown |
| `acc` | `cyr` with the stress mark (see Stress) |
| `pos` | `noun verb adj adv pron prep conj num part interj` |
| `gram` | noun: `м. р.` / `ж. р.` / `с. р.` (`мн.` for plural-only); verb: `несвр.` / `свр.` / `двовид.`; empty otherwise |
| `forms` | short Russian forms a learner needs, with Serbian labels, see below; may be empty |
| `meanings` | 1-4 Serbian equivalents, most common first, each a few words |
| `topic` | one of: basic people family body health home food clothes city transport travel nature weather animals time numbers colors work education money communication emotions character leisure culture tech society actions qualities abstract |
| `rank` | the candidate's rank (integer) |
| `examples` | 2 objects `{"ru", "sr"}` (3 for ranks under 300 when the word has distinct senses) |
| `syn` | 0-3 Russian synonyms, only real ones, stressed |
| `ant` | 0-3 Russian antonyms, only real ones, stressed |
| `related` | 0-3 objects `{"ru", "sr"}`: common Russian words of the same root, stressed (`работать` -> `рабо́та`, `рабо́чий`) |
| `note` | optional short Serbian note, usually empty (see below) |

Forms (Russian words, Serbian labels; stress marks on the forms):
- noun: genitive singular when the stress moves or the stem changes, plural when irregular:
  `ген. отца́; мн. отцы́`, `мн. лю́ди`;
- verb: 1st and 2nd person singular present (future for perfective) and the aspect pair:
  `пишу́, пи́шешь; свр. написа́ть`, `сде́лаю, сде́лаешь; несвр. де́лать`;
- adjective: the comparative when irregular, short forms when the stress moves:
  `комп. лу́чше`, `кратко: хоро́ш, хороша́`;
- pronoun, numeral: key case forms if useful.

Examples:
- natural everyday Russian, 4-12 words, correct grammar and punctuation;
- the headword's form in the sentence is wrapped in square brackets: `Я иду [домо́й].`
  Mark exactly one occurrence, the inflected form as it appears;
- the bracketed words carry stress marks, nothing else in the sentence does;
- each example shows a different meaning or typical use where possible;
- the Serbian translation is natural Serbian, not word-for-word.

## Stress

- A combining acute U+0301 right after the stressed vowel: `рабо́та`, `[пи́шешь]`.
- `ё` is always stressed and takes no mark: `ещё`, `[пришёл]`.
- Words of one syllable take no mark: `дом`, `[был]`. Every word of two or more syllables in
  `acc`, the brackets, `syn`, `ant`, `related.ru` and `forms` has exactly one mark.
- Use the standard dictionary stress (Зализняк); where two are allowed, the more common one.

## Notes

`note` is in Serbian, under 80 characters, and only for what a Serb gets wrong:
- false friends: `живот` is «стомак», not «живот»; `вредный` is «штетан, неваљао», not «вредан»;
  `позор` is «срамота»; `понос` is «пролив», not «понос»; `час` is «сат»; `стол` is «сто», `стул`
  is «столица»; `неделя` is also «седмица»; `право` / `прямо`;
- case government that differs from Serbian: `+ дат.`, `+ инстр.`;
- a stress trap (moving stress), the reading of `о` when unstressed;
- register: `разг.`, `книжн.`, `груб.`.

Leave it empty when there is nothing important to say. Words the same in both languages and
meaning (`вода`, `брат`) need no note; keep their cards short.

Topic: the main area of meaning. `basic` for function words and very general words, `actions`
for general verbs, `qualities` for general adjectives.

## Gold examples

```
{"key":"дом","cyr":"дом","acc":"дом","pos":"noun","gram":"м. р.","forms":"мн. дома́","meanings":["кућа","дом"],"topic":"home","rank":120,"examples":[{"ru":"Наш [дом] недалеко от моря.","sr":"Наша кућа је близу мора."},{"ru":"Я иду [домо́й], я устал.","sr":"Идем кући, уморан сам."}],"syn":[],"ant":[],"related":[{"ru":"дома́шний","sr":"кућни"},{"ru":"домохозя́йка","sr":"домаћица"}],"note":"мн. дома́ с нагласком на крају"}
{"key":"работать","cyr":"работать","acc":"рабо́тать","pos":"verb","gram":"несвр.","forms":"рабо́таю, рабо́таешь; свр. порабо́тать","meanings":["радити"],"topic":"work","rank":45,"examples":[{"ru":"Где ты сейчас [рабо́таешь]?","sr":"Где сада радиш?"},{"ru":"Лифт опять не [рабо́тает].","sr":"Лифт опет не ради."}],"syn":["труди́ться"],"ant":["отдыха́ть"],"related":[{"ru":"рабо́та","sr":"посао, рад"},{"ru":"рабо́чий","sr":"радник; радни"}],"note":"«делать» је радити нешто: что ты делаешь? - шта радиш?"}
{"key":"живот","cyr":"живот","acc":"живо́т","pos":"noun","gram":"м. р.","forms":"ген. живота́","meanings":["стомак","трбух"],"topic":"body","rank":2100,"examples":[{"ru":"У меня болит [живо́т].","sr":"Боли ме стомак."},{"ru":"Он лёг на [живо́т].","sr":"Легао је на стомак."}],"syn":[],"ant":[],"related":[{"ru":"живо́тный","sr":"животињски; трбушни"}],"note":"лажни пријатељ: није «живот» (то је «жизнь»), него стомак"}
{"key":"хороший","cyr":"хороший","acc":"хоро́ший","pos":"adj","gram":"","forms":"комп. лу́чше; кратко: хоро́ш, хороша́","meanings":["добар","леп"],"topic":"qualities","rank":88,"examples":[{"ru":"Он [хоро́ший] человек.","sr":"Он је добар човек."},{"ru":"Сегодня [хоро́шая] погода.","sr":"Данас је лепо време."}],"syn":["до́брый"],"ant":["плохо́й"],"related":[{"ru":"хорошо́","sr":"добро"}],"note":""}
{"key":"очень","cyr":"очень","acc":"о́чень","pos":"adv","gram":"","forms":"","meanings":["веома","врло","много"],"topic":"basic","rank":300,"examples":[{"ru":"Это [о́чень] важно.","sr":"Ово је веома важно."},{"ru":"Я тебя [о́чень] люблю.","sr":"Много те волим."}],"syn":["весьма́"],"ant":[],"related":[],"note":""}
```

## Validation

From the repository root:
`../reci.serbian.local/venv/bin/python data/scripts/build_json.py --deck ru-sr --check --files <your files>`
reports every problem; fix them until it reports 0 rejected.
