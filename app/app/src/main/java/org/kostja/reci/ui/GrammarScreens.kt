package org.kostja.reci.ui

import androidx.compose.foundation.background
import org.kostja.reci.data.State
import org.kostja.reci.data.Repo
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import org.kostja.reci.data.GrammarTopic
import org.kostja.reci.data.Translit
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kostja.reci.data.Grammar
import org.kostja.reci.data.GrammarBlock
import org.kostja.reci.data.Prefs

// {сербский текст} is shown in the chosen script and in the word color, the part after
// "|" inside braces is an ending and gets the accent color; **x** is bold.
fun grammarText(s: String, p: Palette): AnnotatedString = buildAnnotatedString {
    var bold = false
    var i = 0
    while (i < s.length) {
        when {
            s.startsWith("**", i) -> {
                bold = !bold
                i += 2
            }
            s[i] == '{' -> {
                val end = s.indexOf('}', i).takeIf { it > 0 } ?: s.length
                val inner = s.substring(i + 1, end)
                val stem = inner.substringBefore('|')
                val ending = if ('|' in inner) inner.substringAfter('|') else ""
                withStyle(SpanStyle(color = p.word, fontWeight = FontWeight.SemiBold)) {
                    append(Prefs.primary(stem))
                }
                if (ending.isNotEmpty()) {
                    withStyle(SpanStyle(color = p.accent, fontWeight = FontWeight.Bold)) {
                        append(Prefs.primary(ending))
                    }
                }
                i = end + 1
            }
            else -> {
                val next = listOf(s.indexOf("**", i), s.indexOf('{', i)).filter { it >= 0 }.minOrNull() ?: s.length
                if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i, next)) }
                else append(s.substring(i, next))
                i = next
            }
        }
    }
}

