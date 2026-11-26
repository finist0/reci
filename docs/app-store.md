# Описание для App Store

Готовые тексты и ответы для страницы приложения в App Store Connect на трёх языках.
Ограничения Apple указаны в скобках, длина каждого текста проверена скриптом (таблица после
текстов). Как собирается и загружается сама сборка, написано в [ios.md](ios.md).

Apple не разрешает упоминать в описании другие мобильные платформы, поэтому в текстах нет
Android.

## Языки страницы

Везде приложение учит сербскому и русскому, но каждый язык обращается к своему читателю:

- **русский** (Russian): ловушки сербского для тех, кто говорит по-русски;
- **сербский**: ловушки русского для тех, кто говорит по-сербски;
- **английский** (English U.S.): разборы и заметки, написанные для говорящих по-английски.

Сербского среди языков App Store Connect нет. В витринах Сербии, Черногории и Боснии и
Герцеговины основной язык English (U.K.), дополнительный Croatian. Поэтому:

- English (U.K.) не заводим: название в каждой локализации должно быть своим, а второго
  английского названия у нас нет. Витрина Сербии покажет English (U.S.);
- сербский текст записан латиницей и кладётся в локализацию Croatian, читатель хорватского
  его поймёт;
- кто читает на языке, для которого локализации нет, видит основной язык (Primary
  Language). Английский для этого подходит лучше русского.

Название в каждой локализации должно быть своим: имя, которое уже занято (в том числе
другой локализацией), App Store Connect не сохраняет. Поэтому на иконке везде «НЕРодная
речь», а в карточке магазина:

| Локализация | Название |
|---|---|
| Russian | НЕРодная речь |
| Croatian | Nerodna reč |
| English (U.S.) | Nerodnaya Rech |

Ключевые слова Apple считает в байтах UTF-8: буква кириллицы или латиница с диакритикой
(č, ć, š, ž, đ) занимает два байта. Слова из названия и подзаголовка Apple ищет и так, в
ключевых словах их не повторяем.

## Русский (Russian)

### Название (до 30 символов)

```
НЕРодная речь
```

### Подзаголовок (до 30 символов)

```
Учить сербский и русский
```

### Рекламный текст (до 170 символов)

Его можно менять без новой версии и без проверки.

```
Сербское или русское слово на экране блокировки: с ударением, примерами и переводом на русский, сербский или английский. Десять секунд, и оно остаётся с вами.
```

### Ключевые слова (до 100 байт, через запятую)

```
словарь,карточки,ударение,грамматика,srpski,serbian,виджет
```

### Описание (до 4000 символов)

```
Вы берёте телефон в руки десятки раз в день. НЕРодная речь превращает каждый такой момент в маленький урок сербского или русского: виджет на экране блокировки показывает слово с переводом, а круглые виджеты «Знаю» и «Не знаю» отвечают прямо оттуда. Десять секунд, и слово остаётся с вами.

Сербский можно учить с переводом на русский или английский, русский с переводом на сербский или английский.

ПОЧЕМУ ЭТО РАБОТАЕТ

• Не нужно выкраивать время. Карточка уже на экране блокировки, учёба встраивается в ваш обычный день.
• Сначала самое нужное. Слова идут в том порядке, в каком они чаще всего звучат в живой речи.
• Вспоминать, а не перечитывать. После знакомства слово нужно вспомнить по переводу. Выученное возвращается на проверку через неделю, три недели, два месяца, полгода и год.
• Забыли — повторим сразу. Слово с ответом «Не знаю» вернётся через пять карточек, пока оно ещё свежее.

ЛОВУШКИ ДЛЯ ТЕХ, КТО ГОВОРИТ ПО-РУССКИ

Сербский похож на русский, и сходство подводит. Сербские карточки с переводом на русский отмечают такие места.

• Ложные друзья: «празан» значит «пустой», а не «праздный».
• Похожие слова: русскому «договору» соответствует «уговор», а «договор» значит устную договорённость.
• Род не совпадает: «проблем» мужского рода, хотя «проблема» женского.

СЕРБСКИЙ

• 16 000 карточек: 12 500 слов, 2 500 выражений и идиом, 380 разборов похожих слов, 670 заметок о языке.
• Почти 30 000 примеров с переводом, синонимы, антонимы, связанные слова.
• Справочник по грамматике: 26 тем с таблицами склонений и спряжений.

РУССКИЙ

• 8 800 слов с ударениями, формами и примерами.
• 400 разборов похожих слов и заметок о языке и обычаях.
• Больше 18 000 примеров с переводом.

В ОБОИХ ЯЗЫКАХ

• Ударения у слов и формы: множественное число, спряжение, вид глагола.
• Уровни от A1 до C1 и 30 тем: еда, транспорт, работа, семья и другие.

ЧЕТЫРЕ ПАРЫ ЯЗЫКОВ

• Сербский с переводом на русский или английский.
• Русский с переводом на сербский или английский.
• Выученное слово остаётся выученным и с другим переводом.
• Интерфейс на русском, сербском или английском.

ЭКРАН БЛОКИРОВКИ

• Виджеты: широкий с карточкой и круглые «Знаю» и «Не знаю». Ответ сразу показывает следующее слово.
• Или обои: действие для «Быстрых команд» рисует текущую карточку картинкой, автоматизация ставит её на экран блокировки.
• Приложение показывает шаги настройки для вашей версии iOS и подсказывает, работают ли обои.

УДОБНО

• Сербский кириллицей или латиницей, подпись другим алфавитом.
• Произношение голосом телефона.
• Долгое нажатие на «Знаю» сразу отмечает знакомое слово выученным.
• Ошиблись — кнопка «назад» вернёт карточку, и ответ можно поменять.
• Режимы «только перевод» и «только слово».
• Поиск по слову и по переводу, избранное.
• Восемь цветовых тем, светлые и тёмные.
• Исправления карточек приходят сами, без обновления приложения.
• Работает и на iPad.
• Прогресс можно сохранить в файл и перенести на другой телефон или компьютер.

ЧЕСТНО

• Без регистрации и смс.
• Анонимную статистику можно отключить в настройках.
• Открытый исходный код: github.com/finist0/reci
```

