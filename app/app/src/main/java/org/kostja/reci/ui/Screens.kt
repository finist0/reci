package org.kostja.reci.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kostja.reci.R
import org.kostja.reci.data.Labels
import org.kostja.reci.data.Order
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.State
import org.kostja.reci.data.Translit
import org.kostja.reci.data.Word

@Composable
fun Header(title: String, onBack: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(R.drawable.ic_back, "Назад", onBack)
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            fontFamily = Serif,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = p.word,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun RoundIcon(icon: Int, description: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(p.card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = p.text, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun GroupTitle(title: String) {
    Text(
        title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = LocalPalette.current.accent,
        modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LocalPalette.current.card)
    ) { content() }
}

@Composable
fun SelectChip(text: String, selected: Boolean, dot: Color? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) p.accent else p.card)
            .border(1.dp, if (selected) p.accent else p.divider, RoundedCornerShape(50))
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) Color.White else dot))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (selected) Color.White else p.text)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
fun CategoriesScreen(openList: (String) -> Unit, back: () -> Unit) {
    Repo.version.intValue
    val p = LocalPalette.current
    var sel by remember { mutableStateOf(Prefs.categories) }
    var order by remember { mutableStateOf(Prefs.order) }
    val toggle = { id: String -> sel = if (id in sel) sel - id else sel + id }
    val withKnown = "M:known" in sel
    val total = Repo.words.count { w ->
        sel.any { Repo.inCategory(w, it) } && (withKnown || Repo.progress(w).state != State.KNOWN)
    }
    val counts = remember(Repo.version.intValue) { Repo.counts() }
    val count = { id: String -> counts[id]?.get(0) ?: 0 }
    val known = { id: String -> counts[id]?.get(1) ?: 0 }
    val dots = mapOf("M:dontknow" to MarkDontKnow, "M:known" to p.known, "M:fav" to MarkUnsure)

    Column(Modifier.fillMaxSize().background(p.bg).systemBarsPadding()) {
        Header("Что учим", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            GroupTitle("Уровни")
            Panel {
                Labels.levels.entries.forEachIndexed { i, (level, name) ->
                    if (i > 0) HorizontalDivider(color = p.divider)
                    val id = "L$level"
                    LevelRow(name, known(id), count(id), id in sel, onList = { openList(id) }) { toggle(id) }
                }
            }
            GroupTitle("Разделы")
            Text(
                "Долгое нажатие на раздел, тему или отметку открывает список слов",
                fontSize = 12.sp,
                color = p.secondary,
                modifier = Modifier.padding(start = 26.dp, end = 26.dp, bottom = 8.dp),
            )
            ChipFlow {
                for ((id, name) in Labels.sections) {
                    if (count(id) > 0) SelectChip("$name · ${count(id)}", id in sel, onLongClick = { openList(id) }) { toggle(id) }
                }
            }
            GroupTitle("Мои отметки")
            ChipFlow {
                for ((id, name) in Labels.lists) SelectChip("$name · ${count(id)}", id in sel, dots[id], onLongClick = { openList(id) }) { toggle(id) }
            }
            GroupTitle("Темы")
            ChipFlow {
                for ((code, name) in Labels.topics) {
                    val id = "T:$code"
                    if (count(id) > 0) SelectChip("$name · ${count(id)}", id in sel, onLongClick = { openList(id) }) { toggle(id) }
                }
            }
            GroupTitle("Порядок")
            ChipFlow {
                for (o in Order.entries) SelectChip(o.label, order == o) { order = o }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(
            Modifier
                .padding(14.dp)
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(if (sel.isNotEmpty()) p.accent else p.faint)
                .clickable(enabled = sel.isNotEmpty()) {
                    Prefs.order = order
                    Repo.setCategories(sel)
                    back()
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Учить $total ${plural(total, "карточку", "карточки", "карточек")}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

@Composable
private fun LevelRow(name: String, known: Int, total: Int, selected: Boolean, onList: () -> Unit, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) p.accent else p.highlight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.substringBefore(' '),
                fontFamily = Serif,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else p.accent,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name.substringAfter("· "), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = p.text)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(p.panel)) {
                if (total > 0) {
                    Box(Modifier.fillMaxWidth(known.toFloat() / total).fillMaxHeight().background(p.known))
                }
            }
            Text("знаю $known из $total", fontSize = 12.sp, color = p.secondary, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(p.panel)
                .clickable(onClick = onList),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_chevron_right), "Список слов", tint = p.text, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) p.accent else Color.Transparent)
                .border(2.dp, if (selected) p.accent else p.divider, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

private fun searchMatch(w: Word, q: String, byMeaning: Boolean): Boolean {
    if (byMeaning) return w.meanings.any { it.lowercase().contains(q) }
    return w.cyr.lowercase().startsWith(q) || Translit.toLatin(w.cyr).lowercase().startsWith(q)
}

@Composable
fun SearchScreen(open: (String) -> Unit, back: () -> Unit) {
    val p = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    var byMeaning by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val q = query.trim().lowercase()
    val results = if (q.isEmpty()) emptyList() else Repo.words.filter { searchMatch(it, q, byMeaning) }.take(200)

    Column(Modifier.fillMaxSize().background(p.bg).systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 14.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(R.drawable.ic_back, "Назад", back)
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(p.card)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_search), null, tint = p.faint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(if (byMeaning) "слово по-русски" else "реч, reč", color = p.faint, fontSize = 16.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 16.sp, color = p.text),
                        cursorBrush = SolidColor(p.accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
        }
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip("Сербское слово", !byMeaning) { byMeaning = false }
            SelectChip("Перевод", byMeaning) { byMeaning = true }
        }
        LazyColumn(Modifier.weight(1f), state = rememberLazyListState()) {
            items(results, key = { it.key }) { w ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { open(w.key) }
                        .padding(horizontal = 26.dp, vertical = 11.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val shown = Prefs.primary(w.cyr)
                        Text(
                            buildAnnotatedString {
                                val n = if (!byMeaning && shown.lowercase().startsWith(q)) q.length else 0
                                withStyle(SpanStyle(color = p.accent)) { append(shown.take(n)) }
                                append(shown.drop(n))
                            },
                            fontFamily = Serif,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = p.word,
                            modifier = Modifier.weight(1f),
                        )
                        Text(Labels.levels[w.level]?.substringBefore(' ') ?: "", fontSize = 11.sp, color = p.faint)
                    }
                    Text(
                        w.meanings.joinToString(" · "),
                        fontSize = 14.sp,
                        color = p.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 26.dp), color = p.divider)
            }
        }
    }
}

