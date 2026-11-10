package org.kostja.reci.ui

import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.kostja.reci.R
import org.kostja.reci.data.Labels
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.State
import org.kostja.reci.data.Telemetry
import org.kostja.reci.data.Word
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CardScreen(
    onNext: () -> Unit,
    // Set on the lock screen: closes the card, while onNext only flips to the next word.
    onDone: (() -> Unit)? = null,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onCategories: () -> Unit,
    onGrammar: (String?) -> Unit,
    // Set on the lock screen, where a report is written in the app after unlocking.
    onReport: ((String) -> Unit)? = null,
    onSchools: () -> Unit,
) {
    Repo.version.intValue
    Prefs.version.intValue
    val p = LocalPalette.current
    val w = Repo.current()

    // Fixed for one showing of a card: answering moves the word to its next stage, but the
    // card on screen keeps the layout it was shown with. A later answer replaces the earlier one.
    val showId = Prefs.cardCount
    val recall = remember(w?.key, showId) { w != null && Repo.asksRecall(w) }
    val stage = remember(w?.key, showId) { w?.let { stageLabel(it) } }
    var revealed by rememberSaveable(w?.key, showId) { mutableStateOf(w != null && Repo.answerOf(w) != null) }
    var answered by remember(w?.key, showId) { mutableStateOf(w?.let { Repo.answerOf(it) }) }
    val fresh = remember(w?.key, showId) {
        w != null && !Repo.isArticle(w) && Repo.shownProgress(w).let { it.phase <= Repo.INTRO && it.streak == 0 }
    }
    var hintCounted by remember(w?.key, showId) { mutableStateOf(false) }
    var hint by remember(w?.key, showId) { mutableStateOf(false) }
    // A long press on «Знаю» moves on at once, so a toast shows that it worked.
    var learnedToast by remember { mutableIntStateOf(0) }
    LaunchedEffect(learnedToast) {
        if (learnedToast > 0) {
            delay(2500)
            learnedToast = 0
        }
    }
    var reporting by remember { mutableStateOf<Word?>(null) }
    val onAnswer = { a: State, learnedNow: Boolean ->
        if (w != null) {
            if (Repo.answer(w, a, learnedNow)) countAnswer(w, a, learnedNow)
            answered = a
        }
        revealed = true
        if (learnedNow) {
            Prefs.hintDone = true
            hint = false
            learnedToast++
            onNext()
        } else if (a == State.KNOWN && fresh && !hintCounted) {
            hintCounted = true
            hint = Prefs.hintAfterNewKnown()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .systemBarsPadding()
    ) {
        TopBar(onSearch, { onGrammar(null) }, onSettings, onCategories)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .shadow(3.dp, RoundedCornerShape(28.dp), clip = false)
                .clip(RoundedCornerShape(28.dp))
                .background(p.card)
        ) {
            if (w == null) {
                if (Repo.isLoaded) {
                    if (Repo.exhausted.value) Empty(onCategories) else LaunchedEffect(Unit) { Repo.step(1) }
                }
                return@Box
            }
            // The lock card is created and resumed while the screen is off; count the word
            // once the screen is actually on, and once only across activity recreation.
            val ctx = LocalContext.current
            var counted by rememberSaveable(w.key, showId) { mutableStateOf(false) }
            LifecycleResumeEffect(w.key, showId) {
                if (!counted && ctx.getSystemService(PowerManager::class.java).isInteractive) {
                    counted = true
                    if (Repo.markShown(w)) Telemetry.count("cards")
                }
                onPauseOrDispose {}
            }
            key(w.key, showId) {
                CardText(
                    top = 22.dp,
                    onReport = { if (onReport != null) onReport(w.key) else reporting = w },
                    onSchools = onSchools,
                    star = { Star(w, Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp)) },
                ) {
                    if (stage != null) {
                        Text(stage, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.accent)
                        Spacer(Modifier.height(6.dp))
                    }
                    WordBody(
                        w,
                        slide = Prefs.cardMode == 2 && !Repo.isArticle(w),
                        onGrammar = onGrammar,
                        hideWord = recall && !revealed,
                        onReveal = { revealed = true },
                    )
                }
            }
            if (learnedToast > 0) LearnedToast(Modifier.align(Alignment.BottomCenter))
        }
        if (onDone == null && w != null && !Telemetry.noticeClosed) StatsNotice()
        if (Prefs.useMarks && w != null) {
            if (hint) LongPressHint { hint = false; Prefs.hintDone = true }
            MarksRow(answered, Repo.isArticle(w), Modifier.padding(horizontal = 14.dp, vertical = 4.dp), onAnswer)
        }
        BottomNav(onPrev = { Repo.step(-1) }, onNext = onNext, onDone = onDone)
    }
    reporting?.let { ReportDialog(it) { reporting = null } }
}

