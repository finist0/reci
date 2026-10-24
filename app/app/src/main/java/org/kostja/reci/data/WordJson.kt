package org.kostja.reci.data

import android.util.JsonReader
import android.util.JsonWriter
import java.io.StringReader
import java.io.StringWriter

object WordJson {
    fun parse(s: String): Word? = try {
        JsonReader(StringReader(s)).use { read(it) }
    } catch (_: Exception) {
        null
    }

    fun read(r: JsonReader): Word {
        var key = ""
        var cyr = ""
        var acc = ""
        var pos = ""
        var gram = ""
        var ruGram = ""
        var ruNoun = ""
        var forms = ""
        var meanings = emptyList<String>()
        var level = 1
        var topic = "basic"
        var rank = 0
        var examples = emptyList<Example>()
        var syn = emptyList<String>()
        var ant = emptyList<String>()
        var related = emptyList<Related>()
        var note = ""
        var tips = emptyList<String>()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "key" -> key = r.nextString()
                "cyr" -> cyr = r.nextString()
                "acc" -> acc = r.nextString()
                "pos" -> pos = r.nextString()
                "gram" -> gram = r.nextString()
                "ruGram" -> ruGram = r.nextString()
                "ruNoun" -> ruNoun = r.nextString()
                "forms" -> forms = r.nextString()
                "meanings" -> meanings = strings(r)
                "level" -> level = r.nextInt()
                "topic" -> topic = r.nextString()
                "rank" -> rank = r.nextInt()
                "examples" -> examples = pairs(r).map { Example(it.first, it.second) }
                "syn" -> syn = strings(r)
                "ant" -> ant = strings(r)
                "related" -> related = pairs(r).map { Related(it.first, it.second) }
                "note" -> note = r.nextString()
                "tips" -> tips = strings(r)
                else -> r.skipValue()
            }
        }
        r.endObject()
        return Word(key, cyr, acc, pos, gram, ruGram, ruNoun, forms, meanings, level, topic, rank, examples, syn, ant, related, note, tips)
    }

    private fun strings(r: JsonReader): List<String> {
        val out = ArrayList<String>(4)
        r.beginArray()
        while (r.hasNext()) out += r.nextString()
        r.endArray()
        return out
    }

    private fun pairs(r: JsonReader): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>(3)
        r.beginArray()
        while (r.hasNext()) {
            var sr = ""
            var ru = ""
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "sr" -> sr = r.nextString()
                    "ru" -> ru = r.nextString()
                    else -> r.skipValue()
                }
            }
            r.endObject()
            out += sr to ru
        }
        r.endArray()
        return out
    }

    fun write(w: Word): String {
        val sw = StringWriter()
        JsonWriter(sw).use { j ->
            j.beginObject()
            j.name("key").value(w.key)
            j.name("cyr").value(w.cyr)
            j.name("acc").value(w.acc)
            j.name("pos").value(w.pos)
            j.name("gram").value(w.gram)
            j.name("ruGram").value(w.ruGram)
            j.name("ruNoun").value(w.ruNoun)
            j.name("forms").value(w.forms)
            j.name("meanings").beginArray().also { w.meanings.forEach { m -> j.value(m) } }.endArray()
            j.name("level").value(w.level.toLong())
            j.name("topic").value(w.topic)
            j.name("rank").value(w.rank.toLong())
            j.name("examples").beginArray()
            w.examples.forEach { j.beginObject().name("sr").value(it.sr).name("ru").value(it.ru).endObject() }
            j.endArray()
            j.name("syn").beginArray().also { w.syn.forEach { s -> j.value(s) } }.endArray()
            j.name("ant").beginArray().also { w.ant.forEach { s -> j.value(s) } }.endArray()
            j.name("related").beginArray()
            w.related.forEach { j.beginObject().name("sr").value(it.sr).name("ru").value(it.ru).endObject() }
            j.endArray()
            j.name("note").value(w.note)
            j.name("tips").beginArray().also { w.tips.forEach { t -> j.value(t) } }.endArray()
            j.endObject()
        }
        return sw.toString()
    }
}