## Srpski (Croatian)

### Naziv (do 30 znakova)

```
Nerodna reč
```

### Podnaslov (do 30 znakova)

```
Ruski i srpski, reč po reč
```

### Promotivni tekst (do 170 znakova)

```
Ruska ili srpska reč na zaključanom ekranu: sa akcentom, primerima i prevodom na srpski, ruski ili engleski. Deset sekundi, i reč ostaje s vama.
```

### Ključne reči (do 100 bajtova, odvojene zarezom)

```
ruski,rečnik,rječnik,kartice,akcenat,naglasak,riječi,učenje,widget,gramatika,ćirilica
```

### Opis (do 4000 znakova)

```
Telefon uzmete u ruke desetine puta dnevno. НЕРодная речь svaki taj trenutak pretvara u kratak čas ruskog ili srpskog: vidžet na zaključanom ekranu pokazuje reč sa prevodom, a okrugli vidžeti „Znam“ i „Ne znam“ odgovaraju pravo odatle. Deset sekundi, i reč ostaje s vama.

Ruski može da se uči sa prevodom na srpski ili engleski, a srpski sa prevodom na ruski ili engleski. Ime na ruskom znači „nematernji govor“.

ZAŠTO TO FUNKCIONIŠE

• Ne morate da odvajate vreme. Kartica je već na zaključanom ekranu, učenje se uklapa u vaš običan dan.
• Prvo ono najpotrebnije. Reči idu redom kojim se najčešće čuju u živom govoru.
• Prisećanje, a ne ponovno čitanje. Novu reč treba da se setite po prevodu. Naučene reči se vraćaju na proveru posle nedelju dana, tri nedelje, dva meseca, pola godine i godinu dana.
• Zaboravili ste? Reč na koju odgovorite „Ne znam“ vraća se posle pet kartica, dok je još sveža.

ZAMKE ZA ONE KOJI GOVORE SRPSKI

Ruski liči na srpski, i ta sličnost često vara. Ruske kartice sa prevodom na srpski označavaju takva mesta.

• Lažni prijatelji: ruski „живот“ je stomak, a srpskom životu odgovara „жизнь“.
• Ruski „вредный“ znači „štetan“, a ne „vredan“.
• Slične reči: srpski „sto“ je ruski „стол“, srpska „stolica“ je „стул“, a ruska „столица“ je prestonica.
• U ruskom nema „je“ u sadašnjem vremenu: „Он врач.“ znači „On je lekar.“

RUSKI

• 8.800 reči sa akcentima, oblicima i primerima.
• 400 poređenja sličnih reči i beleški o jeziku i običajima: „идти ili ехать?“
• Više od 18.000 primera sa prevodom.

SRPSKI

• 16.000 kartica: 12.500 reči, 2.500 izraza i idioma.
• Priručnik gramatike na ruskom.

U OBA JEZIKA

• Sinonimi, antonimi i srodne reči, sve sa akcentima.
• Nivoi od A1 do C1 i 30 tema: hrana, prevoz, posao, porodica i druge.

ČETIRI JEZIČKA PARA

• Ruski sa prevodom na srpski ili engleski.
• Srpski sa prevodom na ruski ili engleski.
• Naučena reč ostaje naučena i sa drugim prevodom.
• Interfejs na srpskom, ruskom ili engleskom.

ZAKLJUČANI EKRAN

• Vidžeti: široki sa karticom i okrugli „Znam“ i „Ne znam“. Odgovor odmah pokazuje sledeću reč.
• Ili pozadina: radnja za aplikaciju Shortcuts crta trenutnu karticu kao sliku, a automatizacija je stavlja na zaključani ekran.
• Aplikacija pokazuje korake podešavanja za vašu verziju iOS-a i govori da li pozadina radi.

PRAKTIČNO

• Srpski tekst ćirilicom ili latinicom.
• Izgovor glasom telefona.
• Dug pritisak na „Znam“ odmah označava poznatu reč kao naučenu.
• Pogrešili ste? Dugme „nazad“ vraća karticu, i odgovor može da se promeni.
• Pretraga po reči i po prevodu, omiljeno.
• Osam tema boja, svetle i tamne.
• Ispravke kartica stižu same, bez ažuriranja aplikacije.
• Radi i na iPad-u.
• Napredak može da se sačuva u datoteku i prenese na drugi telefon ili računar.

POŠTENO

• Bez registracije.
• Anonimna statistika može da se isključi u podešavanjima.
• Otvoren kod: github.com/finist0/reci
```

