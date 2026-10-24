package org.kostja.reci.data

data class Example(val sr: String, val ru: String)

data class Related(val sr: String, val ru: String)

data class Word(
    val key: String,
    val cyr: String,
    // The headword with stress marks (ку̏ћа), or empty when unknown.
    val acc: String,
    val pos: String,
    val gram: String,
    // Gender of the main Russian translation when it differs from the Serbian one.
    val ruGram: String,
    // Set when the Russian translation looks alike (проблем/проблема), the worst gender trap.
    val ruNoun: String,
    val forms: String,
    val meanings: List<String>,
    val level: Int,
    val topic: String,
    val rank: Int,
    val examples: List<Example>,
    val syn: List<String>,
    val ant: List<String>,
    val related: List<Related>,
    val note: String,
    // Shown as «Полезно знать».
    val tips: List<String> = emptyList(),
)

enum class State { NEW, DONT_KNOW, UNSURE, KNOWN }

data class Progress(
    val state: State = State.NEW,
    val fav: Boolean = false,
    val seen: Int = 0,
    val last: Long = 0,
    // For learned words: when the word returns for a check, and how many checks it has passed.
    val due: Long = 0,
    val step: Int = 0,
    // Learning stage (Repo.INTRO, RECALL, LEARNED; 0 = not started) and «Знаю» answers in a row.
    val phase: Int = 0,
    val streak: Int = 0,
)

enum class Order(val label: String) {
    SEQ("По частоте"),
    RANDOM("Вперемешку"),
    ALPHA("По алфавиту"),
}

object Labels {
    val levels = linkedMapOf(
        1 to "A1 · Начальный",
        2 to "A2 · Элементарный",
        3 to "B1 · Средний",
        4 to "B2 · Выше среднего",
        5 to "C1 · Продвинутый",
    )

    val topics = linkedMapOf(
        "basic" to "Базовые слова",
        "people" to "Люди",
        "family" to "Семья",
        "body" to "Тело",
        "health" to "Здоровье",
        "home" to "Дом",
        "food" to "Еда и напитки",
        "clothes" to "Одежда",
        "city" to "Город",
        "transport" to "Транспорт",
        "travel" to "Путешествия",
        "nature" to "Природа",
        "weather" to "Погода",
        "animals" to "Животные",
        "time" to "Время",
        "numbers" to "Числа и количество",
        "colors" to "Цвета и формы",
        "work" to "Работа",
        "education" to "Учёба",
        "money" to "Деньги и покупки",
        "communication" to "Общение",
        "emotions" to "Чувства",
        "character" to "Характер",
        "leisure" to "Отдых и спорт",
        "culture" to "Культура",
        "tech" to "Техника",
        "society" to "Общество",
        "actions" to "Действия",
        "qualities" to "Качества",
        "abstract" to "Абстрактные понятия",
    )

    val sections = linkedMapOf("X:phrase" to "Выражения", "X:compare" to "Похожие слова", "X:note" to "Заметки")

    val lists = linkedMapOf(
        "M:dontknow" to "Не знаю",
        "M:known" to "Знаю",
        "M:fav" to "Избранное",
    )

    fun category(id: String): String = when {
        id.startsWith("L") -> levels[id.drop(1).toIntOrNull()] ?: id
        id.startsWith("T:") -> topics[id.drop(2)] ?: id
        id.startsWith("X:") -> sections[id] ?: id
        else -> lists[id] ?: id
    }

    private val pos = mapOf(
        "noun" to "сущ.",
        "verb" to "гл.",
        "adj" to "прил.",
        "adv" to "нар.",
        "pron" to "мест.",
        "prep" to "предл.",
        "conj" to "союз",
        "num" to "числ.",
        "part" to "част.",
        "interj" to "межд.",
        "phrase" to "выражение",
        "compare" to "похожие слова",
        "note" to "заметка",
    )

    fun pos(code: String) = pos[code] ?: code

    private val circled = "①②③④⑤⑥⑦⑧⑨"

    fun circled(i: Int) = if (i < circled.length) circled[i].toString() else "${i + 1}."
}
