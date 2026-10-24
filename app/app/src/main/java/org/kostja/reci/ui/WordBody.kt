package org.kostja.reci.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kostja.reci.R
import org.kostja.reci.data.Grammar
import org.kostja.reci.data.Labels
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.Speaker
import org.kostja.reci.data.Word

// "[x]" marks the headword inside an example sentence.
fun highlighted(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    var rest = text
    while (true) {
        val open = rest.indexOf('[')
        val close = if (open >= 0) rest.indexOf(']', open) else -1
        if (open < 0 || close < 0) {
            append(rest)
            break
        }
        append(rest.substring(0, open))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(rest.substring(open + 1, close)) }
        rest = rest.substring(close + 1)
    }
}

fun plain(text: String) = text.replace("[", "").replace("]", "")

@Composable
fun WordBody(
    w: Word,
    slide: Boolean,
    onGrammar: ((String) -> Unit)? = null,
    // The Serbian side is hidden until onReveal, so the user can recall it from the translation.
    hideWord: Boolean = false,
    onReveal: () -> Unit = {},
) {
    if (w.pos == "compare" || w.pos == "note") {
        ArticleBody(w, onGrammar)
        return
    }
    val p = LocalPalette.current
    var covered by remember(w.key, slide) { mutableStateOf(slide) }
    // Phrases are several words long and would wrap badly at the headword size.
    val size = if (w.pos == "phrase") (Prefs.wordSize * 0.72f).toInt() else Prefs.wordSize

    if (hideWord) {
        RecallBody(w, onReveal)
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Prefs.secondary(w.cyr)?.let {
            Text(it, fontSize = 15.sp, color = p.secondary, modifier = Modifier.padding(end = 40.dp))
        }
        Text(
            Prefs.headword(w),
            fontFamily = Serif,
            fontSize = size.sp,
            lineHeight = (size * 1.2f).sp,
            fontWeight = FontWeight.SemiBold,
            color = p.word,
            modifier = Modifier.padding(end = 40.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(p.highlight)
                    .clickable { Speaker.speak(w.cyr) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_volume), "Произнести", tint = p.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Grammar(w)
                if (w.forms.isNotBlank()) {
                    Text(Prefs.primary(w.forms), fontSize = 14.sp, color = p.secondary, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (onGrammar != null) GrammarLinks(w, onGrammar)

        Spacer(Modifier.height(18.dp))
        Box {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                w.meanings.forEachIndexed { i, m -> Meaning(i + 1, m) }
            }
            if (covered) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.highlight)
                        .clickable { covered = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Показать перевод", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.accent)
                }
            }
        }
        Details(w, onGrammar)
    }
}

@Composable
private fun Meaning(n: Int, text: String) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(p.highlight),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.accent)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 19.sp, color = p.meaning, lineHeight = 25.sp)
    }
}