## English (U.S.)

### Name (up to 30 characters)

```
Nerodnaya Rech
```

### Subtitle (up to 30 characters)

```
Serbian and Russian flashcards
```

### Promotional text (up to 170 characters)

```
A Serbian or Russian word on your lock screen, with its stress, examples and an English translation. Ten seconds, and it stays with you. No sign-up.
```

### Keywords (up to 100 bytes, comma-separated)

```
vocabulary,learn,lock screen,widget,cyrillic,srpski,ruski,words,dictionary,grammar,stress
```

### Description (up to 4000 characters)

```
You pick up your phone dozens of times a day. Nerodnaya Rech turns each of those moments into a short Serbian or Russian lesson: a widget on the lock screen shows a word with its English translation, and the round "Know" and "Don't know" widgets answer right from there. Ten seconds, and the word stays with you.

The name is Russian for "non-native speech".

WHY IT WORKS

• No time to set aside. The card is already on your lock screen, so learning fits into your day.
• The most useful words first. Words come in the order in which they are most common in everyday speech.
• Recall, not reread. You recall each new word from its translation. Learned words come back for a check after a week, three weeks, two months, half a year and a year.
• Forgot it? A word you answer "Don't know" returns five cards later, while it is still fresh.

SERBIAN WITH ENGLISH TRANSLATIONS

• 16,000 cards: 12,500 words, 2,500 phrases and idioms.
• 380 comparisons of similar words, such as уговор or договор.
• 630 short notes on the language and customs.
• Almost 30,000 examples with translations.
• Cyrillic or Latin script.

RUSSIAN WITH ENGLISH TRANSLATIONS

• 8,800 words with stress marks, forms and examples.
• 340 comparisons and notes: идти or ехать, and why Russian has no "is" in the present tense.
• Over 18,000 examples with translations.

ALSO INSIDE

• Serbian with Russian translations and Russian with Serbian translations.
• A learned word stays learned with any translation.
• Interface in English, Serbian or Russian.
• Levels from A1 to C1 and 30 topics: food, transport, work, family and more.
• Synonyms, antonyms and related words, all stressed.

LOCK SCREEN

• Widgets: a wide one with the card and the round "Know" and "Don't know". An answer shows the next word at once.
• Or a wallpaper: an action for the Shortcuts app draws the current card as a picture, and an automation puts it on the lock screen.
• The app shows the setup steps for your iOS version and tells you whether the wallpaper works.

HANDY

• Pronunciation in the phone's voice.
• A long press on "Know" marks a word you already know as learned.
• Answered by mistake? The back button brings the card back, and the answer can be changed.
• Search by word or by translation, favorites.
• Eight color themes, light and dark.
• Card fixes arrive by themselves, without an app update.
• Works on iPad too.
• Save your progress to a file and move it to another phone or a computer.

HONEST

• No sign-up.
• Anonymous statistics can be turned off in the settings.
• Open source: github.com/finist0/reci
```

## Длина текстов

Проверено скриптом: символы Unicode, а у ключевых слов байты UTF-8.