@Composable
fun DetailScreen(key: String, back: () -> Unit, onGrammar: ((String) -> Unit)? = null) {
    Repo.version.intValue
    Prefs.version.intValue
    val p = LocalPalette.current
    val w = Repo.word(key)
    Column(Modifier.fillMaxSize().background(p.bg).systemBarsPadding()) {
        Header("", back)
        if (w == null) return@Column
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(p.card)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) { WordBody(w, slide = false, onGrammar = onGrammar) }
        var answered by remember(w.key) { mutableStateOf(Repo.answerOf(w)) }
        var learned by remember(w.key) { mutableStateOf(false) }
        if (learned) LearnedToast(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp))
        MarksRow(answered, Repo.isArticle(w), Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) { a, learnedNow ->
            Repo.answer(w, a, learnedNow)
            answered = a
            learned = learnedNow
        }
    }
}

@Composable
fun WordListScreen(category: String, open: (String) -> Unit, back: () -> Unit) {
    Repo.version.intValue
    Prefs.version.intValue
    val p = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember(Repo.version.intValue, category) { Repo.words.filter { Repo.inCategory(it, category) } }
    val q = query.trim().lowercase()
    val shown = if (q.isEmpty()) all else all.filter { w ->
        w.cyr.lowercase().contains(q) || Translit.toLatin(w.cyr).lowercase().contains(q) || w.meanings.any { it.lowercase().contains(q) }
    }

    Column(Modifier.fillMaxSize().background(p.bg).systemBarsPadding()) {
        Header("${Labels.category(category)} · ${all.size}", back)
        Row(
            Modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(p.card)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_search), null, tint = p.faint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("найти в списке", color = p.faint, fontSize = 15.sp)
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
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp), state = rememberLazyListState()) {
            items(shown, key = { it.key }) { w ->
                val state = Repo.progress(w).state
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { open(w.key) }
                        .padding(horizontal = 26.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (w.pos == "compare") w.meanings.firstOrNull().orEmpty().ifBlank { w.cyr } else Prefs.headword(w),
                            fontFamily = Serif,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = p.word,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (w.pos != "note" && w.pos != "compare") {
                            Text(
                                w.meanings.joinToString(" · "),
                                fontSize = 14.sp,
                                color = p.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (state != State.NEW) {
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.size(9.dp).clip(CircleShape).background(
                                when (state) {
                                    State.KNOWN -> p.known
                                    State.UNSURE -> MarkUnsure
                                    else -> MarkDontKnow
                                }
                            )
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 26.dp), color = p.divider)
            }
        }
    }
}