// The scrolling text of a card. The star, the flag and the school button scroll with it; the
// flag and the school button come after the text, in the bottom corner of a short card and at
// the end of a long one.
@Composable
fun CardText(
    top: Dp,
    onReport: () -> Unit,
    onSchools: (() -> Unit)?,
    star: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).heightIn(min = maxHeight)) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = top), content = content)
                star()
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (onSchools != null) SchoolsButton(onClick = onSchools)
                ReportFlag(onClick = onReport)
            }
        }
        // Text cut by the card edge fades out instead of ending mid-line.
        if (scroll.canScrollForward) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, p.card)))
            )
        }
    }
}

// What the user is asked to do with this card, shown above it.
private fun stageLabel(w: Word): String? {
    if (Repo.isArticle(w)) return null
    val pr = Repo.shownProgress(w)
    val what = if (Prefs.cardMode == 2) "перевод" else "слово"
    return when {
        Repo.isDue(pr) -> "Проверка: вспомните $what"
        pr.phase == Repo.RECALL -> "Вспомните $what · «Знаю» ${pr.streak} из 3"
        pr.phase == Repo.LEARNED -> "Выучено"
        pr.state == State.DONT_KNOW || pr.state == State.UNSURE -> "Ещё раз: запомните слово"
        else -> "Новое слово"
    }
}

@Composable
private fun Empty(onCategories: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Здесь пока пусто", fontFamily = Serif, fontSize = 24.sp, color = p.word)
        Spacer(Modifier.height(8.dp))
        Text("В выбранных разделах не осталось слов", fontSize = 15.sp, color = p.secondary)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onCategories,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = Color.White),
        ) { Text("Выбрать слова") }
    }
}

private val BAR_HEIGHT = 44.dp

// On a narrow screen the top bar gets tighter, so its icons keep their room.
@Composable
private fun narrow() = LocalConfiguration.current.screenWidthDp < 400

@Composable
private fun TopBar(onSearch: () -> Unit, onGrammar: () -> Unit, onSettings: () -> Unit, onCategories: () -> Unit) {
    val p = LocalPalette.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // The clock ticks only while the card is visible, so it costs nothing with the screen off.
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000L * (60 - (now / 1000) % 60))
            }
        }
    }
    val formats = remember {
        val ru = Locale.forLanguageTag("ru")
        listOf("HH:mm", "EEE, d MMM").map { SimpleDateFormat(it, ru) }
    }
    val date = Date(now)
    val narrow = narrow()

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (narrow) 16.dp else 20.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(
                formats[0].format(date),
                fontSize = if (narrow) 26.sp else 32.sp,
                fontWeight = FontWeight.Medium,
                color = p.text,
                lineHeight = if (narrow) 28.sp else 34.sp,
                maxLines = 1,
            )
            Text(formats[1].format(date), fontSize = 13.sp, color = p.secondary, maxLines = 1)
        }
        // The font's internal leading puts the clock digits a little below the box top. The
        // icons never shrink: the deck chip gives up its width first.
        Row(
            Modifier.weight(1f, fill = false).padding(start = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeckChip(onCategories, Modifier.weight(1f, fill = false).height(BAR_HEIGHT).widthIn(max = 150.dp))
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier
                    .height(BAR_HEIGHT)
                    .clip(RoundedCornerShape(50))
                    .background(p.card)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(R.drawable.ic_search, "Поиск", onSearch)
                BarIcon(R.drawable.ic_book, "Грамматика", onGrammar)
                BarIcon(R.drawable.ic_settings, "Настройки", onSettings)
            }
        }
    }
}

