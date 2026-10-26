# Разработка

## Устройство репозитория

| Папка | Что там |
|---|---|
| `app/` | Android-приложение: Kotlin и Compose |
| `web/` | веб-версия: HTML, CSS и JS без сборки |
| `data/` | исходники словаря и скрипты, которые собирают его для приложения |
| `tests/` | сценарии правил обучения и образец файла прогресса, общие для Android и веба |
| `art/` | иконка приложения: `art/izba.py` рисует её для Android и веба |
| `docs/` | описание правил обучения и эта инструкция |

## Сборка Android-приложения

Нужны JDK 17 или новее (подойдёт JBR из Android Studio) и Android SDK.

```sh
cd app
JAVA_HOME=/path/to/jdk ./gradlew assembleRelease
# app/app/build/outputs/apk/release/reci-release.apk
```

Без ключа подписи релизная сборка подписывается локальным отладочным ключом.

Чтобы подписать своим ключом:

1. Держите ключ и файл с его параметрами вне репозитория.
2. Опишите параметры в `keystore.properties`:
   ```properties
   # storeFile указывается относительно этого файла
   storeFile=release.jks
   storePassword=...
   keyAlias=...
   keyPassword=...
   ```
3. Передайте путь к этому файлу при сборке:
   ```sh
   ./gradlew -Preci.signing=/path/keystore.properties assembleRelease
   ```

## Словарь

Словарь собирается из исходников в `data/` в файл `app/app/src/main/assets/words.json`.
Готовый файл лежит в репозитории, так что для сборки приложения пересобирать словарь
не нужно.

- `data/words/*.jsonl`: карточки, по одной в строке. Формат описан в `data/SPEC.md`.
  - `0001-0500.jsonl` … `4001-4500.jsonl`: слова по частотному списку.
  - `extra-words.jsonl`, `phrases.jsonl`, `articles.jsonl`: слова вне частотного
    списка, выражения, разборы похожих слов и заметки.
- `data/accents.tsv`: ударения.
- `data/tips.jsonl`: подсказки «Полезно знать».
- `data/grammar/grammar.json`: справочник.

```sh
python3 -m venv /path/to/venv
/path/to/venv/bin/pip install -r data/requirements.txt
/path/to/venv/bin/python data/scripts/build_json.py
/path/to/venv/bin/python data/scripts/build_grammar.py
```

## Веб-версия

- Код лежит в `web/`.
- Данные берутся из Android-части, а не копируются:
  - словарь и справочник: `app/app/src/main/assets/`;
  - шрифты: `app/app/src/main/res/font/`;
  - номер версии: `app/app/build.gradle.kts`.
- Правила обучения в `web/js/repo.js` повторяют `Repo.kt`. Меняйте их вместе:
  тесты из `tests/learning-scenarios.json` покажут, если реализации разойдутся.
- Публикация: на GitHub Settings → Pages → Deploy from a branch → `main`, `/ (root)`.
  Сайт появится по адресу `https://<логин>.github.io/reci/web/`.
- Проверка у себя:
  ```sh
  python3 -m http.server     # в корне репозитория
  # затем открыть http://localhost:8000/web/
  ```

## Тесты

Общие для Android и веба данные лежат в `tests/`, и каждый файл проверяет обе реализации:

- `learning-scenarios.json`: правила обучения, описанные сценариями. Как устроены сами
  правила, рассказано в [learning.md](learning.md).
- `progress-sample.json`: образец файла прогресса. Обе версии должны его загрузить и
  сохранить такой же, иначе файл с одного устройства не откроется на другом.

| Что проверяется | Команда | Что нужно |
|---|---|---|
| Правила обучения и файл прогресса, Android | `cd app && ./gradlew testDebugUnitTest` | JDK |
| Правила обучения и файл прогресса, веб | `node --test "web/tests/*.test.mjs"` | Node.js |
| Интерфейс веб-версии | `node --test "web/tests/ui/*.test.mjs"` | Node.js и Google Chrome |
| Интерфейс Android | `cd app && ./gradlew connectedDebugAndroidTest` | эмулятор или телефон |

- Если Chrome называется не `google-chrome`, укажите путь: `CHROME=/path/to/chrome`.
- Тесты интерфейса Android ставят отладочную сборку отдельным пакетом
  `org.kostja.reci.debug`, поэтому установленное приложение и его прогресс не трогают.

### Как написать новый сценарий

Сценарий — это шаги и проверки:

```json
{
  "name": "Don't know on a new word brings it back five cards later",
  "settings": {"activeSize": 20, "categories": ["L3"]},
  "steps": [
    {"do": "next"}, {"do": "remember", "as": "a"},
    {"do": "answer", "a": "dontknow"},
    {"do": "until", "word": "a", "max": 30, "answer": "know"},
    {"expect": {"distance": 5}}
  ]
}
```

Настройки сценария (`settings`), все необязательные:

| Настройка | По умолчанию |
|---|---|
| `activeSize`: слов в работе одновременно | 20 |
| `categories`: что учим | `["L3"]` |
| `order`: 0 по частоте, 1 вперемешку, 2 по алфавиту | 0 |
| `reviewKnown`: проверять выученные слова | `true` |

Шаги:

| Шаг | Что делает |
|---|---|
| `next`, `prev` | «Дальше» и «‹» |
| `answer` | ответ на текущую карточку: `know`, `dontknow`, `long`, у статей `hide` и `later` |
| `remember` | запомнить текущую карточку под именем |
| `skim` | пролистать `count` карточек, отвечая `answer` на слова и `articles` на статьи |
| `until` | листать, пока не вернётся запомненная карточка; её расстояние попадёт в `distance` |
| `untilArticle` | листать до первой статьи |
| `advanceDays` | перемотать время вперёд |
| `set` | поменять `activeSize` посреди сценария, как в настройках |
| `fav`, `showArticlesAgain` | избранное и «Снова показывать» из настроек |
| `pick` | запомнить первое слово уровня по частоте или по алфавиту |

Проверки (`expect`): фаза, серия, состояние и срок проверки запомненного слова, текущая
карточка, расстояние, номер карточки, размер активного набора, число скрытых статей, что
было на каких карточках пролистанного отрезка и сколько на нём разных слов. Полный список — в обоих исполнителях:
`app/app/src/test/java/org/kostja/reci/LearningScenariosTest.kt` и
`web/tests/learning.test.mjs`.