| Текст | Предел | Russian | Croatian (сербский) | English |
|---|---|---|---|---|
| Название | 30 символов | 13 | 13 | 13 |
| Подзаголовок | 30 символов | 24 | 26 | 30 |
| Рекламный текст | 170 символов | 158 | 144 | 148 |
| Ключевые слова | 100 байт | 97 | 90 | 89 |
| Описание | 4000 символов | 3090 | 2889 | 2578 |

## Что ещё понадобится в App Store Connect

| Поле | Что указать |
|---|---|
| Основная категория | Education (Образование) |
| Дополнительная категория | Reference (Справочники) |
| Цена | бесплатно |
| Privacy Policy URL | https://finist0.github.io/reci/web/privacy.html |
| Support URL | https://github.com/finist0/reci |
| Copyright | 2026 Konstantin Khorenko |
| Вход для проверки | не нужен: в приложении нет аккаунтов |

### Скриншоты

Apple просит снимки iPhone с Dynamic Island среднего размера (6,1 и 6,3 дюйма) и, раз
приложение работает на iPad, iPad 13 дюймов. Снимки iPhone среднего размера Apple показывает
на всех iPhone. Их снимает сценарий `ios.yml`, артефакты на странице запуска:

| Устройство | Артефакт | Размер снимка | Размер в App Store |
|---|---|---|---|
| iPhone 6,9 дюйма | `ios-screenshots-iphone-17-pro-max` | 1320×2868 | 1206×2622 |
| iPad 13 дюймов | `ios-screenshots-ipad-pro-13` | 2064×2752 | 2064×2752 |

Снимки iPhone перед загрузкой уменьшаем до 1206×2622: пропорции те же. Альфа-канал
убираем.

Порядок: `3-card`, `10-lock-widgets`, `10-lock-wallpaper`, `5-grammar`, `4-search`,
`6-settings`.

Тесты снимают свежую установку на телефоне с тремя языками, и приложение само выбирает
колоду и язык интерфейса по языку телефона. Снимки каждой страницы различаются приставкой:

| Страница | Приставка | Колода | Интерфейс |
|---|---|---|---|
| Russian | нет, `3-card.png` | сербский с русским переводом | русский |
| English (U.S.) | `en-`, `en-3-card.png` | сербский с английским переводом | английский |
| Croatian (сербский) | `sr-`, `sr-3-card.png` | русский с сербским переводом | сербский, кириллица |

### Возрастной рейтинг

Все ответы None, рейтинг 4+:

- грубых слов, которые стоило бы отметить, в словаре нет;
- в приложении нет общения между людьми, покупок и доступа к интернету в браузере;
- раздел «Куда пойти учиться?» отведён под рекламу школ сербского языка.

Итоговый возраст Apple считает сама.

### Конфиденциальность приложения (App Privacy)

Что приложение отправляет, описано в политике конфиденциальности. В ответах Apple это так:

| Тип данных | Зачем | Связаны с человеком | Для слежки |
|---|---|---|---|
| Usage Data → Product Interaction (ежедневная статистика: сколько карточек, какие разделы и настройки) | Analytics | нет | нет |
| Identifiers → Device ID (случайный номер установки, который создаёт само приложение) | Analytics | нет | нет |
| Diagnostics → Crash Data (отчёт о падении, только если человек согласился) | App Functionality | нет | нет |
| Diagnostics → Other Diagnostic Data (устройство, экран и настройки в сообщениях) | App Functionality | нет | нет |
| User Content → Customer Support (текст сообщения разработчику) | App Functionality | да | нет |
| Contact Info → Email Address (только если человек сам указал её для ответа) | App Functionality | да | нет |

Tracking (слежка между приложениями и сайтами других компаний): нет.

### Шифрование

`ITSAppUsesNonExemptEncryption = NO` уже стоит в `Info.plist`: приложение пользуется только
HTTPS, поэтому App Store Connect не спрашивает про экспортные ограничения.

### Заметка для проверяющих (App Review Notes)

```
The app teaches Serbian and Russian words with flashcards: Serbian with Russian or English translations, and Russian with Serbian or English translations. No account or login is needed. The interface follows the device language (English, Serbian or Russian) and can be changed in Settings.

The main feature is on the lock screen: add the "Card" widget and the round "Know" and "Don't know" widgets to the Lock Screen. Settings -> "Card on the lock screen" shows the steps for the iOS version of the device. Answering from a round widget shows the next word in the card widget. The same switch also offers a wallpaper made by the app's Card Wallpaper action for Shortcuts.

The app sends a daily anonymous usage count (cards shown, languages and sections chosen) that can be turned off in Settings, and messages the user writes to the developer. Details: https://finist0.github.io/reci/web/privacy.html
```