@Composable
private fun Grammar(w: Word) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(Labels.pos(w.pos), fontSize = 14.sp, color = p.secondary)
        if (w.gram.isBlank()) return@Row
        Spacer(Modifier.width(8.dp))
        if (w.ruGram.isBlank()) {
            Text(
                w.gram,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (w.pos == "noun") p.word else p.secondary,
            )
        } else {
            val strong = w.ruNoun.isNotBlank()
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(w.gram) }
                    if (strong) append("  ≠ рус. «${w.ruNoun}» ${w.ruGram}") else append("  · в рус. ${w.ruGram}")
                },
                fontSize = 13.sp,
                color = if (strong) Color.White else MarkDontKnow,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MarkDontKnow.copy(alpha = if (strong) 1f else 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GrammarLinks(w: Word, onGrammar: (String) -> Unit) {
    val p = LocalPalette.current
    val links = Grammar.linksFor(w)
    val compares = Repo.comparesFor(w)
    if (links.isEmpty() && compares.isEmpty()) return
    FlowRow(
        Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (c in compares) {
            Text(
                "≈ " + c.meanings.firstOrNull().orEmpty().ifBlank { c.cyr },
                fontSize = 12.sp,
                color = p.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(p.highlight)
                    .clickable { onGrammar(c.key) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        for (id in links) {
            val topic = Grammar.topic(id) ?: continue
            Text(
                "§ " + topic.title,
                fontSize = 12.sp,
                color = p.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, p.accent.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .clickable { onGrammar(id) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = LocalPalette.current.accent,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(w: Word, onGrammar: ((String) -> Unit)?) {
    // "w:" marks a word key for the same navigation callback that opens grammar topics.
    val open = { sr: String ->
        val target = Repo.wordByCyr(sr)
        if (target != null && onGrammar != null && target.key != w.key) onGrammar("w:" + target.key) else Speaker.speak(sr)
    }
    val p = LocalPalette.current
    if (w.examples.isNotEmpty()) {
        HorizontalDivider(Modifier.padding(vertical = 20.dp), color = p.divider)
        SectionLabel("Примеры")
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for (e in w.examples) {
                Row(
                    Modifier
                        .height(IntrinsicSize.Min)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { Speaker.speak(plain(e.sr)) }
                ) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(p.highlight))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(highlighted(Prefs.primary(e.sr), p.accent), fontFamily = Serif, fontSize = 17.sp, color = p.text, lineHeight = 24.sp)
                        Text(e.ru, fontSize = 14.sp, color = p.secondary, lineHeight = 19.sp)
                    }
                }
            }
        }
    }

    if (w.syn.isNotEmpty() || w.ant.isNotEmpty()) {
        HorizontalDivider(Modifier.padding(vertical = 20.dp), color = p.divider)
        if (w.syn.isNotEmpty()) {
            SectionLabel("Синонимы")
            Chips(w.syn, p.word, p.panel, open)
        }
        if (w.syn.isNotEmpty() && w.ant.isNotEmpty()) Spacer(Modifier.height(14.dp))
        if (w.ant.isNotEmpty()) {
            SectionLabel("Антонимы")
            Chips(w.ant, p.accent, p.highlight, open)
        }
    }

    if (w.related.isNotEmpty()) {
        HorizontalDivider(Modifier.padding(vertical = 20.dp), color = p.divider)
        SectionLabel("Однокоренные")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in w.related) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = p.word)) { append(Prefs.primary(r.sr)) }
                        if (Repo.wordByCyr(r.sr) != null) withStyle(SpanStyle(color = p.accent)) { append(" ›") }
                        withStyle(SpanStyle(color = p.faint)) { append("  —  ") }
                        append(r.ru)
                    },
                    fontSize = 15.sp,
                    color = p.text,
                    modifier = Modifier.clickable { open(r.sr) },
                )
            }
        }
    }

    if (w.note.isNotBlank() || w.tips.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(p.panel)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Полезно знать", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.secondary)
            for (t in listOfNotNull(w.note.takeIf { it.isNotBlank() }) + w.tips) {
                Row {
                    Text("•", fontSize = 15.sp, color = p.accent, modifier = Modifier.width(14.dp))
                    Text(t, fontSize = 15.sp, color = p.text, lineHeight = 21.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(items: List<String>, color: Color, bg: Color, open: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (s in items) {
            Text(
                Prefs.primary(s) + if (Repo.wordByCyr(s) != null) " ›" else "",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable { open(s) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

// Comparisons ("compare") and short notes ("note") share the card but not the word layout.
@Composable
private fun ArticleBody(w: Word, onGrammar: ((String) -> Unit)?) {
    val p = LocalPalette.current
    val open = { sr: String ->
        val target = Repo.wordByCyr(sr)
        if (target != null && onGrammar != null) onGrammar("w:" + target.key) else Speaker.speak(sr)
    }
    val compare = w.pos == "compare"
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(if (compare) "Похожие слова" else "Заметка")
        Text(
            if (compare) w.meanings.firstOrNull().orEmpty().ifBlank { Prefs.primary(w.cyr) } else w.cyr,
            fontFamily = Serif,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
            color = p.word,
            modifier = Modifier.padding(end = 40.dp),
        )
        Spacer(Modifier.height(16.dp))
        if (compare) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (r in w.related) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.panel)
                            .clickable { open(r.sr) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Text(
                                Prefs.primary(r.sr) + if (Repo.wordByCyr(r.sr) != null) " ›" else "",
                                fontFamily = Serif,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = p.word,
                            )
                            Text(r.ru, fontSize = 14.sp, color = p.text, lineHeight = 19.sp)
                        }
                    }
                }
            }
            if (w.note.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(w.note, fontSize = 16.sp, color = p.text, lineHeight = 23.sp)
            }
        } else {
            Text(w.note, fontSize = 16.sp, color = p.text, lineHeight = 23.sp)
            if (w.related.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Слова")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (r in w.related) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = p.word)) { append(Prefs.primary(r.sr)) }
                                withStyle(SpanStyle(color = p.faint)) { append("  —  ") }
                                append(r.ru)
                            },
                            fontSize = 15.sp,
                            color = p.text,
                            modifier = Modifier.clickable { open(r.sr) },
                        )
                    }
                }
            }
        }
        if (w.examples.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 20.dp), color = p.divider)
            SectionLabel("Примеры")
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (e in w.examples) {
                    Column(Modifier.clickable { Speaker.speak(plain(e.sr)) }) {
                        Text(highlighted(Prefs.primary(e.sr), p.accent), fontFamily = Serif, fontSize = 17.sp, color = p.text)
                        Text(e.ru, fontSize = 14.sp, color = p.secondary)
                    }
                }
            }
        }
    }
}

// The translation side only: meanings and grammar, with a button that shows the word.
@Composable
private fun RecallBody(w: Word, onReveal: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            "· · ·",
            fontFamily = Serif,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = p.faint,
            modifier = Modifier.padding(end = 40.dp),
        )
        Spacer(Modifier.height(8.dp))
        Grammar(w)
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            w.meanings.forEachIndexed { i, m -> Meaning(i + 1, m) }
        }
        // The Russian side of the examples as a hint; the Serbian sentences would give the word away.
        if (w.examples.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 20.dp), color = p.divider)
            SectionLabel("Как сказать по-сербски")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (e in w.examples) Text("«${e.ru}»", fontSize = 16.sp, color = p.text, lineHeight = 22.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Показать слово",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = p.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(p.highlight)
                .clickable(onClick = onReveal)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}