@Composable
fun GrammarIndexScreen(open: (String) -> Unit, openCard: (String) -> Unit, back: () -> Unit) {
    Prefs.version.intValue
    Repo.version.intValue
    val p = LocalPalette.current
    val all = Grammar.topics
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    // Comparisons and notes are cards in the dictionary; the reference lists them for browsing.
    val articles = remember(Repo.version.intValue) {
        Repo.words.filter { it.pos == "compare" || it.pos == "note" }.groupBy { it.pos }
    }
    val cards = articles[if (tab == 1) "compare" else "note"].orEmpty().filter { w ->
        q.isEmpty() || w.cyr.lowercase().contains(q) || Translit.toLatin(w.cyr).lowercase().contains(q) ||
            w.meanings.any { it.lowercase().contains(q) } || w.related.any { it.sr.contains(q) }
    }
    val counts = intArrayOf(all.size, articles["compare"].orEmpty().size, articles["note"].orEmpty().size)

    Column(Modifier.fillMaxSize().background(p.bg).systemBarsPadding()) {
        Header("Справочник", back)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Грамматика", "Похожие", "Заметки").forEachIndexed { i, name ->
                SelectChip(if (i == 0) name else "$name · ${counts[i]}", tab == i) { tab = i; query = "" }
            }
        }
        if (tab != 0) {
            Row(
                Modifier
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp)
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(p.card)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("найти по слову или названию", color = p.faint, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = p.text),
                        cursorBrush = SolidColor(p.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f), state = rememberLazyListState(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (tab != 0) {
                items(cards, key = { it.key }) { w ->
                    val state = Repo.progress(w).state
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(p.card)
                            .clickable { openCard(w.key) }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (w.pos == "compare") w.meanings.firstOrNull().orEmpty().ifBlank { w.cyr } else w.cyr,
                            fontSize = 16.sp,
                            color = p.text,
                            modifier = Modifier.weight(1f),
                        )
                        if (state != State.NEW) {
                            Box(
                                Modifier.padding(start = 10.dp).size(8.dp).clip(CircleShape).background(
                                    when (state) {
                                        State.KNOWN -> MarkKnown
                                        State.UNSURE -> MarkUnsure
                                        else -> MarkDontKnow
                                    }
                                )
                            )
                        }
                    }
                }
            }
            // A group's topics are not contiguous in the file, so group them before listing.
            for ((group, topics) in (if (tab == 0) all else emptyList()).groupBy { it.group })
            for (t in topics) {
                if (t === topics.first()) item(key = "g:$group") { GroupTitle(group) }
                item(key = t.id) {
                    Text(
                        t.title,
                        fontSize = 16.sp,
                        color = p.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(p.card)
                            .clickable { open(t.id) }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun GrammarTopicScreen(id: String, open: (String) -> Unit, back: () -> Unit) {
    Prefs.version.intValue
    val p = LocalPalette.current
    val t = Grammar.topic(id)
    Column(Modifier.fillMaxSize().background(p.card).systemBarsPadding()) {
        Header(t?.title ?: "", back)
        if (t == null) return@Column
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            for (b in t.blocks) {
                Block(b)
                Spacer(Modifier.height(12.dp))
            }
            val i = Grammar.topics.indexOfFirst { it.id == id }
            Grammar.topics.getOrNull(i + 1)?.let { next ->
                Text(
                    "Дальше: ${next.title} ›",
                    fontSize = 15.sp,
                    color = p.accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { open(next.id) }
                        .padding(vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun Block(b: GrammarBlock) {
    val p = LocalPalette.current
    when (b.type) {
        "p" -> Text(grammarText(b.text, p), fontSize = 16.sp, color = p.text, lineHeight = 23.sp)
        "tip" -> Text(
            grammarText(b.text, p),
            fontSize = 15.sp,
            color = p.text,
            lineHeight = 22.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(p.highlight.copy(alpha = if (p.dark) 1f else 0.6f), RoundedCornerShape(8.dp))
                .padding(12.dp),
        )
        "list" -> Column {
            for (item in b.items) {
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("•", fontSize = 16.sp, color = p.accent, modifier = Modifier.width(16.dp))
                    Text(grammarText(item, p), fontSize = 16.sp, color = p.text, lineHeight = 22.sp)
                }
            }
        }
        "ex" -> Column(
            Modifier
                .fillMaxWidth()
                .background(p.panel, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(highlighted(Prefs.primary(b.sr), p.accent), fontSize = 16.sp, color = p.text)
            Text(b.ru, fontSize = 14.sp, color = p.secondary)
        }
        "table" -> Table(b.head, b.rows)
    }
}

@Composable
private fun Table(head: List<String>, rows: List<List<String>>) {
    val p = LocalPalette.current
    val all = listOf(head) + rows
    val base = LocalTextStyle.current.merge(TextStyle(fontSize = 14.sp))
    val measurer = rememberTextMeasurer()
    // Per column: the widest word and the whole widest cell, in px.
    val bounds = remember(all, base) {
        List(head.size) { c ->
            var lo = 0
            var hi = 0
            for ((r, row) in all.withIndex()) {
                val text = grammarText(row.getOrElse(c) { "" }, p)
                val style = base.copy(fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal)
                fun width(t: AnnotatedString) = measurer.measure(t, style, softWrap = false).size.width
                hi = maxOf(hi, width(text))
                for (m in Regex("""\S+""").findAll(text.text)) {
                    lo = maxOf(lo, width(text.subSequence(m.range.first, m.range.last + 1)))
                }
            }
            lo to hi
        }
    }
    BoxWithConstraints {
        val density = LocalDensity.current
        val pad = with(density) { 17.dp.roundToPx() }
        val avail = constraints.maxWidth - pad * head.size
        val loSum = bounds.sumOf { it.first }
        val hiSum = bounds.sumOf { it.second }
        // As in HTML tables: no column is narrower than its longest word, so words never
        // break; the spare width goes to the columns with the most text.
        val widths = bounds.map { (lo, hi) ->
            when {
                hiSum <= avail -> hi + (avail - hiSum) * hi / maxOf(hiSum, 1)
                loSum >= avail -> lo
                else -> lo + ((avail - loSum).toLong() * (hi - lo) / (hiSum - loSum)).toInt()
            }
        }.map { with(density) { (it + 1).toDp() } + 16.dp }
        Column(
            Modifier
                .horizontalScroll(rememberScrollState())
                .border(1.dp, p.divider, RoundedCornerShape(8.dp))
        ) {
            for ((r, row) in all.withIndex()) {
                Row(
                    Modifier
                        .height(IntrinsicSize.Min)
                        .background(if (r == 0) p.panel else p.card)
                ) {
                    row.forEachIndexed { c, cell ->
                        Text(
                            grammarText(cell, p),
                            fontSize = 14.sp,
                            fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (r == 0) p.chipText else p.text,
                            modifier = Modifier
                                .width(widths.getOrElse(c) { 88.dp })
                                .fillMaxHeight()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                        if (c < row.size - 1) Spacer(Modifier.width(1.dp).fillMaxHeight().background(p.divider))
                    }
                }
                if (r < rows.size) HorizontalDivider(color = p.divider)
            }
        }
    }
}
