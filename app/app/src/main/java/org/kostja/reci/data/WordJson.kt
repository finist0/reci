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
        var synAcc = emptyList<String>()
        var antAcc = emptyList<String>()
        val d = Decks.current
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
                "examples" -> examples = pairs(r, d).map { Example(it[0], it[1]) }
                "syn" -> syn = strings(r)
                "ant" -> ant = strings(r)
                "synAcc" -> synAcc = strings(r)
                "antAcc" -> antAcc = strings(r)
                "related" -> related = pairs(r, d).map { Related(it[0], it[1], it[2]) }
                "note" -> note = r.nextString()
                "tips" -> tips = strings(r)
                else -> r.skipValue()
            }
        }
        r.endObject()
        return Word(key, cyr, acc, pos, gram, ruGram, ruNoun, forms, meanings, level, topic, rank, examples, syn, ant, related, note, tips, synAcc, antAcc)
    }

    private fun strings(r: JsonReader): List<String> {
        val out = ArrayList<String>(4)
        r.beginArray()
        while (r.hasNext()) out += r.nextString()
        r.endArray()
        return out
    }

    // Objects keyed by language code: the learned text, its translation and the stressed form.
    private fun pairs(r: JsonReader, d: Deck): List<Array<String>> {
        val out = ArrayList<Array<String>>(3)
        r.beginArray()
        while (r.hasNext()) {
            val v = arrayOf("", "", "")
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    d.target -> v[0] = r.nextString()
                    d.native -> v[1] = r.nextString()
                    "acc" -> v[2] = r.nextString()
                    else -> r.skipValue()
                }
            }
            r.endObject()
            out += v
        }
        r.endArray()
        return out
    }

    fun write(w: Word): String {
        val d = Decks.current
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
            w.examples.forEach { j.beginObject().name(d.target).value(it.text).name(d.native).value(it.tr).endObject() }
            j.endArray()
            j.name("syn").beginArray().also { w.syn.forEach { s -> j.value(s) } }.endArray()
            j.name("ant").beginArray().also { w.ant.forEach { s -> j.value(s) } }.endArray()
            j.name("synAcc").beginArray().also { w.synAcc.forEach { s -> j.value(s) } }.endArray()
            j.name("antAcc").beginArray().also { w.antAcc.forEach { s -> j.value(s) } }.endArray()
            j.name("related").beginArray()
            w.related.forEach { j.beginObject().name(d.target).value(it.word).name(d.native).value(it.tr).name("acc").value(it.acc).endObject() }
            j.endArray()
            j.name("note").value(w.note)
            j.name("tips").beginArray().also { w.tips.forEach { t -> j.value(t) } }.endArray()
            j.endObject()
        }
        return sw.toString()
    }
}