@Composable
private fun BarIcon(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(if (narrow()) 40.dp else 42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = LocalPalette.current.secondary, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun DeckChip(onCategories: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    val cats = Prefs.categories
    val first = cats.minOrNull()
    val title = when {
        first == null -> "—"
        first.startsWith("L") -> Labels.category(first).substringBefore(' ')
        else -> Labels.category(first)
    }
    val more = if (cats.size > 1) " +${cats.size - 1}" else ""
    // Known words out of all words in the chosen sections, the marked ones included.
    val (known, total) = remember(Repo.version.intValue, cats) {
        var k = 0
        var t = 0
        for (w in Repo.words) {
            if (cats.none { Repo.inCategory(w, it) }) continue
            t++
            if (Repo.progress(w).state == State.KNOWN) k++
        }
        k to t
    }
    // Short of room, the chip drops the count, then the "+N" of further sections, and only
    // then cuts the title.
    val style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Layout(
        content = {
            Text(
                title,
                style = style,
                color = p.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(painterResource(R.drawable.ic_expand_more), null, tint = p.accent, modifier = Modifier.size(18.dp))
            Text(more, style = style, color = p.accent, maxLines = 1)
            if (Repo.isLoaded) Text("  $known/$total", fontSize = 13.sp, color = p.secondary, maxLines = 1)
        },
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(p.card)
            .clickable(onClick = onCategories)
            .padding(start = 14.dp, end = 8.dp),
    ) { items, c ->
        val icon = items[1].measure(Constraints())
        val titleWidth = items[0].maxIntrinsicWidth(c.maxHeight)
        val moreWidth = items[2].maxIntrinsicWidth(c.maxHeight)
        val count = items.getOrNull(3)
            ?.takeIf { titleWidth + moreWidth + it.maxIntrinsicWidth(c.maxHeight) + icon.width <= c.maxWidth }
            ?.measure(Constraints())
        val plus = items[2].takeIf { titleWidth + moreWidth + icon.width <= c.maxWidth }?.measure(Constraints())
        val room = c.maxWidth - icon.width - (count?.width ?: 0) - (plus?.width ?: 0)
        val text = items[0].measure(Constraints(maxWidth = room.coerceAtLeast(0)))
        val height = maxOf(c.minHeight, text.height, icon.height)
        layout(text.width + (plus?.width ?: 0) + (count?.width ?: 0) + icon.width, height) {
            var x = 0
            for (item in listOfNotNull(text, plus, count, icon)) {
                item.placeRelative(x, (height - item.height) / 2)
                x += item.width
            }
        }
    }
}

@Composable
private fun Star(w: Word, modifier: Modifier) {
    val p = LocalPalette.current
    val fav = Repo.progress(w).fav
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable { Repo.toggleFav(w) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (fav) R.drawable.ic_star else R.drawable.ic_star_border),
            "Избранное",
            tint = if (fav) MarkUnsure else p.faint,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun LearnedToast(modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 14.dp)
            .clip(RoundedCornerShape(50))
            .background(LocalPalette.current.known)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Слово отмечено выученным", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LongPressHint(onClose: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.highlight)
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Слово уже знакомо? Удерживайте «Знаю» — оно сразу станет выученным.",
            fontSize = 13.sp,
            color = p.text,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", fontSize = 15.sp, color = p.secondary)
        }
    }
}

// Long-press on «Знаю» marks a word the user already knows as learned at once. Articles are
// read, not learned, so their buttons only say whether to show them again.
@Composable
fun MarksRow(answered: State?, article: Boolean, modifier: Modifier = Modifier, onAnswer: (State, Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(p.card)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Mark(
            if (article) "Перечитать позже" else "Не знаю",
            answered == State.DONT_KNOW,
            if (article) p.secondary else MarkDontKnow,
            Modifier.weight(1f),
        ) { onAnswer(State.DONT_KNOW, false) }
        Mark(
            if (article) "Не показывать" else "Знаю",
            answered == State.KNOWN,
            p.known,
            Modifier.weight(1f),
            onLongClick = if (article) null else ({ onAnswer(State.KNOWN, true) }),
        ) { onAnswer(State.KNOWN, false) }
    }
}

@Composable
private fun Mark(
    label: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) color else Color.Transparent)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) Color.White else color))
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else LocalPalette.current.text,
        )
    }
}

@Composable
private fun BottomNav(onPrev: () -> Unit, onNext: () -> Unit, onDone: (() -> Unit)?) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavCircle(R.drawable.ic_chevron_left, "Предыдущее слово", onPrev)
        Row(
            Modifier
                .weight(1f)
                .height(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(p.accent)
                .clickable(onClick = onDone ?: onNext),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onDone != null) {
                Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Готово", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Дальше", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(R.drawable.ic_arrow_forward), null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        if (onDone != null) NavCircle(R.drawable.ic_chevron_right, "Следующее слово", onNext)
    }
}

@Composable
private fun NavCircle(icon: Int, description: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(p.card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = p.text, modifier = Modifier.size(28.dp))
    }
}
